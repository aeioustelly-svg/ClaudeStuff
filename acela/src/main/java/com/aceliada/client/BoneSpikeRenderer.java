package com.aceliada.client;

import com.aceliada.entity.BoneSpikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Draws the vanilla bone item, stood upright and scaled up, sliding out of the floor. */
public class BoneSpikeRenderer extends EntityRenderer<BoneSpikeEntity> {
    private static final float SCALE = 1.4F;
    /** Half the height of the upright bone: 14 px diagonal, so about 0.62 blocks per unit of scale. */
    private static final float HALF_HEIGHT = 0.62F * SCALE;
    private final ItemRenderer itemRenderer;
    private final ItemStack bone = new ItemStack(Items.BONE);

    public BoneSpikeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BoneSpikeEntity entity, float yaw, float partialTicks, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float rise = entity.getRise(partialTicks);
        if (rise <= 0.0F) {
            return;
        }
        pose.pushPose();
        pose.translate(0.0F, -HALF_HEIGHT + 2.0F * HALF_HEIGHT * rise, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(90.0F - entity.getYRot()));
        // The bone sprite runs corner to corner; turn it 45 degrees so it stands straight up.
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        pose.scale(SCALE, SCALE, SCALE);
        itemRenderer.renderStatic(bone, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BoneSpikeEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
