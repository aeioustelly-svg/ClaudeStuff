package com.pixelclouds;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(PixelClouds.MODID)
public class PixelClouds {
    public static final String MODID = "pixel_clouds";

    public PixelClouds() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PixelCloudsConfig.SPEC);
    }
}
