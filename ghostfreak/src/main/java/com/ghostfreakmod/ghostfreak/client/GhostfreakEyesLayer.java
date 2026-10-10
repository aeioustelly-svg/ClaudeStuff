package com.ghostfreakmod.ghostfreak.client;

import com.ghostfreakmod.ghostfreak.GhostfreakMod;
import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The glowing eye. The eyes texture holds only the eye pixels and is drawn full-bright over the
 * body, ignoring the body's transparency, so the eye stays visible while the rest fades. The
 * glow is faint and drifts slowly.
 */
public class GhostfreakEyesLayer extends RenderLayer<GhostfreakEntity, GhostfreakModel<GhostfreakEntity>> {
    private static final RenderType EYES = RenderType.eyes(
            new ResourceLocation(GhostfreakMod.MODID, "textures/entity/ghostfreak_eyes.png"));

    public GhostfreakEyesLayer(RenderLayerParent<GhostfreakEntity, GhostfreakModel<GhostfreakEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, GhostfreakEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        float glow = 0.5F + 0.12F * Mth.sin(ageInTicks * 0.08F);
        GhostfreakModel<GhostfreakEntity> model = getParentModel();
        model.setEyePass(true);
        model.renderToBuffer(poseStack, buffer.getBuffer(EYES), 15728640, OverlayTexture.NO_OVERLAY,
                glow, glow, glow, 1.0F);
        model.setEyePass(false);
    }
}
