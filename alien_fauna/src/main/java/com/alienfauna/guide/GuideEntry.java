package com.alienfauna.guide;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * One creature in the Field Guide. The text of every section is the language key
 * guide.alien_fauna.(id).(section), the first section is the short description shown under the
 * model, and the title is the creature's own name.
 */
public record GuideEntry(String id, Supplier<EntityType<? extends LivingEntity>> type, List<String> sections, boolean hasBallForm) {
    public String titleKey() {
        return "entity.alien_fauna." + id;
    }

    public String textKey(String section) {
        return "guide.alien_fauna." + id + "." + section;
    }
}
