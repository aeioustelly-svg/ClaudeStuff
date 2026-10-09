package com.gnoblarmod.gnoblars.entity;

import com.gnoblarmod.gnoblars.Gnoblars;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarAvoidMonstersGoal;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarCakeGoal;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarFollowOwnerGoal;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarPartyGoal;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarSleepGoal;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarPesterGoal;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarScavengeGoal;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarSniffGoal;
import com.gnoblarmod.gnoblars.registry.ModItems;
import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.tags.ItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
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
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
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
    private static final EntityDataAccessor<Boolean> NAPPING =
            SynchedEntityData.defineId(GnoblarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DANCING =
            SynchedEntityData.defineId(GnoblarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MUDDY =
            SynchedEntityData.defineId(GnoblarEntity.class, EntityDataSerializers.BOOLEAN);
    /** The dye colour id of a dyed sash, or -1 for the sash it was born with. */
    private static final EntityDataAccessor<Integer> SASH_COLOR =
            SynchedEntityData.defineId(GnoblarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(GnoblarEntity.class, EntityDataSerializers.INT);

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
        goalSelector.addGoal(4, new GnoblarFollowOwnerGoal(this, 1.0D, 5.0F, 2.0F));
        goalSelector.addGoal(4, new GnoblarPartyGoal(this));
        goalSelector.addGoal(5, new GnoblarSleepGoal(this));
        goalSelector.addGoal(5, new GnoblarCakeGoal(this));
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
        entityData.define(VARIANT, 0);
        entityData.define(NAPPING, false);
        entityData.define(DANCING, false);
        entityData.define(MUDDY, false);
        entityData.define(SASH_COLOR, -1);
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

        // kindness that anyone can show: brush it, or wash the mud off with a water bottle
        if (held.is(Items.BRUSH)) {
            if (!level().isClientSide) {
                brush(player, hand, held);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isWaterBottle(held) && isMuddy()) {
            if (!level().isClientSide) {
                wash(player, hand, held);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

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
            if (held.getItem() instanceof DyeItem dye) {
                if (dye.getDyeColor().getId() == entityData.get(SASH_COLOR)) {
                    return InteractionResult.PASS;   // already that colour
                }
                if (!level().isClientSide) {
                    setSashColor(dye.getDyeColor());
                    consume(player, held);
                    playSound(SoundEvents.DYE_USE, 1.0F, 1.0F);
                    hearts(3);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (held.is(ItemTags.BANNERS)) {
                if (!level().isClientSide) {
                    wearBanner(player, held);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (held.is(Items.SHEARS) && !getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                if (!level().isClientSide) {
                    spawnAtLocation(getItemBySlot(EquipmentSlot.HEAD).copy());
                    setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                    playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.2F);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (!level().isClientSide) {
                if (player.isShiftKeyDown()) {
                    climbOnto(player);
                } else {
                    setMode(getMode().next());
                    player.displayClientMessage(Component.translatable("message.gnoblars.mode." + mode.id(), getDisplayName()), true);
                }
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
            setNapping(false);
            if (isTame()) {
                if (mode == GnoblarMode.SIT) {
                    setMode(GnoblarMode.FOLLOW);   // a hurt friend gets up and comes back to its owner
                }
            } else if (source.getEntity() instanceof Player) {
                setTrust(0);   // unkindness is remembered
            }
        }
        return super.hurt(source, amount);
    }

    // ---- kindness: brushing, washing, dyeing, banners, naps, dancing and cake ------------------------------

    private int careCooldown;
    /** Ticks of party left (server only). While it runs the gnoblar dances on the spot. */
    private int partyTicks;
    private int cakeCooldown;
    private int napCooldown;
    private BlockPos jukebox;
    private boolean jukeboxDancing;

    private static boolean isWaterBottle(ItemStack stack) {
        return stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER;
    }

    /** What being looked after does: a wild gnoblar trusts a little more, a friend feels better. */
    private void comfort(Player player, int hearts) {
        if (isTame()) {
            heal(1.0F);
        } else {
            setTrust(getTrust() + 1);
            if (getTrust() >= TAME_TRUST) {
                befriend(player);
            }
        }
        hearts(hearts);
        playSound(SoundEvents.VILLAGER_YES, 1.0F, getVoicePitch());
    }

    private void brush(Player player, InteractionHand hand, ItemStack brush) {
        if (careCooldown > 0) {
            playSound(SoundEvents.VILLAGER_AMBIENT, 0.6F, getVoicePitch());   // content, but it has had enough for now
            return;
        }
        careCooldown = 200;
        brush.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        playSound(SoundEvents.BRUSH_GENERIC, 1.0F, 1.0F);
        comfort(player, 3);
    }

    private void wash(Player player, InteractionHand hand, ItemStack bottle) {
        setMuddy(false);
        player.setItemInHand(hand, ItemUtils.createFilledResult(bottle, player, new ItemStack(Items.GLASS_BOTTLE)));
        playSound(SoundEvents.BOTTLE_EMPTY, 1.0F, 1.0F);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SPLASH, getX(), getY(0.6D), getZ(), 14, 0.3D, 0.3D, 0.3D, 0.05D);
        }
        comfort(player, 3);
    }

    private void wearBanner(Player player, ItemStack banner) {
        ItemStack old = getItemBySlot(EquipmentSlot.HEAD);
        ItemStack hat = banner.copy();
        hat.setCount(1);
        setItemSlot(EquipmentSlot.HEAD, hat);
        setGuaranteedDrop(EquipmentSlot.HEAD);
        consume(player, banner);
        if (!old.isEmpty() && !player.getInventory().add(old)) {
            spawnAtLocation(old);
        }
        playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.2F);
        hearts(3);
    }

    public boolean isMuddy() {
        return entityData.get(MUDDY);
    }

    public void setMuddy(boolean muddy) {
        entityData.set(MUDDY, muddy);
    }

    /** The dye the sash has been coloured with, or null for its original colour. */
    public DyeColor getSashColor() {
        int id = entityData.get(SASH_COLOR);
        return id < 0 ? null : DyeColor.byId(id);
    }

    public void setSashColor(DyeColor color) {
        entityData.set(SASH_COLOR, color == null ? -1 : color.getId());
    }

    public boolean isNapping() {
        return entityData.get(NAPPING);
    }

    public void setNapping(boolean napping) {
        entityData.set(NAPPING, napping);
    }

    /** Dancing to a jukebox (known only to the client, like a parrot) or at a party. */
    public boolean isDancing() {
        return entityData.get(DANCING) || jukeboxDancing;
    }

    public boolean isPartying() {
        return partyTicks > 0;
    }

    public void startParty() {
        partyTicks = 200;
        entityData.set(DANCING, true);
    }

    public boolean canEatCake() {
        return cakeCooldown <= 0;
    }

    public boolean canLookForABed() {
        return napCooldown <= 0;
    }

    public void setNapCooldown(int ticks) {
        napCooldown = ticks;
    }

    public void setCakeCooldown(int ticks) {
        cakeCooldown = ticks;
    }

    /** A gnoblar has taken a bite of the cake: it and everyone near the cake cheer and dance. */
    public void celebrateCake(BlockPos cake) {
        playSound(SoundEvents.VILLAGER_CELEBRATE, 1.0F, getVoicePitch());
        if (isTame()) {
            heal(2.0F);
        } else {
            setTrust(Math.min(getTrust() + 1, TAME_TRUST - 1));   // a party warms it up, but only a player can befriend it
        }
        hearts(4);
        for (GnoblarEntity other : level().getEntitiesOfClass(GnoblarEntity.class, new AABB(cake).inflate(8.0D))) {
            other.startParty();
        }
    }

    /** Like a parrot, a gnoblar only learns of a jukebox on the client. */
    @Override
    public void setRecordPlayingNearby(BlockPos pos, boolean playing) {
        jukebox = pos;
        jukeboxDancing = playing;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isNapping();
    }

    // ---- modes: follow, sit, wander, and riding on the owner's back ---------------------------

    private GnoblarMode mode = GnoblarMode.FOLLOW;
    /** How far a wandering friend strays from the spot where it was told to wander. */
    public static final int WANDER_RADIUS = 10;

    public GnoblarMode getMode() {
        return mode;
    }

    /** Sets what the friend does and updates sitting and the wander restriction to match. */
    public void setMode(GnoblarMode newMode) {
        mode = newMode;
        setOrderedToSit(newMode == GnoblarMode.SIT);
        if (newMode == GnoblarMode.WANDER) {
            restrictTo(blockPosition(), WANDER_RADIUS);
        } else {
            clearRestriction();
        }
        setJumping(false);
        navigation.stop();
        setTarget(null);
    }

    /** The owner picks the gnoblar up, piggyback style. */
    private void climbOnto(Player player) {
        if (isPassenger() || !player.getPassengers().isEmpty()) {
            return;
        }
        setMode(GnoblarMode.FOLLOW);
        if (startRiding(player, true)) {
            player.displayClientMessage(Component.translatable("message.gnoblars.mounted", getDisplayName()), true);
        }
    }

    /** Puts every gnoblar that rides on this player's back on the ground. Returns whether there was one. */
    public static boolean putDownPassengers(Player player) {
        boolean any = false;
        for (Entity passenger : List.copyOf(player.getPassengers())) {
            if (passenger instanceof GnoblarEntity gnoblar) {
                gnoblar.stopRiding();
                gnoblar.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                gnoblar.playSound(SoundEvents.VILLAGER_YES, 0.8F, gnoblar.getVoicePitch());
                any = true;
            }
        }
        return any;
    }

    /**
     * Vanilla never tells a player's own client that the player has a passenger: ServerEntity sends the passengers packet with
     * broadcast(), which skips the entity's own player (ChunkMap.TrackedEntity.updatePlayer ignores the entity itself). Every other
     * player sees the rider, but the carrier's client would leave it frozen where it climbed on. So send the packet by hand
     * whenever a gnoblar mounts or leaves a player, however it leaves.
     */
    private static void syncPassengers(Entity vehicle) {
        if (vehicle instanceof ServerPlayer carrier && carrier.connection != null) {
            carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
        }
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        boolean mounted = super.startRiding(vehicle, force);
        if (mounted) {
            syncPassengers(vehicle);
        }
        return mounted;
    }

    @Override
    public void stopRiding() {
        Entity vehicle = getVehicle();
        super.stopRiding();
        if (vehicle != null) {
            syncPassengers(vehicle);
        }
    }

    /** A rider is carried through walls and water by its owner, so it cannot suffocate, drown or fall. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || (isPassenger()
                && (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.FALL)));
    }

    /** While on its owner's back it sits behind them, facing the same way, and does not wander off. */
    @Override
    public void rideTick() {
        super.rideTick();
        if (getVehicle() instanceof LivingEntity vehicle) {
            fallDistance = 0.0F;
            setAirSupply(getMaxAirSupply());
            double yaw = Math.toRadians(vehicle.yBodyRot);
            double back = 0.4D;
            setPos(vehicle.getX() + Math.sin(yaw) * back, vehicle.getY() + (vehicle.isShiftKeyDown() ? 0.7D : 0.9D),
                    vehicle.getZ() - Math.cos(yaw) * back);
            setYRot(vehicle.yBodyRot);
            setYBodyRot(vehicle.yBodyRot);
            setYHeadRot(vehicle.yBodyRot);
            navigation.stop();
        }
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
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
        setMuddy(true);   // digging is dirty work
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

    /** Which look this gnoblar has, decided when it spawns. */
    public GnoblarVariant getVariant() {
        return GnoblarVariant.byId(entityData.get(VARIANT));
    }

    public void setVariant(GnoblarVariant variant) {
        entityData.set(VARIANT, variant.ordinal());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData data, CompoundTag tag) {
        setVariant(GnoblarVariant.roll(random));
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
            if (jukebox == null || !jukebox.closerToCenterThan(position(), 3.46D)
                    || !level().getBlockState(jukebox).is(Blocks.JUKEBOX)) {
                jukeboxDancing = false;
                jukebox = null;
            }
            return;
        }
        if (careCooldown > 0) {
            careCooldown--;
        }
        if (cakeCooldown > 0) {
            cakeCooldown--;
        }
        if (napCooldown > 0) {
            napCooldown--;
        }
        if (partyTicks > 0 && --partyTicks == 0) {
            entityData.set(DANCING, false);
        }
        if (isMuddy()) {
            if (isInWaterOrRain()) {
                setMuddy(false);   // washed clean by the rain or a swim
            }
        } else if (onGround() && random.nextInt(60) == 0) {
            BlockState below = level().getBlockState(getOnPos());   // getOnPos copes with a block shorter than a full one, like mud
            if (below.is(Blocks.MUD) || below.is(Blocks.MUDDY_MANGROVE_ROOTS)) {
                setMuddy(true);
            }
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
        tag.putString("Variant", getVariant().id());
        tag.putInt("SniffCooldown", sniffCooldown);
        tag.putInt("HoardTicks", hoardTicks);
        tag.putString("Mode", mode.id());
        tag.putBoolean("Muddy", isMuddy());
        tag.putInt("SashColor", entityData.get(SASH_COLOR));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTrust(tag.getInt("Trust"));
        setVariant(tag.contains("Variant") ? GnoblarVariant.byName(tag.getString("Variant")) : GnoblarVariant.roll(random));
        sniffCooldown = tag.getInt("SniffCooldown");
        hoardTicks = tag.getInt("HoardTicks");
        mode = GnoblarMode.byName(tag.getString("Mode"));
        setMuddy(tag.getBoolean("Muddy"));
        entityData.set(SASH_COLOR, tag.contains("SashColor") ? tag.getInt("SashColor") : -1);
        if (mode == GnoblarMode.WANDER && !hasRestriction()) {
            restrictTo(blockPosition(), WANDER_RADIUS);   // the restriction is not saved, so wander around the spot it was loaded at
        }
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
