package com.pixelclouds.cloud;

/**
 * The four cloud tones for a given sky. Day colours are taken from the reference art, muted a
 * little to sit with vanilla's palette. At dawn and dusk the clouds turn peach and pink with
 * mauve shadows, at night they go slate blue, and rain or thunder pulls them towards grey.
 *
 * <p>Every palette has the same four slots (deep, shadow, body, light), so blending between
 * them never adds colours: each cloud stays a four-colour sprite at any time of day.
 */
public final class CloudPalette {
    // deep, shadow, body, light
    static final int[] DAY = {0x587FC6, 0x7DA5E2, 0xB4D3FA, 0xFFFFFF};
    static final int[] DUSK = {0x765A8C, 0xB17A9E, 0xEFAFA6, 0xFFE4C9};
    static final int[] NIGHT = {0x262D44, 0x343D58, 0x454F6C, 0x5B6684};
    static final int[] STORM = {0x3E424A, 0x535861, 0x6E747D, 0x8F959D};
    static final int[] STORM_NIGHT = {0x17191D, 0x202328, 0x2B2E34, 0x383C43};

    private CloudPalette() {
    }

    /**
     * @param sunHeight cosine of the sun's angle: 1 at noon, 0 at the horizon, -1 at midnight
     * @param rain      rain level, 0 to 1
     * @param thunder   thunder level, 0 to 1
     * @param flash     lightning flash, 0 to 1
     * @return opaque RGB colours indexed by {@link CloudImage} tone (index 0 is unused)
     */
    public static int[] compute(double sunHeight, double rain, double thunder, double flash) {
        double day = smoothstep(-0.22, 0.28, sunHeight);
        // Strongest just above the horizon, fading out by mid-morning and soon after dark.
        double dusk = 1.0 - Math.abs(sunHeight - 0.04) / 0.30;
        dusk = smoothstep(0.0, 1.0, dusk);
        double storm = clamp(rain * 0.8 + thunder * 0.35, 0.0, 1.0);

        int[] out = new int[CloudImage.TONES];
        for (int i = 0; i < 4; i++) {
            int c = lerp(NIGHT[i], DAY[i], day);
            c = lerp(c, DUSK[i], dusk * (1.0 - 0.7 * storm));
            int grey = lerp(STORM_NIGHT[i], STORM[i], day);
            c = lerp(c, grey, storm);
            c = lerp(c, 0xF4F2FF, flash * 0.55);
            out[i + 1] = c;
        }
        return out;
    }

    static int lerp(int a, int b, double t) {
        t = clamp(t, 0.0, 1.0);
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static double smoothstep(double lo, double hi, double x) {
        double t = clamp((x - lo) / (hi - lo), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : x > hi ? hi : x;
    }
}
