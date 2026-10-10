package com.dacianmod.draco.preview.ectonurite;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Candidate B, "Wisp": a small companion about 1.5 blocks tall, in the manner of the Allay and the
 * Vex. A big hooded head, a short teardrop body, stubby arms with three claw planes each and a
 * short tail. Suits a tameable follower. Texture 64x64.
 */
public class EctonuriteModelB extends EctonuriteBase {
    private final ModelPart body, head, armR, armL;
    private final ModelPart[] chain;

    public EctonuriteModelB(ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = body.getChild("head");
        armR = body.getChild("arm_r");
        armL = body.getChild("arm_l");
        ModelPart mid1 = body.getChild("mid1");
        ModelPart mid2 = mid1.getChild("mid2");
        ModelPart tip = mid2.getChild("tip");
        chain = new ModelPart[]{body, mid1, mid2, tip};
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        TexPacker tex = new TexPacker(64);

        PartDefinition body = box(root, "body", tex, -3, 0, -2, 6, 5, 4, PartPose.offset(0, 9, 0));
        PartDefinition mid1 = box(body, "mid1", tex, -2, 0, -1, 4, 4, 2, PartPose.offset(0, 5, 0));
        PartDefinition mid2 = box(mid1, "mid2", tex, -1, 0, -1, 2, 3, 2, PartPose.offset(0, 4, 0));
        box(mid2, "tip", tex, -1, 0, 0, 2, 4, 0, PartPose.offset(0, 3, 0));

        // Oversized hood with a short peak.
        PartDefinition head = box(body, "head", tex, -4, -7, -4, 8, 7, 7, PartPose.ZERO);
        box(head, "hood", tex, -4, -3, 0, 8, 3, 7, PartPose.offsetAndRotation(0, -6, -4, 0.25F, 0, 0));

        arm(body, "arm_r", tex, 4, -0.2F);
        arm(body, "arm_l", tex, -4, 0.2F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void arm(PartDefinition body, String name, TexPacker tex, float x, float tilt) {
        PartDefinition arm = box(body, name, tex, -1, 0, -1, 2, 6, 2, PartPose.offsetAndRotation(x, 1, 0, 0, 0, tilt));
        for (int i = 0; i < 3; i++) {
            box(arm, "c" + i, tex, 0, 0, 0, 1, 3, 0,
                    PartPose.offsetAndRotation(-1 + i, 6, i - 1, 0.3F, 0, (i - 1) * 0.35F));
        }
    }

    @Override
    public void applyPose(float t, float move, float headYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);

        body.y += Mth.sin(t * 0.14F) * 1.5F;                     // livelier bob than the tall candidates
        body.xRot += 0.08F + 0.35F * move;
        sway(chain, t * 0.18F + move, 0.08F + 0.08F * move);

        head.yRot += Mth.clamp(headYaw, -60, 60) * Mth.DEG_TO_RAD;
        head.xRot += Mth.clamp(headPitch, -40, 40) * Mth.DEG_TO_RAD - body.xRot * 0.7F;

        // Arms flap lightly, a little like an Allay's.
        float flap = Mth.sin(t * 0.3F) * 0.15F;
        armR.zRot += 0.35F + flap + 0.2F * move;
        armL.zRot += -0.35F - flap - 0.2F * move;
    }
}
