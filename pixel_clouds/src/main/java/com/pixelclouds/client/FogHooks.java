package com.pixelclouds.client;

import com.pixelclouds.PixelClouds;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Thickens the fog and tints it with the cloud's colour while the camera is inside a cloud. */
@Mod.EventBusSubscriber(modid = PixelClouds.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FogHooks {
    private FogHooks() {}

    private static float strength() {
        return Mth.clamp(CloudRenderer.insideFactor() * 3f, 0f, 1f);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        float f = strength();
        if (f <= 0.001f || event.getType() != FogType.NONE) {
            return;
        }
        event.setNearPlaneDistance(Mth.lerp(f, event.getNearPlaneDistance(), 0f));
        event.setFarPlaneDistance(Mth.lerp(f, event.getFarPlaneDistance(), 26f));
    }

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        float f = strength();
        if (f <= 0.001f) {
            return;
        }
        float[] c = CloudRenderer.insideColour();
        event.setRed(Mth.lerp(f, event.getRed(), c[0]));
        event.setGreen(Mth.lerp(f, event.getGreen(), c[1]));
        event.setBlue(Mth.lerp(f, event.getBlue(), c[2]));
    }
}
