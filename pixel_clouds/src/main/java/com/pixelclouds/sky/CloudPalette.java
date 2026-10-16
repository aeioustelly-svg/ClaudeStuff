package com.pixelclouds.sky;

/**
 * Four shade colours (darkest to lightest) plus a rim colour, chosen from the sun's height,
 * whether it is morning or evening, and the weather. Pure maths, no Minecraft types.
 */
public final class CloudPalette {
    /** Darkest to lightest, RGB 0..1. */
    public final float[][] shade = new float[4][3];
    public final float[] rim = new float[3];
    /** 0..1: how much of the sky is overcast, used to dim the haze. */
    public float storm;

    private record Key(float sun, int[] c, int rim) {}

    private static Key key(float sun, int rim, int... c) {
        return new Key(sun, c, rim);
    }

    // Colours follow the muted pixel-art skies this mod is based on.
    private static final Key[] EVENING = {
            key(-0.32f, 0x9FB2E0, 0x1C2238, 0x2A3458, 0x3C4A7A, 0x5C6C9C),
            key(-0.10f, 0xF0A090, 0x3A3566, 0x5A4C86, 0x7F689E, 0xB08AB0),
            key(0.00f, 0xFFD9A0, 0x5A5C8E, 0x8A7AA4, 0xD0948C, 0xF4C09A),
            key(0.14f, 0xFFE8B8, 0x7886B4, 0xA89CB4, 0xE8B8A0, 0xFFE4C8),
            key(0.42f, 0xFFFFFF, 0x8F9FCB, 0xB6C4E2, 0xDCE6F4, 0xFAFCFF)};

    private static final Key[] MORNING = {
            key(-0.32f, 0x9FB2E0, 0x1C2238, 0x2A3458, 0x3C4A7A, 0x5C6C9C),
            key(-0.10f, 0xF4A8A0, 0x3E3A6E, 0x61528E, 0x8C70A6, 0xC096B6),
            key(0.00f, 0xFFE0C8, 0x7C74B4, 0xB48CBC, 0xF4A09C, 0xFFD2CC),
            key(0.14f, 0xFFEEC8, 0x8A94C4, 0xB8A8C8, 0xF4C4B4, 0xFFEBDD),
            key(0.42f, 0xFFFFFF, 0x8F9FCB, 0xB6C4E2, 0xDCE6F4, 0xFAFCFF)};

    private static final int[] RAIN = {0x434B5C, 0x5E687A, 0x7A8596, 0x9AA4B2};
    private static final int[] THUNDER = {0x2A303C, 0x3C4452, 0x535C6C, 0x6E7888};

    private CloudPalette() {}

    /**
     * @param sunY    vertical component of the unit vector towards the sun (-1 below, 1 overhead)
     * @param morning true while the sun is in the eastern half of its path (rising)
     */
    public static CloudPalette compute(float sunY, boolean morning, float rain, float thunder) {
        CloudPalette p = new CloudPalette();
        Key[] keys = morning ? MORNING : EVENING;
        int lo = 0;
        while (lo < keys.length - 2 && sunY >= keys[lo + 1].sun) {
            lo++;
        }
        Key a = keys[lo];
        Key b = keys[lo + 1];
        float t = clamp01((sunY - a.sun) / (b.sun - a.sun));
        t = t * t * (3f - 2f * t);
        for (int i = 0; i < 4; i++) {
            mix(p.shade[i], a.c[i], b.c[i], t);
        }
        mix(p.rim, a.rim, b.rim, t);

        float w = clamp01(rain * 0.88f + thunder * 0.12f);
        p.storm = w;
        if (w > 0f) {
            float daylight = smooth(-0.15f, 0.25f, sunY);
            float scale = 0.32f + 0.68f * daylight;
            for (int i = 0; i < 4; i++) {
                float[] stormColour = new float[3];
                mix(stormColour, RAIN[i], THUNDER[i], clamp01(thunder));
                for (int k = 0; k < 3; k++) {
                    p.shade[i][k] += (stormColour[k] * scale - p.shade[i][k]) * w * 0.9f;
                }
            }
            for (int k = 0; k < 3; k++) {
                p.rim[k] *= 1f - 0.7f * w;
            }
        }
        return p;
    }

    private static void mix(float[] out, int a, int b, float t) {
        for (int k = 0; k < 3; k++) {
            int shift = 16 - 8 * k;
            float ca = ((a >> shift) & 0xFF) / 255f;
            float cb = ((b >> shift) & 0xFF) / 255f;
            out[k] = ca + (cb - ca) * t;
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static float smooth(float e0, float e1, float v) {
        float t = clamp01((v - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }
}
