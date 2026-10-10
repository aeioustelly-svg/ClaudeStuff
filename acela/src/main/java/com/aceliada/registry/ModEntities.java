package com.aceliada.registry;

import com.aceliada.Aceliada;
import com.aceliada.entity.AcelaEntity;
import com.aceliada.entity.BoneSpikeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Aceliada.MODID);

    public static final RegistryObject<EntityType<AcelaEntity>> ACELA = ENTITIES.register("acela",
            () -> EntityType.Builder.of(AcelaEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(Aceliada.MODID + ":acela"));

    public static final RegistryObject<EntityType<BoneSpikeEntity>> BONE_SPIKE = ENTITIES.register("bone_spike",
            () -> EntityType.Builder.<BoneSpikeEntity>of(BoneSpikeEntity::new, MobCategory.MISC)
                    .sized(0.5F, 1.4F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build(Aceliada.MODID + ":bone_spike"));

    @Mod.EventBusSubscriber(modid = Aceliada.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Events {
        @SubscribeEvent
        public static void onAttributes(EntityAttributeCreationEvent event) {
            event.put(ACELA.get(), AcelaEntity.createAttributes().build());
        }
    }
}
