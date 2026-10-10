package com.ghostfreakmod.ghostfreak.client;

import com.ghostfreakmod.ghostfreak.GhostfreakMod;
import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
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
 * Vanilla-style (no GeckoLib) Ectonurite: a vertical chain of boxes, each pivoting at its top
 * edge and carrying everything below it, a single-box head, single-box arms with three flat claw
 * planes each, and eight striped tentacles that are hidden until it fights or dances.
 *
 * Texture is 128x64. The body is packed from v = 0, the tentacles from v = 32, which tools/paint_texture.py
 * relies on to paint them black and white.
 */
public class GhostfreakModel<T extends GhostfreakEntity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(GhostfreakMod.MODID, "ghostfreak"), "main");

    public static final int TEX_W = 128;
    public static final int TEX_H = 64;
    public static final int TENTACLE_V = 32;
    private static final int TENTACLES = 8;
    private static final int SEGMENTS = 4;

    private final ModelPart root, torso, head, armR, armL;
    private final ModelPart[] chain;
    private final ModelPart[] clawsR, clawsL;
    private final ModelPart[][] tentacle = new ModelPart[TENTACLES][SEGMENTS];

    /** Body opacity, set per frame from the entity. The eye layer ignores it. */
    private float bodyAlpha = 1.0F;
    private boolean eyePass;

    public GhostfreakModel(ModelPart root) {
        this.root = root;
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
        for (int i = 0; i < TENTACLES; i++) {
            ModelPart part = torso.getChild("tentacle" + i);
            tentacle[i][0] = part;
            for (int k = 1; k < SEGMENTS; k++) {
                part = part.getChild("seg" + k);
                tentacle[i][k] = part;
            }
        }
    }

    private static PartDefinition box(PartDefinition parent, String name, TexPacker tex,
                                      float x, float y, float z, int dx, int dy, int dz, PartPose pose) {
        int[] o = tex.next(dx, dy, dz);
        return parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(o[0], o[1]).addBox(x, y, z, dx, dy, dz), pose);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        TexPacker tex = new TexPacker(TEX_W, 0);

        // Chain, every pivot at the top edge of its box. Torso top sits at y = -5, the tail tip ends at about y = 24.
        PartDefinition torso = box(root, "torso", tex, -4, 0, -2, 8, 6, 4, PartPose.offset(0, -5, 0));
        PartDefinition mid = box(torso, "mid", tex, -3, 0, -2, 6, 6, 4, PartPose.offset(0, 6, 0));
        PartDefinition hip = box(mid, "hip", tex, -3, 0, -1, 6, 4, 2, PartPose.offset(0, 6, 0));
        PartDefinition tail1 = box(hip, "tail1", tex, -2, 0, -1, 4, 4, 2, PartPose.offset(0, 4, 0));
        PartDefinition tail2 = box(tail1, "tail2", tex, -1, 0, -1, 2, 4, 2, PartPose.offset(0, 4, 0));
        box(tail2, "tip", tex, -1, 0, 0, 2, 5, 0, PartPose.offset(0, 4, 0));            // flat ribbon

        // Head: one tall hooded box, so there is no seam to read as a split.
        box(torso, "head", tex, -3, -9, -3, 6, 9, 5, PartPose.ZERO);

        arm(torso, "arm_r", tex, 5, -0.15F);
        arm(torso, "arm_l", tex, -5, 0.15F);

        // Tentacles: two rows of four on the back, each a chain of three boxes and a flat tip, all
        // five pixels long so the black and white bands of the texture line up with the segments.
        TexPacker tentacleTex = new TexPacker(TEX_W, TENTACLE_V);
        for (int i = 0; i < TENTACLES; i++) {
            int row = i / 4, col = i % 4;
            PartDefinition part = box(torso, "tentacle" + i, tentacleTex, -1, 0, -1, 2, 5, 2,
                    PartPose.offset(-3 + 2 * col, 2, 3 + 2 * row));
            part = box(part, "seg1", tentacleTex, -1, 0, -1, 2, 5, 2, PartPose.offset(0, 5, 0));
            part = box(part, "seg2", tentacleTex, -1, 0, -1, 2, 5, 2, PartPose.offset(0, 5, 0));
            box(part, "seg3", tentacleTex, -1, 0, 0, 2, 5, 0, PartPose.offset(0, 5, 0));
        }

        return LayerDefinition.create(mesh, TEX_W, TEX_H);
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
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        float partial = ageInTicks - entity.tickCount;
        bodyAlpha = entity.getAlpha(partial);
        applyPose(ageInTicks, entity.getFlySpeed(partial), netHeadYaw, headPitch,
                entity.getRevealAnim(partial), entity.getStrikeAnim(partial), entity.getDanceAnim(partial),
                entity.getSitAnim(partial), 1.0F - bodyAlpha);
    }

    /** Pure function of its inputs so the preview tool can pose the model without an entity. */
    public void applyPose(float t, float move, float headYaw, float headPitch,
                          float reveal, float strike, float dance, float sit, float phase) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float beat = t * 0.4F;
        float lively = 1.0F - sit;

        // ---- body: hover, lean into travel, ripple down the chain ----
        torso.y += Mth.sin(t * 0.1F) * 1.2F * lively + 5.0F * sit;
        torso.xRot += 0.06F + 0.32F * move + 0.35F * strike + 0.25F * sit;
        sway(chain, t * 0.12F + move * 2.0F, (0.05F + 0.07F * move + 0.05F * phase) * (1.0F - 0.6F * sit) + 0.06F * dance);
        for (int i = 3; i < chain.length; i++) {
            chain[i].xRot -= 0.1F * move * (i - 2);                        // the tail streams out behind
        }
        // Dance: twist, sway and bounce, with the tail whipping along.
        torso.zRot += Mth.sin(beat) * 0.22F * dance;
        torso.yRot += Mth.sin(beat * 0.5F) * 0.4F * dance;
        torso.y -= Math.abs(Mth.sin(beat)) * 2.5F * dance;
        for (int i = 3; i < chain.length; i++) {
            chain[i].zRot += Mth.sin(beat * 2.0F - i * 0.6F) * 0.3F * dance;
        }

        // ---- head follows the look direction, counter-tilted against the lean ----
        head.yRot += Mth.clamp(headYaw, -50.0F, 50.0F) * Mth.DEG_TO_RAD + Mth.sin(beat * 0.5F) * 0.3F * dance;
        head.xRot += Mth.clamp(headPitch, -35.0F, 35.0F) * Mth.DEG_TO_RAD - torso.xRot * 0.7F;
        head.zRot += -Mth.sin(beat) * 0.25F * dance;

        // ---- arms: trail back when moving, thrust forward to strike, rise when dancing, fold when sitting ----
        float drift = Mth.sin(t * 0.09F) * 0.06F;
        armR.zRot += -0.18F + drift + (-0.5F + Mth.sin(beat) * 0.3F) * dance + 0.5F * sit;
        armL.zRot += 0.18F - drift + (0.5F - Mth.sin(beat + Mth.PI) * 0.3F) * dance - 0.5F * sit;
        armR.xRot += 0.15F + 0.5F * move + Mth.cos(t * 0.07F) * 0.05F - 1.3F * strike
                + (-2.5F + Mth.sin(beat) * 0.4F) * dance - 0.5F * sit;
        armL.xRot += 0.15F + 0.5F * move + Mth.cos(t * 0.07F + 1.0F) * 0.05F - 1.3F * strike
                + (-2.5F + Mth.sin(beat + Mth.PI) * 0.4F) * dance - 0.5F * sit;
        for (ModelPart claw : clawsR) claw.xRot += Mth.sin(t * 0.15F) * 0.1F + 0.3F * strike;
        for (ModelPart claw : clawsL) claw.xRot += Mth.sin(t * 0.15F + 2.0F) * 0.1F + 0.3F * strike;

        // ---- tentacles: unfurl from the back in a fan, curling upwards, whipping forward to strike ----
        float open = Mth.clamp(reveal, 0.0F, 1.0F);
        boolean show = open > 0.04F;
        for (int i = 0; i < TENTACLES; i++) {
            int row = i / 4, col = i % 4;
            float spread = (col - 1.5F) * 0.55F + (row == 1 ? (col < 2 ? -0.18F : 0.18F) : 0.0F);
            float wave = t * 0.17F + i * 0.85F;
            tentacle[i][0].visible = show;
            tentacle[i][0].xRot += Mth.lerp(open, 0.15F, 1.35F + row * 0.35F) - strike * 2.3F
                    + Mth.sin(wave) * 0.12F * open;
            tentacle[i][0].yRot += spread * open;
            for (int k = 1; k < SEGMENTS; k++) {
                tentacle[i][k].xRot += open * (0.55F - strike * 0.35F)
                        + Mth.sin(wave - k * 0.9F) * 0.28F * open * (1.0F + dance);
                tentacle[i][k].zRot += Mth.cos(wave * 0.8F - k * 0.8F) * 0.18F * open;
            }
        }
    }

    /** Sideways sway travelling down a chain: later segments lag behind and swing wider. */
    private static void sway(ModelPart[] chain, float time, float amplitude) {
        for (int i = 0; i < chain.length; i++) {
            float growth = 1.0F + i * 0.18F;
            chain[i].zRot += Mth.sin(time - i * 0.7F) * amplitude * growth;
            chain[i].xRot += Mth.cos(time * 0.8F - i * 0.7F) * amplitude * 0.6F * growth;
        }
    }

    public void setEyePass(boolean eyePass) {
        this.eyePass = eyePass;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue,
                eyePass ? alpha : alpha * bodyAlpha);
    }
}
