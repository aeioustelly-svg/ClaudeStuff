package com.pixelclouds.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.pixelclouds.PixelClouds;
import com.pixelclouds.PixelCloudsConfig;
import com.pixelclouds.gen.CloudAtlas;
import com.pixelclouds.gen.CloudSprites;
import com.pixelclouds.sky.CloudPalette;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

/** Draws the cloud field as lit, camera-facing sprites. Called from {@link PixelSkyEffects#renderClouds}. */
public final class CloudRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ATLAS_ID = new ResourceLocation(PixelClouds.MODID, "cloud_atlas");

    /** Canvas width in model units, and model units covered by a cloud's nominal width. */
    private static final float CANVAS_PER_WIDTH = (CloudSprites.SPRITE_W / CloudSprites.PX_PER_UNIT) / 2f;

    private static final CloudField FIELD = new CloudField();
    private static final Billboard BILLBOARD = new Billboard();
    private static CompletableFuture<CloudAtlas> pending;
    private static CloudAtlas atlas;
    private static DynamicTexture texture;
    private static boolean failed;

    /** Camera immersion in the nearest cloud, 0..1, and the colour fog should take on, read by the fog hooks. */
    private static volatile float insideFactor;
    private static final float[] insideColour = new float[3];

    private CloudRenderer() {}

    /** Starts building the sprite atlas off the render thread. */
    public static void startGeneration() {
        if (pending == null) {
            pending = CompletableFuture.supplyAsync(CloudAtlas::build, Util.backgroundExecutor());
        }
    }

    static float insideFactor() {
        return insideFactor;
    }

    static float[] insideColour() {
        return insideColour;
    }

    private static boolean ensureTexture() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        if (atlas != null) {
            // A resource reload can drop dynamic textures, so check ours is still registered.
            if (textures.getTexture(ATLAS_ID, null) != texture) {
                uploadAtlas(textures);
            }
            return true;
        }
        if (failed || pending == null || !pending.isDone()) {
            return false;
        }
        try {
            atlas = pending.get();
            uploadAtlas(textures);
            LOGGER.info("Pixel Clouds: cloud atlas ready ({}x{})", atlas.width, atlas.height);
            return true;
        } catch (Exception e) {
            atlas = null;
            fail("could not build the cloud atlas", e);
            return false;
        }
    }

    private static void uploadAtlas(TextureManager textures) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, atlas.width, atlas.height, false);
        for (int y = 0; y < atlas.height; y++) {
            for (int x = 0; x < atlas.width; x++) {
                image.setPixelRGBA(x, y, atlas.pixels[y * atlas.width + x]);
            }
        }
        texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        textures.register(ATLAS_ID, texture);
    }

    private static void fail(String what, Exception e) {
        failed = true;
        LOGGER.error("Pixel Clouds: {}, falling back to vanilla clouds", what, e);
    }

    /** @return true if the clouds were drawn and vanilla clouds should be skipped. */
    public static boolean render(ClientLevel level, float partialTick, PoseStack poseStack,
                                 double camX, double camY, double camZ) {
        insideFactor = 0f;
        if (failed) {
            return false;
        }
        try {
            return draw(level, partialTick, poseStack, camX, camY, camZ);
        } catch (RuntimeException e) {
            // Never let a rendering problem crash the game: drop the half-built mesh and use vanilla clouds.
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            if (builder.building()) {
                builder.discard();
            }
            insideFactor = 0f;
            fail("rendering failed", e);
            return false;
        }
    }

    private static boolean draw(ClientLevel level, float partialTick, PoseStack poseStack,
                                double camX, double camY, double camZ) {
        ShaderInstance shader = PixelCloudShader.get();
        if (shader == null || !PixelCloudsConfig.ENABLED.get() || !ensureTexture()) {
            return false;
        }

        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        float sunAngle = level.getSunAngle(partialTick);
        float sunX = -Mth.sin(sunAngle);
        float sunY = Mth.cos(sunAngle);

        CloudPalette palette = CloudPalette.compute(sunY, sunX > 0f, rain, thunder);

        float[] light = Billboard.lightDirection(sunX, sunY);
        float lightX = light[0];
        float lightY = light[1];

        float range = PixelCloudsConfig.RANGE.get();
        double seconds = (level.getGameTime() + partialTick) / 20.0;
        FIELD.update(camX, camY, camZ, seconds, rain, thunder,
                PixelCloudsConfig.COVERAGE.get().floatValue(), PixelCloudsConfig.SIZE.get().floatValue(),
                PixelCloudsConfig.WIND_SPEED.get().floatValue(), PixelCloudsConfig.HEIGHT.get(), range);

        insideFactor = PixelCloudsConfig.CLOUD_FOG.get() ? FIELD.inside : 0f;
        System.arraycopy(palette.shade[2], 0, insideColour, 0, 3);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);

        for (int i = 0; i < FIELD.count; i++) {
            CloudField.Cloud c = FIELD.clouds[i];
            float rx = (float) (c.x - camX);
            float ry = (float) (c.y - camY);
            float rz = (float) (c.z - camZ);
            float dist = Mth.sqrt(rx * rx + ry * ry + rz * rz);
            float fade = c.presence * Billboard.distanceFade(dist, c.width, range);
            if (fade <= 0.01f) {
                continue;
            }
            float haze = Billboard.haze(dist, range);

            if (!BILLBOARD.set(rx, ry, rz, lightX, lightY)) {
                continue;
            }
            Billboard bb = BILLBOARD;
            float rX = bb.rX;
            float rZ = bb.rZ;
            float uX = bb.uX;
            float uY = bb.uY;
            float uZ = bb.uZ;
            float lr = bb.lr;
            float lu = bb.lu;
            float lc = bb.lc;
            int lo = bb.bin;
            float mix = bb.mix;

            float halfW = c.width * 0.5f * CANVAS_PER_WIDTH;
            float halfH = halfW * CloudSprites.SPRITE_H / CloudSprites.SPRITE_W;
            float g = Mth.clamp(c.bias, 0f, 1f);

            if (1f - mix > 0.01f) {
                quad(builder, atlas, c.variant, lo, rx, ry, rz, rX, rZ, uX, uY, uZ, halfW, halfH,
                        haze, g, fade * (1f - mix), lr, lu, lc);
            }
            if (mix > 0.01f) {
                quad(builder, atlas, c.variant, lo + 1, rx, ry, rz, rX, rZ, uX, uY, uZ, halfW, halfH,
                        haze, g, fade * mix, lr, lu, lc);
            }
        }

        BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
        if (rendered == null) {
            return true;
        }

        setUniform(shader, "Shade0", palette.shade[0]);
        setUniform(shader, "Shade1", palette.shade[1]);
        setUniform(shader, "Shade2", palette.shade[2]);
        setUniform(shader, "Shade3", palette.shade[3]);
        setUniform(shader, "RimColor", palette.rim);
        // Distant clouds blend into the horizon, which in vanilla is the world fog colour.
        float[] fog = RenderSystem.getShaderFogColor();
        shader.safeGetUniform("HazeColor").set(fog[0], fog[1], fog[2]);

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, ATLAS_ID);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.mulPoseMatrix(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();
        BufferUploader.drawWithShader(rendered);
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        return true;
    }

    private static void setUniform(ShaderInstance shader, String name, float[] rgb) {
        shader.safeGetUniform(name).set(rgb[0], rgb[1], rgb[2]);
    }


    private static void quad(VertexConsumer out, CloudAtlas atlas, int variant, int bin,
                             float cx, float cy, float cz, float rX, float rZ, float uX, float uY, float uZ,
                             float halfW, float halfH, float haze, float bias, float fade,
                             float lr, float lu, float lc) {
        float u0 = atlas.u0(variant, bin);
        float v0 = atlas.v0(variant, bin);
        float u1 = u0 + atlas.du();
        float v1 = v0 + atlas.dv();
        int r = Math.round(Mth.clamp(haze, 0f, 1f) * 255f);
        int g = Math.round(Mth.clamp(bias, 0f, 1f) * 255f);
        int a = Math.round(Mth.clamp(fade, 0f, 1f) * 255f);
        // Neighbouring angles dissolve with complementary dither patterns, flagged here.
        int parity = (bin & 1) == 1 ? 255 : 0;

        float rx = rX * halfW;
        float rz = rZ * halfW;
        float ux = uX * halfH;
        float uy = uY * halfH;
        float uz = uZ * halfH;

        vertex(out, cx - rx + ux, cy + uy, cz - rz + uz, u0, v0, r, g, parity, a, lr, lu, lc);
        vertex(out, cx + rx + ux, cy + uy, cz + rz + uz, u1, v0, r, g, parity, a, lr, lu, lc);
        vertex(out, cx + rx - ux, cy - uy, cz + rz - uz, u1, v1, r, g, parity, a, lr, lu, lc);
        vertex(out, cx - rx - ux, cy - uy, cz - rz - uz, u0, v1, r, g, parity, a, lr, lu, lc);
    }

    private static void vertex(VertexConsumer out, float x, float y, float z, float u, float v,
                               int r, int g, int parity, int a, float lr, float lu, float lc) {
        out.vertex(x, y, z).uv(u, v).color(r, g, parity, a).normal(lr, lu, lc).endVertex();
    }
}
