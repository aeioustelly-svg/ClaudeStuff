package com.pixelclouds.client;

import com.pixelclouds.gen.CloudAtlas;

/**
 * Decides where the clouds are. The sky is split into a grid of cells and each cell hashes to at most one
 * cloud, so the layout is endless, costs nothing to store, and is identical every time. Two decks exist:
 * a fair-weather one and a lower, denser overcast one that fills in as rain builds.
 */
final class CloudField {
    static final class Cloud {
        float x;
        float y;
        float z;
        /** Width of the cloud body in blocks. */
        float width;
        int variant;
        /** 0..1 fade-in as the weather makes this cloud appear. */
        float presence;
        /** Brightness bias, 0.5 neutral. */
        float bias;
    }

    private static final int MAX = 3000;
    final Cloud[] clouds = new Cloud[MAX];
    int count;
    /** 0..1: how deep inside a cloud the camera is. */
    float inside;

    CloudField() {
        for (int i = 0; i < MAX; i++) {
            clouds[i] = new Cloud();
        }
    }

    // Variant index ranges, matching CloudAtlas.
    private static final int CUMULUS_FIRST = 0;
    private static final int CUMULUS_COUNT = 3;
    private static final int TOWER_FIRST = 3;
    private static final int TOWER_COUNT = 2;
    private static final int FLAT_FIRST = 5;
    private static final int FLAT_COUNT = 3;
    private static final int WISP_FIRST = 8;
    private static final int WISP_COUNT = 2;
    private static final int STORM_FIRST = 10;
    private static final int STORM_COUNT = 2;

    void update(double camX, double camY, double camZ, double seconds, float rain, float thunder,
                float coverage, float size, float windSpeed, float height, float range) {
        count = 0;
        inside = 0f;
        double windX = Math.cos(0.45) * windSpeed * seconds;
        double windZ = Math.sin(0.45) * windSpeed * seconds;

        // Fair-weather deck.
        scan(0, 110f, camX, camY, camZ, windX, windZ, range, rain, coverage, size, height);
        // Overcast deck, lower and a little faster. Empty in fine weather.
        if (rain > 0.01f) {
            scan(1, 85f, camX, camY, camZ, windX * 1.25, windZ * 1.25, range, rain, coverage, size, height - 45f);
        }
    }

    private void scan(int deck, float cell, double camX, double camY, double camZ, double windX, double windZ,
                      float range, float rain, float coverage, float size, float baseHeight) {
        double lx = camX - windX;
        double lz = camZ - windZ;
        int x0 = (int) Math.floor((lx - range) / cell);
        int x1 = (int) Math.floor((lx + range) / cell);
        int z0 = (int) Math.floor((lz - range) / cell);
        int z1 = (int) Math.floor((lz + range) / cell);
        long salt = deck == 0 ? 0x51F15E1DL : 0x7A11F00DL;

        for (int cz = z0; cz <= z1; cz++) {
            for (int cx = x0; cx <= x1; cx++) {
                if (count >= MAX) {
                    return;
                }
                long h = hash(cx, cz, salt);
                float rOcc = rnd(h, 1);
                float rType = rnd(h, 2);
                float rJx = rnd(h, 3);
                float rJz = rnd(h, 4);
                float rY = rnd(h, 5);
                float rSize = rnd(h, 6);
                float rVar = rnd(h, 7);
                float rBias = rnd(h, 8);

                int variant;
                float widthBase;
                float chance;
                float bias = 0.5f + (rBias - 0.5f) * 0.06f;
                if (deck == 0) {
                    if (rType < 0.30f) {
                        variant = CUMULUS_FIRST + (int) (rVar * CUMULUS_COUNT);
                        widthBase = 85f + rSize * 40f;
                        chance = coverage * (1f - 0.5f * rain);
                    } else if (rType < 0.52f) {
                        variant = TOWER_FIRST + (int) (rVar * TOWER_COUNT);
                        widthBase = 95f + rSize * 45f;
                        chance = coverage * 0.75f * (1f - 0.3f * rain);
                    } else if (rType < 0.78f) {
                        variant = FLAT_FIRST + (int) (rVar * FLAT_COUNT);
                        widthBase = 100f + rSize * 50f;
                        chance = coverage;
                    } else if (rType < 0.90f) {
                        variant = WISP_FIRST + (int) (rVar * WISP_COUNT);
                        widthBase = 110f + rSize * 60f;
                        chance = coverage * 0.9f * (1f - rain);
                        bias += 0.05f;
                    } else {
                        variant = STORM_FIRST + (int) (rVar * STORM_COUNT);
                        widthBase = 120f + rSize * 50f;
                        chance = coverage * 0.5f + rain * 0.5f;
                        bias -= 0.06f;
                    }
                } else {
                    if (rType < 0.7f) {
                        variant = FLAT_FIRST + (int) (rVar * FLAT_COUNT);
                        widthBase = 100f + rSize * 50f;
                    } else {
                        variant = STORM_FIRST + (int) (rVar * STORM_COUNT);
                        widthBase = 120f + rSize * 50f;
                        bias -= 0.04f;
                    }
                    chance = rain * 1.15f;
                    bias -= 0.04f;
                }

                // Clouds fade in over a small range of the weather value rather than popping.
                float presence = clamp01((chance - rOcc) / 0.08f);
                if (presence <= 0f) {
                    continue;
                }
                float width = widthBase * size * (deck == 1 ? 1f + 0.25f * rain : 1f);
                double wx = (cx + 0.5 + (rJx - 0.5) * 0.7) * cell + windX;
                double wz = (cz + 0.5 + (rJz - 0.5) * 0.7) * cell + windZ;
                double dx = wx - camX;
                double dz = wz - camZ;
                if (dx * dx + dz * dz > (double) range * range) {
                    continue;
                }
                float wy = deck == 0 ? baseHeight + rY * 45f - 20f * rain : baseHeight + rY * 20f;
                // Bigger clouds sit a little higher, since the model's base is below its centre.
                wy += width * 0.2f;

                Cloud c = clouds[count++];
                c.x = (float) wx;
                c.y = wy;
                c.z = (float) wz;
                c.width = width;
                c.variant = Math.min(variant, CloudAtlas.VARIANTS - 1);
                c.presence = presence;
                c.bias = bias;

                // How far the camera is inside this cloud's body.
                double rx = camX - wx;
                double ry = camY - wy;
                double rz = camZ - wz;
                double rh = width * 0.42;
                double rv = width * 0.20;
                double d = Math.sqrt((rx * rx + rz * rz) / (rh * rh) + (ry * ry) / (rv * rv));
                float in = (float) (1.0 - d) * presence;
                if (in > inside) {
                    inside = in;
                }
            }
        }
    }

    private static long hash(int x, int z, long salt) {
        long h = (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL ^ salt;
        return mix(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float rnd(long h, int k) {
        long v = mix(h + k * 0x9E3779B97F4A7C15L);
        return (v >>> 40) / (float) (1L << 24);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
