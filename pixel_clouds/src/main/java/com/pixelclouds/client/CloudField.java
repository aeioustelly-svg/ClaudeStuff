package com.pixelclouds.client;

/**
 * Decides where the clouds are. Several layers drift at different speeds and altitudes, each an endless hashed
 * grid with at most one cloud per cell. Because the layers move through each other, clouds overlap, merge into
 * larger masses and slide apart again. A lower, denser overcast layer fills in as rain builds.
 */
final class CloudField {
    static final class Cloud {
        /** Centre of the cloud body. */
        float x;
        float y;
        float z;
        int sprite;
        /** Quad size in blocks. */
        float width;
        float height;
        /** Visible body width in blocks, for fading near the camera. */
        float bodyW;
        /** 0..1 fade-in as the weather makes this cloud appear. */
        float presence;
    }

    /** Blocks per art pixel before the size setting is applied. */
    static final float BLOCKS_PER_PIXEL = 0.9f;

    /** cell size, speed multiplier, altitude offset, salt, share of the coverage setting. */
    private static final double[][] LAYERS = {
            {118, 1.00, 0, 0x51F15E1DL, 1.0},
            {97, 1.35, 14, 0x2C1B3A77L, 0.8},
            {131, 0.70, -10, 0x6D0E5B13L, 0.8},
    };
    private static final double[] OVERCAST = {90, 1.25, -45, 0x7A11F00DL, 1.0};

    private static final int MAX = 2500;
    final Cloud[] clouds = new Cloud[MAX];
    int count;
    /** 0..1: how deep inside a cloud the camera is. */
    float inside;

    CloudField() {
        for (int i = 0; i < MAX; i++) {
            clouds[i] = new Cloud();
        }
    }

    void update(SpriteAtlas atlas, double camX, double camY, double camZ, double seconds, float rain,
                float coverage, float size, float windSpeed, float height, float range) {
        count = 0;
        inside = 0f;
        double dirX = Math.cos(0.45) * windSpeed * seconds;
        double dirZ = Math.sin(0.45) * windSpeed * seconds;
        for (double[] l : LAYERS) {
            scan(atlas, l, false, camX, camY, camZ, dirX, dirZ, range, rain, coverage, size, height);
        }
        if (rain > 0.01f) {
            scan(atlas, OVERCAST, true, camX, camY, camZ, dirX, dirZ, range, rain, coverage, size, height);
        }
    }

    private void scan(SpriteAtlas atlas, double[] layer, boolean overcast, double camX, double camY, double camZ,
                      double dirX, double dirZ, float range, float rain, float coverage, float size, float height) {
        float cell = (float) layer[0];
        double windX = dirX * layer[1];
        double windZ = dirZ * layer[1];
        float baseHeight = height + (float) layer[2];
        long salt = (long) layer[3];
        double lx = camX - windX;
        double lz = camZ - windZ;
        // Clouds are wide, so look a little beyond the range for those whose body reaches into view.
        float reach = range + 150f;
        int x0 = (int) Math.floor((lx - reach) / cell);
        int x1 = (int) Math.floor((lx + reach) / cell);
        int z0 = (int) Math.floor((lz - reach) / cell);
        int z1 = (int) Math.floor((lz + reach) / cell);

        for (int cz = z0; cz <= z1; cz++) {
            for (int cx = x0; cx <= x1; cx++) {
                if (count >= MAX) {
                    return;
                }
                long h = hash(cx, cz, salt);
                float rOcc = rnd(h, 1);
                float rJx = rnd(h, 3);
                float rJz = rnd(h, 4);
                float rY = rnd(h, 5);
                float rSize = rnd(h, 6);
                float rVar = rnd(h, 7);
                float rAspect = rnd(h, 8);
                float rFlip = rnd(h, 9);

                float patch = (smoothNoise(cx * 0.21f, cz * 0.21f, (int) salt) - 0.5f) * 0.9f;
                float cover = Math.max(0f, coverage * (float) layer[4] + patch);
                float chance = overcast ? rain * 1.15f : cover * (1f - 0.55f * rain);

                // Clouds fade in over a small range of the weather value rather than popping.
                float presence = clamp01((chance - rOcc) / 0.08f);
                if (presence <= 0f) {
                    continue;
                }
                int sprite = Math.min(atlas.count - 1, (int) (rVar * atlas.count));
                float pixel = BLOCKS_PER_PIXEL * size * (0.45f + 0.75f * rSize) * (overcast ? 1.5f : 1f);
                float aspect = overcast ? 0.55f + 0.15f * rAspect : 0.8f + 0.5f * rAspect;
                float w = atlas.spriteWidth(sprite) * pixel * (overcast ? 1.2f : 1f);
                float hgt = atlas.spriteHeight(sprite) * pixel * aspect;
                double wx = (cx + 0.5 + (rJx - 0.5) * 0.7) * cell + windX;
                double wz = (cz + 0.5 + (rJz - 0.5) * 0.7) * cell + windZ;
                double dx = wx - camX;
                double dz = wz - camZ;
                double lim = range + w * 0.5;
                if (dx * dx + dz * dz > lim * lim) {
                    continue;
                }

                Cloud c = clouds[count++];
                c.sprite = sprite;
                c.width = w;
                c.height = hgt;
                c.bodyW = atlas.bodyWidth(sprite) * (w / atlas.spriteWidth(sprite));
                c.x = (float) wx;
                c.z = (float) wz;
                c.y = baseHeight + rY * (overcast ? 20f : 40f) - 20f * rain * (overcast ? 0f : 1f) + hgt * 0.5f;
                c.presence = presence;

                double rx = camX - c.x;
                double ry = camY - c.y;
                double rz = camZ - c.z;
                double rh = c.bodyW * 0.36;
                double rv = hgt * 0.38;
                double d = Math.sqrt((rx * rx + rz * rz) / (rh * rh) + (ry * ry) / (rv * rv));
                float in = (float) (1.0 - d) * presence;
                if (in > inside) {
                    inside = in;
                }
            }
        }
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
