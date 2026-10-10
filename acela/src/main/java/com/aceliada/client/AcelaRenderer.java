package com.aceliada.client;

import com.aceliada.Aceliada;
import com.aceliada.entity.AcelaEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Player-shaped model (wide arms) with a pipe, plus a glowing layer for the red eyes and the pipe ember. */
public class AcelaRenderer extends HumanoidMobRenderer<AcelaEntity, PlayerModel<AcelaEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(Aceliada.MODID, "acela"), "main");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Aceliada.MODID, "textures/entity/acela.png");
    private static final RenderType EYES = RenderType.eyes(new ResourceLocation(Aceliada.MODID, "textures/entity/acela_eyes.png"));

    public AcelaRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(LAYER), false), 0.5F);
        this.addLayer(new Eyes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(AcelaEntity entity) {
        return TEXTURE;
    }

    private static class Eyes extends EyesLayer<AcelaEntity, PlayerModel<AcelaEntity>> {
        Eyes(RenderLayerParent<AcelaEntity, PlayerModel<AcelaEntity>> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }
    }
}
