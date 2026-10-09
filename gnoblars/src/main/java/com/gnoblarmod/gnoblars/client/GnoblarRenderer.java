package com.gnoblarmod.gnoblars.client;

import com.gnoblarmod.gnoblars.entity.GnoblarEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class GnoblarRenderer extends MobRenderer<GnoblarEntity, GnoblarModel<GnoblarEntity>> {
    public GnoblarRenderer(EntityRendererProvider.Context context) {
        super(context, new GnoblarModel<>(context.bakeLayer(GnoblarModel.LAYER_LOCATION)), 0.3F);
        addLayer(new GnoblarSashLayer(this));
        addLayer(new GnoblarMudLayer(this));
        addLayer(new GnoblarBannerLayer(this, context.getItemInHandRenderer()));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(GnoblarEntity entity) {
        return entity.getVariant().texture();
    }
}
