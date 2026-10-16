package com.pixelclouds.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pixelclouds.PixelCloudsConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.joml.Matrix4f;

/**
 * The vanilla overworld sky with the cloud pass swapped out. Everything else (sky colour, fog,
 * sunrise glow) is inherited unchanged.
 */
public class PixelCloudEffects extends DimensionSpecialEffects.OverworldEffects {
    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f projectionMatrix) {
        if (!PixelCloudsConfig.ENABLED.get()) {
            return false;
        }
        CloudRenderer.INSTANCE.render(level, partialTick, poseStack, camX, camY, camZ, projectionMatrix);
        return true;
    }
}
