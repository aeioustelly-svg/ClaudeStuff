package com.okapimod.okapi.registry;

import com.okapimod.okapi.OkapiMod;
import com.okapimod.okapi.entity.OkapiEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, OkapiMod.MODID);

    public static final RegistryObject<EntityType<OkapiEntity>> OKAPI = ENTITIES.register("okapi",
            () -> EntityType.Builder.of(OkapiEntity::new, MobCategory.CREATURE)
                    .sized(1.2F, 1.9F)
                    .clientTrackingRange(8)
                    .build(OkapiMod.MODID + ":okapi"));

    @Mod.EventBusSubscriber(modid = OkapiMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Events {
        @SubscribeEvent
        public static void onAttributes(EntityAttributeCreationEvent event) {
            event.put(OKAPI.get(), OkapiEntity.createAttributes().build());
        }

        @SubscribeEvent
        public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
            event.register(OKAPI.get(), SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules,
                    SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }
}
