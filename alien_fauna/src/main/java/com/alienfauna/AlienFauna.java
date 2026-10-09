package com.alienfauna;

import com.alienfauna.config.GnoblarConfig;
import com.alienfauna.registry.ModEntities;
import com.alienfauna.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AlienFauna.MODID)
public class AlienFauna {
    public static final String MODID = "alien_fauna";

    public AlienFauna(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModEntities.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        context.registerConfig(ModConfig.Type.COMMON, GnoblarConfig.SPEC);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.CANNONBOLT_SPAWN_EGG);
            event.accept(ModItems.GNOBLAR_SPAWN_EGG);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.FIELD_GUIDE);
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModItems.NOSE_PICKLE);
        }
    }
}
