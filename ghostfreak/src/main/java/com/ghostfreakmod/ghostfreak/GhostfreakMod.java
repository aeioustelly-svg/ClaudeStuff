package com.ghostfreakmod.ghostfreak;

import com.ghostfreakmod.ghostfreak.registry.ModBlocks;
import com.ghostfreakmod.ghostfreak.registry.ModEntities;
import com.ghostfreakmod.ghostfreak.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GhostfreakMod.MODID)
public class GhostfreakMod {
    public static final String MODID = "ghostfreak";

    public GhostfreakMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModEntities.ENTITIES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.GHOSTFREAK_SPAWN_EGG);
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModItems.ECTOPLASM_BOTTLE);
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.SPECTRAL_LANTERN);
        }
    }
}
