package com.pixelclouds.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * Colours for shade indices 1 to 4 (navy underside, lavender folds, light-blue body, white tops), blended from a
 * few hand-picked palettes by sun height and weather: day, night, a pink dawn and dusk, and a grey storm.
 */
public final class CloudPalette {
    private CloudPalette() {}

    //                                     underside  folds     body      tops
    private static final int[][] DAY   = {{31, 66, 148}, {138, 150, 222}, {164, 196, 250}, {255, 255, 255}};
    private static final int[][] DUSK  = {{88, 56, 112}, {190, 116, 150}, {238, 160, 160}, {255, 216, 184}};
    private static final int[][] NIGHT = {{14, 18, 40}, {40, 46, 82}, {58, 68, 108}, {92, 104, 146}};
    private static final int[][] STORM = {{46, 50, 60}, {84, 88, 100}, {118, 122, 134}, {160, 164, 174}};

    /** ARGB colours indexed by shade (index 0 unused). */
    public static int[] current(ClientLevel level, float partialTick) {
        float angle = level.getTimeOfDay(partialTick);
        float sun = Mth.cos(angle * Mth.TWO_PI);                  // 1 at noon, -1 at midnight
        float day = smooth(-0.15f, 0.25f, sun);
        float dusk = 1f - smooth(0f, 0.3f, Math.abs(sun - 0.02f));  // strongest as the sun crosses the horizon
        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        float storm = Mth.clamp(rain * 0.8f + thunder * 0.2f, 0f, 1f);

        int[] out = new int[5];
        for (int i = 0; i < 4; i++) {
            float[] c = new float[3];
            for (int k = 0; k < 3; k++) {
                float v = Mth.lerp(day, NIGHT[i][k], DAY[i][k]);
                v = Mth.lerp(dusk * 0.85f, v, DUSK[i][k]);
                float stormed = STORM[i][k] * Mth.lerp(day, 0.35f, 1f);  // storms are dark at night too
                c[k] = Mth.lerp(storm, v, stormed);
            }
            out[i + 1] = 0xFF000000 | (Math.round(c[0]) << 16) | (Math.round(c[1]) << 8) | Math.round(c[2]);
        }
        return out;
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
