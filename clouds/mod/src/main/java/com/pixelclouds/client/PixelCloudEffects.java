package com.pixelclouds.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.joml.Matrix4f;

/** Vanilla overworld sky, with the clouds swapped for {@link CloudRenderer}. */
public class PixelCloudEffects extends DimensionSpecialEffects.OverworldEffects {
    private final CloudRenderer clouds = new CloudRenderer();

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f projectionMatrix) {
        clouds.render(level, ticks, partialTick, poseStack, camX, camY, camZ);
        return true;    // vanilla clouds are not drawn
    }
}
