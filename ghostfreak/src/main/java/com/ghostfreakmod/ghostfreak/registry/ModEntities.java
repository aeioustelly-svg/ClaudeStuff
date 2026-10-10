package com.ghostfreakmod.ghostfreak.registry;

import com.ghostfreakmod.ghostfreak.GhostfreakMod;
import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
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
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GhostfreakMod.MODID);

    public static final RegistryObject<EntityType<GhostfreakEntity>> GHOSTFREAK = ENTITIES.register("ghostfreak",
            () -> EntityType.Builder.of(GhostfreakEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.4F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(GhostfreakMod.MODID + ":ghostfreak"));

    @Mod.EventBusSubscriber(modid = GhostfreakMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Events {
        @SubscribeEvent
        public static void onAttributes(EntityAttributeCreationEvent event) {
            event.put(GHOSTFREAK.get(), GhostfreakEntity.createAttributes().build());
        }

        /** Like a monster: never on peaceful, and only where it is dark enough. */
        @SubscribeEvent
        public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
            event.register(GHOSTFREAK.get(), SpawnPlacements.Type.NO_RESTRICTIONS,
                    Heightmap.Types.MOTION_BLOCKING,
                    (type, level, reason, pos, random) -> level.getDifficulty() != Difficulty.PEACEFUL
                            && Monster.isDarkEnoughToSpawn(level, pos, random)
                            && Mob.checkMobSpawnRules(type, level, reason, pos, random),
                    SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }
}
