package com.alienfauna.client;

import com.alienfauna.AlienFauna;
import com.alienfauna.entity.GnoblarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Mud splashed on the legs, arms, belly and face of a gnoblar that has been digging or wading in a swamp. */
public class GnoblarMudLayer extends RenderLayer<GnoblarEntity, GnoblarModel<GnoblarEntity>> {
    private static final ResourceLocation MUD = new ResourceLocation(AlienFauna.MODID, "textures/entity/gnoblar_mud.png");

    public GnoblarMudLayer(RenderLayerParent<GnoblarEntity, GnoblarModel<GnoblarEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, GnoblarEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isMuddy() && !entity.isInvisible()) {
            renderColoredCutoutModel(getParentModel(), MUD, poseStack, buffer, packedLight, entity, 1.0F, 1.0F, 1.0F);
        }
    }
}
