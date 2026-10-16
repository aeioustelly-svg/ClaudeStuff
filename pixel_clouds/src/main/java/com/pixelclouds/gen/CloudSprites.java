package com.pixelclouds.gen;

/**
 * Renders a {@link CloudVolume} into a lighting-ready pixel sprite as seen from a given elevation.
 *
 * <p>Texel layout (0xAABBGGRR):
 * <ul>
 *   <li>R, G: the surface normal's x and y in sprite space (z towards the viewer is implied). It blends the
 *       fine surface with the large-scale form of the cloud, so light and shadow read as broad masses with
 *       bumpy edges rather than a highlight on every lobe.</li>
 *   <li>B: openness, 255 for an exposed surface and lower in crevices. Drives the secondary shadows.</li>
 *   <li>A: 0 for empty, {@link #FRINGE} for a soft outer fringe pixel, otherwise
 *       {@link #BODY_BASE} + 8 * hue + jitter, where hue and jitter are 0..7. Hue varies slowly across the cloud
 *       and tints the shadows. Jitter is fine noise that roughens the edges between shade bands.</li>
 * </ul>
 *
 * <p>No Minecraft dependencies, so the preview tool can run it.
 */
public final class CloudSprites {
    /** Camera elevation angles, in degrees, at which sprites are baked. Positive: cloud above the camera. */
    public static final float[] ELEVATIONS = {-72f, -50f, -30f, -14f, 0f, 14f, 30f, 50f, 72f};
    public static final int BINS = ELEVATIONS.length;

    public static final int FRINGE = 40;
    public static final int BODY_BASE = 96;

    /** Weight of the large-scale normal against the fine one. */
    private static final float COARSE_BLEND = 0.5f;
    private static final double STEP = 0.7;
    private static final int[] BAYER4 = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private CloudSprites() {}

    /** Canvas height needed to hold the volume at every baked elevation. */
    public static int canvasHeight(CloudVolume v, int margin) {
        double hx = (v.maxX - v.minX + 1) / 2.0;
        double hy = (v.maxY - v.minY + 1) / 2.0;
        double hz = (v.maxZ - v.minZ + 1) / 2.0;
        double best = 0;
        for (float e : ELEVATIONS) {
            double s = Math.sin(Math.toRadians(e));
            double c = Math.cos(Math.toRadians(e));
            best = Math.max(best, hy * c + hz * Math.abs(s));
        }
        return (int) Math.ceil(best * 2) + 2 * margin;
    }

    public static int[] render(CloudVolume v, float elevationDeg, int w, int h, int seed) {
        double e = Math.toRadians(elevationDeg);
        double sin = Math.sin(e);
        double cos = Math.cos(e);
        double cyc = -sin;
        double czc = cos;
        double cx = (v.minX + v.maxX + 1) / 2.0;
        double cy = (v.minY + v.maxY + 1) / 2.0;
        double cz = (v.minZ + v.maxZ + 1) / 2.0;

        int[] kind = new int[w * h]; // 0 empty, 1 body, 2 fringe candidate
        float[] nxs = new float[w * h];
        float[] nys = new float[w * h];
        float[] ao = new float[w * h];
        int[] hue = new int[w * h];
        int[] jit = new int[w * h];
        float[] fringe = new float[w * h];
        double[] n = new double[3];
        double[] nc = new double[3];

        for (int py = 0; py < h; py++) {
            double sy = h / 2.0 - (py + 0.5);
            for (int px = 0; px < w; px++) {
                double sx = px + 0.5 - w / 2.0;
                double qx = cx + sx;
                double qy = cy + sy * cos;
                double qz = cz + sy * sin;
                if (qx < 0 || qx >= v.nx) {
                    continue;
                }
                double tLo = -1e9;
                double tHi = 1e9;
                if (Math.abs(cyc) > 1e-9) {
                    double t1 = (0 - qy) / cyc;
                    double t2 = (v.ny - qy) / cyc;
                    tLo = Math.max(tLo, Math.min(t1, t2));
                    tHi = Math.min(tHi, Math.max(t1, t2));
                } else if (qy < 0 || qy >= v.ny) {
                    continue;
                }
                double t1 = (0 - qz) / czc;
                double t2 = (v.nz - qz) / czc;
                tLo = Math.max(tLo, Math.min(t1, t2));
                tHi = Math.min(tHi, Math.max(t1, t2));
                if (tHi <= tLo) {
                    continue;
                }

                float maxD = 0f;
                boolean hit = false;
                double t = tHi;
                for (; t >= tLo; t -= STEP) {
                    float d = v.nearest(v.full, qx, qy + t * cyc, qz + t * czc);
                    if (d > maxD) {
                        maxD = d;
                    }
                    if (d >= CloudVolume.ISO) {
                        hit = true;
                        break;
                    }
                }
                int i = py * w + px;
                if (!hit) {
                    if (maxD >= 0.58f * CloudVolume.ISO) {
                        kind[i] = 2;
                        fringe[i] = (maxD - 0.58f * CloudVolume.ISO) / (0.42f * CloudVolume.ISO);
                    }
                    continue;
                }

                double tOut = Math.min(t + STEP, tHi);
                double tIn = t;
                for (int k = 0; k < 4; k++) {
                    double tm = (tIn + tOut) / 2;
                    float d = v.sample(v.full, qx, qy + tm * cyc, qz + tm * czc);
                    if (d >= CloudVolume.ISO) {
                        tIn = tm;
                    } else {
                        tOut = tm;
                    }
                }
                double hx = qx;
                double hy = qy + tIn * cyc;
                double hz = qz + tIn * czc;

                boolean okF = outward(v, v.full, hx, hy, hz, 1.6, n);
                boolean okC = outward(v, v.coarse, hx, hy, hz, 3.0, nc);
                if (!okF && !okC) {
                    n[0] = 0;
                    n[1] = 0;
                    n[2] = 1;
                } else if (!okF) {
                    n[0] = nc[0];
                    n[1] = nc[1];
                    n[2] = nc[2];
                } else if (okC) {
                    double bx = nc[0] * COARSE_BLEND + n[0] * (1 - COARSE_BLEND);
                    double by = nc[1] * COARSE_BLEND + n[1] * (1 - COARSE_BLEND);
                    double bz = nc[2] * COARSE_BLEND + n[2] * (1 - COARSE_BLEND);
                    double len = Math.sqrt(bx * bx + by * by + bz * bz) + 1e-9;
                    n[0] = bx / len;
                    n[1] = by / len;
                    n[2] = bz / len;
                }

                double nvx = n[0];
                double nvy = n[1] * cos + n[2] * sin;
                double nvz = -n[1] * sin + n[2] * cos;
                if (nvz < 0) {
                    double len = Math.sqrt(nvx * nvx + nvy * nvy) + 1e-9;
                    nvx /= len;
                    nvy /= len;
                }
                kind[i] = 1;
                nxs[i] = (float) nvx;
                nys[i] = (float) nvy;
                ao[i] = openness(v, hx, hy, hz, n);
                hue[i] = Math.min(7, (int) (Noise.value(seed + 11, (float) hx * 0.05f, (float) hy * 0.05f, (float) hz * 0.05f) * 8f));
                jit[i] = Math.min(7, (int) (Noise.value(seed + 23, (float) hx * 0.23f, (float) hy * 0.23f, (float) hz * 0.23f) * 8f));
            }
        }

        // Drop lone body pixels.
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int i = py * w + px;
                if (kind[i] == 1 && neighbours(kind, w, h, px, py, 1) < 2) {
                    kind[i] = 0;
                }
            }
        }

        int[] out = new int[w * h];
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int i = py * w + px;
                if (kind[i] == 1) {
                    int r = Math.round((nxs[i] * 0.5f + 0.5f) * 255f);
                    int g = Math.round((nys[i] * 0.5f + 0.5f) * 255f);
                    int b = Math.round(Math.max(0f, Math.min(1f, ao[i])) * 255f);
                    int a = BODY_BASE + hue[i] * 8 + jit[i];
                    out[i] = (a << 24) | (b << 16) | (g << 8) | r;
                }
            }
        }

        // Soft fringe: dithered pixels just outside the body, facing away from it.
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int i = py * w + px;
                if (kind[i] != 2) {
                    continue;
                }
                float s = fringe[i] * 0.7f + 0.08f;
                if ((BAYER4[(py & 3) * 4 + (px & 3)] + 0.5f) / 16f >= s) {
                    continue;
                }
                float ax = 0f;
                float ay = 0f;
                int count = 0;
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        int qx = px + dx;
                        int qy = py + dy;
                        if ((dx != 0 || dy != 0) && qx >= 0 && qy >= 0 && qx < w && qy < h && kind[qy * w + qx] == 1) {
                            ax -= dx;
                            ay += dy;
                            count++;
                        }
                    }
                }
                if (count == 0) {
                    continue;
                }
                float len = (float) Math.sqrt(ax * ax + ay * ay) + 1e-4f;
                int r = Math.round((ax / len * 0.9f * 0.5f + 0.5f) * 255f);
                int g = Math.round((ay / len * 0.9f * 0.5f + 0.5f) * 255f);
                out[i] = (FRINGE << 24) | (255 << 16) | (g << 8) | r;
            }
        }
        return out;
    }

    private static int neighbours(int[] kind, int w, int h, int px, int py, int radius) {
        int c = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int qx = px + dx;
                int qy = py + dy;
                if ((dx != 0 || dy != 0) && qx >= 0 && qy >= 0 && qx < w && qy < h && kind[qy * w + qx] == 1) {
                    c++;
                }
            }
        }
        return c;
    }

    /** Outward surface normal from the density gradient. False when the gradient vanishes. */
    private static boolean outward(CloudVolume v, float[] grid, double x, double y, double z, double hStep, double[] out) {
        double gx = v.sample(grid, x - hStep, y, z) - v.sample(grid, x + hStep, y, z);
        double gy = v.sample(grid, x, y - hStep, z) - v.sample(grid, x, y + hStep, z);
        double gz = v.sample(grid, x, y, z - hStep) - v.sample(grid, x, y, z + hStep);
        double len = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (len < 1e-6) {
            return false;
        }
        out[0] = gx / len;
        out[1] = gy / len;
        out[2] = gz / len;
        return true;
    }

    /** 1 for an exposed surface, lower where neighbouring lobes close in around the point. */
    private static float openness(CloudVolume v, double x, double y, double z, double[] n) {
        double ux = Math.abs(n[1]) < 0.9 ? 0 : 1;
        double uy = Math.abs(n[1]) < 0.9 ? 1 : 0;
        // tangent 1 = n x up, tangent 2 = n x t1
        double t1x = n[1] * 0 - n[2] * uy;
        double t1y = n[2] * ux - n[0] * 0;
        double t1z = n[0] * uy - n[1] * ux;
        double l1 = Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z) + 1e-9;
        t1x /= l1;
        t1y /= l1;
        t1z /= l1;
        double t2x = n[1] * t1z - n[2] * t1y;
        double t2y = n[2] * t1x - n[0] * t1z;
        double t2z = n[0] * t1y - n[1] * t1x;
        float occ = 0f;
        int samples = 0;
        final double[] radii = {2.0, 4.5, 8.0};
        for (int k = 0; k < 6; k++) {
            double dx;
            double dy;
            double dz;
            if (k == 0) {
                dx = n[0];
                dy = n[1];
                dz = n[2];
            } else {
                double a = (k - 1) * Math.PI * 2 / 5;
                double ca = Math.cos(a) * 0.95;
                double sa = Math.sin(a) * 0.95;
                dx = n[0] + t1x * ca + t2x * sa;
                dy = n[1] + t1y * ca + t2y * sa;
                dz = n[2] + t1z * ca + t2z * sa;
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-9;
                dx /= l;
                dy /= l;
                dz /= l;
            }
            for (double r : radii) {
                occ += Math.min(1.2f, v.nearest(v.full, x + dx * r, y + dy * r, z + dz * r));
                samples++;
            }
        }
        return Math.max(0f, 1f - occ / samples * 1.25f);
    }
}
