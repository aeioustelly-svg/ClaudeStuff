package com.dacianmod.draco.registry;

import com.dacianmod.draco.DacianDraco;
import com.dacianmod.draco.entity.DracoEntity;
import net.minecraft.core.registries.Registries;
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
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DacianDraco.MODID);

    public static final RegistryObject<EntityType<DracoEntity>> DRACO = ENTITIES.register("draco",
            () -> EntityType.Builder.of(DracoEntity::new, MobCategory.CREATURE)
                    .sized(1.1F, 0.9F)
                    .clientTrackingRange(10)
                    .build(DacianDraco.MODID + ":draco"));

    @Mod.EventBusSubscriber(modid = DacianDraco.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Events {
        @SubscribeEvent
        public static void onAttributes(EntityAttributeCreationEvent event) {
            event.put(DRACO.get(), DracoEntity.createAttributes().build());
        }

        @SubscribeEvent
        public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
            event.register(DRACO.get(), SpawnPlacements.Type.NO_RESTRICTIONS,
                    Heightmap.Types.MOTION_BLOCKING, Mob::checkMobSpawnRules,
                    SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }
}
