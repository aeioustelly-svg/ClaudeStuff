package com.pixelclouds.cloud;

/**
 * A rasterised cloud: one tone index per texel. The renderer maps tones to the current palette,
 * so a sunset or a storm recolours every cloud without redrawing its shape.
 *
 * <p>{@code minU}/{@code minV} place the grid in the cloud's texel space, so the sprite stays
 * anchored to the cloud's origin while puffs grow, shrink or drift.
 */
public final class CloudImage {
    public static final byte EMPTY = 0;
    /** Underside rim, the darkest tone. */
    public static final byte DEEP = 1;
    public static final byte SHADOW = 2;
    public static final byte BODY = 3;
    public static final byte LIGHT = 4;
    public static final int TONES = 5;

    public final int minU;
    public final int minV;
    public final int width;
    public final int height;
    public final byte[] tones;

    public CloudImage(int minU, int minV, int width, int height, byte[] tones) {
        this.minU = minU;
        this.minV = minV;
        this.width = width;
        this.height = height;
        this.tones = tones;
    }

    public byte tone(int x, int y) {
        return tones[y * width + x];
    }

    public int filledTexels() {
        int n = 0;
        for (byte t : tones) {
            if (t != EMPTY) {
                n++;
            }
        }
        return n;
    }
}
