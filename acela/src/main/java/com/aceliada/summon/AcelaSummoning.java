package com.aceliada.summon;

import com.aceliada.entity.AcelaEntity;
import com.aceliada.network.AcelaNetwork;
import com.aceliada.network.RedSkyPacket;
import com.aceliada.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The summoning sequence: "3:55 3:55 3:55!!!" flashes in chat and the sky turns red, then everyone near
 * the jukebox is taken to Dealul Bohii on the Nether roof, blinded, and left facing Acela until the dialogue opens.
 */
public final class AcelaSummoning {
    public static final String RETURN_TAG = "aceliada_return";
    public static final int TELEPORT_DELAY = 50;
    public static final int DIALOGUE_DELAY = 50;
    public static final int INTRO_BLINDNESS = 20 * 90;
    public static final Component FLASH_TEXT = Component.literal("3:55 3:55 3:55!!!");
    public static final Component ARENA_NAME = Component.literal("Dealul Bohii");

    private static final Set<UUID> BUSY = new HashSet<>();

    private AcelaSummoning() {
    }

    /** True between the flash and the arrival in the arena. */
    public static boolean isBusy(ServerPlayer player) {
        return BUSY.contains(player.getUUID());
    }

    public static void begin(ServerLevel level, BlockPos jukebox) {
        double r2 = SummonEvents.SUMMON_RADIUS * SummonEvents.SUMMON_RADIUS;
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && !isBusy(player) && player.distanceToSqr(jukebox.getCenter()) <= r2) {
                ids.add(player.getUUID());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        BUSY.addAll(ids);
        MinecraftServer server = level.getServer();
        for (int i = 0; i < 3; i++) {
            int flash = i;
            ServerScheduler.schedule(1 + 8 * i, () -> online(server, ids).forEach(p -> flash(p, flash)));
        }
        ResourceKey<Level> source = level.dimension();
        ServerScheduler.schedule(TELEPORT_DELAY, () -> {
            ids.forEach(BUSY::remove);
            List<ServerPlayer> players = online(server, ids);
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (players.isEmpty() || nether == null) {
                players.forEach(p -> AcelaNetwork.sendTo(p, new RedSkyPacket(false)));
                return;
            }
            summonAt(nether, arenaCentre(nether, source, jukebox), players);
        });
    }

    private static List<ServerPlayer> online(MinecraftServer server, List<UUID> ids) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.isAlive()) {
                players.add(player);
            }
        }
        return players;
    }

    private static void flash(ServerPlayer player, int index) {
        Component text = FLASH_TEXT.copy().withStyle(index % 2 == 0 ? ChatFormatting.RED : ChatFormatting.DARK_RED,
                ChatFormatting.BOLD);
        player.sendSystemMessage(text);
        if (index == 0) {
            AcelaNetwork.sendTo(player, new RedSkyPacket(true));
        }
        send(player, new ClientboundSetTitlesAnimationPacket(0, 6, 2));
        send(player, new ClientboundSetTitleTextPacket(text));
        player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1.0F, 0.5F);
    }

    static void send(ServerPlayer player, Packet<?> packet) {
        if (player.connection != null) {
            player.connection.send(packet);
        }
    }

    /** The floor block in the middle of the arena, on the top bedrock layer of the Nether. */
    public static BlockPos arenaCentre(ServerLevel nether, ResourceKey<Level> source, BlockPos jukebox) {
        boolean fromNether = source == Level.NETHER;
        int x = fromNether ? jukebox.getX() : Math.floorDiv(jukebox.getX(), 8);
        int z = fromNether ? jukebox.getZ() : Math.floorDiv(jukebox.getZ(), 8);
        int roof = nether.getMinBuildHeight() + nether.getLogicalHeight() - 1;
        return new BlockPos(x, roof, z);
    }

    /** Builds the arena, brings the players in and spawns Acela in front of the first one. */
    public static AcelaEntity summonAt(ServerLevel level, BlockPos centre, List<ServerPlayer> players) {
        AABB area = new AABB(centre).inflate(48.0);
        for (AcelaEntity old : level.getEntitiesOfClass(AcelaEntity.class, area)) {
            old.discardQuietly();
        }
        ArenaBuilder.build(level, centre);

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            saveReturn(player);
            BlockPos spot = ArenaBuilder.playerSpot(centre, i);
            player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yawTowards(spot, centre), 0.0F);
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, INTRO_BLINDNESS, 0, false, false));
            send(player, new ClientboundSetTitlesAnimationPacket(20, 60, 20));
            send(player, new ClientboundSetTitleTextPacket(ARENA_NAME.copy().withStyle(ChatFormatting.DARK_RED)));
        }

        AcelaEntity acela = ModEntities.ACELA.get().create(level);
        if (acela == null) {
            return null;
        }
        BlockPos first = ArenaBuilder.playerSpot(centre, 0);
        Vec3 towardsCentre = Vec3.atLowerCornerOf(centre.above().subtract(first)).normalize();
        Vec3 spawn = Vec3.atBottomCenterOf(first).add(towardsCentre.scale(3.0));
        float yaw = yawTowards(BlockPos.containing(spawn), first);
        acela.moveTo(spawn.x, spawn.y, spawn.z, yaw, 0.0F);
        acela.setYHeadRot(yaw);
        acela.setYBodyRot(yaw);
        acela.finalizeSpawn(level, level.getCurrentDifficultyAt(acela.blockPosition()), MobSpawnType.EVENT, null, null);
        acela.beginIntro(centre, players);
        level.addFreshEntity(acela);
        ServerScheduler.schedule(DIALOGUE_DELAY, acela::openDialogue);
        return acela;
    }

    private static float yawTowards(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static void saveReturn(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", player.level().dimension().location().toString());
        tag.putDouble("X", player.getX());
        tag.putDouble("Y", player.getY());
        tag.putDouble("Z", player.getZ());
        tag.putFloat("Yaw", player.getYRot());
        tag.putFloat("Pitch", player.getXRot());
        player.getPersistentData().put(RETURN_TAG, tag);
    }

    /**
     * Sends a player back to where they were summoned from, if they are still in the arena.
     * Players who already left (died, walked off) just lose the saved spot.
     */
    public static void returnHome(ServerPlayer player, ResourceKey<Level> arenaLevel, BlockPos centre) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(RETURN_TAG)) {
            return;
        }
        CompoundTag tag = data.getCompound(RETURN_TAG);
        data.remove(RETURN_TAG);
        boolean inArena = player.level().dimension() == arenaLevel
                && player.position().distanceToSqr(Vec3.atCenterOf(centre)) < 48.0 * 48.0;
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dimension"));
        ServerLevel home = dim == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (!inArena || home == null || !player.isAlive()) {
            return;
        }
        player.teleportTo(home, tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                tag.getFloat("Yaw"), tag.getFloat("Pitch"));
    }
}
