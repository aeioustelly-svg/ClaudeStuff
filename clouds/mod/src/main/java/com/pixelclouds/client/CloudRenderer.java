package com.pixelclouds.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the clouds as flat quads that always face the camera. Their layout is fixed by a hash of the cell they
 * occupy in a frame that drifts with the wind, so a cloud keeps its sprite and height as it crosses the sky and
 * comes back the same when the player returns. Broken-off pieces are separate quads that float on their own.
 */
public final class CloudRenderer {
    private static final float PIXEL = 3f;          // blocks per sprite pixel
    private static final int CELL = 220;            // blocks per placement cell
    private static final float CHANCE = 0.6f;       // share of cells holding a cloud in clear weather
    private static final float STORM_CHANCE = 0.9f; // and in a storm
    private static final float WIND = 0.05f;        // blocks per tick, towards +X like vanilla clouds
    private static final float HEIGHT_SPREAD = 24f; // clouds sit between the dimension's cloud height and this much above
    private static final float FADE_START = 0.55f;  // share of the far clipping distance where clouds start to fade
    private static final float FADE_END = 0.95f;    // and where they are gone

    private CloudAtlas atlas;

    private record Quad(float x, float y, float z, float halfW, float halfH, CloudAtlas.Rect rect, float alpha,
                        float dist) {}

    public void render(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                       double camX, double camY, double camZ) {
        if (atlas == null) {
            atlas = CloudAtlas.load();
        }
        atlas.paint(CloudPalette.current(level, partialTick));

        float time = ticks + partialTick;
        double wind = time * WIND;
        float base = level.effects().getCloudHeight();
        if (Float.isNaN(base)) {
            return;
        }

        float far = Minecraft.getInstance().gameRenderer.getDepthFar();
        float fadeStart = far * FADE_START, fadeEnd = far * FADE_END;
        float chance = Mth.lerp(level.getRainLevel(partialTick), CHANCE, STORM_CHANCE);

        List<Quad> quads = new ArrayList<>();
        int range = Mth.ceil(fadeEnd / CELL) + 1;
        // Cells are laid out in the wind's frame: world x = cell x + wind.
        int cx0 = Mth.floor((camX - wind) / CELL), cz0 = Mth.floor(camZ / CELL);
        for (int cx = cx0 - range; cx <= cx0 + range; cx++) {
            for (int cz = cz0 - range; cz <= cz0 + range; cz++) {
                long h = hash(cx, cz);
                if (unit(h, 0) > chance) {
                    continue;
                }
                CloudAtlas.Sprite sprite = atlas.sprites.get((int) (unit(h, 1) * atlas.sprites.size()) % atlas.sprites.size());
                float x = (float) (cx * CELL + unit(h, 2) * CELL * 0.7 + wind - camX);
                float z = (float) (cz * CELL + unit(h, 3) * CELL * 0.7 - camZ);
                float y = (float) (base + unit(h, 4) * HEIGHT_SPREAD - camY);
                float dist = Mth.sqrt(x * x + z * z);
                if (dist > fadeEnd) {
                    continue;
                }
                float alpha = 1f - smooth(fadeStart, fadeEnd, dist);
                float halfW = sprite.rect().w() * PIXEL / 2f, halfH = sprite.rect().h() * PIXEL / 2f;
                quads.add(new Quad(x, y, z, halfW, halfH, sprite.rect(), alpha, dist));
                addPieces(quads, sprite, x, y, z, halfW, halfH, alpha, dist, time, h);
            }
        }
        if (quads.isEmpty()) {
            return;
        }
        quads.sort((a, b) -> Float.compare(b.dist, a.dist));    // far to near, for the faded edges

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();

        // The level renderer has already put the camera rotation on the model-view stack by the time clouds are
        // drawn; set it to exactly the pose given instead of multiplying (which rotated the clouds twice).
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CloudAtlas.TEXTURE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Quad q : quads) {
            float u0 = q.rect.x() / (float) atlas.width, u1 = (q.rect.x() + q.rect.w()) / (float) atlas.width;
            float v0 = q.rect.y() / (float) atlas.height, v1 = (q.rect.y() + q.rect.h()) / (float) atlas.height;
            // The camera's left vector points left, so the image's right edge is at -left.
            corner(buffer, q, left, up, 1, 1, u0, v0);
            corner(buffer, q, left, up, 1, -1, u0, v1);
            corner(buffer, q, left, up, -1, -1, u1, v1);
            corner(buffer, q, left, up, -1, 1, u1, v0);
        }
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /** Pieces sit where they broke off, plus a slow drift of their own (a few blocks each way, with a bob). */
    private static void addPieces(List<Quad> quads, CloudAtlas.Sprite sprite, float x, float y, float z,
                                  float halfW, float halfH, float alpha, float dist, float time, long h) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        int i = 0;
        for (CloudAtlas.Piece p : sprite.pieces()) {
            float phase = unit(h, 10 + i) * Mth.TWO_PI;
            float speed = 0.004f + unit(h, 20 + i) * 0.004f;
            float drift = Mth.sin(time * speed + phase) * 4f;          // blocks, across the view
            float bob = Mth.sin(time * speed * 1.7f + phase * 2f) * 1.2f;
            float pw = p.rect().w() * PIXEL / 2f, ph = p.rect().h() * PIXEL / 2f;
            // Offset of the piece's centre from the cloud's centre, in blocks on the billboard plane.
            float right = (p.dx() + p.rect().w() / 2f) * PIXEL - halfW;
            float down = (p.dy() + p.rect().h() / 2f) * PIXEL - halfH;
            float sx = right + drift, sy = -down + bob;
            float px = x - left.x() * sx + up.x() * sy;
            float py = y - left.y() * sx + up.y() * sy;
            float pz = z - left.z() * sx + up.z() * sy;
            quads.add(new Quad(px, py, pz, pw, ph, p.rect(), alpha, dist - 0.01f));
            i++;
        }
    }

    private static void corner(BufferBuilder buffer, Quad q, Vector3f left, Vector3f up, int sl, int su,
                               float u, float v) {
        float lx = left.x() * q.halfW * sl, ly = left.y() * q.halfW * sl, lz = left.z() * q.halfW * sl;
        float ux = up.x() * q.halfH * su, uy = up.y() * q.halfH * su, uz = up.z() * q.halfH * su;
        buffer.vertex(q.x + lx + ux, q.y + ly + uy, q.z + lz + uz).uv(u, v).color(1f, 1f, 1f, q.alpha).endVertex();
    }

    private static long hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        return h;
    }

    /** A number in [0, 1) from the hash, different for each salt. */
    private static float unit(long h, int salt) {
        long v = h + salt * 0x632BE59BD9B4E019L;
        v ^= v >>> 33;
        v *= 0xFF51AFD7ED558CCDL;
        v ^= v >>> 33;
        return (v >>> 40) / (float) (1L << 24);
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
