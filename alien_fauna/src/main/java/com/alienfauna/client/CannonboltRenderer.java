package com.alienfauna.client;

import com.alienfauna.AlienFauna;
import com.alienfauna.entity.CannonboltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CannonboltRenderer extends MobRenderer<CannonboltEntity, CannonboltModel<CannonboltEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AlienFauna.MODID, "textures/entity/cannonbolt.png");

    private static final ResourceLocation BABY_TEXTURE =
            new ResourceLocation(AlienFauna.MODID, "textures/entity/cannonbolt_baby.png");

    public CannonboltRenderer(EntityRendererProvider.Context context) {
        super(context, new CannonboltModel<>(context.bakeLayer(CannonboltModel.LAYER_LOCATION)), 0.9F);
    }

    @Override
    public ResourceLocation getTextureLocation(CannonboltEntity entity) {
        return entity.isBaby() ? BABY_TEXTURE : TEXTURE;
    }

    /** A baby is the same model at half the size, with its own texture (bigger eyes). */
    @Override
    protected void scale(CannonboltEntity entity, PoseStack poseStack, float partialTick) {
        float s = entity.isBaby() ? 0.5F : 1.0F;
        poseStack.scale(s, s, s);
        this.shadowRadius = 0.9F * s;
    }
}
