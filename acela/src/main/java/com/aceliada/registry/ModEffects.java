package com.aceliada.registry;

import com.aceliada.Aceliada;
import com.aceliada.item.ZombieDrugEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Aceliada.MODID);

    public static final RegistryObject<MobEffect> DROGUL_ZOMBIE = EFFECTS.register("drogul_zombie", ZombieDrugEffect::new);
}
