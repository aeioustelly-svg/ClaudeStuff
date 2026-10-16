package com.pixelclouds.client;

import com.pixelclouds.gen.CloudAtlas;
import com.pixelclouds.gen.CloudKind;

/**
 * Decides where the clouds are. The sky is split into a grid of cells and each cell hashes to at most one
 * cloud, so the layout is endless, costs nothing to store, and is identical every time. Coverage also varies
 * smoothly across the sky, so there are clear patches and crowded ones. Two decks exist: a fair-weather one and
 * a lower, denser overcast one that fills in as rain builds.
 */
final class CloudField {
    static final class Cloud {
        /** Centre of the cloud body. */
        float x;
        float y;
        float z;
        int variant;
        /** World size of one art pixel in blocks. */
        float pixel;
        /** Body size in blocks. */
        float bodyW;
        float bodyH;
        /** 0..1 fade-in as the weather makes this cloud appear. */
        float presence;
        /** Brightness bias, 0.5 neutral. */
        float bias;
    }

    /** Blocks per art pixel before the size setting is applied. */
    static final float BLOCKS_PER_PIXEL = 1.3f;

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

    // Fair-weather mix, as cumulative thresholds over rType.
    private static final CloudKind[] DECK0_KINDS = {
            CloudKind.CUMULUS, CloudKind.TOWER, CloudKind.PUFF, CloudKind.BANK,
            CloudKind.CLUSTER, CloudKind.WISP, CloudKind.ANVIL, CloudKind.STRATUS};
    private static final float[] DECK0_CUT = {0.20f, 0.29f, 0.49f, 0.60f, 0.70f, 0.81f, 0.86f, 1.0f};

    void update(CloudAtlas atlas, double camX, double camY, double camZ, double seconds, float rain, float thunder,
                float coverage, float size, float windSpeed, float height, float range) {
        count = 0;
        inside = 0f;
        double windX = Math.cos(0.45) * windSpeed * seconds;
        double windZ = Math.sin(0.45) * windSpeed * seconds;

        scan(atlas, 0, 110f, camX, camY, camZ, windX, windZ, range, rain, coverage, size, height);
        // Overcast deck, lower and a little faster. Empty in fine weather.
        if (rain > 0.01f) {
            scan(atlas, 1, 85f, camX, camY, camZ, windX * 1.25, windZ * 1.25, range, rain, coverage, size, height - 45f);
        }
    }

    private void scan(CloudAtlas atlas, int deck, float cell, double camX, double camY, double camZ,
                      double windX, double windZ, float range, float rain, float coverage, float size,
                      float baseHeight) {
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

                // Slowly varying coverage: clear patches and crowded ones.
                float patch = (smoothNoise(cx * 0.21f, cz * 0.21f, (int) salt) - 0.5f) * 0.9f;
                float cover = Math.max(0f, coverage + patch);

                CloudKind kind;
                float chance;
                float bias = 0.5f + (rBias - 0.5f) * 0.06f;
                float altitude;
                if (deck == 0) {
                    kind = pick(rType);
                    switch (kind) {
                        case CUMULUS -> {
                            chance = cover * (1f - 0.5f * rain);
                            altitude = baseHeight + rY * 35f;
                        }
                        case TOWER -> {
                            chance = cover * 0.7f * (1f - 0.3f * rain);
                            altitude = baseHeight + rY * 30f;
                        }
                        case PUFF -> {
                            chance = cover * 1.1f * (1f - 0.8f * rain);
                            altitude = baseHeight + rY * 40f;
                        }
                        case BANK -> {
                            chance = cover * 0.9f;
                            altitude = baseHeight - 10f + rY * 20f;
                        }
                        case CLUSTER -> {
                            chance = cover * 0.9f * (1f - rain);
                            altitude = baseHeight + 10f + rY * 40f;
                        }
                        case WISP -> {
                            chance = cover * 0.9f * (1f - rain);
                            altitude = baseHeight + 70f + rY * 40f;
                            bias += 0.05f;
                        }
                        case ANVIL -> {
                            chance = cover * 0.5f + rain * 0.5f;
                            altitude = baseHeight + rY * 20f;
                            bias -= 0.06f;
                        }
                        default -> {
                            chance = cover * 0.8f + 0.5f * rain;
                            altitude = baseHeight - 10f + rY * 20f;
                        }
                    }
                    altitude -= 20f * rain;
                } else {
                    kind = rType < 0.55f ? CloudKind.STRATUS : rType < 0.8f ? CloudKind.BANK : CloudKind.ANVIL;
                    chance = rain * 1.15f;
                    altitude = baseHeight + rY * 20f;
                    bias -= kind == CloudKind.ANVIL ? 0.08f : 0.04f;
                }

                // Clouds fade in over a small range of the weather value rather than popping.
                float presence = clamp01((chance - rOcc) / 0.08f);
                if (presence <= 0f) {
                    continue;
                }
                int variant = CloudAtlas.first(kind) + Math.min(CloudAtlas.count(kind) - 1, (int) (rVar * CloudAtlas.count(kind)));
                float pixel = BLOCKS_PER_PIXEL * size * (0.9f + 0.22f * rSize);
                double wx = (cx + 0.5 + (rJx - 0.5) * 0.7) * cell + windX;
                double wz = (cz + 0.5 + (rJz - 0.5) * 0.7) * cell + windZ;
                double dx = wx - camX;
                double dz = wz - camZ;
                if (dx * dx + dz * dz > (double) range * range) {
                    continue;
                }

                Cloud c = clouds[count++];
                c.variant = variant;
                c.pixel = pixel;
                c.bodyW = atlas.bodyWidth(variant) * pixel;
                c.bodyH = atlas.bodyHeight(variant) * pixel;
                c.x = (float) wx;
                c.z = (float) wz;
                c.y = altitude + c.bodyH * 0.5f;
                c.presence = presence;
                c.bias = bias;

                // How far the camera is inside this cloud's body.
                double rx = camX - c.x;
                double ry = camY - c.y;
                double rz = camZ - c.z;
                double rh = c.bodyW * 0.36;
                double rv = c.bodyH * 0.42;
                double d = Math.sqrt((rx * rx + rz * rz) / (rh * rh) + (ry * ry) / (rv * rv));
                float in = (float) (1.0 - d) * presence;
                if (in > inside) {
                    inside = in;
                }
            }
        }
    }

    private static CloudKind pick(float r) {
        for (int i = 0; i < DECK0_CUT.length; i++) {
            if (r < DECK0_CUT[i]) {
                return DECK0_KINDS[i];
            }
        }
        return DECK0_KINDS[DECK0_KINDS.length - 1];
    }

    private static float smoothNoise(float x, float y, int seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float tx = x - x0;
        float ty = y - y0;
        tx = tx * tx * (3f - 2f * tx);
        ty = ty * ty * (3f - 2f * ty);
        float a = rnd(hash(x0, y0, seed), 9);
        float b = rnd(hash(x0 + 1, y0, seed), 9);
        float c = rnd(hash(x0, y0 + 1, seed), 9);
        float d = rnd(hash(x0 + 1, y0 + 1, seed), 9);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
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
