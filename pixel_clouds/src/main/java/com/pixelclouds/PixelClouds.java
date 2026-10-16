package com.pixelclouds;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Pixel Clouds: replaces the blocky vanilla clouds with flat, camera-facing pixel-art cloud
 * sprites. Purely client-side; all the work happens in {@link com.pixelclouds.client}.
 */
@Mod(PixelClouds.MOD_ID)
public class PixelClouds {
    public static final String MOD_ID = "pixel_clouds";

    public PixelClouds() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PixelCloudsConfig.SPEC);
    }
}
