package com.alienfauna.client;

import com.alienfauna.AlienFauna;
import com.alienfauna.entity.CannonboltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * Vanilla-style Cannonbolt, built from the references and the character description.
 *
 * Standing: one wide white torso that carries the face (no separate head and no neck), a rounded
 * yellow dome on each shoulder, three yellow bands across the upper back like an armadillo's
 * shell, long one-box arms with a round yellow plate on the outside of the forearm, short
 * one-box legs with a round plate on the outside of the thigh, and flat claws on hands and toes.
 * Curled up it is a rounded yellow ball built from three crossing bars that spins by the
 * distance rolled.
 *
 * The round plates are thin slabs painted as discs with a thick black border and transparent
 * corners. Texture is 128x128 and painted by tools/paint_cannonbolt.py, which mirrors the boxes
 * below. Keep the two in step when changing a box.
 */
public class CannonboltModel<T extends CannonboltEntity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(AlienFauna.MODID, "cannonbolt"), "main");

    private final ModelPart body, rightArm, leftArm, rightDome, leftDome, rightLeg, leftLeg, ball;

    public CannonboltModel(ModelPart root) {
        this.body = root.getChild("body");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightDome = body.getChild("right_dome");
        this.leftDome = body.getChild("left_dome");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.ball = root.getChild("ball");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Feet are at y = 24, the hips at y = 17, the shoulders at y = 6.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 72).addBox(-8.0F, -14.0F, -5.0F, 16, 14, 10)
                        // three bands across the upper back with a gap between each
                        .texOffs(92, 72).addBox(-8.0F, -14.0F, 5.0F, 16, 4, 2)
                        .texOffs(92, 72).addBox(-8.0F, -9.0F, 5.0F, 16, 4, 2)
                        .texOffs(92, 72).addBox(-8.0F, -4.0F, 5.0F, 16, 4, 2),
                PartPose.offset(0.0F, 17.0F, 0.0F));

        // Shoulder domes: one box each, resting on the arm and against the torso, tilted slightly
        // outwards about the inner bottom corner so they never cut into the torso.
        body.addOrReplaceChild("right_dome",
                CubeListBuilder.create().texOffs(56, 72).addBox(-9.0F, -6.0F, -4.0F, 9, 6, 8),
                PartPose.offsetAndRotation(-8.0F, -12.0F, 0.0F, 0.0F, 0.0F, -0.15F));
        body.addOrReplaceChild("left_dome",
                CubeListBuilder.create().mirror().texOffs(56, 72).addBox(0.0F, -6.0F, -4.0F, 9, 6, 8),
                PartPose.offsetAndRotation(8.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.15F));

        // Arms: one long box from the shoulder, a round plate on the outside of the forearm and a
        // flat card of three claws hanging flush from the front of the hand.
        body.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 98).addBox(-3.0F, -1.0F, -3.0F, 6, 15, 6)
                        .texOffs(0, 120).addBox(-4.0F, 14.0F, -3.0F, 8, 3, 0)
                        .texOffs(56, 88).addBox(-5.0F, 5.0F, -4.0F, 2, 8, 8),
                PartPose.offset(-11.0F, -11.0F, 0.0F));
        body.addOrReplaceChild("left_arm",
                CubeListBuilder.create().mirror().texOffs(0, 98).addBox(-3.0F, -1.0F, -3.0F, 6, 15, 6)
                        .texOffs(0, 120).addBox(-4.0F, 14.0F, -3.0F, 8, 3, 0)
                        .texOffs(56, 88).addBox(3.0F, 5.0F, -4.0F, 2, 8, 8),
                PartPose.offset(11.0F, -11.0F, 0.0F));

        // Legs: one box from the hip, a round plate on the outside of the thigh, flat claws on the toes.
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(24, 98).addBox(-3.0F, 0.0F, -3.0F, 6, 7, 6)
                        .texOffs(20, 120).addBox(-3.0F, 7.0F, -5.0F, 6, 0, 2)
                        .texOffs(80, 88).addBox(-5.0F, 0.0F, -3.0F, 2, 6, 6),
                PartPose.offset(-4.0F, 17.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().mirror().texOffs(24, 98).addBox(-3.0F, 0.0F, -3.0F, 6, 7, 6)
                        .texOffs(20, 120).addBox(-3.0F, 7.0F, -5.0F, 6, 0, 2)
                        .texOffs(80, 88).addBox(3.0F, 0.0F, -3.0F, 2, 6, 6),
                PartPose.offset(4.0F, 17.0F, 0.0F));

        // The ball: three bars crossing at the centre, which together make a cube with chamfered
        // edges, 20 pixels across. Each pair of bars has different cross-sections, so no two faces
        // share a plane.
        root.addOrReplaceChild("ball",
                CubeListBuilder.create()
                        .texOffs(0, 34).addBox(-10.0F, -8.0F, -7.0F, 20, 16, 14)
                        .texOffs(68, 34).addBox(-7.0F, -10.0F, -8.0F, 14, 20, 16)
                        .texOffs(0, 0).addBox(-8.0F, -7.0F, -10.0F, 16, 14, 20),
                PartPose.offset(0.0F, 14.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        float partialTick = ageInTicks - entity.tickCount;
        applyPose(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
                entity.getRollAnim(partialTick), entity.getRollAngle(partialTick),
                entity.getDizzyAnim(partialTick), entity.getSitAnim(partialTick));
    }

    /** Pure function of its inputs so the preview tool can pose the model without an entity. */
    public void applyPose(float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch, float roll, float rollAngle,
                          float dizzy, float sit) {
        for (ModelPart part : new ModelPart[]{body, rightArm, leftArm, rightDome, leftDome, rightLeg, leftLeg, ball}) {
            part.resetPose();
        }

        // Standing parts curl up over the first half of the roll animation, then the ball takes over.
        float curl = Math.min(1.0F, roll * 2.0F);
        boolean curled = roll > 0.5F;
        body.visible = !curled;
        rightLeg.visible = !curled;
        leftLeg.visible = !curled;
        ball.visible = curled;
        float ballScale = 0.75F + 0.25F * Math.min(1.0F, (roll - 0.5F) * 2.0F);
        ball.xScale = ballScale;
        ball.yScale = ballScale;
        ball.zScale = ballScale;
        ball.xRot = rollAngle;
        ball.y += (1.0F - ballScale) * 10.0F;

        // Heavy walk: slow strides, arms against the legs, a little sway.
        float walk = limbSwingAmount * (1.0F - sit);
        float stride = limbSwing * 0.5F;
        rightLeg.xRot = Mth.cos(stride) * 1.1F * walk;
        leftLeg.xRot = -Mth.cos(stride) * 1.1F * walk;
        rightArm.xRot = -Mth.cos(stride) * 0.6F * walk + Mth.sin(ageInTicks * 0.067F) * 0.04F;
        leftArm.xRot = Mth.cos(stride) * 0.6F * walk - Mth.sin(ageInTicks * 0.067F) * 0.04F;
        rightArm.zRot = 0.04F + Mth.cos(ageInTicks * 0.09F) * 0.02F;
        leftArm.zRot = -rightArm.zRot;
        body.zRot = Mth.cos(stride) * 0.05F * walk;
        body.y += Math.abs(Mth.cos(stride)) * -1.0F * walk;

        // No head: the whole torso turns a little towards what it looks at.
        body.yRot = Mth.clamp(netHeadYaw, -45.0F, 45.0F) * Mth.DEG_TO_RAD * 0.3F;

        // Sitting: lowered onto the haunches, legs forward, arms resting.
        body.y += 6.0F * sit;
        rightLeg.y += 6.0F * sit;
        leftLeg.y += 6.0F * sit;
        rightLeg.xRot -= 1.45F * sit;
        leftLeg.xRot -= 1.45F * sit;
        rightArm.xRot -= 0.5F * sit;
        leftArm.xRot -= 0.5F * sit;

        // Dizzy: slumped, head lolling, arms swinging loosely.
        body.xRot += 0.2F * dizzy;
        body.yRot += Mth.cos(ageInTicks * 0.3F) * 0.2F * dizzy;
        body.zRot += Mth.sin(ageInTicks * 0.4F) * 0.1F * dizzy;
        rightArm.xRot += Mth.sin(ageInTicks * 0.4F) * 0.15F * dizzy;
        leftArm.xRot -= Mth.sin(ageInTicks * 0.4F) * 0.15F * dizzy;

        // Curling up: hunched forward, legs and arms pulled in, dropping towards the ball's centre.
        body.xRot += 0.9F * curl;
        rightLeg.xRot -= 1.4F * curl;
        leftLeg.xRot -= 1.4F * curl;
        rightArm.xRot -= 1.2F * curl;
        leftArm.xRot -= 1.2F * curl;
        body.y += 3.0F * curl;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        body.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        rightLeg.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        leftLeg.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        ball.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
