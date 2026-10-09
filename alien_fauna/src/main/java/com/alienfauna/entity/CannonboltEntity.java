package com.alienfauna.entity;

import com.alienfauna.entity.goal.CannonboltPlayRollGoal;
import com.alienfauna.entity.goal.CannonboltRollAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.event.ForgeEventFactory;
import javax.annotation.Nullable;

/**
 * A gentle, armoured alien that curls into a ball to roll about.
 *
 * Wild, it wanders, and now and then curls up and rolls somewhere for the fun of it (it is dizzy
 * for a moment afterwards). It only fights whoever hurts it, by rolling into them, and is then
 * dizzy and easy to hurt. A melon slice calms an angry one, and a few of them befriend it: a tame
 * Cannonbolt follows its owner, sits on command and rolls into whatever attacks the owner.
 * Nothing needs killing, and it drops nothing.
 */
public class CannonboltEntity extends TamableAnimal {
    private static final EntityDataAccessor<Boolean> ROLLING =
            SynchedEntityData.defineId(CannonboltEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DIZZY =
            SynchedEntityData.defineId(CannonboltEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CURLING =
            SynchedEntityData.defineId(CannonboltEntity.class, EntityDataSerializers.BOOLEAN);

    /** Ticks it takes to lean forward and bring its arms together before it becomes a ball. */
    private static final int CURL_TIME = 10;

    /** The curled-up ball is smaller than the standing body, so curling up always fits. */
    private static final EntityDimensions ROLLING_DIMENSIONS = EntityDimensions.scalable(1.4F, 1.4F);

    private int rollTicks;
    private int curlTicks;
    private int rollCooldown;
    private int dizzyTicks;
    private boolean wantsUnroll;
    private int dizzyAfterUnroll;

    // Client-side animation state (also ticked on the server, which is harmless).
    private float curlAnim, curlAnimO, ballAnim, ballAnimO;
    private float dizzyAnim, dizzyAnimO;
    private float sitAnim, sitAnimO;
    private float rollAngle, rollAngleO;

    public CannonboltEntity(EntityType<? extends CannonboltEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new CannonboltRollAttackGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true) {
            // a baby never fights
            @Override public boolean canUse() { return !isBaby() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isBaby() && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.1D, 8.0F, 3.0F, false));
        this.goalSelector.addGoal(5, new CannonboltPlayRollGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ROLLING, false);
        this.entityData.define(DIZZY, false);
        this.entityData.define(CURLING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (ROLLING.equals(key)) {
            refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return isRolling() ? ROLLING_DIMENSIONS.scale(getScale()) : super.getDimensions(pose);
    }

    // ---- rolling ----

    /** Curls into a ball. Does nothing while sitting or dizzy. */
    public void startRolling() {
        if (isInSittingPose() || isDizzy()) {
            return;
        }
        wantsUnroll = false;
        if (!isRolling() && !isCurling()) {
            // First it leans forward and puts its arms together, standing still; then it is a ball.
            curlTicks = CURL_TIME;
            this.entityData.set(CURLING, true);
            playSound(SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.6F);
        }
    }

    /**
     * Asks the Cannonbolt to uncurl and then stand dizzy for the given time. It stays curled up for
     * as long as there is no room to stand, so it can never end up inside a block.
     */
    public void stopRolling(int dizzyTicksAfterwards) {
        wantsUnroll = true;
        dizzyAfterUnroll = dizzyTicksAfterwards;
    }

    private boolean canUnroll() {
        return level().noCollision(this, getType().getDimensions().scale(getScale()).makeBoundingBox(position()));
    }

    /** The crash at the end of an attack roll. Returns whether the target was hurt. */
    public boolean rollImpact(LivingEntity target) {
        boolean hurt = doHurtTarget(target);
        target.knockback(1.6D, getX() - target.getX(), getZ() - target.getZ());
        playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.6F);
        playSound(SoundEvents.ANVIL_LAND, 0.3F, 0.5F);
        return hurt;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (rollCooldown > 0) rollCooldown--;

        if (isCurling() && --curlTicks <= 0) {
            this.entityData.set(CURLING, false);
            if (wantsUnroll) {
                // told to stop before it finished curling up: it simply straightens again
                wantsUnroll = false;
                dizzyTicks = Math.max(dizzyAfterUnroll, 0);
                dizzyAfterUnroll = 0;
            } else {
                rollTicks = 0;
                setRolling(true);
                playSound(SoundEvents.ANVIL_LAND, 0.4F, 0.6F);
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.7D, getZ(), 10, 0.5D, 0.35D, 0.5D, 0.04D);
                }
            }
        }

        if (isRolling()) {
            rollTicks++;
            if (rollTicks % 5 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.004D) {
                playSound(SoundEvents.IRON_GOLEM_STEP, 0.7F, 0.5F);
            }
            if ((wantsUnroll || rollTicks > 400) && canUnroll()) {
                setRolling(false);
                wantsUnroll = false;
                dizzyTicks = Math.max(dizzyAfterUnroll, 0);
                dizzyAfterUnroll = 0;
                if (dizzyTicks > 0) {
                    playSound(SoundEvents.ARMOR_EQUIP_IRON, 0.8F, 0.5F);
                }
            }
        }

        if (dizzyTicks > 0) {
            dizzyTicks--;
            if (tickCount % 6 == 0 && level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.95D, getZ(),
                        2, 0.3D, 0.1D, 0.3D, 0.05D);
            }
        }
        if (isDizzy() != dizzyTicks > 0) {
            this.entityData.set(DIZZY, dizzyTicks > 0);
        }
    }

    /** A dizzy Cannonbolt reels in place: no goals run, as with a stunned ravager. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isDizzy() || isCurling();
    }

    @Override
    public void tick() {
        super.tick();
        curlAnimO = curlAnim;
        ballAnimO = ballAnim;
        dizzyAnimO = dizzyAnim;
        sitAnimO = sitAnim;
        rollAngleO = rollAngle;
        curlAnim = approach(curlAnim, (isCurling() || isRolling()) ? 1.0F : 0.0F, 1.0F / CURL_TIME);
        ballAnim = approach(ballAnim, isRolling() ? 1.0F : 0.0F, 0.25F);
        dizzyAnim = approach(dizzyAnim, isDizzy() ? 1.0F : 0.0F, 0.1F);
        sitAnim = approach(sitAnim, isInSittingPose() ? 1.0F : 0.0F, 0.08F);
        if (isRolling()) {
            // A ball of radius 0.625 blocks turns 1.6 radians per block travelled.
            rollAngle += (float) Math.hypot(getX() - xo, getZ() - zo) * 1.6F;
        }
    }

    private static float approach(float value, float target, float step) {
        if (value < target) return Math.min(value + step, target);
        return Math.max(value - step, target);
    }

    // ---- combat / physics ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDizzy()) {
            amount *= 1.25F;
        } else if (isRolling() && source.is(DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.5F;
        }
        if (!level().isClientSide) {
            setOrderedToSit(false);
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return super.causeFallDamage(distance, isRolling() ? 0.0F : multiplier, source);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (!isRolling()) {
            playSound(SoundEvents.IRON_GOLEM_STEP, 0.7F, 0.6F);
        }
    }

    // ---- befriending ----

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean food = isFood(held);

        if (level().isClientSide) {
            boolean handled = isTame() ? (isOwnedBy(player) || food) : food;
            return handled ? InteractionResult.CONSUME : InteractionResult.PASS;
        }

        if (food) {
            if (getTarget() != null) {
                // A melon slice calms an angry Cannonbolt, so no fight ever has to be finished.
                consume(player, held);
                calmDown();
                return InteractionResult.SUCCESS;
            }
            if (isTame() && isBaby()) {
                // feeding a tame baby makes it grow up faster
                consume(player, held);
                ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-getAge()));
                gameEvent(net.minecraft.world.level.gameevent.GameEvent.EAT);
                return InteractionResult.SUCCESS;
            }
            if (isTame()) {
                if (getHealth() < getMaxHealth()) {
                    consume(player, held);
                    heal(4.0F);
                    gameEvent(net.minecraft.world.level.gameevent.GameEvent.EAT);
                    return InteractionResult.SUCCESS;
                }
            } else {
                consume(player, held);
                if (this.random.nextInt(3) == 0 && !ForgeEventFactory.onAnimalTame(this, player)) {
                    tame(player);
                    this.navigation.stop();
                    setTarget(null);
                    setOrderedToSit(true);
                    level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    level().broadcastEntityEvent(this, (byte) 6);
                }
                return InteractionResult.SUCCESS;
            }
        }

        if (isTame() && isOwnedBy(player)) {
            // anything else toggles sitting, like a wolf
            setOrderedToSit(!isOrderedToSit());
            this.jumping = false;
            this.navigation.stop();
            setTarget(null);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void consume(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    /** Forgets the attacker and stops rolling. */
    public void calmDown() {
        setTarget(null);
        setLastHurtByMob(null);
        getNavigation().stop();
        stopRolling(0);
        rollCooldown = Math.max(rollCooldown, 200);
        playSound(SoundEvents.COW_AMBIENT, 1.0F, 0.5F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * 0.7D, getZ(),
                    6, 0.5D, 0.4D, 0.5D, 0.0D);
        }
    }

    /** Compares UUIDs, so ownership still resolves when the owner entity cannot be looked up. */
    @Override
    public boolean isOwnedBy(LivingEntity entity) {
        return entity != null && entity.getUUID().equals(getOwnerUUID());
    }

    @Override
    public void setTame(boolean tamed) {
        super.setTame(tamed);
        if (tamed) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(50.0D);
            setHealth(50.0F);
        } else {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
        }
    }

    /** Melon slices: round, sweet and easy to hand over. */
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.MELON_SLICE);
    }

    /** Now and then a natural group includes a baby. Spawn eggs and commands give an adult. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        if (data == null) {
            boolean natural = reason != MobSpawnType.SPAWN_EGG && reason != MobSpawnType.COMMAND;
            data = natural ? new AgeableMob.AgeableMobGroupData(0.15F) : new AgeableMob.AgeableMobGroupData(false);
        }
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    // ---- sounds (cow and iron golem sounds pitched down) ----

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COW_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return (isBaby() ? 1.0F : 0.5F) + (this.random.nextFloat() - this.random.nextFloat()) * 0.08F;
    }

    // ---- state accessors ----

    public boolean isRolling() { return this.entityData.get(ROLLING); }
    private void setRolling(boolean value) { this.entityData.set(ROLLING, value); }
    public boolean isDizzy() { return this.entityData.get(DIZZY); }
    public boolean isCurling() { return this.entityData.get(CURLING); }

    public int getRollCooldown() { return rollCooldown; }
    public void setRollCooldown(int ticks) { this.rollCooldown = ticks; }

    public float getCurlAnim(float partialTick) { return Mth.lerp(partialTick, curlAnimO, curlAnim); }
    public float getBallAnim(float partialTick) { return Mth.lerp(partialTick, ballAnimO, ballAnim); }
    public float getDizzyAnim(float partialTick) { return Mth.lerp(partialTick, dizzyAnimO, dizzyAnim); }
    public float getSitAnim(float partialTick) { return Mth.lerp(partialTick, sitAnimO, sitAnim); }
    public float getRollAngle(float partialTick) { return Mth.lerp(partialTick, rollAngleO, rollAngle); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RollCooldown", rollCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        rollCooldown = tag.getInt("RollCooldown");
    }
}
