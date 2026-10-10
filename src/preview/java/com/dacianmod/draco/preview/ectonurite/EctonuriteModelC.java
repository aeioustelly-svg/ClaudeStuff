package com.dacianmod.draco.preview.ectonurite;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Candidate C, "Stalker": the looming pose from the second reference. The body is long (about 48
 * pixels end to end) and held at a forward slant, with a lopsided arm pose, one hand raised high
 * and the other reaching low. The most menacing of the three, and the one that reads best as a
 * wild, shy haunter. Texture 128x64.
 */
public class EctonuriteModelC extends EctonuriteBase {
    private final ModelPart torso, head, armR, armL;
    private final ModelPart[] chain;
    private final ModelPart[] clawsR, clawsL;

    public EctonuriteModelC(ModelPart root) {
        super(root);
        torso = root.getChild("torso");
        head = torso.getChild("head");
        armR = torso.getChild("arm_r");
        armL = torso.getChild("arm_l");
        ModelPart mid = torso.getChild("mid");
        ModelPart hip = mid.getChild("hip");
        ModelPart tail1 = hip.getChild("tail1");
        ModelPart tail2 = tail1.getChild("tail2");
        ModelPart tail3 = tail2.getChild("tail3");
        ModelPart tip = tail3.getChild("tip");
        chain = new ModelPart[]{torso, mid, hip, tail1, tail2, tail3, tip};
        clawsR = new ModelPart[]{armR.getChild("c0"), armR.getChild("c1"), armR.getChild("c2")};
        clawsL = new ModelPart[]{armL.getChild("c0"), armL.getChild("c1"), armL.getChild("c2")};
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        TexPacker tex = new TexPacker(128);

        PartDefinition torso = box(root, "torso", tex, -4, 0, -2, 8, 8, 4, PartPose.offset(0, -4, 0));
        PartDefinition mid = box(torso, "mid", tex, -3, 0, -2, 6, 8, 4, PartPose.offset(0, 8, 0));
        PartDefinition hip = box(mid, "hip", tex, -3, 0, -1, 6, 6, 3, PartPose.offset(0, 8, 0));
        PartDefinition tail1 = box(hip, "tail1", tex, -2, 0, -1, 4, 6, 2, PartPose.offset(0, 6, 0));
        PartDefinition tail2 = box(tail1, "tail2", tex, -2, 0, -1, 4, 6, 2, PartPose.offset(0, 6, 0));
        PartDefinition tail3 = box(tail2, "tail3", tex, -1, 0, -1, 2, 6, 2, PartPose.offset(0, 6, 0));
        box(tail3, "tip", tex, -1, 0, 0, 2, 8, 0, PartPose.offset(0, 6, 0));

        // Hood pushed forward over the face, with a long swept-back rear.
        PartDefinition head = box(torso, "head", tex, -3, -6, -4, 6, 6, 6, PartPose.ZERO);
        box(head, "hood", tex, -3, -3, 0, 6, 3, 8, PartPose.offsetAndRotation(0, -5, -4, 0.3F, 0, 0));

        arm(torso, "arm_r", tex, 5);
        arm(torso, "arm_l", tex, -5);

        return LayerDefinition.create(mesh, 128, 64);
    }

    private static void arm(PartDefinition torso, String name, TexPacker tex, float x) {
        PartDefinition arm = box(torso, name, tex, -1, 0, -1, 2, 20, 2, PartPose.offset(x, 1, 0));
        for (int i = 0; i < 3; i++) {
            box(arm, "c" + i, tex, 0, 0, 0, 1, 8, 0,
                    PartPose.offsetAndRotation(-1 + i, 20, i - 1, 0.35F, 0, (i - 1) * 0.45F));
        }
    }

    @Override
    public void applyPose(float t, float move, float headYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);

        // Body slants forward and the tail curls back under it, as in the crouching reference.
        torso.y += Mth.sin(t * 0.08F) * 1.0F;
        torso.xRot += 0.75F + 0.2F * move;
        chain[1].xRot += -0.2F;
        chain[2].xRot += -0.4F;
        chain[3].xRot += -0.45F;
        chain[4].xRot += -0.3F;
        chain[5].xRot += -0.2F;
        sway(chain, t * 0.1F + move, 0.04F + 0.05F * move);

        head.yRot += Mth.clamp(headYaw, -50, 50) * Mth.DEG_TO_RAD;
        head.xRot += Mth.clamp(headPitch, -35, 35) * Mth.DEG_TO_RAD - 0.6F;

        // Right arm raised with the claws up, left arm reaching low and forward.
        armR.xRot += -3.3F + Mth.sin(t * 0.06F) * 0.08F;      // torso slant included, so the hand ends up high
        armR.zRot += -0.2F;
        armL.xRot += -1.8F + Mth.sin(t * 0.06F + 1.5F) * 0.08F;
        armL.zRot += 0.15F;
        for (ModelPart claw : clawsR) claw.xRot += Mth.sin(t * 0.12F) * 0.12F - 0.2F;
        for (ModelPart claw : clawsL) claw.xRot += Mth.sin(t * 0.12F + 2.0F) * 0.12F;
    }
}
