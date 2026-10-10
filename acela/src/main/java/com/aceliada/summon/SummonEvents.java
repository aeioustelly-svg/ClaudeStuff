package com.aceliada.summon;

import com.aceliada.Aceliada;
import com.aceliada.registry.ModItems;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Watches the clock. While the in-game time reads 3:55, every jukebox near a player that is playing
 * "La crucea din mormant" summons Acela, once per jukebox per night.
 */
@Mod.EventBusSubscriber(modid = Aceliada.MODID)
public final class SummonEvents {
    /** How far from a player jukeboxes are looked for, in chunks. */
    private static final int SCAN_CHUNKS = 2;
    /** Players this close to the jukebox are taken to the arena. */
    public static final double SUMMON_RADIUS = 24.0;

    private static final Set<String> TRIGGERED = new HashSet<>();

    private SummonEvents() {
    }

    /** Hour on the in-game clock (0 to 23). Day time 0 is 6:00. */
    public static int hour(long dayTime) {
        return (int) ((Math.floorMod(dayTime, 24000L) / 1000L + 6L) % 24L);
    }

    /** Minute on the in-game clock (0 to 59). */
    public static int minute(long dayTime) {
        return (int) (Math.floorMod(dayTime, 1000L) * 60L / 1000L);
    }

    public static boolean isSummonTime(long dayTime) {
        return hour(dayTime) == 3 && minute(dayTime) == 55;
    }

    public static boolean isPlayingTheDisc(BlockEntity blockEntity) {
        return blockEntity instanceof JukeboxBlockEntity jukebox
                && jukebox.isRecordPlaying()
                && jukebox.getFirstItem().is(ModItems.LA_CRUCEA_DIN_MORMANT_DISC.get());
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        long dayTime = level.getDayTime();
        if (!isSummonTime(dayTime)) {
            return;
        }
        long day = Math.floorDiv(dayTime, 24000L);
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player.isSpectator() || AcelaSummoning.isBusy(player)) {
                continue;
            }
            ChunkPos origin = player.chunkPosition();
            for (int cx = -SCAN_CHUNKS; cx <= SCAN_CHUNKS; cx++) {
                for (int cz = -SCAN_CHUNKS; cz <= SCAN_CHUNKS; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(origin.x + cx, origin.z + cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
                        BlockPos pos = blockEntity.getBlockPos();
                        if (!isPlayingTheDisc(blockEntity)
                                || player.distanceToSqr(pos.getCenter()) > SUMMON_RADIUS * SUMMON_RADIUS) {
                            continue;
                        }
                        String key = level.dimension().location() + "@" + pos.asLong() + "#" + day;
                        if (TRIGGERED.add(key)) {
                            AcelaSummoning.begin(level, pos);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRIGGERED.clear();
    }
}
