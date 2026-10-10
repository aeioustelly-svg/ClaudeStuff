package com.ghostfreakmod.ghostfreak.client;

import com.ghostfreakmod.ghostfreak.GhostfreakMod;
import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class GhostfreakRenderer extends MobRenderer<GhostfreakEntity, GhostfreakModel<GhostfreakEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(GhostfreakMod.MODID, "textures/entity/ghostfreak.png");

    public GhostfreakRenderer(EntityRendererProvider.Context context) {
        super(context, new GhostfreakModel<>(context.bakeLayer(GhostfreakModel.LAYER_LOCATION)), 0.4F);
        addLayer(new GhostfreakEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(GhostfreakEntity entity) {
        return TEXTURE;
    }

    /** Always translucent, so the model can fade its own alpha when it phases. */
    @Nullable
    @Override
    protected RenderType getRenderType(GhostfreakEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (!bodyVisible && !translucent && !glowing) {
            return null;
        }
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }
}
