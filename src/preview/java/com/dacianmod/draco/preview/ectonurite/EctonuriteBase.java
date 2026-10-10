package com.dacianmod.draco.preview.ectonurite;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Shared plumbing for the candidate Ectonurite models. All three are vanilla-style (no GeckoLib):
 * a vertical chain of boxes, each pivoting at its top edge and carrying everything below it, with
 * single-box arms and zero-thickness planes for the claws and the tail tip.
 */
abstract class EctonuriteBase extends EntityModel<Entity> {
    protected final ModelPart root;

    EctonuriteBase(ModelPart root) {
        this.root = root;
    }

    /** Pure function of its inputs, so the preview tool can pose the model without an entity. */
    public abstract void applyPose(float ageInTicks, float moveAmount, float headYaw, float headPitch);

    @Override
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        applyPose(ageInTicks, limbSwingAmount, netHeadYaw, headPitch);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** Adds one box, taking its texture offset from the packer. */
    static PartDefinition box(PartDefinition parent, String name, TexPacker tex,
                              float x, float y, float z, int dx, int dy, int dz, PartPose pose) {
        int[] o = tex.next(dx, dy, dz);
        return parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(o[0], o[1]).addBox(x, y, z, dx, dy, dz), pose);
    }

    /** Sideways sway travelling down a chain: later segments lag behind and swing wider. */
    static void sway(ModelPart[] chain, float time, float amplitude) {
        for (int i = 0; i < chain.length; i++) {
            float growth = 1.0F + i * 0.18F;
            chain[i].zRot += Mth.sin(time - i * 0.7F) * amplitude * growth;
            chain[i].xRot += Mth.cos(time * 0.8F - i * 0.7F) * amplitude * 0.6F * growth;
        }
    }
}
