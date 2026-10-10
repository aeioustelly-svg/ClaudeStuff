package com.ghostfreakmod.ghostfreak.event;

import com.ghostfreakmod.ghostfreak.GhostfreakMod;
import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.NoteBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A note block played near a Ghostfreak makes it dance for a few seconds. Jukeboxes are polled by the entity. */
@Mod.EventBusSubscriber(modid = GhostfreakMod.MODID)
public class MusicEvents {
    private static final double RANGE = 12.0D;

    @SubscribeEvent
    public static void onNote(NoteBlockEvent.Play event) {
        if (!(event.getLevel() instanceof net.minecraft.world.level.Level level) || level.isClientSide) {
            return;
        }
        AABB area = new AABB(event.getPos()).inflate(RANGE);
        for (GhostfreakEntity ghost : level.getEntitiesOfClass(GhostfreakEntity.class, area)) {
            ghost.danceFor(80);
        }
    }
}
