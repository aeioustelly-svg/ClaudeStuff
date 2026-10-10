package com.aceliada.item;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Marker effect of Drogul Zombie: while it lasts, undead mobs take the user for one of their own
 * and do not target them (see {@link ZombieDrugEvents}).
 */
public class ZombieDrugEffect extends MobEffect {
    public ZombieDrugEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x5E7A3A);
    }
}
