package com.ghostfreakmod.ghostfreak.entity;

import com.ghostfreakmod.ghostfreak.entity.goal.GhostfreakAttackGoal;
import com.ghostfreakmod.ghostfreak.entity.goal.GhostfreakAvoidLightGoal;
import com.ghostfreakmod.ghostfreak.entity.goal.GhostfreakDanceGoal;
import com.ghostfreakmod.ghostfreak.entity.goal.GhostfreakFollowOwnerGoal;
import com.ghostfreakmod.ghostfreak.entity.goal.GhostfreakSitGoal;
import com.ghostfreakmod.ghostfreak.entity.goal.GhostfreakWanderGoal;
import com.ghostfreakmod.ghostfreak.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * An Ectonurite, the ghost species of the Ghostfreak. Wild ones are hostile, but only in the
 * dark: they flee light, they stop at once when music plays, and an offering of soul sand calms
 * them and may tame them. A tame one follows, sits or wanders, dances to music and gives
 * ectoplasm to its owner.
 *
 * It turns see-through (phased) when it has to cross a wall or is fleeing light, passes through
 * blocks while phased, and unfurls tentacles to fight or to dance.
 *
 * Nothing about it has to be killed: it drops nothing.
 */
public class GhostfreakEntity extends TamableAnimal {
    public static final int MODE_FOLLOW = 0;
    public static final int MODE_SIT = 1;
    public static final int MODE_WANDER = 2;

    /** A wild Ghostfreak is uncomfortable at this light level and above. */
    public static final int LIGHT_LIMIT = 9;
    /** A wild Ghostfreak that stays at this level or above fades away for good. */
    public static final int LIGHT_FATAL = 12;
    public static final int LIGHT_FATAL_TICKS = 400;

    private static final int CALM_TICKS = 600;
    private static final int ECTOPLASM_COOLDOWN = 1200;
    private static final float TAME_CHANCE = 1.0F / 3.0F;

    private static final EntityDataAccessor<Boolean> PHASED =
            SynchedEntityData.defineId(GhostfreakEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> REVEALED =
            SynchedEntityData.defineId(GhostfreakEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LASHING =
            SynchedEntityData.defineId(GhostfreakEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DANCING =
            SynchedEntityData.defineId(GhostfreakEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> MODE =
            SynchedEntityData.defineId(GhostfreakEntity.class, EntityDataSerializers.INT);

    private int calmTicks;
    private int danceTicks;
    private int ectoplasmCooldown;
    private int phaseHold;
    private int strikeTicks;
    private int revealHold;
    private int lightExposure;
    private boolean fleeingLight;

    // Animation state, ticked on both sides.
    private float alpha = 1.0F, alphaO = 1.0F;
    private float reveal, revealO;
    private float strike, strikeO;
    private float dance, danceO;
    private float sitAnim, sitAnimO;
    private float speed, speedO;

    public GhostfreakEntity(EntityType<? extends GhostfreakEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.FLYING_SPEED, 0.45D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new GhostfreakDanceGoal(this));
        this.goalSelector.addGoal(1, new GhostfreakSitGoal(this));
        this.goalSelector.addGoal(2, new GhostfreakAvoidLightGoal(this));
        this.goalSelector.addGoal(3, new GhostfreakAttackGoal(this));
        this.goalSelector.addGoal(4, new GhostfreakFollowOwnerGoal(this));
        this.goalSelector.addGoal(5, new GhostfreakWanderGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        // Wild ones hunt players in the dark. They sense them through walls, so no line of sight is needed.
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                player -> !isTame() && !isCalm() && !isInLight()));
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
        this.entityData.define(PHASED, false);
        this.entityData.define(REVEALED, false);
        this.entityData.define(LASHING, false);
        this.entityData.define(DANCING, false);
        this.entityData.define(MODE, MODE_FOLLOW);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Mode", getMode());
        tag.putInt("EctoplasmCooldown", ectoplasmCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setMode(tag.getInt("Mode"));
        ectoplasmCooldown = tag.getInt("EctoplasmCooldown");
    }

    // ---- ticking ----

    @Override
    public void tick() {
        this.noPhysics = isPhased();
        super.tick();

        alphaO = alpha;
        revealO = reveal;
        strikeO = strike;
        danceO = dance;
        sitAnimO = sitAnim;
        speedO = speed;
        alpha = approach(alpha, isPhased() ? 0.2F : 1.0F, 0.06F);
        reveal = approach(reveal, isRevealed() || isDancing() ? 1.0F : 0.0F, 0.08F);
        strike = approach(strike, isLashing() ? 1.0F : 0.0F, 0.34F);
        dance = approach(dance, isDancing() ? 1.0F : 0.0F, 0.1F);
        sitAnim = approach(sitAnim, getMode() == MODE_SIT && !isDancing() ? 1.0F : 0.0F, 0.08F);
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        float travelled = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.3F;
        speed += (Mth.clamp(travelled, 0.0F, 1.0F) - speed) * 0.2F;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        if (ectoplasmCooldown > 0) ectoplasmCooldown--;
        if (calmTicks > 0) calmTicks--;
        if (revealHold > 0) revealHold--;
        if (strikeTicks > 0 && --strikeTicks == 0) {
            setLashing(false);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        updateDancing();
        updateReveal();
        updatePhase();
        updateLightExposure();
    }

    /** Looks for music once a second: a playing jukebox within a few blocks. Note blocks arrive through an event. */
    private void updateDancing() {
        if (tickCount % 20 == 0 && findJukebox()) {
            danceFor(60);
        }
        if (danceTicks > 0) {
            danceTicks--;
        }
        boolean dancing = danceTicks > 0;
        if (dancing != isDancing()) {
            this.entityData.set(DANCING, dancing);
            if (dancing) {
                setTarget(null);
            }
        }
    }

    private boolean findJukebox() {
        BlockPos center = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -3, -6), center.offset(6, 3, 6))) {
            if (level().getBlockState(pos).is(Blocks.JUKEBOX)
                    && level().getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox
                    && jukebox.isRecordPlaying()) {
                return true;
            }
        }
        return false;
    }

    private void updateReveal() {
        LivingEntity target = getTarget();
        boolean fighting = target != null && target.isAlive() && !isCalm() && distanceToSqr(target) < 144.0D;
        if (fighting) {
            revealHold = 40;
        }
        boolean revealed = revealHold > 0;
        if (revealed != isRevealed()) {
            this.entityData.set(REVEALED, revealed);
            if (revealed) {
                playSound(SoundEvents.PHANTOM_SWOOP, 0.8F, 0.7F);
            }
        }
    }

    /**
     * Phased means see-through and able to cross blocks. It is switched on when something solid
     * is in the way of where the Ghostfreak wants to go, when it is fleeing light, or when it
     * is already inside a block, and it is held for a second so it does not flicker. It is never
     * switched off while the hitbox is inside a block.
     */
    private void updatePhase() {
        boolean inside = !level().noCollision(this, getBoundingBox());
        boolean want = inside || fleeingLight || (moveControl.hasWanted() && isBlockedToward(
                new Vec3(moveControl.getWantedX(), moveControl.getWantedY(), moveControl.getWantedZ())));
        if (want) {
            phaseHold = 20;
        } else if (phaseHold > 0) {
            phaseHold--;
        }
        setPhased(want || phaseHold > 0);
    }

    private boolean isBlockedToward(Vec3 wanted) {
        Vec3 from = position().add(0.0D, getBbHeight() * 0.5D, 0.0D);
        Vec3 to = new Vec3(wanted.x, wanted.y + getBbHeight() * 0.5D, wanted.z);
        if (level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() != HitResult.Type.MISS) {
            return true;
        }
        Vec3 ahead = to.subtract(from);
        if (ahead.lengthSqr() < 1.0E-4D) {
            return false;
        }
        return !level().noCollision(this, getBoundingBox().move(ahead.normalize().scale(0.9D)));
    }

    /** A wild one that lingers in bright light fades away, so it cannot be trapped and farmed. */
    public void updateLightExposure() {
        if (isTame()) {
            return;
        }
        if (lightAt() >= LIGHT_FATAL) {
            if (++lightExposure >= LIGHT_FATAL_TICKS && !hasCustomName()) {
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0D, getZ(), 20, 0.3D, 0.8D, 0.3D, 0.02D);
                }
                discard();
            }
        } else if (lightExposure > 0) {
            lightExposure--;
        }
    }

    private void clientParticles() {
        if (isPhased() && this.random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.SOUL, getRandomX(0.6D), getRandomY(), getRandomZ(0.6D), 0.0D, 0.03D, 0.0D);
        }
        if (isDancing() && tickCount % 8 == 0) {
            level().addParticle(ParticleTypes.NOTE, getX(), getY() + getBbHeight() + 0.3D, getZ(),
                    this.random.nextInt(24) / 24.0D, 0.0D, 0.0D);
        }
    }

    private static float approach(float value, float target, float step) {
        if (value < target) return Math.min(value + step, target);
        return Math.max(value - step, target);
    }

    // ---- light ----

    /** Light level around the middle of the body. */
    public int lightAt() {
        return level().getMaxLocalRawBrightness(BlockPos.containing(getX(), getY() + 1.0D, getZ()));
    }

    public boolean isInLight() {
        return lightAt() >= LIGHT_LIMIT;
    }

    // ---- combat / physics ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isPhased() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;                                   // nothing solid to hit
        }
        return super.hurt(source, amount);
    }

    @Override
    public void setTarget(LivingEntity target) {
        if (target != null && isCalm()) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        setLashing(true);
        strikeTicks = 10;
        playSound(SoundEvents.PHANTOM_BITE, 1.0F, 0.6F);
        return super.doHurtTarget(target);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof Creeper || target instanceof Ghast) {
            return false;
        }
        if (target instanceof GhostfreakEntity other) {
            return !other.isTame() || other.getOwner() != owner;
        }
        if (target instanceof Player player && owner instanceof Player ownerPlayer && !ownerPlayer.canHarmPlayer(player)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isPushable() {
        return !isPhased() && super.isPushable();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    // ---- taming, modes and ectoplasm ----

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean soul = isSoulOffering(held);

        if (level().isClientSide) {
            boolean handled = isTame() ? isOwnedBy(player) : soul;
            return handled ? InteractionResult.CONSUME : InteractionResult.PASS;
        }

        if (isTame()) {
            if (!isOwnedBy(player)) {
                return super.mobInteract(player, hand);
            }
            if (held.is(Items.GLASS_BOTTLE)) {
                return giveEctoplasm(player, held) ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
            }
            if (soul && getHealth() < getMaxHealth()) {
                heal(6.0F);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level().broadcastEntityEvent(this, (byte) 7);
                return InteractionResult.SUCCESS;
            }
            if (held.isEmpty()) {
                cycleMode(player);
                return InteractionResult.SUCCESS;
            }
            return super.mobInteract(player, hand);
        }

        if (soul) {
            offerSoul(player, held, TAME_CHANCE);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public static boolean isSoulOffering(ItemStack stack) {
        return stack.is(Items.SOUL_SAND) || stack.is(Items.SOUL_SOIL);
    }

    /**
     * Takes an offering of soul sand or soul soil. It always calms a wild one for half a minute,
     * even when it refuses to be tamed, so there is a peaceful way past a hostile Ghostfreak.
     */
    public void offerSoul(Player player, ItemStack stack, float chance) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        calmTicks = CALM_TICKS;
        setTarget(null);
        if (this.random.nextFloat() < chance && !ForgeEventFactory.onAnimalTame(this, player)) {
            tame(player);
            setPersistenceRequired();
            setMode(MODE_FOLLOW);
            this.navigation.stop();
            level().broadcastEntityEvent(this, (byte) 7);
        } else {
            level().broadcastEntityEvent(this, (byte) 6);
        }
    }

    /** Hands over a bottle of ectoplasm. Returns false while it is still recovering. */
    public boolean giveEctoplasm(Player player, ItemStack bottles) {
        if (ectoplasmCooldown > 0) {
            playSound(SoundEvents.SOUL_ESCAPE, 0.6F, 0.6F);
            return false;
        }
        if (!player.getAbilities().instabuild) {
            bottles.shrink(1);
        }
        ItemStack ectoplasm = new ItemStack(ModItems.ECTOPLASM_BOTTLE.get());
        if (!player.getInventory().add(ectoplasm)) {
            player.drop(ectoplasm, false);
        }
        ectoplasmCooldown = ECTOPLASM_COOLDOWN;
        playSound(SoundEvents.BOTTLE_FILL, 1.0F, 0.7F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.4D, getZ(), 8, 0.3D, 0.4D, 0.3D, 0.02D);
        }
        return true;
    }

    /** Follow, then sit, then wander, then follow again. */
    public void cycleMode(Player player) {
        setMode((getMode() + 1) % 3);
        this.navigation.stop();
        setTarget(null);
        player.displayClientMessage(Component.translatable("message.ghostfreak.mode." + getMode(), getDisplayName()), true);
    }

    public int getMode() {
        return this.entityData.get(MODE);
    }

    public void setMode(int mode) {
        mode = Mth.clamp(mode, MODE_FOLLOW, MODE_WANDER);
        this.entityData.set(MODE, mode);
        setOrderedToSit(mode == MODE_SIT);
        setInSittingPose(mode == MODE_SIT);
    }

    @Override
    public boolean isOwnedBy(LivingEntity entity) {
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

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    // ---- music ----

    /** Called for a playing jukebox found nearby and for note blocks heard nearby. */
    public void danceFor(int ticks) {
        danceTicks = Math.max(danceTicks, ticks);
    }

    /** True while a sacrifice of soul sand or music has settled it. */
    public boolean isCalm() {
        return calmTicks > 0 || danceTicks > 0 || isDancing();
    }

    public int getCalmTicks() { return calmTicks; }

    // ---- sounds ----

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + (this.random.nextFloat() - this.random.nextFloat()) * 0.08F;
    }

    // ---- state accessors ----

    public boolean isPhased() { return this.entityData.get(PHASED); }

    public void setPhased(boolean value) {
        if (value != isPhased()) {
            this.entityData.set(PHASED, value);
            this.noPhysics = value;
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE,
                    0.5F, value ? 0.7F : 1.1F);
        }
    }

    public boolean isRevealed() { return this.entityData.get(REVEALED); }
    public void setRevealed(boolean value) {
        if (value) revealHold = Math.max(revealHold, 40);
        this.entityData.set(REVEALED, value);
    }
    public boolean isLashing() { return this.entityData.get(LASHING); }
    public void setLashing(boolean value) { this.entityData.set(LASHING, value); }
    public boolean isDancing() { return this.entityData.get(DANCING); }

    public boolean isFleeingLight() { return fleeingLight; }
    public void setFleeingLight(boolean value) { this.fleeingLight = value; }

    public void setLightExposure(int ticks) { this.lightExposure = ticks; }
    public int getEctoplasmCooldown() { return ectoplasmCooldown; }
    public void setEctoplasmCooldown(int ticks) { this.ectoplasmCooldown = ticks; }

    public float getAlpha(float partialTick) { return Mth.lerp(partialTick, alphaO, alpha); }
    public float getRevealAnim(float partialTick) { return Mth.lerp(partialTick, revealO, reveal); }
    public float getStrikeAnim(float partialTick) { return Mth.lerp(partialTick, strikeO, strike); }
    public float getDanceAnim(float partialTick) { return Mth.lerp(partialTick, danceO, dance); }
    public float getSitAnim(float partialTick) { return Mth.lerp(partialTick, sitAnimO, sitAnim); }
    public float getFlySpeed(float partialTick) { return Mth.lerp(partialTick, speedO, speed); }
}
