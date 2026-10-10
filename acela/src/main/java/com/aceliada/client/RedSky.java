package com.aceliada.client;

import com.aceliada.Aceliada;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The red sky from the summoning until Acela dies. Right after the sky is drawn, a blood red wash goes
 * over it (terrain is drawn afterwards, so only the sky, sun, moon and stars are tinted), and the fog
 * turns red so the horizon matches. It fades in and out over about two seconds.
 */
@Mod.EventBusSubscriber(modid = Aceliada.MODID, value = Dist.CLIENT)
public final class RedSky {
    private static final float RED = 0.62F;
    private static final float GREEN = 0.05F;
    private static final float BLUE = 0.04F;
    private static final float MAX_ALPHA = 0.72F;
    private static final float FADE_PER_TICK = 0.025F;

    private static boolean on;
    private static float intensity;
    private static float previous;

    private RedSky() {
    }

    public static void set(boolean value) {
        on = value;
    }

    private static float intensity(float partialTick) {
        return Mth.lerp(partialTick, previous, intensity);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        previous = intensity;
        intensity = Mth.clamp(intensity + (on ? FADE_PER_TICK : -FADE_PER_TICK), 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        on = false;
        intensity = 0.0F;
        previous = 0.0F;
    }

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        float t = intensity((float) event.getPartialTick());
        if (t <= 0.0F) {
            return;
        }
        event.setRed(Mth.lerp(t, event.getRed(), RED));
        event.setGreen(Mth.lerp(t, event.getGreen(), GREEN));
        event.setBlue(Mth.lerp(t, event.getBlue(), BLUE));
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        float t = intensity(event.getPartialTick());
        if (t <= 0.0F) {
            return;
        }
        // A quad over the whole screen in clip space, drawn with blending and no depth.
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float a = MAX_ALPHA * t;
        buffer.vertex(-1.0F, -1.0F, 0.0F).color(RED, GREEN, BLUE, a).endVertex();
        buffer.vertex(1.0F, -1.0F, 0.0F).color(RED, GREEN, BLUE, a).endVertex();
        buffer.vertex(1.0F, 1.0F, 0.0F).color(RED, GREEN, BLUE, a).endVertex();
        buffer.vertex(-1.0F, 1.0F, 0.0F).color(RED, GREEN, BLUE, a).endVertex();
        Tesselator.getInstance().end();

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }
}
