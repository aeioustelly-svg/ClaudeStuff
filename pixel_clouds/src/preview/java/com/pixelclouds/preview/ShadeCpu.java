package com.pixelclouds.preview;

import com.pixelclouds.sky.CloudPalette;

/** CPU twin of assets/pixel_clouds/shaders/core/pixel_cloud.fsh (minus the fade dither). Keep the two in step. */
public final class ShadeCpu {
    private ShadeCpu() {}

    /**
     * @param texel 0xAABBGGRR atlas texel
     * @param l     unit light direction in sprite space
     * @return RGB 0..1, or null if the texel is empty
     */
    public static float[] shade(int texel, float[] l, float bias, float haze, CloudPalette pal, float[] hazeColour) {
        int code = texel >>> 24;
        if (code < 1) {
            return null;
        }
        float nx = (texel & 0xFF) / 255f * 2f - 1f;
        float ny = ((texel >> 8) & 0xFF) / 255f * 2f - 1f;
        float open = ((texel >> 16) & 0xFF) / 255f;
        float nz = (float) Math.sqrt(Math.max(0f, 1f - nx * nx - ny * ny));
        boolean fringe = code < 70;
        float v = Math.max(code - 96, 0);
        float hueCell = (float) Math.floor(v / 8f);
        float hue = hueCell / 7f;
        float jit = (v - hueCell * 8f) / 7f;

        float lit = clamp((nx * l[0] + ny * l[1] + nz * 0.3f * l[2]) * 0.5f + 0.5f);
        lit *= 0.55f + 0.45f * open;
        lit += (jit - 0.5f) * 0.12f + (bias - 0.5f) * 0.7f;
        float back = clamp(-l[2]);
        float nl = (float) Math.sqrt(nx * nx + ny * ny) + 1e-5f;
        float ll = (float) Math.sqrt(l[0] * l[0] + l[1] * l[1]) + 1e-5f;
        float towards = clamp((nx * l[0] + ny * l[1]) / (nl * ll));
        float rim = back * (float) Math.pow(1f - nz, 1.5) * 1.6f * (0.35f + 0.65f * towards);
        lit += rim * 0.4f;

        float[] col = new float[3];
        if (fringe) {
            float[] base = lit > 0.6f ? pal.shade[3] : pal.shade[2];
            for (int i = 0; i < 3; i++) {
                col[i] = base[i] + (hazeColour[i] - base[i]) * 0.55f;
            }
        } else {
            float[] base = lit < 0.26f ? pal.shade[0] : lit < 0.44f ? pal.shade[1] : lit < 0.62f ? pal.shade[2] : lit < 0.80f ? pal.shade[3] : pal.shade[4];
            float sw = lit < 0.44f ? 1f : lit < 0.62f ? 0.6f : 0f;
            for (int i = 0; i < 3; i++) {
                col[i] = base[i] + (pal.violet[i] - base[i]) * sw * hue * 0.55f;
            }
            if (rim > 0.40f) {
                col = pal.rim.clone();
            }
        }
        for (int i = 0; i < 3; i++) {
            col[i] = col[i] + (hazeColour[i] - col[i]) * haze;
        }
        return col;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
