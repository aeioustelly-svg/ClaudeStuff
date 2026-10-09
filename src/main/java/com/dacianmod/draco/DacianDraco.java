package com.dacianmod.draco;

import com.dacianmod.draco.registry.ModEntities;
import com.dacianmod.draco.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DacianDraco.MODID)
public class DacianDraco {
    public static final String MODID = "dacian_draco";

    public DacianDraco(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModEntities.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.DRACO_SPAWN_EGG);
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.DACIAN_FELT_CAP);
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.SHED_SKIN);
        }
    }
}
