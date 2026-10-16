package com.pixelclouds.sky;

/**
 * Five shade colours (darkest to lightest), a rim colour and a violet tint, chosen from the sun's height,
 * whether it is morning or evening, and the weather. Pure maths, no Minecraft types.
 */
public final class CloudPalette {
    public static final int SHADES = 5;

    /** Darkest to lightest, RGB 0..1. */
    public final float[][] shade = new float[SHADES][3];
    public final float[] rim = new float[3];
    /** Tint that the shadows lean towards in some parts of a cloud. */
    public final float[] violet = new float[3];
    /** 0..1: how much of the sky is overcast. */
    public float storm;

    private record Key(float sun, int rim, int violet, int[] c) {}

    private static Key key(float sun, int rim, int violet, int... c) {
        return new Key(sun, rim, violet, c);
    }

    // Colours follow the muted pixel-art skies this mod is based on.
    private static final Key[] EVENING = {
            key(-0.32f, 0xA8BAE8, 0x4A3A80, 0x151A2E, 0x20284A, 0x2F3A66, 0x45548A, 0x6A7CB0),
            key(-0.10f, 0xF0A090, 0x6A4A9A, 0x2C2850, 0x453C70, 0x62508C, 0x866AA4, 0xB690B6),
            key(0.00f, 0xFFD9A0, 0x7A5CA0, 0x474A7E, 0x66608E, 0x8E7A9E, 0xD0948C, 0xF4C09A),
            key(0.14f, 0xFFE8B8, 0x8070B0, 0x6874A6, 0x8A88B0, 0xB89CB4, 0xE8B8A0, 0xFFE4C8),
            key(0.42f, 0xFFFFFF, 0x8A7CC8, 0x7585C0, 0x93A6DA, 0xB4C6EE, 0xD6E4FA, 0xFFFFFF)};

    private static final Key[] MORNING = {
            key(-0.32f, 0xA8BAE8, 0x4A3A80, 0x151A2E, 0x20284A, 0x2F3A66, 0x45548A, 0x6A7CB0),
            key(-0.10f, 0xF4A8A0, 0x704C9E, 0x302C58, 0x4C4078, 0x6C5494, 0x906EAA, 0xC498B8),
            key(0.00f, 0xFFE0C8, 0x8468B0, 0x6A62A4, 0x8E78B4, 0xB48CBC, 0xF4A09C, 0xFFD2CC),
            key(0.14f, 0xFFEEC8, 0x8C78B8, 0x7882B8, 0x9C98C0, 0xC4A8C4, 0xF4C4B4, 0xFFEBDD),
            key(0.42f, 0xFFFFFF, 0x8A7CC8, 0x7585C0, 0x93A6DA, 0xB4C6EE, 0xD6E4FA, 0xFFFFFF)};

    private static final int[] RAIN = {0x38404E, 0x4A5464, 0x5E6A7C, 0x78849A, 0x94A0B0};
    private static final int[] THUNDER = {0x232832, 0x323946, 0x424B5A, 0x566070, 0x6E7A8C};

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
        for (int i = 0; i < SHADES; i++) {
            mix(p.shade[i], a.c[i], b.c[i], t);
        }
        mix(p.rim, a.rim, b.rim, t);
        mix(p.violet, a.violet, b.violet, t);

        float w = clamp01(rain * 0.88f + thunder * 0.12f);
        p.storm = w;
        if (w > 0f) {
            float daylight = smooth(-0.15f, 0.25f, sunY);
            float scale = 0.32f + 0.68f * daylight;
            for (int i = 0; i < SHADES; i++) {
                float[] stormColour = new float[3];
                mix(stormColour, RAIN[i], THUNDER[i], clamp01(thunder));
                for (int k = 0; k < 3; k++) {
                    p.shade[i][k] += (stormColour[k] * scale - p.shade[i][k]) * w * 0.9f;
                }
            }
            for (int k = 0; k < 3; k++) {
                p.rim[k] += (p.shade[4][k] - p.rim[k]) * w * 0.85f;
                p.violet[k] += (p.shade[1][k] - p.violet[k]) * w * 0.7f;
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
