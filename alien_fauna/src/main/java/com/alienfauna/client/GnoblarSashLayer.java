package com.alienfauna.client;

import com.alienfauna.entity.GnoblarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.DyeColor;

/** The dyed sash: a grey copy of the outfit's coloured part, drawn over the original in the dye's colour, like a wolf's collar. */
public class GnoblarSashLayer extends RenderLayer<GnoblarEntity, GnoblarModel<GnoblarEntity>> {
    public GnoblarSashLayer(RenderLayerParent<GnoblarEntity, GnoblarModel<GnoblarEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, GnoblarEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        DyeColor dye = entity.getSashColor();
        if (dye != null && !entity.isInvisible()) {
            float[] rgb = dye.getTextureDiffuseColors();
            renderColoredCutoutModel(getParentModel(), entity.getVariant().sashTexture(), poseStack, buffer, packedLight,
                    entity, rgb[0], rgb[1], rgb[2]);
        }
    }
}
