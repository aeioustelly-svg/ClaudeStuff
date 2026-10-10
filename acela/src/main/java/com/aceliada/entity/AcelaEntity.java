package com.aceliada.entity;

import com.aceliada.entity.goal.AcelaSpecialAttackGoal;
import com.aceliada.network.AcelaNetwork;
import com.aceliada.network.CloseDialoguePacket;
import com.aceliada.network.OpenDialoguePacket;
import com.aceliada.registry.ModItems;
import com.aceliada.registry.ModSounds;
import com.aceliada.summon.AcelaSummoning;
import com.aceliada.summon.ArenaBuilder;
import com.aceliada.summon.ServerScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Acela: a man made of darkness with two red eyes, a leather jacket and a pipe.
 *
 * <p>Summoned at 3:55 he starts in an intro state: invulnerable, motionless, staring at the players
 * while they are blind, until one of them answers his question. Then the fight starts. Like Sans he
 * dodges a share of the hits by stepping aside (less often as he tires), and throws bone spikes,
 * a gravity slam and a blast of pipe smoke.
 */
public class AcelaEntity extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_INTRO =
            SynchedEntityData.defineId(AcelaEntity.class, EntityDataSerializers.BOOLEAN);

    public static final String QUESTION = "Mă, da știi ce mă fute?";
    public static final List<String> OPTIONS = List.of("Școala Frumoșilor", "Al", "Femeile Perverse", "Poseri");
    public static final List<String> TAUNTS = List.of(
            "Mă da' n-ar ave noroc!",
            "Da duti mă în pulă wă",
            "Lapurfagiu.",
            "Mă mut în Mongolia...",
            "*fucks aîr*",
            "poate numai Dorel Vodafone și Patronu' de m-ar putea învinge...");
    /** Said over a player he has just killed. */
    public static final String KILL_LINE = "N-ai să te mi scoli... ca pula lui khab";
    /** Said now and then when one of his hits lands on a player. */
    public static final String HIT_LINE = "Ce să-ți povestesc nepoate, viața satanelor";
    public static final String LAST_WORDS = "Mă da tu n-ai empatie...";
    private static final float HIT_LINE_CHANCE = 0.3F;
    private static final int HIT_LINE_COOLDOWN = 20 * 20;

    /** If nobody answers in this time, the fight starts anyway. */
    public static final int INTRO_TIMEOUT = 20 * 60;
    public static final int RETURN_DELAY = 20 * 10;
    private static final double ARENA_RANGE = 48.0;
    private static final double LEASH = 14.0;

    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(
            getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);
    private final List<UUID> participants = new ArrayList<>();
    @Nullable
    private BlockPos arenaCentre;
    private int introTicks;
    private int talkCooldown = 60;
    private int lastTaunt = -1;
    private int musicTicks;
    private int specialCooldown = 60;
    private boolean quietRemoval;
    private int hitLineCooldown;
    private AcelaSpecialAttackGoal specialAttackGoal;

    public AcelaEntity(EntityType<? extends AcelaEntity> type, Level level) {
        super(type, level);
        this.xpReward = 100;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 250.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_INTRO, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.specialAttackGoal = new AcelaSpecialAttackGoal(this);
        this.goalSelector.addGoal(1, specialAttackGoal);
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- state ------------------------------------------------------------------------------

    public boolean isIntro() {
        return this.entityData.get(DATA_INTRO);
    }

    public boolean isFighting() {
        return !isIntro() && isAlive();
    }

    public AcelaSpecialAttackGoal getSpecialAttackGoal() {
        return specialAttackGoal;
    }

    public int getSpecialCooldown() {
        return specialCooldown;
    }

    public void setSpecialCooldown(int ticks) {
        this.specialCooldown = ticks;
    }

    public void setTalkCooldown(int ticks) {
        this.talkCooldown = ticks;
    }

    @Nullable
    public BlockPos getArenaCentre() {
        return arenaCentre;
    }

    public boolean isParticipant(Player player) {
        return participants.contains(player.getUUID());
    }

    /** Called once by the summoning, before the entity is added to the level. */
    public void beginIntro(BlockPos centre, List<? extends Player> players) {
        this.arenaCentre = centre.immutable();
        this.participants.clear();
        players.forEach(p -> participants.add(p.getUUID()));
        this.entityData.set(DATA_INTRO, true);
        this.setGlowingTag(true);
        this.introTicks = 0;
    }

    public void openDialogue() {
        if (!isIntro() || !isAlive()) {
            return;
        }
        for (ServerPlayer player : onlineParticipants()) {
            AcelaNetwork.sendTo(player, new OpenDialoguePacket(getId()));
        }
    }

    /** A participant picked an answer. Whatever it is, the fight begins. */
    public boolean onDialogueChoice(Player chooser, int option) {
        if (!isIntro() || option < 0 || option >= OPTIONS.size()) {
            return false;
        }
        broadcast(Component.literal("<").append(chooser.getDisplayName()).append("> " + OPTIONS.get(option)));
        startFight(chooser);
        return true;
    }

    public void startFight(@Nullable LivingEntity target) {
        if (!isIntro()) {
            return;
        }
        this.entityData.set(DATA_INTRO, false);
        this.setGlowingTag(false);
        for (ServerPlayer player : onlineParticipants()) {
            player.removeEffect(MobEffects.BLINDNESS);
            AcelaNetwork.sendTo(player, new CloseDialoguePacket());
        }
        this.musicTicks = 0;
        this.talkCooldown = 50;
        this.specialCooldown = 60;
        if (target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            this.setTarget(target);
        }
    }

    private List<ServerPlayer> onlineParticipants() {
        List<ServerPlayer> players = new ArrayList<>();
        MinecraftServer server = getServer();
        if (server == null) {
            return players;
        }
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    /** Players who should hear him: everyone near, plus the participants wherever they are in this level. */
    private List<ServerPlayer> audience() {
        List<ServerPlayer> players = new ArrayList<>();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return players;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(this) < ARENA_RANGE * ARENA_RANGE || participants.contains(player.getUUID())) {
                players.add(player);
            }
        }
        return players;
    }

    private void broadcast(Component message) {
        audience().forEach(p -> p.sendSystemMessage(message));
    }

    public void say(String line) {
        broadcast(Component.literal("<")
                .append(getDisplayName().copy().withStyle(ChatFormatting.DARK_RED))
                .append("> " + line));
    }

    // ---- ticking ----------------------------------------------------------------------------

    /** During the intro the AI does not run at all: no goals, no movement. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isIntro();
    }

    /** Intro: stand still, stare, keep everyone blind until somebody answers. */
    private void introTick() {
        this.getNavigation().stop();
        introTicks++;
        Player nearest = level().getNearestPlayer(this, ARENA_RANGE);
        if (nearest != null) {
            this.lookAt(nearest, 30.0F, 30.0F);
            this.setYHeadRot(this.getYRot());
            this.setYBodyRot(this.getYRot());
        }
        if (introTicks % 20 == 0) {
            for (ServerPlayer player : onlineParticipants()) {
                if (player.distanceToSqr(this) < ARENA_RANGE * ARENA_RANGE) {
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false));
                }
            }
        }
        if (introTicks > INTRO_TIMEOUT) {
            startFight(nearest);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (!isFighting()) {
            return;
        }
        if (specialCooldown > 0) {
            specialCooldown--;
        }
        if (hitLineCooldown > 0) {
            hitLineCooldown--;
        }
        if (--talkCooldown <= 0) {
            int index = random.nextInt(TAUNTS.size() - 1);
            if (index >= lastTaunt && lastTaunt >= 0) {
                index++;
            }
            lastTaunt = index;
            say(TAUNTS.get(index));
            talkCooldown = 160 + random.nextInt(180);
        }
        if (arenaCentre != null) {
            if (--musicTicks <= 0) {
                level().playSound(null, arenaCentre.above(2), ModSounds.BOSS_MUSIC.get(), SoundSource.RECORDS, 4.0F, 1.0F);
                musicTicks = ModItems.DISC_LENGTH_TICKS + 40;
            }
            Vec3 c = Vec3.atBottomCenterOf(arenaCentre.above());
            if (horizontalDistanceTo(c) > LEASH + 2.0) {
                returnToArena(c);
            }
        }
    }

    private double horizontalDistanceTo(Vec3 point) {
        double dx = getX() - point.x;
        double dz = getZ() - point.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void returnToArena(Vec3 centre) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = 7.0 + random.nextDouble() * 4.0;
            if (teleportWithPuff(centre.x + Math.cos(angle) * r, centre.y + 1.0, centre.z + Math.sin(angle) * r)) {
                return;
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            pipeSmoke();
        } else if (isIntro() && isAlive()) {
            introTick();
        }
    }

    /** Smoke rising from the pipe bowl: 2 px to his left, 8 px in front, 2 px above the neck. */
    private void pipeSmoke() {
        if (random.nextInt(3) != 0) {
            return;
        }
        float yaw = this.yHeadRot * Mth.DEG_TO_RAD;
        double forwardX = -Mth.sin(yaw);
        double forwardZ = Mth.cos(yaw);
        double leftX = Mth.cos(yaw);
        double leftZ = Mth.sin(yaw);
        double x = getX() + forwardX * 0.5 + leftX * 0.125;
        double y = getY() + 1.625;
        double z = getZ() + forwardZ * 0.5 + leftZ * 0.125;
        level().addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.03D, 0.0D);
        if (random.nextInt(25) == 0) {
            level().addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.1, z, forwardX * 0.02, 0.05D, forwardZ * 0.02);
        }
    }

    // ---- combat -----------------------------------------------------------------------------

    /** Chance to step aside from a hit. 45 percent at full health, 15 percent when nearly dead. */
    public float dodgeChance() {
        return 0.15F + 0.30F * (getHealth() / getMaxHealth());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (isIntro()) {
            return false;
        }
        if (!level().isClientSide && source.getEntity() instanceof LivingEntity attacker && attacker != this
                && random.nextFloat() < dodgeChance() && dodge(attacker)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof Player && target.isAlive() && hitLineCooldown <= 0
                && random.nextFloat() < HIT_LINE_CHANCE) {
            say(HIT_LINE);
            hitLineCooldown = HIT_LINE_COOLDOWN;
            talkCooldown = Math.max(talkCooldown, 100);
        }
        return hit;
    }

    /** Called when something he gets the kill credit for dies, bones and smoke included. */
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        if (victim instanceof Player) {
            say(KILL_LINE);
            talkCooldown = Math.max(talkCooldown, 100);
        }
        return super.killedEntity(level, victim);
    }

    private boolean dodge(Entity attacker) {
        Vec3 away = position().subtract(attacker.position()).multiply(1.0, 0.0, 1.0);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        Vec3 side = new Vec3(-away.z, 0.0, away.x);
        Vec3 centre = arenaCentre == null ? null : Vec3.atBottomCenterOf(arenaCentre.above());
        for (int attempt = 0; attempt < 8; attempt++) {
            double sideways = (random.nextBoolean() ? 1.0 : -1.0) * (2.5 + random.nextDouble() * 2.5);
            Vec3 target = position().add(side.scale(sideways)).add(away.scale(random.nextDouble() * 2.0));
            if (centre != null && Math.hypot(target.x - centre.x, target.z - centre.z) > LEASH) {
                continue;
            }
            if (teleportWithPuff(target.x, getY() + 1.0, target.z)) {
                return true;
            }
        }
        return false;
    }

    private boolean teleportWithPuff(double x, double y, double z) {
        double oldX = getX();
        double oldY = getY();
        double oldZ = getZ();
        if (!randomTeleport(x, y, z, false)) {
            return false;
        }
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, oldX, oldY + 1.0, oldZ, 12, 0.25, 0.5, 0.25, 0.02);
        }
        level().playSound(null, oldX, oldY, oldZ, SoundEvents.ILLUSIONER_MIRROR_MOVE, getSoundSource(), 1.0F, 0.7F);
        return true;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !this.dead && !this.isRemoved()) {
            say(LAST_WORDS);
            finishFight();
        }
        super.die(source);
    }

    /** Stops the music and sends the participants home a little later. */
    private void finishFight() {
        List<ServerPlayer> listeners = audience();
        if (arenaCentre != null) {
            ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(ModSounds.BOSS_MUSIC.getId(), SoundSource.RECORDS);
            listeners.forEach(p -> {
                if (p.connection != null) {
                    p.connection.send(stop);
                }
            });
        }
        MinecraftServer server = getServer();
        BlockPos centre = arenaCentre;
        if (server == null || centre == null || participants.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(participants);
        ResourceKey<Level> arenaLevel = level().dimension();
        ServerScheduler.schedule(RETURN_DELAY, () -> {
            for (UUID id : ids) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    AcelaSummoning.returnHome(player, arenaLevel, centre);
                }
            }
        });
    }

    /** Removes him without sending anybody home, used when a new summoning replaces him. */
    public void discardQuietly() {
        this.quietRemoval = true;
        this.discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        // Despawned without dying (peaceful difficulty, a command): do not strand the players on the roof.
        if (!level().isClientSide && reason == RemovalReason.DISCARDED && !this.dead && !quietRemoval) {
            finishFight();
        }
        super.remove(reason);
    }

    // ---- boss bar, sounds, persistence ------------------------------------------------------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    /** Red outline while he glows during the intro, unless a team says otherwise. */
    @Override
    public int getTeamColor() {
        return getTeam() == null ? 0xC01818 : super.getTeamColor();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.65F;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Intro", isIntro());
        tag.putInt("IntroTicks", introTicks);
        ListTag list = new ListTag();
        participants.forEach(id -> list.add(NbtUtils.createUUID(id)));
        tag.put("Participants", list);
        if (arenaCentre != null) {
            tag.put("ArenaCentre", NbtUtils.writeBlockPos(arenaCentre));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_INTRO, tag.getBoolean("Intro"));
        this.introTicks = tag.getInt("IntroTicks");
        participants.clear();
        for (Tag t : tag.getList("Participants", Tag.TAG_INT_ARRAY)) {
            participants.add(NbtUtils.loadUUID(t));
        }
        this.arenaCentre = tag.contains("ArenaCentre") ? NbtUtils.readBlockPos(tag.getCompound("ArenaCentre")) : null;
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    /** Keeps the arena in mind for the special attacks: never aim bones beyond the wall. */
    public boolean insideArena(Vec3 pos) {
        if (arenaCentre == null) {
            return true;
        }
        return Math.hypot(pos.x - (arenaCentre.getX() + 0.5), pos.z - (arenaCentre.getZ() + 0.5)) < ArenaBuilder.FLOOR_RADIUS;
    }
}
