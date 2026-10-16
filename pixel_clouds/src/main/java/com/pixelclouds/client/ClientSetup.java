package com.pixelclouds.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import com.pixelclouds.PixelClouds;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = PixelClouds.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CloudRenderer.startGeneration();
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        PixelCloudShader.set(null);
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), new ResourceLocation(PixelClouds.MODID, "pixel_cloud"),
                            DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
                    PixelCloudShader::set);
        } catch (Exception e) {
            LOGGER.error("Pixel Clouds: the cloud shader failed to load, vanilla clouds will be used", e);
        }
    }

    @SubscribeEvent
    public static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(new ResourceLocation("minecraft", "overworld"), new PixelSkyEffects());
    }
}
