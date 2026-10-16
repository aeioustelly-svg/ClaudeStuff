package com.pixelclouds.gen;

/** Small deterministic value noise. Returns values in 0..1. */
final class Noise {
    private Noise() {}

    private static float hash(int seed, int x, int y, int z) {
        int n = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 362437;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0xFFFFFF) / 16777216f;
    }

    private static float fade(float t) {
        return t * t * (3f - 2f * t);
    }

    static float value(int seed, float x, float y, float z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        float tx = fade(x - x0);
        float ty = fade(y - y0);
        float tz = fade(z - z0);
        float c000 = hash(seed, x0, y0, z0);
        float c100 = hash(seed, x0 + 1, y0, z0);
        float c010 = hash(seed, x0, y0 + 1, z0);
        float c110 = hash(seed, x0 + 1, y0 + 1, z0);
        float c001 = hash(seed, x0, y0, z0 + 1);
        float c101 = hash(seed, x0 + 1, y0, z0 + 1);
        float c011 = hash(seed, x0, y0 + 1, z0 + 1);
        float c111 = hash(seed, x0 + 1, y0 + 1, z0 + 1);
        float a = c000 + (c100 - c000) * tx;
        float b = c010 + (c110 - c010) * tx;
        float c = c001 + (c101 - c001) * tx;
        float d = c011 + (c111 - c011) * tx;
        float e = a + (b - a) * ty;
        float f = c + (d - c) * ty;
        return e + (f - e) * tz;
    }
}
