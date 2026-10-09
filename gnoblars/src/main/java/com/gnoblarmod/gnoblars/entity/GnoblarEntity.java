package com.gnoblarmod.gnoblars.entity;

import com.gnoblarmod.gnoblars.Gnoblars;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarAvoidMonstersGoal;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarPesterGoal;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarScavengeGoal;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarSniffGoal;
import com.gnoblarmod.gnoblars.registry.ModItems;
import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * A tiny, big-nosed scavenger. Harmless and a nuisance: wild gnoblars follow players around,
 * squeak, run from monsters and carry off dropped items. Kindness tames them: every gift of junk
 * food earns trust, and at {@link #TAME_TRUST} the gnoblar becomes a friend that follows its owner,
 * sits on command and sniffs up bits of scrap from the ground. Nothing here needs any fighting.
 */
public class GnoblarEntity extends TamableAnimal {
    public static final int TAME_TRUST = 4;
    /** A held item is dropped again after this many ticks (5 minutes) if nobody trades for it. */
    public static final int HOARD_BORED_TICKS = 6000;
    public static final ResourceLocation SNIFF_LOOT = new ResourceLocation(Gnoblars.MODID, "gameplay/gnoblar_sniffing");

    private static final EntityDataAccessor<Integer> TRUST =
            SynchedEntityData.defineId(GnoblarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SNIFFING =
            SynchedEntityData.defineId(GnoblarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SCARED =
            SynchedEntityData.defineId(GnoblarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WART =
            SynchedEntityData.defineId(GnoblarEntity.class, EntityDataSerializers.BOOLEAN);

    private int sniffCooldown = 1200;
    private int pesterCooldown;
    private int hoardTicks;
    private int sniffsCompleted;

    public GnoblarEntity(EntityType<? extends GnoblarEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new GnoblarAvoidMonstersGoal(this));
        goalSelector.addGoal(3, new PanicGoal(this, 1.5D));
        goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0D, 5.0F, 2.0F, false));
        goalSelector.addGoal(5, new GnoblarSniffGoal(this));
        goalSelector.addGoal(6, new GnoblarScavengeGoal(this));
        goalSelector.addGoal(7, new GnoblarPesterGoal(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TRUST, 0);
        entityData.define(SNIFFING, false);
        entityData.define(SCARED, false);
        entityData.define(WART, false);
    }

    // ---- gifts, trust and taming -------------------------------------------------------------

    /** Junk food that gnoblars love. Every item here has a peaceful source (fishing, farming, crafting). */
    private static final List<Item> LIKED = List.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
            Items.BONE, Items.DRIED_KELP, Items.BROWN_MUSHROOM);

    public static boolean isGift(ItemStack stack) {
        return giftValue(stack) > 0;
    }

    /** Trust earned by one item: a nose pickle counts double. */
    public static int giftValue(ItemStack stack) {
        if (stack.is(ModItems.NOSE_PICKLE.get())) {
            return 2;
        }
        return LIKED.contains(stack.getItem()) ? 1 : 0;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return isGift(stack);
    }

    /** Gnoblars never breed, so gifts must not start love mode. */
    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean gift = isGift(held);

        if (isTame()) {
            if (!isOwnedBy(player)) {
                return InteractionResult.PASS;
            }
            if (gift && getHealth() < getMaxHealth()) {
                if (!level().isClientSide) {
                    heal(2.0F * giftValue(held));
                    consume(player, held);
                    hearts(3);
                    playSound(SoundEvents.VILLAGER_YES, 1.0F, getVoicePitch());
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (!level().isClientSide) {
                setOrderedToSit(!isOrderedToSit());
                setJumping(false);
                navigation.stop();
                setTarget(null);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (!gift) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            acceptGift(player, held);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private void consume(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private void acceptGift(Player player, ItemStack stack) {
        int value = giftValue(stack);
        consume(player, stack);
        returnHoard();
        setTrust(getTrust() + value);
        hearts(2 + value);
        playSound(SoundEvents.VILLAGER_YES, 1.0F, getVoicePitch());
        if (getTrust() >= TAME_TRUST) {
            befriend(player);
        }
    }

    private void befriend(Player player) {
        setTame(true);
        setOwnerUUID(player.getUUID());
        setOrderedToSit(false);
        navigation.stop();
        setTarget(null);
        level().broadcastEntityEvent(this, (byte) 7);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger(serverPlayer, this);
        }
    }

    private void hearts(int count) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART, getX(), getY(0.9D), getZ(), count, 0.25D, 0.2D, 0.25D, 0.02D);
        }
    }

    /** Compares UUIDs, so ownership still resolves when the owner entity cannot be looked up. */
    @Override
    public boolean isOwnedBy(LivingEntity entity) {
        return entity != null && entity.getUUID().equals(getOwnerUUID());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (!level().isClientSide) {
            if (isTame()) {
                setOrderedToSit(false);
            } else if (source.getEntity() instanceof Player) {
                setTrust(0);   // unkindness is remembered
            }
        }
        return super.hurt(source, amount);
    }

    // ---- hoarding ----------------------------------------------------------------------------

    public boolean isHoarding() {
        return !getMainHandItem().isEmpty();
    }

    /** Carries one item in its hand until it is traded for a gift, gets bored, or dies. */
    public void hoard(ItemStack stack) {
        setItemSlot(EquipmentSlot.MAINHAND, stack);
        setGuaranteedDrop(EquipmentSlot.MAINHAND);
        hoardTicks = 0;
        playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F);
    }

    /** Puts a held item back on the ground. */
    public void returnHoard() {
        ItemStack held = getMainHandItem();
        if (!held.isEmpty()) {
            spawnAtLocation(held.copy());
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            hoardTicks = 0;
        }
    }

    // ---- sniffing for scrap (friends only) -----------------------------------------------------

    public void setSniffCooldown(int ticks) {
        sniffCooldown = ticks;
    }

    public int getSniffCooldown() {
        return sniffCooldown;
    }

    public int getSniffsCompleted() {
        return sniffsCompleted;
    }

    /** Rolls the sniffing loot table and drops the result at the gnoblar's feet. */
    public void finishSniff() {
        sniffsCompleted++;
        sniffCooldown = 2400 + random.nextInt(2400);
        if (level() instanceof ServerLevel serverLevel) {
            LootParams params = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, position())
                    .withParameter(LootContextParams.THIS_ENTITY, this)
                    .create(LootContextParamSets.GIFT);
            serverLevel.getServer().getLootData().getLootTable(SNIFF_LOOT).getRandomItems(params)
                    .forEach(this::spawnAtLocation);
            playSound(SoundEvents.VILLAGER_CELEBRATE, 0.8F, getVoicePitch());
            hearts(2);
        }
    }

    // ---- state -------------------------------------------------------------------------------

    public int getTrust() {
        return entityData.get(TRUST);
    }

    public void setTrust(int trust) {
        entityData.set(TRUST, Math.max(0, Math.min(TAME_TRUST, trust)));
    }

    public boolean isSniffing() {
        return entityData.get(SNIFFING);
    }

    public void setSniffing(boolean sniffing) {
        entityData.set(SNIFFING, sniffing);
    }

    /** About one gnoblar in five has a wart on its nose, decided when it spawns. */
    public boolean hasWart() {
        return entityData.get(WART);
    }

    public void setWart(boolean wart) {
        entityData.set(WART, wart);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData data, CompoundTag tag) {
        setWart(random.nextInt(5) == 0);
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    public boolean isScared() {
        return entityData.get(SCARED);
    }

    public void setScared(boolean scared) {
        entityData.set(SCARED, scared);
    }

    public boolean canPester() {
        return pesterCooldown <= 0;
    }

    public void setPesterCooldown(int ticks) {
        pesterCooldown = ticks;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (sniffCooldown > 0) {
            sniffCooldown--;
        }
        if (pesterCooldown > 0) {
            pesterCooldown--;
        }
        if (isHoarding() && ++hoardTicks >= HOARD_BORED_TICKS) {
            returnHoard();   // bored of it; it can be picked up again
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Trust", getTrust());
        tag.putBoolean("Wart", hasWart());
        tag.putInt("SniffCooldown", sniffCooldown);
        tag.putInt("HoardTicks", hoardTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTrust(tag.getInt("Trust"));
        setWart(tag.getBoolean("Wart"));
        sniffCooldown = tag.getInt("SniffCooldown");
        hoardTicks = tag.getInt("HoardTicks");
    }

    // ---- sounds: vanilla villager sounds pitched up into a squeak --------------------------------

    @Override
    public float getVoicePitch() {
        return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.7F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    /** A short squeak, used by the pestering goal. */
    public void squeak() {
        playSound(SoundEvents.VILLAGER_AMBIENT, 0.9F, getVoicePitch());
    }
}
