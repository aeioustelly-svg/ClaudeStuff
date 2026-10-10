package com.okapimod.okapi.entity;

import com.okapimod.okapi.entity.goal.OkapiDeliverGoal;
import com.okapimod.okapi.entity.goal.OkapiForageGoal;
import com.okapimod.okapi.entity.goal.OkapiSitGoal;
import com.okapimod.okapi.registry.ModEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
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
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * A shy jungle browser with a long tongue. Wild okapis keep away from anyone who is not sneaking and
 * never attack. Leaves fed to one by a sneaking player build trust, and after three gifts it becomes a
 * companion that follows, sits when told to, and forages for its owner: it harvests ripe cocoa pods,
 * sweet berries and glow berries and trims leaves (blocks stay in place), then carries the haul to a
 * nearby barrel or to the owner. Nothing is ever killed, and death drops nothing but the haul.
 */
public class OkapiEntity extends TamableAnimal {
    public static final int TRUST_NEEDED = 3;
    public static final int PACK_SIZE = 9;
    /** Items carried before the okapi heads off to deliver them. */
    public static final int DELIVER_AT = 16;

    private static final EntityDataAccessor<Boolean> TONGUE_OUT =
            SynchedEntityData.defineId(OkapiEntity.class, EntityDataSerializers.BOOLEAN);

    private final SimpleContainer pack = new SimpleContainer(PACK_SIZE);
    private int trust;
    private int forageCooldown = 100;
    private int deliverCooldown;
    private int ticksSinceHarvest;
    private long barrelBlockedUntil;

    // Client-side animation state (also ticked on the server, which is harmless).
    private float tongueAnim, tongueAnimO;
    private float sitAnim, sitAnimO;

    public OkapiEntity(EntityType<? extends OkapiEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OkapiSitGoal(this));
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.6D));
        // Wild adults keep away from anyone who is not sneaking.
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 10.0F, 1.0D, 1.5D,
                player -> !isTame() && !isBaby() && !player.isShiftKeyDown()));
        this.goalSelector.addGoal(4, new OkapiDeliverGoal(this));
        this.goalSelector.addGoal(5, new OkapiForageGoal(this));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0D, 8.0F, 3.0F, false));
        this.goalSelector.addGoal(7, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(11, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TONGUE_OUT, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Trust", this.trust);
        tag.put("Pack", this.pack.createTag());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.trust = tag.getInt("Trust");
        if (tag.contains("Pack", Tag.TAG_LIST)) {
            this.pack.fromTag(tag.getList("Pack", Tag.TAG_COMPOUND));
        }
    }

    // ---- ticking ----

    @Override
    public void tick() {
        super.tick();
        tongueAnimO = tongueAnim;
        sitAnimO = sitAnim;
        tongueAnim = approach(tongueAnim, isTongueOut() ? 1.0F : 0.0F, 0.12F);
        sitAnim = approach(sitAnim, isInSittingPose() ? 1.0F : 0.0F, 0.06F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (forageCooldown > 0) forageCooldown--;
        if (deliverCooldown > 0) deliverCooldown--;
        ticksSinceHarvest++;
    }

    private static float approach(float value, float target, float step) {
        if (value < target) return Math.min(value + step, target);
        return Math.max(value - step, target);
    }

    // ---- size ----

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        EntityDimensions dimensions = super.getDimensions(pose);
        return isBaby() ? dimensions.scale(0.55F) : dimensions;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height * 0.92F;
    }

    // ---- interaction: befriending, healing, sitting and the pack ----

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean client = level().isClientSide;

        if (isBaby()) {
            return super.mobInteract(player, hand);
        }

        if (isTame()) {
            if (!isOwnedBy(player)) {
                return super.mobInteract(player, hand);
            }
            if (isFood(held)) {
                if (getHealth() < getMaxHealth()) {
                    if (!client) {
                        heal(4.0F);
                        usePlayerItem(player, hand, held);
                        playSound(SoundEvents.GENERIC_EAT, 0.6F, 0.8F);
                        gameEvent(GameEvent.EAT);
                    }
                    return InteractionResult.sidedSuccess(client);
                }
                InteractionResult bred = super.mobInteract(player, hand);
                return bred.consumesAction() ? bred : InteractionResult.PASS;
            }
            if (hand == InteractionHand.MAIN_HAND && held.isEmpty()) {
                if (!client) {
                    if (player.isShiftKeyDown()) {
                        handPackTo(player);
                    } else {
                        setOrderedToSit(!isOrderedToSit());
                        this.jumping = false;
                        this.navigation.stop();
                    }
                }
                return InteractionResult.sidedSuccess(client);
            }
            return InteractionResult.PASS;
        }

        if (isFood(held)) {
            if (!player.isShiftKeyDown()) {
                // Offered carelessly, the gift only startles it.
                if (!client) {
                    level().broadcastEntityEvent(this, (byte) 6);
                }
                return InteractionResult.CONSUME;
            }
            if (!client) {
                usePlayerItem(player, hand, held);
                playSound(SoundEvents.GENERIC_EAT, 0.6F, 0.8F);
                gainTrust(player);
            }
            return InteractionResult.sidedSuccess(client);
        }
        return super.mobInteract(player, hand);
    }

    private void gainTrust(Player player) {
        this.trust++;
        this.navigation.stop();
        if (this.trust >= TRUST_NEEDED && !ForgeEventFactory.onAnimalTame(this, player)) {
            tame(player);
            this.trust = 0;
            level().broadcastEntityEvent(this, (byte) 7);
        } else if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2D, getZ(), 4, 0.4D, 0.4D, 0.4D, 0.0D);
        }
    }

    /** Compares UUIDs, so ownership still resolves when the owner entity cannot be looked up. */
    @Override
    public boolean isOwnedBy(LivingEntity entity) {
        return entity != null && entity.getUUID().equals(getOwnerUUID());
    }

    /** Leaves, apples and sweet berries: browse food that tames, heals and feeds calves. */
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.LEAVES) || stack.is(Items.APPLE) || stack.is(Items.SWEET_BERRIES);
    }

    @Override
    public boolean canFallInLove() {
        return isTame() && super.canFallInLove();
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        OkapiEntity baby = ModEntities.OKAPI.get().create(level);
        if (baby != null && getOwnerUUID() != null) {
            baby.setOwnerUUID(getOwnerUUID());
            baby.setTame(true);
        }
        return baby;
    }

    // ---- the pack ----

    public SimpleContainer getPack() {
        return pack;
    }

    public int packCount() {
        int count = 0;
        for (int i = 0; i < pack.getContainerSize(); i++) {
            count += pack.getItem(i).getCount();
        }
        return count;
    }

    public boolean packHasRoom() {
        for (int i = 0; i < pack.getContainerSize(); i++) {
            if (pack.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Puts a harvested item in the pack. Whatever does not fit falls at the okapi's feet. */
    public void stash(ItemStack stack) {
        ItemStack rest = pack.addItem(stack);
        if (!rest.isEmpty()) {
            spawnAtLocation(rest);
        }
        ticksSinceHarvest = 0;
    }

    /** Spits the whole pack out in front of the player (sneak with an empty hand). */
    public void handPackTo(LivingEntity target) {
        List<ItemStack> items = pack.removeAllItems();
        if (items.isEmpty()) {
            playSound(getAmbientSound(), 0.5F, getVoicePitch());
            return;
        }
        for (ItemStack stack : items) {
            spitOut(stack, target.position().add(0.0D, 0.6D, 0.0D));
        }
        playSound(SoundEvents.GENERIC_EAT, 0.6F, 1.2F);
    }

    /** Throws an item from the mouth towards a point. */
    public void spitOut(ItemStack stack, Vec3 towards) {
        Vec3 mouth = getEyePosition().add(getLookAngle().scale(0.6D)).add(0.0D, -0.3D, 0.0D);
        ItemEntity item = new ItemEntity(level(), mouth.x, mouth.y, mouth.z, stack);
        Vec3 push = towards.subtract(mouth).normalize().scale(0.25D).add(0.0D, 0.1D, 0.0D);
        item.setDeltaMovement(push);
        item.setPickUpDelay(20);
        level().addFreshEntity(item);
    }

    /** The haul belongs to the owner, so it is dropped when the okapi dies. Nothing else is. */
    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        Containers.dropContents(level(), blockPosition(), pack);
        pack.clearContent();
    }

    // ---- physics ----

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.COW_STEP, 0.12F, 0.8F);
    }

    // ---- sounds (cow sounds pitched down and kept quiet: okapis are almost silent) ----

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COW_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COW_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COW_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 320;
    }

    @Override
    public float getVoicePitch() {
        float base = isBaby() ? 1.0F : 0.6F;
        return base + (this.random.nextFloat() - this.random.nextFloat()) * 0.08F;
    }

    // ---- state accessors ----

    public boolean isTongueOut() { return this.entityData.get(TONGUE_OUT); }
    public void setTongueOut(boolean value) { this.entityData.set(TONGUE_OUT, value); }

    public int getTrust() { return trust; }
    public void setTrust(int value) { this.trust = value; }

    public int getForageCooldown() { return forageCooldown; }
    public void setForageCooldown(int ticks) { this.forageCooldown = ticks; }
    public int getDeliverCooldown() { return deliverCooldown; }
    public void setDeliverCooldown(int ticks) { this.deliverCooldown = ticks; }
    public int getTicksSinceHarvest() { return ticksSinceHarvest; }
    public void setTicksSinceHarvest(int ticks) { this.ticksSinceHarvest = ticks; }
    public long getBarrelBlockedUntil() { return barrelBlockedUntil; }
    public void setBarrelBlockedUntil(long gameTime) { this.barrelBlockedUntil = gameTime; }

    public float getTongueAnim(float partialTick) { return Mth.lerp(partialTick, tongueAnimO, tongueAnim); }
    public float getSitAnim(float partialTick) { return Mth.lerp(partialTick, sitAnimO, sitAnim); }
}
