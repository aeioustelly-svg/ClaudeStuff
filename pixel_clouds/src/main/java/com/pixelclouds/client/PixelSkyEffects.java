package com.pixelclouds.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.joml.Matrix4f;

/** The overworld's special effects with the cloud pass replaced. Everything else stays vanilla. */
public final class PixelSkyEffects extends DimensionSpecialEffects.OverworldEffects {
    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f projectionMatrix) {
        return CloudRenderer.render(level, partialTick, poseStack, camX, camY, camZ);
    }
}
