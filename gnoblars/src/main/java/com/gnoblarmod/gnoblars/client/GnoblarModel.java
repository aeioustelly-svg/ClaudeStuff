package com.gnoblarmod.gnoblars.client;

import com.gnoblarmod.gnoblars.Gnoblars;
import com.gnoblarmod.gnoblars.entity.GnoblarEntity;
import com.gnoblarmod.gnoblars.entity.GnoblarVariant;
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
 * Vanilla-style gnoblar: a hunched little body, an oversized head with one big block of a nose, flat
 * pointed ears, and long single-box arms. Texture is 64x64, one texel per 1/16 block.
 *
 * Every part is baked unrotated so the texture painter can read axis-aligned geometry. All the
 * rotation lives in {@link #applyPose}, which is a pure function of its inputs.
 */
public class GnoblarModel<T extends GnoblarEntity> extends EntityModel<T> implements ArmedModel {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(Gnoblars.MODID, "gnoblar"), "main");

    private static final float HUNCH = 0.5F;
    // Flat parts (the ears and the loincloth) are done the way vanilla does its chicken legs: an ordinary box whose
    // texture paints ONE face and leaves every other face transparent. The cutout render drops the transparent
    // faces, so what shows is a single flat sheet with nothing coincident to z-fight. The box is 1 px thick.

    private final ModelPart root;
    private final ModelPart body, head, nose, noseWart, noseSideWart, cheekWart, foreheadWart, leftEar, rightEar, leftArm, rightArm, leftLeg, rightLeg;

    public GnoblarModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.nose = head.getChild("nose");
        this.noseWart = nose.getChild("wart_nose");
        this.noseSideWart = nose.getChild("wart_nose_side");
        this.cheekWart = head.getChild("wart_cheek");
        this.foreheadWart = head.getChild("wart_forehead");
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
                CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -5.0F, -2.0F, 8, 5, 4),
                PartPose.offset(0.0F, 21.0F, 0.0F));
        // a rag hanging from the belt, its front face level with the front of the body (only that face is painted)
        body.addOrReplaceChild("loincloth",
                CubeListBuilder.create().texOffs(44, 16).addBox(-2.0F, 0.0F, 0.0F, 4, 2, 1),
                PartPose.offset(0.0F, 0.0F, -2.0F));

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -6.0F, -3.0F, 8, 6, 6),
                PartPose.offset(0.0F, -6.0F, -1.0F));
        // a thin neck between the shoulders and the big head
        body.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(44, 20).addBox(-1.0F, -6.0F, -2.0F, 2, 1, 2),
                PartPose.ZERO);
        // the nose: one big block, taller than it is deep and hanging a pixel below the chin,
        // so it reads as a nose and not as a snout
        PartDefinition nose = head.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(28, 0).addBox(-2.0F, -5.0F, -6.0F, 4, 6, 3),
                PartPose.ZERO);
        // A wart is a 1x1x1 cube. Each spot has its own cube and a variant shows one of them (or none), so every
        // look has its wart in a different place. All share one small patch of the texture.
        nose.addOrReplaceChild("wart_nose",
                CubeListBuilder.create().texOffs(44, 24).addBox(0.0F, -6.0F, -5.0F, 1, 1, 1), PartPose.ZERO);
        nose.addOrReplaceChild("wart_nose_side",
                CubeListBuilder.create().texOffs(44, 24).addBox(2.0F, -3.0F, -5.0F, 1, 1, 1), PartPose.ZERO);
        head.addOrReplaceChild("wart_cheek",
                CubeListBuilder.create().texOffs(44, 24).addBox(3.0F, -2.0F, -4.0F, 1, 1, 1), PartPose.ZERO);
        head.addOrReplaceChild("wart_forehead",
                CubeListBuilder.create().texOffs(44, 24).addBox(-3.0F, -7.0F, -2.0F, 1, 1, 1), PartPose.ZERO);

        // pointed ears: stepped boxes that climb and narrow to a tip, so the outline is not a rectangle. Only the
        // front face of each is painted (see the note on flat parts above), so each ear is one flat sheet. Its
        // texture is two-toned (skin rim, pink middle) so it looks right from the front and from behind.
        head.addOrReplaceChild("left_ear",
                CubeListBuilder.create()
                        .texOffs(0, 32).addBox(0.0F, -2.0F, 0.0F, 3, 4, 1)
                        .texOffs(8, 32).addBox(3.0F, -3.0F, 0.0F, 2, 3, 1)
                        .texOffs(14, 32).addBox(5.0F, -4.0F, 0.0F, 1, 2, 1),
                PartPose.offset(4.0F, -3.0F, 0.0F));
        head.addOrReplaceChild("right_ear",
                CubeListBuilder.create()
                        .texOffs(20, 32).addBox(-3.0F, -2.0F, 0.0F, 3, 4, 1)
                        .texOffs(28, 32).addBox(-5.0F, -3.0F, 0.0F, 2, 3, 1)
                        .texOffs(34, 32).addBox(-6.0F, -4.0F, 0.0F, 1, 2, 1),
                PartPose.offset(-4.0F, -3.0F, 0.0F));

        // long arms, one box each, swinging from the shoulder. The body is 8 wide and the legs 2+2 inset from it:
        // if a leg were flush with the body (both 6 wide) the hunch tilt would push the belt over the top of
        // the leg's side face, two coplanar faces that z-fight and flicker.
        body.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(28, 16).addBox(-1.0F, -1.0F, -1.0F, 2, 7, 2),
                PartPose.offset(-5.0F, -3.0F, 0.0F));
        body.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(36, 16).addBox(-1.0F, -1.0F, -1.0F, 2, 7, 2),
                PartPose.offset(5.0F, -3.0F, 0.0F));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 25).addBox(-1.0F, 0.0F, -1.0F, 2, 3, 2),
                PartPose.offset(-2.0F, 21.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(8, 25).addBox(-1.0F, 0.0F, -1.0F, 2, 3, 2),
                PartPose.offset(2.0F, 21.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Shows the wart cube for this spot and hides the others. A null spot shows every cube (for the preview tools). */
    public void setWartSpot(GnoblarVariant.WartSpot spot) {
        noseWart.visible = spot == null || spot == GnoblarVariant.WartSpot.NOSE_TOP;
        noseSideWart.visible = spot == null || spot == GnoblarVariant.WartSpot.NOSE_SIDE;
        cheekWart.visible = spot == null || spot == GnoblarVariant.WartSpot.CHEEK;
        foreheadWart.visible = spot == null || spot == GnoblarVariant.WartSpot.FOREHEAD;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        setWartSpot(entity.getVariant().wart());
        applyPose(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
                entity.isInSittingPose() ? 1.0F : 0.0F,
                entity.isSniffing() ? 1.0F : 0.0F,
                entity.isScared() ? 1.0F : 0.0F,
                entity.isPassenger() ? 1.0F : 0.0F,
                entity.isDancing() ? 1.0F : 0.0F,
                entity.isNapping() ? 1.0F : 0.0F);
    }

    /**
     * Pure function of its inputs; the preview tool calls it directly. sit, sniff, scared, ride, dance and sleep are 0 or 1.
     */
    public void applyPose(float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch, float sit, float sniff, float scared, float ride,
                          float dance, float sleep) {
        float walk = Mth.cos(limbSwing * 0.6662F);
        float walkOpposite = Mth.cos(limbSwing * 0.6662F + Mth.PI);
        float idleSway = Mth.sin(ageInTicks * 0.09F) * 0.05F + 0.05F;

        // ---- reset ----
        body.setPos(0.0F, 21.0F, 0.0F);
        body.xRot = HUNCH;
        head.zRot = 0.0F;
        head.xRot = -0.35F;
        head.yRot = 0.0F;
        nose.xRot = 0.0F;
        leftEar.zRot = 0.6F;
        rightEar.zRot = -0.6F;
        leftEar.yRot = -0.5F;
        rightEar.yRot = 0.5F;
        leftArm.zRot = -idleSway - 0.12F;
        rightArm.zRot = idleSway + 0.12F;
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
        head.yRot = Mth.clamp(netHeadYaw, -45.0F, 45.0F) * Mth.DEG_TO_RAD * 0.6F;
        // The hunched body already tips the head forward, so looking up is limited to level; any
        // further and the back of the head would swing down into the shoulders and arms.
        head.xRot = Math.max(-0.5F, head.xRot + headPitch * Mth.DEG_TO_RAD * 0.8F);
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
            leftArm.zRot = -0.6F;
            rightArm.zRot = 0.6F;
            leftEar.zRot = 0.8F;
            rightEar.zRot = -0.8F;
            leftEar.yRot = -0.8F;
            rightEar.yRot = 0.8F;
        }

        // ---- scared: arms flung up and flailing, ears pinned back ----
        if (scared > 0.5F) {
            float flail = Mth.sin(ageInTicks * 0.9F) * 0.5F;
            leftArm.xRot = -HUNCH + flail * 0.6F;
            rightArm.xRot = -HUNCH - flail * 0.6F;
            leftArm.zRot = -2.2F;
            rightArm.zRot = 2.2F;
            leftEar.zRot = 0.1F;
            rightEar.zRot = -0.1F;
            leftEar.yRot = -1.1F;
            rightEar.yRot = 1.1F;
        }

        // ---- sitting: on its bottom, legs out in front ----
        if (sit > 0.5F) {
            body.setPos(0.0F, 22.0F, 0.0F);
            body.xRot = 0.3F;
            head.xRot = Math.max(-0.5F, -0.2F + headPitch * Mth.DEG_TO_RAD * 0.8F);
            leftLeg.setPos(2.0F, 22.0F, 0.0F);
            rightLeg.setPos(-2.0F, 22.0F, 0.0F);
            leftLeg.xRot = -1.5F;
            rightLeg.xRot = -1.5F;
            leftLeg.yRot = -0.25F;
            rightLeg.yRot = 0.25F;
            leftArm.xRot = -0.9F;
            rightArm.xRot = -0.9F;
        }

        // ---- riding on the owner's back: sitting upright behind them, legs round their waist, arms reaching over
        // their shoulders ----
        if (ride > 0.5F) {
            body.setPos(0.0F, 22.0F, 0.0F);
            body.xRot = 0.15F;
            head.xRot = Math.max(-0.5F, -0.3F + headPitch * Mth.DEG_TO_RAD * 0.8F);
            head.yRot = Mth.clamp(netHeadYaw, -30.0F, 30.0F) * Mth.DEG_TO_RAD * 0.5F;   // arms are forward, so turn less
            leftLeg.setPos(2.0F, 22.0F, 0.0F);
            rightLeg.setPos(-2.0F, 22.0F, 0.0F);
            leftLeg.xRot = -1.2F;
            rightLeg.xRot = -1.2F;
            leftLeg.yRot = -0.6F;
            rightLeg.yRot = 0.6F;
            leftArm.xRot = -1.8F;
            rightArm.xRot = -1.8F;
            leftArm.zRot = -0.25F;
            rightArm.zRot = 0.25F;
        }

        // ---- dancing: hopping, arms up and swaying, ears flapping to the beat ----
        if (dance > 0.5F) {
            float beat = ageInTicks * 0.55F;
            float hop = Math.abs(Mth.sin(beat)) * 1.5F;
            body.setPos(0.0F, 21.0F - hop, 0.0F);
            leftLeg.setPos(2.0F, 21.0F - hop, 0.0F);
            rightLeg.setPos(-2.0F, 21.0F - hop, 0.0F);
            body.xRot = 0.2F;
            head.xRot = -0.3F + Mth.sin(beat * 2.0F) * 0.1F;
            head.zRot = Mth.sin(beat) * 0.15F;
            // hands up and out beside the head, one reaching higher as the other drops
            leftArm.xRot = -HUNCH;
            rightArm.xRot = -HUNCH;
            leftArm.zRot = -1.9F - Mth.sin(beat) * 0.4F;
            rightArm.zRot = 1.9F - Mth.sin(beat) * 0.4F;
            leftLeg.xRot = Mth.sin(beat) * 0.7F;
            rightLeg.xRot = -Mth.sin(beat) * 0.7F;
            leftEar.zRot = 0.6F + Mth.sin(beat * 2.0F) * 0.3F;
            rightEar.zRot = -0.6F - Mth.sin(beat * 2.0F) * 0.3F;
        }

        // ---- sleeping: curled up on the bed, head on the knees, breathing slowly ----
        if (sleep > 0.5F) {
            float breath = Mth.sin(ageInTicks * 0.08F) * 0.3F;
            body.setPos(0.0F, 22.0F + breath * 0.3F, 0.0F);
            body.xRot = 0.9F;
            head.xRot = 0.7F;
            head.yRot = 0.0F;
            nose.xRot = breath * 0.05F;
            leftLeg.setPos(2.0F, 22.0F, 0.0F);
            rightLeg.setPos(-2.0F, 22.0F, 0.0F);
            leftLeg.xRot = -1.5F;
            rightLeg.xRot = -1.5F;
            leftLeg.yRot = -0.25F;
            rightLeg.yRot = 0.25F;
            leftArm.xRot = -1.2F;
            rightArm.xRot = -1.2F;
            leftArm.zRot = -0.45F;
            rightArm.zRot = 0.45F;
            leftEar.zRot = 1.0F;
            rightEar.zRot = -1.0F;
        }
    }

    /** Moves the pose stack to the top of the head, for items worn there (a banner). */
    public void translateToHead(PoseStack poseStack) {
        body.translateAndRotate(poseStack);
        head.translateAndRotate(poseStack);
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
