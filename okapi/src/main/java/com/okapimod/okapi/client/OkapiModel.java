package com.okapimod.okapi.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.okapimod.okapi.OkapiMod;
import com.okapimod.okapi.entity.OkapiEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Vanilla-style okapi: a horse-sized body on four single-box legs, a long neck, a narrow head with
 * two ossicones and large flat ears, and a tongue that is scaled out along the head. Texture is 128x64.
 * Ground is at y = 24 in model space. Front of the animal is -Z.
 */
public class OkapiModel<T extends OkapiEntity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(OkapiMod.MODID, "okapi"), "main");

    private final ModelPart root;
    private final ModelPart body, neck, head, tongue, earLeft, earRight, tail;
    private final ModelPart legFrontRight, legFrontLeft, legRearRight, legRearLeft;

    public OkapiModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.head = neck.getChild("head");
        this.tongue = head.getChild("tongue");
        this.earLeft = head.getChild("ear_left");
        this.earRight = head.getChild("ear_right");
        this.tail = body.getChild("tail");
        this.legFrontRight = body.getChild("leg_front_right");
        this.legFrontLeft = body.getChild("leg_front_left");
        this.legRearRight = body.getChild("leg_rear_right");
        this.legRearLeft = body.getChild("leg_rear_left");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -10.0F, 10, 10, 20),
                PartPose.ZERO);

        // ---- legs: one box each, swinging from the hip or shoulder ----
        body.addOrReplaceChild("leg_front_right",
                CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4, 14, 4),
                PartPose.offset(-3.0F, 10.0F, -7.0F));
        body.addOrReplaceChild("leg_front_left",
                CubeListBuilder.create().texOffs(16, 32).addBox(-2.0F, 0.0F, -2.0F, 4, 14, 4),
                PartPose.offset(3.0F, 10.0F, -7.0F));
        body.addOrReplaceChild("leg_rear_right",
                CubeListBuilder.create().texOffs(32, 32).addBox(-2.0F, 0.0F, -2.0F, 4, 14, 4),
                PartPose.offset(-3.0F, 10.0F, 7.0F));
        body.addOrReplaceChild("leg_rear_left",
                CubeListBuilder.create().texOffs(48, 32).addBox(-2.0F, 0.0F, -2.0F, 4, 14, 4),
                PartPose.offset(3.0F, 10.0F, 7.0F));

        // ---- tail ----
        body.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(62, 20).addBox(-1.0F, 0.0F, 0.0F, 2, 9, 2),
                PartPose.offsetAndRotation(0.0F, 1.0F, 10.0F, 0.35F, 0.0F, 0.0F));

        // ---- neck and head ----
        PartDefinition neck = body.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(62, 0).addBox(-2.0F, -14.0F, -2.0F, 4, 14, 4),
                PartPose.offsetAndRotation(0.0F, 2.0F, -9.0F, 0.5F, 0.0F, 0.0F));

        PartDefinition head = neck.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(80, 0).addBox(-3.0F, -3.0F, -6.0F, 6, 6, 6)       // skull
                        .texOffs(80, 12).addBox(-2.0F, -1.0F, -12.0F, 4, 4, 6)     // muzzle
                        .texOffs(104, 0).addBox(-3.0F, -6.0F, -4.0F, 2, 3, 2)      // ossicone
                        .texOffs(104, 5).addBox(1.0F, -6.0F, -4.0F, 2, 3, 2),      // ossicone
                PartPose.offsetAndRotation(0.0F, -13.0F, 0.0F, -0.3F, 0.0F, 0.0F));

        // Big flat ears held out to the sides.
        head.addOrReplaceChild("ear_left",
                CubeListBuilder.create().texOffs(112, 0).addBox(-2.0F, -5.0F, 0.0F, 4, 5, 1),
                PartPose.offsetAndRotation(3.0F, -3.0F, -1.0F, 0.0F, 0.0F, 0.75F));
        head.addOrReplaceChild("ear_right",
                CubeListBuilder.create().texOffs(112, 6).addBox(-2.0F, -5.0F, 0.0F, 4, 5, 1),
                PartPose.offsetAndRotation(-3.0F, -3.0F, -1.0F, 0.0F, 0.0F, -0.75F));

        // The tongue starts at the tip of the muzzle and is scaled out along the head.
        head.addOrReplaceChild("tongue",
                CubeListBuilder.create().texOffs(80, 24).addBox(-1.0F, 0.0F, -12.0F, 2, 1, 12),
                PartPose.offset(0.0F, 2.0F, -12.0F));

        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        float partialTick = ageInTicks - entity.tickCount;
        applyPose(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
                entity.getTongueAnim(partialTick), entity.getSitAnim(partialTick), entity.isBaby());
    }

    /** Pure function of its inputs so the preview tool can pose the model without an entity. */
    public void applyPose(float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch, float tongueOut, float sit, boolean baby) {
        root.getAllParts().forEach(ModelPart::resetPose);

        // Walk: diagonal pairs, like the vanilla cow and horse.
        float swing = Mth.cos(limbSwing * 0.6662F) * 1.1F * limbSwingAmount;
        float counter = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.1F * limbSwingAmount;
        legFrontRight.xRot = counter * (1.0F - sit);
        legFrontLeft.xRot = swing * (1.0F - sit);
        legRearRight.xRot = swing * (1.0F - sit);
        legRearLeft.xRot = counter * (1.0F - sit);

        // Lying down: the body sinks onto folded legs.
        body.y += 10.0F * sit;
        legFrontRight.xRot += -1.5F * sit;
        legFrontLeft.xRot += -1.5F * sit;
        legRearRight.xRot += -1.5F * sit;
        legRearLeft.xRot += -1.5F * sit;

        // The head follows the look direction, split between neck and head.
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        neck.yRot = yaw * 0.4F;
        head.yRot = yaw * 0.6F;
        neck.xRot += pitch * 0.3F + Mth.sin(ageInTicks * 0.07F) * 0.02F;
        head.xRot += pitch * 0.7F;

        // Reaching: the neck stretches up and the tongue unrolls.
        neck.xRot += 0.25F * tongueOut;
        head.xRot -= 0.35F * tongueOut;
        tongue.visible = tongueOut > 0.04F;
        tongue.zScale = Math.max(tongueOut, 0.01F);
        tongue.yScale = 1.0F;
        tongue.xScale = 1.0F;

        // Ears flick now and then.
        float flick = Mth.sin(ageInTicks * 0.11F) * Mth.sin(ageInTicks * 0.37F);
        earLeft.zRot += flick * 0.12F;
        earRight.zRot -= flick * 0.12F;

        tail.xRot += Mth.sin(ageInTicks * 0.09F) * 0.06F;
        tail.zRot += Mth.sin(ageInTicks * 0.13F) * 0.08F;

        // Calves have proportionally bigger heads.
        float headScale = baby ? 1.35F : 1.0F;
        head.xScale = headScale;
        head.yScale = headScale;
        head.zScale = headScale;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
