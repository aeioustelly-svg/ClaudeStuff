package com.dacianmod.draco.preview.ectonurite;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Candidate A, "Faithful": about 2.4 blocks tall, the proportions of the reference art. A hooded
 * head on a narrow shoulder line, long arms that hang past the hips, and a body that tapers into a
 * tail ending in a flat ribbon. Texture 64x64.
 */
public class EctonuriteModelA extends EctonuriteBase {
    private final ModelPart torso, head, armR, armL;
    private final ModelPart[] chain;   // torso down to the tail tip
    private final ModelPart[] clawsR, clawsL;

    public EctonuriteModelA(ModelPart root) {
        super(root);
        torso = root.getChild("torso");
        head = torso.getChild("head");
        armR = torso.getChild("arm_r");
        armL = torso.getChild("arm_l");
        ModelPart mid = torso.getChild("mid");
        ModelPart hip = mid.getChild("hip");
        ModelPart tail1 = hip.getChild("tail1");
        ModelPart tail2 = tail1.getChild("tail2");
        ModelPart tip = tail2.getChild("tip");
        chain = new ModelPart[]{torso, mid, hip, tail1, tail2, tip};
        clawsR = new ModelPart[]{armR.getChild("c0"), armR.getChild("c1"), armR.getChild("c2")};
        clawsL = new ModelPart[]{armL.getChild("c0"), armL.getChild("c1"), armL.getChild("c2")};
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        TexPacker tex = new TexPacker(64);

        // Chain, every pivot at the top edge of its box. Torso top sits at y = -5, the tip ends at y = 24.
        PartDefinition torso = box(root, "torso", tex, -4, 0, -2, 8, 6, 4, PartPose.offset(0, -5, 0));
        PartDefinition mid = box(torso, "mid", tex, -3, 0, -2, 6, 6, 4, PartPose.offset(0, 6, 0));
        PartDefinition hip = box(mid, "hip", tex, -3, 0, -1, 6, 4, 2, PartPose.offset(0, 6, 0));
        PartDefinition tail1 = box(hip, "tail1", tex, -2, 0, -1, 4, 4, 2, PartPose.offset(0, 4, 0));
        PartDefinition tail2 = box(tail1, "tail2", tex, -1, 0, -1, 2, 4, 2, PartPose.offset(0, 4, 0));
        box(tail2, "tip", tex, -1, 0, 0, 2, 5, 0, PartPose.offset(0, 4, 0));          // flat ribbon

        // Head: one tall hooded box, so there is no seam to read as a split. The slope of the
        // reference hood is left to the texture shading.
        box(torso, "head", tex, -3, -9, -3, 6, 9, 5, PartPose.ZERO);

        // Arms: one box from the shoulder, three flat claw planes at the hand.
        arm(torso, "arm_r", tex, 5, -0.15F);
        arm(torso, "arm_l", tex, -5, 0.15F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void arm(PartDefinition torso, String name, TexPacker tex, float x, float splay) {
        PartDefinition arm = box(torso, name, tex, -1, 0, -1, 2, 15, 2, PartPose.offset(x, 1, 0));
        // Claws start one pixel inside the arm end and stay within its width, so they come out of
        // the hand instead of hanging from it. The splay opens them up towards the tips.
        for (int i = 0; i < 3; i++) {
            box(arm, "c" + i, tex, 0, 0, 0, 1, 6, 0,
                    PartPose.offsetAndRotation(-1 + 0.5F * i, 14, i - 1, 0.25F, 0, (i - 1) * 0.4F + splay * 0.5F));
        }
    }

    @Override
    public void applyPose(float t, float move, float headYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);

        torso.y += Mth.sin(t * 0.1F) * 1.2F;                     // hover bob
        torso.xRot += 0.06F + 0.3F * move;                       // leans into the direction of travel
        sway(chain, t * 0.12F + move, 0.05F + 0.06F * move);

        head.yRot += Mth.clamp(headYaw, -50, 50) * Mth.DEG_TO_RAD;
        head.xRot += Mth.clamp(headPitch, -35, 35) * Mth.DEG_TO_RAD - torso.xRot * 0.7F;

        // Arms trail behind when moving and drift gently when still.
        armR.zRot += -0.18F + Mth.sin(t * 0.09F) * 0.06F;
        armL.zRot += 0.18F - Mth.sin(t * 0.09F + 1.0F) * 0.06F;
        armR.xRot += 0.15F + 0.5F * move + Mth.cos(t * 0.07F) * 0.05F;
        armL.xRot += 0.15F + 0.5F * move + Mth.cos(t * 0.07F + 1.0F) * 0.05F;
        for (ModelPart claw : clawsR) claw.xRot += Mth.sin(t * 0.15F) * 0.1F;
        for (ModelPart claw : clawsL) claw.xRot += Mth.sin(t * 0.15F + 2.0F) * 0.1F;
    }
}
