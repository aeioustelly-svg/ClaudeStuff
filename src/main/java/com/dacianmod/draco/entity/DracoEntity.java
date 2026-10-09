package com.dacianmod.draco.entity;

import com.dacianmod.draco.entity.goal.DracoDiveGoal;
import com.dacianmod.draco.entity.goal.DracoHowlGoal;
import com.dacianmod.draco.entity.goal.DracoStalkGoal;
import com.dacianmod.draco.entity.goal.DracoTailLashGoal;
import com.dacianmod.draco.entity.goal.FollowCapWearerGoal;
import com.dacianmod.draco.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A neutral, wolf-headed flying serpent. It only fights whoever hurts it.
 * Attacks: diving bite, howl cone, tail lash. Sheds skin periodically (no killing required).
 */
public class DracoEntity extends PathfinderMob implements FlyingAnimal {
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

    // Client-side animation state (also ticked on the server, which is harmless).
    private float howlAnim, howlAnimO;
    private float diveAnim, diveAnimO;
    private float lashAnim, lashAnimO;
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
        this.goalSelector.addGoal(0, new DracoDiveGoal(this));
        this.goalSelector.addGoal(1, new DracoHowlGoal(this));
        this.goalSelector.addGoal(2, new DracoTailLashGoal(this));
        this.goalSelector.addGoal(3, new DracoStalkGoal(this));
        this.goalSelector.addGoal(4, new FollowCapWearerGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
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
    public float getBodyPitch(float partialTick) { return Mth.lerp(partialTick, bodyPitchO, bodyPitch); }
}
