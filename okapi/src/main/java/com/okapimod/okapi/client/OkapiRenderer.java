package com.okapimod.okapi.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.okapimod.okapi.OkapiMod;
import com.okapimod.okapi.entity.OkapiEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class OkapiRenderer extends MobRenderer<OkapiEntity, OkapiModel<OkapiEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(OkapiMod.MODID, "textures/entity/okapi.png");

    public OkapiRenderer(EntityRendererProvider.Context context) {
        super(context, new OkapiModel<>(context.bakeLayer(OkapiModel.LAYER_LOCATION)), 0.7F);
    }

    @Override
    public ResourceLocation getTextureLocation(OkapiEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(OkapiEntity entity, PoseStack poseStack, float partialTick) {
        if (entity.isBaby()) {
            poseStack.scale(0.55F, 0.55F, 0.55F);
            this.shadowRadius = 0.4F;
        } else {
            this.shadowRadius = 0.7F;
        }
    }
}
