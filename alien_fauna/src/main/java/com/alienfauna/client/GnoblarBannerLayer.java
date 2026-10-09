package com.alienfauna.client;

import com.alienfauna.entity.GnoblarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** A banner worn as a hat, drawn the way vanilla draws a helmet item, scaled to a gnoblar's head. */
public class GnoblarBannerLayer extends RenderLayer<GnoblarEntity, GnoblarModel<GnoblarEntity>> {
    private final ItemInHandRenderer itemRenderer;

    public GnoblarBannerLayer(RenderLayerParent<GnoblarEntity, GnoblarModel<GnoblarEntity>> parent,
                              ItemInHandRenderer itemRenderer) {
        super(parent);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, GnoblarEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack hat = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (hat.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        getParentModel().translateToHead(poseStack);
        // the vanilla helmet transform is translate(0, -0.25, 0), turn 180 degrees, scale(0.625, -0.625, -0.625) for a
        // head 8 px tall; this head is 6 px tall and 8 wide, so it sits a little lower and smaller
        poseStack.translate(0.0F, -0.1875F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.5F, -0.5F, -0.5F);
        itemRenderer.renderItem(entity, hat, ItemDisplayContext.HEAD, false, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
