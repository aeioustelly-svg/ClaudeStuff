package com.dacianmod.draco.entity;

import com.dacianmod.draco.entity.goal.DracoDiveGoal;
import com.dacianmod.draco.entity.goal.DracoHowlGoal;
import com.dacianmod.draco.entity.goal.DracoPerchGoal;
import com.dacianmod.draco.entity.goal.DracoStalkGoal;
import com.dacianmod.draco.entity.goal.DracoTailLashGoal;
import com.dacianmod.draco.entity.goal.FollowCapWearerGoal;
import com.dacianmod.draco.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * A neutral, wolf-headed flying serpent. Wild, it only fights whoever hurts it.
 * Attacks: diving bite, howl cone, tail lash. Sheds skin periodically (no killing required).
 * Tamed with bones like a wolf: it then follows and defends its owner, perches when told to
 * sit, and its owner can get mamaliga from it with a bowl.
 */
public class DracoEntity extends TamableAnimal implements FlyingAnimal {
    private static final EntityDataAccessor<Boolean> HOWLING =
            SynchedEntityData.defineId(DracoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DIVING =
            SynchedEntityData.defineId(DracoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LASHING =
            SynchedEntityData.defineId(DracoEntity.class, EntityDataSerializers.BOOLEAN);

    private int diveCooldown;
    private int howlCooldown;
    private int lashCooldown;
    private int shedTimer;
    private boolean recovering;
    private int polentaCooldown;

    // Client-side animation state (also ticked on the server, which is harmless).
    private float howlAnim, howlAnimO;
    private float diveAnim, diveAnimO;
    private float lashAnim, lashAnimO;
    private float sitAnim, sitAnimO;
    private float bodyPitch, bodyPitchO;

    public DracoEntity(EntityType<? extends DracoEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.shedTimer = nextShedTime();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new DracoPerchGoal(this));
        this.goalSelector.addGoal(0, new DracoDiveGoal(this));
        this.goalSelector.addGoal(1, new DracoHowlGoal(this));
        this.goalSelector.addGoal(2, new DracoTailLashGoal(this));
        this.goalSelector.addGoal(3, new DracoStalkGoal(this));
        this.goalSelector.addGoal(4, new FollowCapWearerGoal(this));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0D, 8.0F, 3.0F, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HOWLING, false);
        this.entityData.define(DIVING, false);
        this.entityData.define(LASHING, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ShedTimer", this.shedTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ShedTimer")) {
            this.shedTimer = tag.getInt("ShedTimer");
        }
    }

    // ---- ticking ----

    @Override
    public void tick() {
        super.tick();

        howlAnimO = howlAnim;
        diveAnimO = diveAnim;
        lashAnimO = lashAnim;
        howlAnim = approach(howlAnim, isHowling() ? 1.0F : 0.0F, 0.2F);
        diveAnim = approach(diveAnim, isDiving() ? 1.0F : 0.0F, 0.25F);
        lashAnim = approach(lashAnim, isLashing() ? 1.0F : 0.0F, 0.15F);
        sitAnimO = sitAnim;
        sitAnim = approach(sitAnim, isInSittingPose() ? 1.0F : 0.0F, 0.08F);

        bodyPitchO = bodyPitch;
        Vec3 motion = getDeltaMovement();
        double horizontal = motion.horizontalDistance();
        float targetPitch = 0.0F;
        if (horizontal + Math.abs(motion.y) > 0.05D) {
            targetPitch = Mth.clamp((float) Math.toDegrees(Math.atan2(-motion.y, horizontal)), -70.0F, 70.0F);
        }
        bodyPitch += (targetPitch - bodyPitch) * 0.15F;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (diveCooldown > 0) diveCooldown--;
        if (howlCooldown > 0) howlCooldown--;
        if (lashCooldown > 0) lashCooldown--;
        if (polentaCooldown > 0) polentaCooldown--;

        if (--shedTimer <= 0) {
            shed();
        }

        // The wind whistles through the head faster it flies.
        double speed = getDeltaMovement().length();
        if (speed > 0.3D && tickCount % 10 == 0) {
            playSound(SoundEvents.ELYTRA_FLYING, (float) Math.min(1.5D, speed * 1.5D), 0.7F + (float) speed * 0.5F);
        }
    }

    private void shed() {
        spawnAtLocation(new ItemStack(ModItems.SHED_SKIN.get()));
        playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.6F);
        shedTimer = nextShedTime();
    }

    private int nextShedTime() {
        return 6000 + this.random.nextInt(6000);
    }

    public void setShedTimer(int ticks) {
        this.shedTimer = ticks;
    }

    /** Called every tick by FollowCapWearerGoal so a companion Draco sheds about three times as fast. */
    public void boostShedding() {
        shedTimer -= 2;
    }

    private static float approach(float value, float target, float step) {
        if (value < target) return Math.min(value + step, target);
        return Math.max(value - step, target);
    }

    public static boolean isWearingCap(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.DACIAN_FELT_CAP.get());
    }

    // ---- combat / physics ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (recovering) {
            amount *= 1.25F;
        }
        if (!level().isClientSide) {
            setOrderedToSit(false);
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }


    // ---- taming, sitting and the polenta trade ----

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (level().isClientSide) {
            boolean handled = isTame() ? isOwnedBy(player) : (held.is(Items.BONE) && getTarget() == null);
            return handled ? InteractionResult.CONSUME : InteractionResult.PASS;
        }

        if (isTame()) {
            if (!isOwnedBy(player)) {
                return super.mobInteract(player, hand);
            }
            if (held.is(Items.BOWL) && getTarget() == null) {
                // only a tamed Draco gives mamaliga, and only to its owner
                if (polentaCooldown > 0) {
                    return InteractionResult.CONSUME;
                }
                if (this.random.nextFloat() < getPolentaChance(player)) {
                    givePolenta(player, held);
                } else {
                    polentaCooldown = 40;
                    playSound(SoundEvents.WOLF_WHINE, 0.8F, 0.7F);
                }
                return InteractionResult.CONSUME;
            }
            if (isFood(held) && getHealth() < getMaxHealth()) {
                heal(held.getFoodProperties(this).getNutrition());
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                gameEvent(net.minecraft.world.level.gameevent.GameEvent.EAT);
                return InteractionResult.SUCCESS;
            }
            // anything else toggles sitting, like a wolf
            setOrderedToSit(!isOrderedToSit());
            this.jumping = false;
            this.navigation.stop();
            setTarget(null);
            return InteractionResult.SUCCESS;
        }

        if (held.is(Items.BONE) && getTarget() == null) {
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
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
        return super.mobInteract(player, hand);
    }

    /** Compares UUIDs, so ownership still resolves when the owner entity cannot be looked up. */
    @Override
    public boolean isOwnedBy(net.minecraft.world.entity.LivingEntity entity) {
        return entity != null && entity.getUUID().equals(getOwnerUUID());
    }

    @Override
    public void setTame(boolean tamed) {
        super.setTame(tamed);
        if (tamed) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
            setHealth(40.0F);
        } else {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(30.0D);
        }
    }

    /** Meat heals a tamed Draco. Mamaliga is for people. */
    @Override
    public boolean isFood(ItemStack stack) {
        var food = stack.getFoodProperties(this);
        return food != null && food.isMeat();
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, net.minecraft.world.level.LevelReader level) {
        return 0.0F;
    }

    /** 30 percent, doubled for a player wearing the Dacian felt cap. */
    public float getPolentaChance(Player player) {
        return isWearingCap(player) ? 0.6F : 0.3F;
    }

    public void givePolenta(Player player, ItemStack bowls) {
        if (!player.getAbilities().instabuild) {
            bowls.shrink(1);
        }
        ItemStack polenta = new ItemStack(ModItems.MAMALIGA.get());
        if (!player.getInventory().add(polenta)) {
            player.drop(polenta, false);
        }
        polentaCooldown = 1200;
        playSound(SoundEvents.WOLF_PANT, 1.0F, 0.8F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.6D, getZ(), 6, 0.5D, 0.3D, 0.5D, 0.0D);
        }
    }

    public int getPolentaCooldown() { return polentaCooldown; }

    // ---- sounds (wolf sounds pitched down so the Draco is related to, but not, a wolf) ----

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.65F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F;
    }

    // ---- state accessors ----

    public boolean isHowling() { return this.entityData.get(HOWLING); }
    public void setHowling(boolean value) { this.entityData.set(HOWLING, value); }
    public boolean isDiving() { return this.entityData.get(DIVING); }
    public void setDiving(boolean value) { this.entityData.set(DIVING, value); }
    public boolean isLashing() { return this.entityData.get(LASHING); }
    public void setLashing(boolean value) { this.entityData.set(LASHING, value); }

    public boolean isRecovering() { return recovering; }
    public void setRecovering(boolean value) { this.recovering = value; }

    public int getDiveCooldown() { return diveCooldown; }
    public void setDiveCooldown(int ticks) { this.diveCooldown = ticks; }
    public int getHowlCooldown() { return howlCooldown; }
    public void setHowlCooldown(int ticks) { this.howlCooldown = ticks; }
    public int getLashCooldown() { return lashCooldown; }
    public void setLashCooldown(int ticks) { this.lashCooldown = ticks; }

    public float getHowlAnim(float partialTick) { return Mth.lerp(partialTick, howlAnimO, howlAnim); }
    public float getDiveAnim(float partialTick) { return Mth.lerp(partialTick, diveAnimO, diveAnim); }
    public float getLashAnim(float partialTick) { return Mth.lerp(partialTick, lashAnimO, lashAnim); }
    public float getSitAnim(float partialTick) { return Mth.lerp(partialTick, sitAnimO, sitAnim); }
    public float getBodyPitch(float partialTick) { return Mth.lerp(partialTick, bodyPitchO, bodyPitch); }
}
