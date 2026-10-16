package com.pixelclouds.gen;

import java.util.List;

/**
 * Rasterises a {@link CloudModel} into a lighting-ready pixel sprite.
 *
 * <p>Each opaque texel stores: R and G as the surface normal's x and y in sprite space
 * (z towards the viewer is implied), B as a crease/occlusion value and A as
 * coverage. Empty texels have A = 0. Dissolving in and out is done by the shader in screen space.
 * Colour is deliberately absent. The shader lights the sprite with the current sun and palette.
 *
 * <p>This class has no Minecraft dependencies so it can run in the preview tool.
 */
public final class CloudSprites {
    public static final int SPRITE_W = 128;
    public static final int SPRITE_H = 80;
    /** Pixels per model unit. A model about 2.2 units wide fills most of the sprite width. */
    public static final float PX_PER_UNIT = 56f;
    private static final double GLOBAL_BLEND = 0.56;

    /** Camera elevation angles, in degrees, at which sprites are baked. Positive: cloud above the camera. */
    public static final float[] ELEVATIONS = {-72f, -50f, -30f, -14f, 0f, 14f, 30f, 50f, 72f};
    public static final int BINS = ELEVATIONS.length;

    private CloudSprites() {}

    /** Returns SPRITE_W * SPRITE_H texels packed as 0xAABBGGRR. */
    public static int[] render(CloudModel model, float elevationDeg) {
        final int w = SPRITE_W;
        final int h = SPRITE_H;
        double e = Math.toRadians(elevationDeg);
        double sin = Math.sin(e);
        double cos = Math.cos(e);
        // View frame: r is screen right, u screen up, c points from the cloud to the camera.
        final double cy = -sin;
        final double cz = cos;
        final double uy = cos;
        final double uz = sin;

        float[] depth = new float[w * h];
        float[] nx = new float[w * h];
        float[] ny = new float[w * h];
        boolean[] hit = new boolean[w * h];
        List<CloudModel.Sphere> spheres = model.spheres;
        double baseY = model.baseY;
        boolean clipped = baseY > Double.NEGATIVE_INFINITY;

        for (int py = 0; py < h; py++) {
            double sy = (h / 2.0 - (py + 0.5)) / PX_PER_UNIT;
            for (int px = 0; px < w; px++) {
                double sx = (px + 0.5 - w / 2.0) / PX_PER_UNIT;
                double qx = sx;
                double qy = sy * uy;
                double qz = sy * uz;
                double bestT = -1e9;
                double bnx = 0;
                double bny = 0;
                double bnz = 0;
                boolean found = false;
                for (CloudModel.Sphere s : spheres) {
                    double ax = qx - s.x();
                    double ay = qy - s.y();
                    double az = qz - s.z();
                    double b = ay * cy + az * cz;
                    double disc = b * b - (ax * ax + ay * ay + az * az - (double) s.r() * s.r());
                    if (disc < 0) {
                        continue;
                    }
                    double sq = Math.sqrt(disc);
                    double tBack = -b - sq;
                    double tFront = -b + sq;
                    double tHit = tFront;
                    boolean plane = false;
                    if (clipped) {
                        if (Math.abs(cy) < 1e-6) {
                            if (qy < baseY) {
                                continue;
                            }
                        } else {
                            double tp = (baseY - qy) / cy;
                            if (cy > 0) {
                                if (tFront < tp) {
                                    continue;
                                }
                            } else {
                                if (tp < tFront) {
                                    tHit = tp;
                                    plane = true;
                                }
                                if (tHit < tBack) {
                                    continue;
                                }
                            }
                        }
                    }
                    if (tHit > bestT) {
                        bestT = tHit;
                        found = true;
                        double wx;
                        double wy;
                        double wz;
                        if (plane) {
                            wx = 0;
                            wy = -1;
                            wz = 0;
                        } else {
                            wx = (ax) / s.r();
                            wy = (ay + tHit * cy) / s.r();
                            wz = (az + tHit * cz) / s.r();
                        }
                        bnx = wx;
                        bny = wy * uy + wz * uz;
                        bnz = wy * cy + wz * cz;
                    }
                }
                if (found) {
                    int i = py * w + px;
                    hit[i] = true;
                    depth[i] = (float) bestT;
                    // Blend the puff's own normal with one for the whole cloud so the lighting reads as a
                    // single mass with broad light and shadow instead of a highlight on every puff.
                    double gx = sx / 1.15;
                    double gy = (sy - 0.05) / 0.75;
                    double gl = Math.sqrt(gx * gx + gy * gy);
                    if (gl > 0.97) {
                        gx *= 0.97 / gl;
                        gy *= 0.97 / gl;
                    }
                    double gz = Math.sqrt(Math.max(0, 1 - gx * gx - gy * gy));
                    double mx = bnx * (1 - GLOBAL_BLEND) + gx * GLOBAL_BLEND;
                    double my = bny * (1 - GLOBAL_BLEND) + gy * GLOBAL_BLEND;
                    double mz = bnz * (1 - GLOBAL_BLEND) + gz * GLOBAL_BLEND;
                    double len = Math.sqrt(mx * mx + my * my + mz * mz);
                    nx[i] = (float) (mx / len);
                    ny[i] = (float) (my / len);
                }
            }
        }

        // Cleanup: drop texels with almost no neighbours, which read as stray noise.
        boolean[] keep = new boolean[w * h];
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int i = py * w + px;
                if (!hit[i]) {
                    continue;
                }
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx != 0 || dy != 0) && inside(px + dx, py + dy, w, h) && hit[(py + dy) * w + px + dx]) {
                            n++;
                        }
                    }
                }
                keep[i] = n >= 3;
            }
        }

        // Crease occlusion: texels beside a nearer neighbour darken, which gives the cauliflower seams.
        int[] out = new int[w * h];
        final int[][] taps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int i = py * w + px;
                if (!keep[i]) {
                    continue;
                }
                float occ = 0f;
                int samples = 0;
                for (int ring = 1; ring <= 3; ring++) {
                    for (int[] t : taps) {
                        int qx2 = px + t[0] * ring * 2;
                        int qy2 = py + t[1] * ring * 2;
                        samples++;
                        if (!inside(qx2, qy2, w, h) || !keep[qy2 * w + qx2]) {
                            continue;
                        }
                        float d = depth[qy2 * w + qx2] - depth[i];
                        if (d > 0.015f) {
                            occ += Math.min(1f, d / 0.10f);
                        }
                    }
                }
                float ao = 1f - Math.min(1f, occ / samples * 2.2f);
                int r = Math.round((nx[i] * 0.5f + 0.5f) * 255f);
                int g = Math.round((ny[i] * 0.5f + 0.5f) * 255f);
                int b = Math.round(ao * 255f);
                out[i] = 0xFF000000 | (b << 16) | (g << 8) | r;
            }
        }
        return out;
    }

    private static boolean inside(int x, int y, int w, int h) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }
}
