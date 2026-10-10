package com.aceliada.item;

import com.aceliada.Aceliada;
import com.aceliada.registry.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Aceliada.MODID)
public class ZombieDrugEvents {
    /** Undead mobs ignore anyone on Drogul Zombie, unless that person hits them first. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        LivingEntity target = event.getNewTarget();
        if (target == null || mob.getMobType() != MobType.UNDEAD) {
            return;
        }
        if (target.hasEffect(ModEffects.DROGUL_ZOMBIE.get()) && mob.getLastHurtByMob() != target) {
            event.setCanceled(true);
        }
    }
}
