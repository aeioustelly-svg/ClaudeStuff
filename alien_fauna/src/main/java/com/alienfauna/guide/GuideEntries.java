package com.alienfauna.guide;

import com.alienfauna.registry.ModEntities;
import java.util.List;
import net.minecraft.world.entity.EntityType;

/** Every creature of the mod, in the order the Field Guide lists them. Add a line per new creature. */
public class GuideEntries {
    public static final List<GuideEntry> ALL = List.of(
            new GuideEntry("cannonbolt", () -> ModEntities.CANNONBOLT.get(),
                    List.of("description", "habitat", "behaviour", "befriending", "drops"), true, true, false, false),
            new GuideEntry("gnoblar", () -> ModEntities.GNOBLAR.get(),
                    List.of("description", "habitat", "behaviour", "looks", "befriending", "kindness", "drops"),
                    false, false, true, true));

    public static boolean has(EntityType<?> type) {
        return indexOf(type) >= 0;
    }

    /** Position of the creature's page in the guide, or -1 if the guide has none. */
    public static int indexOf(EntityType<?> type) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).type().get() == type) {
                return i;
            }
        }
        return -1;
    }
}
