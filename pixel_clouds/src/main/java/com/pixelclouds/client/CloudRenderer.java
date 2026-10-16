package com.pixelclouds.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.pixelclouds.PixelCloudsConfig;
import com.pixelclouds.cloud.Cloud;
import com.pixelclouds.cloud.CloudField;
import com.pixelclouds.cloud.CloudImage;
import com.pixelclouds.cloud.CloudPalette;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Ticks the cloud simulation and draws every cloud as one flat, camera-facing quad, the way
 * particles are drawn. There is no cloud geometry beyond those quads.
 */
public final class CloudRenderer {
    public static final CloudRenderer INSTANCE = new CloudRenderer();

    private CloudField field;
    private ClientLevel level;
    private CloudAtlas atlas;
    private int[] palette = new int[CloudImage.TONES];
    private int paletteVersion;

    private CloudRenderer() {
    }

    void reset() {
        field = null;
        level = null;
    }

    void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        if (current == null || !PixelCloudsConfig.ENABLED.get() || !(current.effects() instanceof PixelCloudEffects)
                || mc.options.getCloudsType() == CloudStatus.OFF) {
            reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        CloudField.Settings settings = settings();
        if (field == null || level != current) {
            level = current;
            field = new CloudField(settings, current.random.nextLong());
        } else {
            field.setSettings(settings);
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        CloudField.View view = new CloudField.View();
        view.x = camera.getPosition().x;
        view.y = camera.getPosition().y;
        view.z = camera.getPosition().z;
        double yaw = Math.toRadians(camera.getYRot());
        // Minecraft faces -sin(yaw), cos(yaw); the camera's right is that turned a quarter.
        view.rightX = -Math.cos(yaw);
        view.rightZ = -Math.sin(yaw);
        view.rain = current.getRainLevel(1.0F);
        view.thunder = current.getThunderLevel(1.0F);
        field.tick(view);
    }

    private static CloudField.Settings settings() {
        CloudField.Settings s = new CloudField.Settings();
        s.altitude = PixelCloudsConfig.ALTITUDE.get();
        s.altitudeSpread = PixelCloudsConfig.ALTITUDE_SPREAD.get();
        s.radius = PixelCloudsConfig.RADIUS.get();
        s.pixelSize = PixelCloudsConfig.PIXEL_SIZE.get();
        s.coverage = PixelCloudsConfig.COVERAGE.get();
        s.splitAndMerge = PixelCloudsConfig.SPLIT_AND_MERGE.get();
        s.maxClouds = CloudAtlas.SLOTS_X * CloudAtlas.SLOTS_Y;
        return s;
    }

    void render(ClientLevel level, float partialTick, PoseStack poseStack,
                double camX, double camY, double camZ, Matrix4f projection) {
        if (field == null || level != this.level) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera.getFluidInCamera() != FogType.NONE || isBlinded(camera.getEntity())) {
            return;
        }
        if (atlas == null) {
            atlas = new CloudAtlas();
        }

        int[] next = CloudPalette.compute(Math.cos(level.getSunAngle(partialTick)),
                level.getRainLevel(partialTick), level.getThunderLevel(partialTick),
                level.getSkyFlashTime() > 0 ? 0.8 : 0.0);
        if (!Arrays.equals(next, palette)) {
            palette = next;
            paletteVersion++;
        }

        CloudField.Settings settings = field.settings();
        double px = settings.pixelSize;
        double radius = settings.radius;
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float rx = -left.x(), ry = -left.y(), rz = -left.z();
        float ux = up.x(), uy = up.y(), uz = up.z();

        // Clouds can lie beyond the far clipping plane at short render distances. Those are pulled
        // closer and shrunk by the same factor, which looks identical on screen. Terrain never
        // reaches that far, so nothing is drawn in front of anything it should be behind.
        double limit = mc.gameRenderer.getDepthFar() * 0.9;
        double knee = limit * 0.5;
        double farthest = radius * 1.1 + CloudField.MAX_WIDTH * px;

        atlas.beginFrame();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, atlas.location);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);

        Matrix4f pose = poseStack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int drawn = 0;
        for (Cloud cloud : field.clouds()) {
            CloudImage image = cloud.image(CloudAtlas.MAX_IMAGE_WIDTH, CloudAtlas.MAX_IMAGE_HEIGHT);
            double ox = cloud.lerpX(partialTick) - camX;
            double oy = cloud.lerpY(partialTick) - camY;
            double oz = cloud.lerpZ(partialTick) - camZ;

            double fade = fade(cloud, image, ox, oy, oz, px, radius);
            int steps = (int) Math.round(fade * CloudAtlas.FADE_STEPS);
            if (steps <= 0) {
                continue;
            }
            CloudAtlas.Slot slot = atlas.update(cloud, palette, paletteVersion, steps);
            if (slot == null) {
                continue;
            }

            double u0 = image.minU * px, u1 = (image.minU + image.width) * px;
            double v0 = image.minV * px, v1 = (image.minV + image.height) * px;
            double dist = Math.sqrt(ox * ox + oy * oy + oz * oz);
            double scale = 1.0;
            if (farthest > limit && dist > knee) {
                double squeezed = knee + (Math.min(dist, farthest) - knee) * (limit - knee) / (farthest - knee);
                scale = squeezed / dist;
            }

            float tu0 = (float) slot.texX() / CloudAtlas.WIDTH;
            float tv0 = (float) slot.texY() / CloudAtlas.HEIGHT;
            float tu1 = (float) (slot.texX() + image.width) / CloudAtlas.WIDTH;
            float tv1 = (float) (slot.texY() + image.height) / CloudAtlas.HEIGHT;

            corner(builder, pose, ox, oy, oz, u0, v0, rx, ry, rz, ux, uy, uz, scale, tu0, tv0);
            corner(builder, pose, ox, oy, oz, u0, v1, rx, ry, rz, ux, uy, uz, scale, tu0, tv1);
            corner(builder, pose, ox, oy, oz, u1, v1, rx, ry, rz, ux, uy, uz, scale, tu1, tv1);
            corner(builder, pose, ox, oy, oz, u1, v0, rx, ry, rz, ux, uy, uz, scale, tu1, tv0);
            drawn++;
        }
        if (drawn > 0) {
            BufferUploader.drawWithShader(builder.end());
        } else {
            builder.end();
        }
        atlas.endFrame();

        RenderSystem.restoreProjectionMatrix();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    /** Texel {@code (u, v)} of the sprite lies along the camera's right and down its up vector. */
    private static void corner(BufferBuilder builder, Matrix4f pose, double ox, double oy, double oz,
                               double u, double v, float rx, float ry, float rz, float ux, float uy, float uz,
                               double scale, float tu, float tv) {
        double x = (ox + rx * u - ux * v) * scale;
        double y = (oy + ry * u - uy * v) * scale;
        double z = (oz + rz * u - uz * v) * scale;
        builder.vertex(pose, (float) x, (float) y, (float) z).uv(tu, tv).color(255, 255, 255, 255).endVertex();
    }

    /**
     * How much of the cloud to show: it pixel-dissolves in when it forms, out towards the edge of
     * the cloud radius, and away as the camera flies into it.
     */
    private static double fade(Cloud cloud, CloudImage image, double ox, double oy, double oz, double px, double radius) {
        double life = Math.min(1.0, cloud.age() / 40.0);

        double horizontal = Math.sqrt(ox * ox + oz * oz);
        double edge = clamp((radius - horizontal) / (radius * 0.18), 0, 1);

        // Treat the cloud as occupying an ellipse around its sprite for the fly-through check.
        double halfW = Math.max(1, image.width * 0.5 * px * 0.8);
        double halfH = Math.max(1, image.height * 0.5 * px * 0.9);
        double centreV = (image.minV + image.height * 0.5) * px;
        double dy = oy - centreV;
        double inside = Math.sqrt(sq(horizontal / halfW) + sq(dy / halfH));
        double near = clamp((inside - 0.45) / 0.4, 0, 1);

        return life * edge * near;
    }

    private static boolean isBlinded(Entity entity) {
        return entity instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    private static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : x > hi ? hi : x;
    }

    private static double sq(double x) {
        return x * x;
    }
}
