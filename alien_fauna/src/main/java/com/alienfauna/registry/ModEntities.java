package com.alienfauna.registry;

import com.alienfauna.AlienFauna;
import com.alienfauna.entity.CannonboltEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
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
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AlienFauna.MODID);

    public static final RegistryObject<EntityType<CannonboltEntity>> CANNONBOLT = ENTITIES.register("cannonbolt",
            () -> EntityType.Builder.of(CannonboltEntity::new, MobCategory.CREATURE)
                    .sized(1.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(AlienFauna.MODID + ":cannonbolt"));

    @Mod.EventBusSubscriber(modid = AlienFauna.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Events {
        @SubscribeEvent
        public static void onAttributes(EntityAttributeCreationEvent event) {
            event.put(CANNONBOLT.get(), CannonboltEntity.createAttributes().build());
        }

        @SubscribeEvent
        public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
            event.register(CANNONBOLT.get(), SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                    SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }
}
