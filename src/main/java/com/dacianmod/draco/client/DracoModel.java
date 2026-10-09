package com.dacianmod.draco.client;

import com.dacianmod.draco.DacianDraco;
import com.dacianmod.draco.entity.DracoEntity;
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
 * Vanilla-style (no GeckoLib) segmented serpent with a wolf-derived head.
 *
 * Every body segment is a child of the previous one and pivots at its end, so rotating one
 * segment carries everything behind it. The wave is a phase-shifted cosine along the chain,
 * the same idea as the chainSwing helper used by the snakes in Alex's Mobs.
 *
 * Texture is 128x128. Offsets below match the layout the placeholder generator produced.
 */
public class DracoModel<T extends DracoEntity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(DacianDraco.MODID, "draco"), "main");

    private final ModelPart root;
    private final ModelPart neck1, neck2, head, jaw;
    private final ModelPart body, seg1, seg2, seg3, seg4, cloth1, cloth2, cloth3, cloth4;
    /** Front to back, so index grows towards the tail. */
    private final ModelPart[] chain;

    public DracoModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck1 = body.getChild("neck1");
        this.neck2 = neck1.getChild("neck2");
        this.head = neck2.getChild("head");
        this.jaw = head.getChild("jaw");
        this.seg1 = body.getChild("seg1");
        this.seg2 = seg1.getChild("seg2");
        this.seg3 = seg2.getChild("seg3");
        this.seg4 = seg3.getChild("seg4");
        this.cloth1 = seg4.getChild("cloth1");
        this.cloth2 = cloth1.getChild("cloth2");
        this.cloth3 = cloth2.getChild("cloth3");
        this.cloth4 = cloth3.getChild("cloth4");
        this.chain = new ModelPart[]{neck2, neck1, body, seg1, seg2, seg3, seg4, cloth1, cloth2, cloth3, cloth4};
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -3.5F, 6, 6, 7),
                PartPose.offset(0.0F, 17.0F, 0.0F));

        // ---- tail ----
        PartDefinition seg1 = body.addOrReplaceChild("seg1",
                CubeListBuilder.create().texOffs(26, 0).addBox(-2.5F, -2.5F, 0.0F, 5, 5, 6),
                PartPose.offset(0.0F, 0.0F, 3.5F));
        PartDefinition seg2 = seg1.addOrReplaceChild("seg2",
                CubeListBuilder.create().texOffs(48, 0).addBox(-2.0F, -2.0F, 0.0F, 4, 4, 6),
                PartPose.offset(0.0F, 0.0F, 6.0F));
        PartDefinition seg3 = seg2.addOrReplaceChild("seg3",
                CubeListBuilder.create().texOffs(68, 0).addBox(-1.5F, -1.5F, 0.0F, 3, 3, 6),
                PartPose.offset(0.0F, 0.0F, 6.0F));
        PartDefinition seg4 = seg3.addOrReplaceChild("seg4",
                CubeListBuilder.create().texOffs(86, 0).addBox(-1.0F, -1.0F, 0.0F, 2, 2, 5),
                PartPose.offset(0.0F, 0.0F, 6.0F));

        // Cloth streamer, like the windsock tail of the Dacian standard.
        PartDefinition cloth1 = seg4.addOrReplaceChild("cloth1",
                CubeListBuilder.create().texOffs(100, 0).addBox(-0.5F, -3.5F, 0.0F, 1, 7, 6),
                PartPose.offset(0.0F, 0.0F, 5.0F));
        PartDefinition cloth2 = cloth1.addOrReplaceChild("cloth2",
                CubeListBuilder.create().texOffs(114, 0).addBox(-0.5F, -3.0F, 0.0F, 1, 6, 6),
                PartPose.offset(0.0F, 0.0F, 6.0F));
        PartDefinition cloth3 = cloth2.addOrReplaceChild("cloth3",
                CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, -2.5F, 0.0F, 1, 5, 6),
                PartPose.offset(0.0F, 0.0F, 6.0F));
        cloth3.addOrReplaceChild("cloth4",
                CubeListBuilder.create().texOffs(14, 13).addBox(-0.5F, -2.0F, 0.0F, 1, 4, 5),
                PartPose.offset(0.0F, 0.0F, 6.0F));

        // ---- neck and head ----
        PartDefinition neck1 = body.addOrReplaceChild("neck1",
                CubeListBuilder.create().texOffs(26, 13).addBox(-2.5F, -2.5F, -6.0F, 5, 5, 6),
                PartPose.offset(0.0F, 0.0F, -3.5F));
        PartDefinition neck2 = neck1.addOrReplaceChild("neck2",
                CubeListBuilder.create()
                        .texOffs(48, 13).addBox(-2.0F, -2.0F, -5.0F, 4, 4, 5)
                        .texOffs(66, 13).addBox(-4.0F, -3.5F, -4.0F, 8, 7, 4), // fur ruff
                PartPose.offset(0.0F, 0.0F, -6.0F));

        // Wolf-like head, longer and heavier than the vanilla wolf's, with big ears and fangs.
        PartDefinition head = neck2.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(90, 13).addBox(-3.5F, -3.0F, -6.0F, 7, 6, 6)    // skull
                        .texOffs(0, 25).addBox(-2.0F, -0.5F, -11.0F, 4, 3, 5)    // snout
                        .texOffs(18, 25).addBox(-1.0F, -0.5F, -12.0F, 2, 2, 1)   // nose
                        .texOffs(24, 25).addBox(-3.5F, -6.0F, -2.0F, 2, 3, 1)    // ear
                        .texOffs(24, 25).addBox(1.5F, -6.0F, -2.0F, 2, 3, 1)     // ear
                        .texOffs(30, 25).addBox(-1.75F, 2.5F, -10.5F, 1, 2, 1)   // fang
                        .texOffs(30, 25).addBox(0.75F, 2.5F, -10.5F, 1, 2, 1),   // fang
                PartPose.offset(0.0F, 0.0F, -5.0F));
        head.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(34, 25).addBox(-2.0F, 0.0F, -7.0F, 4, 2, 7),
                PartPose.offset(0.0F, 2.5F, -4.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);

        float partialTick = ageInTicks - entity.tickCount;
        float howl = entity.getHowlAnim(partialTick);
        float dive = entity.getDiveAnim(partialTick);
        float lash = entity.getLashAnim(partialTick);

        // Serpentine wave: the faster it flies the wider the wave, and a dive straightens the body.
        float amplitude = (0.12F + 0.16F * Math.min(1.0F, limbSwingAmount)) * (1.0F - 0.7F * dive);
        float phase = ageInTicks * 0.15F + limbSwing * 0.6F;
        for (int i = 0; i < chain.length; i++) {
            float growth = 1.0F + i * 0.06F;
            chain[i].yRot += Mth.cos(phase - i * 0.7F) * amplitude * growth;
            chain[i].xRot += Mth.sin(phase * 0.8F - i * 0.7F) * 0.05F * growth;
        }

        // Tail lash: the rear half whips side to side.
        for (int i = 3; i < chain.length; i++) {
            chain[i].yRot += Mth.sin(ageInTicks * 0.9F - i * 0.35F) * 0.55F * lash;
        }

        // Head follows the look direction, split between neck and head.
        float yaw = Mth.clamp(netHeadYaw, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(headPitch, -40.0F, 40.0F) * Mth.DEG_TO_RAD;
        neck2.yRot += yaw * 0.4F;
        head.yRot += yaw * 0.5F;
        neck2.xRot += pitch * 0.4F;
        head.xRot += pitch * 0.5F;

        // Howl: head thrown back, jaw wide. Dive: jaw half open, head tucked down.
        neck1.xRot -= howl * 0.35F;
        head.xRot -= howl * 0.45F;
        head.xRot += dive * 0.35F;
        float jawOpen = Math.max(howl, dive * 0.7F);
        jaw.xRot += jawOpen * 0.9F + Mth.sin(ageInTicks * 0.12F) * 0.03F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
