package com.alienfauna.guide;

import com.alienfauna.registry.ModEntities;
import java.util.List;
import net.minecraft.world.entity.EntityType;

/** Every creature of the mod, in the order the Field Guide lists them. Add a line per new creature. */
public class GuideEntries {
    public static final List<GuideEntry> ALL = List.of(
            new GuideEntry("cannonbolt", () -> ModEntities.CANNONBOLT.get(),
                    List.of("description", "habitat", "behaviour", "befriending", "drops"), true));

    public static boolean has(EntityType<?> type) {
        return ALL.stream().anyMatch(entry -> entry.type().get() == type);
    }
}
