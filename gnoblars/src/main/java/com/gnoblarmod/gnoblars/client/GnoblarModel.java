package com.gnoblarmod.gnoblars.client;

import com.gnoblarmod.gnoblars.Gnoblars;
import com.gnoblarmod.gnoblars.entity.GnoblarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ArmedModel;
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
import net.minecraft.world.entity.HumanoidArm;

/**
 * Vanilla-style gnoblar: a hunched little body, an oversized head with a huge hooked nose, flat
 * drooping ears, and long single-box arms. Texture is 64x64, one texel per 1/16 block.
 *
 * Every part is baked unrotated so the texture painter can read axis-aligned geometry. All the
 * rotation lives in {@link #applyPose}, which is a pure function of its inputs.
 */
public class GnoblarModel<T extends GnoblarEntity> extends EntityModel<T> implements ArmedModel {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(Gnoblars.MODID, "gnoblar"), "main");

    private static final float HUNCH = 0.5F;

    private final ModelPart root;
    private final ModelPart body, head, nose, leftEar, rightEar, leftArm, rightArm, leftLeg, rightLeg;

    public GnoblarModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.nose = head.getChild("nose");
        this.leftEar = head.getChild("left_ear");
        this.rightEar = head.getChild("right_ear");
        this.leftArm = body.getChild("left_arm");
        this.rightArm = body.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, -5.0F, -2.0F, 6, 5, 4),
                PartPose.offset(0.0F, 21.0F, 0.0F));
        // a rag hanging from the belt
        body.addOrReplaceChild("loincloth",
                CubeListBuilder.create().texOffs(44, 16).addBox(-2.0F, 0.0F, 0.0F, 4, 2, 0),
                PartPose.offset(0.0F, 0.0F, -2.0F));

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -6.0F, -3.0F, 8, 6, 6),
                PartPose.offset(0.0F, -4.0F, -1.0F));
        // the nose: a long block with a drooping hook at the tip
        head.addOrReplaceChild("nose",
                CubeListBuilder.create()
                        .texOffs(28, 0).addBox(-2.0F, -4.0F, -8.0F, 4, 3, 5)
                        .texOffs(46, 0).addBox(-2.0F, -1.0F, -8.0F, 4, 2, 2),
                PartPose.ZERO);
        // flat, see-through-thin ears
        head.addOrReplaceChild("left_ear",
                CubeListBuilder.create().texOffs(0, 12).addBox(0.0F, 0.0F, -2.0F, 6, 0, 4),
                PartPose.offset(4.0F, -3.0F, 0.0F));
        head.addOrReplaceChild("right_ear",
                CubeListBuilder.create().texOffs(20, 12).addBox(-6.0F, 0.0F, -2.0F, 6, 0, 4),
                PartPose.offset(-4.0F, -3.0F, 0.0F));

        // long arms, one box each, swinging from the shoulder
        body.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(28, 16).addBox(-1.0F, -1.0F, -1.0F, 2, 8, 2),
                PartPose.offset(-4.0F, -4.0F, 0.0F));
        body.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(36, 16).addBox(-1.0F, -1.0F, -1.0F, 2, 8, 2),
                PartPose.offset(4.0F, -4.0F, 0.0F));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 25).addBox(-1.0F, 0.0F, -1.0F, 2, 3, 2),
                PartPose.offset(-2.0F, 21.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(8, 25).addBox(-1.0F, 0.0F, -1.0F, 2, 3, 2),
                PartPose.offset(2.0F, 21.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        applyPose(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
                entity.isInSittingPose() ? 1.0F : 0.0F,
                entity.isSniffing() ? 1.0F : 0.0F,
                entity.isScared() ? 1.0F : 0.0F);
    }

    /** Pure function of its inputs; the preview tool calls it directly. sit, sniff, scared are 0 or 1. */
    public void applyPose(float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch, float sit, float sniff, float scared) {
        float walk = Mth.cos(limbSwing * 0.6662F);
        float walkOpposite = Mth.cos(limbSwing * 0.6662F + Mth.PI);
        float idleSway = Mth.sin(ageInTicks * 0.09F) * 0.05F + 0.05F;

        // ---- reset ----
        body.setPos(0.0F, 21.0F, 0.0F);
        body.xRot = HUNCH;
        head.xRot = -0.35F;
        head.yRot = 0.0F;
        nose.xRot = 0.0F;
        leftEar.zRot = 0.95F;
        rightEar.zRot = -0.95F;
        leftEar.yRot = -0.35F;
        rightEar.yRot = 0.35F;
        leftArm.zRot = -idleSway;
        rightArm.zRot = idleSway;
        leftArm.yRot = 0.0F;
        rightArm.yRot = 0.0F;
        leftLeg.setPos(2.0F, 21.0F, 0.0F);
        rightLeg.setPos(-2.0F, 21.0F, 0.0F);
        leftLeg.yRot = 0.0F;
        rightLeg.yRot = 0.0F;

        // ---- walking: long arms swing against the legs ----
        leftLeg.xRot = walk * 1.2F * limbSwingAmount;
        rightLeg.xRot = walkOpposite * 1.2F * limbSwingAmount;
        leftArm.xRot = -0.45F + walkOpposite * 0.9F * limbSwingAmount;
        rightArm.xRot = -0.45F + walk * 0.9F * limbSwingAmount;
        // the nose twitches all the time
        nose.xRot = Mth.sin(ageInTicks * 0.2F) * 0.03F;

        // ---- head follows the look direction ----
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.8F;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.8F;
        // ears flap with the walk
        leftEar.zRot += Mth.sin(limbSwing * 0.6662F) * 0.25F * limbSwingAmount;
        rightEar.zRot -= Mth.sin(limbSwing * 0.6662F) * 0.25F * limbSwingAmount;

        // ---- sniffing: nose to the ground, arms braced ----
        if (sniff > 0.5F) {
            body.xRot = 0.95F;
            head.xRot = 0.55F + Mth.sin(ageInTicks * 0.9F) * 0.12F;
            head.yRot = 0.0F;
            nose.xRot = Mth.sin(ageInTicks * 1.4F) * 0.12F;
            leftArm.xRot = -1.1F;
            rightArm.xRot = -1.1F;
            leftEar.zRot = 1.2F;
            rightEar.zRot = -1.2F;
        }

        // ---- scared: arms flung up and flailing, ears pinned back ----
        if (scared > 0.5F) {
            float flail = Mth.sin(ageInTicks * 0.9F) * 0.5F;
            leftArm.xRot = -2.9F + flail;
            rightArm.xRot = -2.9F - flail;
            leftArm.zRot = -0.35F;
            rightArm.zRot = 0.35F;
            leftEar.zRot = 0.3F;
            rightEar.zRot = -0.3F;
            leftEar.yRot = -0.9F;
            rightEar.yRot = 0.9F;
        }

        // ---- sitting: on its bottom, legs out in front ----
        if (sit > 0.5F) {
            body.setPos(0.0F, 22.0F, 0.0F);
            body.xRot = 0.3F;
            head.xRot = -0.2F + headPitch * Mth.DEG_TO_RAD * 0.8F;
            leftLeg.setPos(2.0F, 22.0F, 0.0F);
            rightLeg.setPos(-2.0F, 22.0F, 0.0F);
            leftLeg.xRot = -1.5F;
            rightLeg.xRot = -1.5F;
            leftLeg.yRot = -0.25F;
            rightLeg.yRot = 0.25F;
            leftArm.xRot = -0.9F;
            rightArm.xRot = -0.9F;
        }
    }

    @Override
    public void translateToHand(HumanoidArm side, PoseStack poseStack) {
        ModelPart arm = side == HumanoidArm.RIGHT ? rightArm : leftArm;
        body.translateAndRotate(poseStack);
        arm.translateAndRotate(poseStack);
        // The item layer expects a full-size arm; shrink so a held item sits in a small hand.
        poseStack.scale(0.65F, 0.65F, 0.65F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
