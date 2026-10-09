package com.alienfauna.client;

import com.alienfauna.AlienFauna;
import com.alienfauna.entity.CannonboltEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CannonboltRenderer extends MobRenderer<CannonboltEntity, CannonboltModel<CannonboltEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AlienFauna.MODID, "textures/entity/cannonbolt.png");

    public CannonboltRenderer(EntityRendererProvider.Context context) {
        super(context, new CannonboltModel<>(context.bakeLayer(CannonboltModel.LAYER_LOCATION)), 0.9F);
    }

    @Override
    public ResourceLocation getTextureLocation(CannonboltEntity entity) {
        return TEXTURE;
    }
}
