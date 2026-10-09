package com.dacianmod.draco.client;

import com.dacianmod.draco.DacianDraco;
import com.dacianmod.draco.entity.DracoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class DracoRenderer extends MobRenderer<DracoEntity, DracoModel<DracoEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(DacianDraco.MODID, "textures/entity/draco.png");

    public DracoRenderer(EntityRendererProvider.Context context) {
        super(context, new DracoModel<>(context.bakeLayer(DracoModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(DracoEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(DracoEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        // Pitch the whole body along its flight path, pivoting around the middle of the hitbox.
        poseStack.translate(0.0F, 0.4F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getBodyPitch(partialTicks)));
        poseStack.translate(0.0F, -0.4F, 0.0F);
    }
}
