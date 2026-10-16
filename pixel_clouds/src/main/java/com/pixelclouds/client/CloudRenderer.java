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
import com.pixelclouds.sky.CloudPalette;
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

    private static final CloudField FIELD = new CloudField();
    private static final Billboard BILLBOARD = new Billboard();
    private static SpriteAtlas atlas;
    private static DynamicTexture texture;
    private static boolean failed;

    /** Camera immersion in the nearest cloud, 0..1, and the colour fog should take on, read by the fog hooks. */
    private static volatile float insideFactor;
    private static final float[] insideColour = new float[3];

    private CloudRenderer() {}

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
        if (failed) {
            return false;
        }
        try {
            atlas = SpriteAtlas.load(Minecraft.getInstance().getResourceManager());
            uploadAtlas(textures);
            LOGGER.info("Pixel Clouds: {} cloud sprite(s) loaded", atlas.count);
            return true;
        } catch (Exception e) {
            atlas = null;
            fail("could not load the cloud sprites", e);
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
        FIELD.update(atlas, camX, camY, camZ, seconds, rain,
                PixelCloudsConfig.COVERAGE.get().floatValue(), PixelCloudsConfig.SIZE.get().floatValue(),
                PixelCloudsConfig.WIND_SPEED.get().floatValue(), PixelCloudsConfig.HEIGHT.get(), range);

        insideFactor = PixelCloudsConfig.CLOUD_FOG.get() ? FIELD.inside : 0f;
        System.arraycopy(palette.shade[3], 0, insideColour, 0, 3);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);

        for (int i = 0; i < FIELD.count; i++) {
            CloudField.Cloud c = FIELD.clouds[i];
            float rx = (float) (c.x - camX);
            float ry = (float) (c.y - camY);
            float rz = (float) (c.z - camZ);
            float dist = Mth.sqrt(rx * rx + ry * ry + rz * rz);
            float fade = c.presence * Billboard.distanceFade(dist, c.bodyW, range);
            if (fade <= 0.01f || !BILLBOARD.set(rx, ry, rz, lightX, lightY)) {
                continue;
            }
            // Sprites are lit from the top left, so mirror them when the light comes from the right.
            boolean mirror = BILLBOARD.lr > 0f;
            quad(builder, atlas, c, rx, ry, rz, BILLBOARD, mirror, Billboard.haze(dist, range), fade);
        }

        BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
        if (rendered == null) {
            return true;
        }

        setUniform(shader, "Band1", palette.shade[1]);
        float[] lav = new float[3];
        for (int k = 0; k < 3; k++) {
            lav[k] = palette.shade[2][k] + (palette.violet[k] - palette.shade[2][k]) * 0.45f;
        }
        setUniform(shader, "Band2", lav);
        setUniform(shader, "Band3", palette.shade[3]);
        setUniform(shader, "Band4", palette.shade[4]);
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


    private static void quad(VertexConsumer out, SpriteAtlas atlas, CloudField.Cloud c,
                             float cx, float cy, float cz, Billboard bb, boolean mirror, float haze, float fade) {
        float u0 = atlas.u0(c.sprite);
        float u1 = atlas.u1(c.sprite);
        if (mirror) {
            float t = u0;
            u0 = u1;
            u1 = t;
        }
        float v0 = atlas.v0(c.sprite);
        float v1 = atlas.v1(c.sprite);
        int r = Math.round(Mth.clamp(haze, 0f, 1f) * 255f);
        int a = Math.round(Mth.clamp(fade, 0f, 1f) * 255f);

        float hw = c.width * 0.5f;
        float hh = c.height * 0.5f;
        float rx = bb.rX * hw;
        float rz = bb.rZ * hw;
        float ux = bb.uX * hh;
        float uy = bb.uY * hh;
        float uz = bb.uZ * hh;

        vertex(out, cx - rx + ux, cy + uy, cz - rz + uz, u0, v0, r, a);
        vertex(out, cx + rx + ux, cy + uy, cz + rz + uz, u1, v0, r, a);
        vertex(out, cx + rx - ux, cy - uy, cz + rz - uz, u1, v1, r, a);
        vertex(out, cx - rx - ux, cy - uy, cz - rz - uz, u0, v1, r, a);
    }

    private static void vertex(VertexConsumer out, float x, float y, float z, float u, float v, int r, int a) {
        out.vertex(x, y, z).uv(u, v).color(r, 0, 0, a).normal(0f, 1f, 0f).endVertex();
    }
}
