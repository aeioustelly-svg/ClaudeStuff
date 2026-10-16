package com.pixelclouds.gen;

/**
 * Lays out every cloud variant at every viewing elevation in one texture.
 * Slot index = variant * BINS + bin.
 */
public final class CloudAtlas {
    public static final int COLUMNS = 8;

    private static final CloudModel.Kind[] KINDS = {
            CloudModel.Kind.CUMULUS, CloudModel.Kind.CUMULUS, CloudModel.Kind.CUMULUS,
            CloudModel.Kind.TOWER, CloudModel.Kind.TOWER,
            CloudModel.Kind.FLAT, CloudModel.Kind.FLAT, CloudModel.Kind.FLAT,
            CloudModel.Kind.WISP, CloudModel.Kind.WISP,
            CloudModel.Kind.STORM, CloudModel.Kind.STORM};

    public static final int VARIANTS = KINDS.length;

    public final int width;
    public final int height;
    /** Packed 0xAABBGGRR. */
    public final int[] pixels;

    private CloudAtlas(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public static CloudModel.Kind kindOf(int variant) {
        return KINDS[variant];
    }

    public static int rows() {
        return (VARIANTS * CloudSprites.BINS + COLUMNS - 1) / COLUMNS;
    }

    public static CloudAtlas build() {
        int w = COLUMNS * CloudSprites.SPRITE_W;
        int h = rows() * CloudSprites.SPRITE_H;
        int[] px = new int[w * h];
        for (int v = 0; v < VARIANTS; v++) {
            CloudModel model = CloudModel.create(KINDS[v], 1000L + v * 7919L);
            for (int b = 0; b < CloudSprites.BINS; b++) {
                int[] sprite = CloudSprites.render(model, CloudSprites.ELEVATIONS[b]);
                int slot = v * CloudSprites.BINS + b;
                int ox = (slot % COLUMNS) * CloudSprites.SPRITE_W;
                int oy = (slot / COLUMNS) * CloudSprites.SPRITE_H;
                for (int y = 0; y < CloudSprites.SPRITE_H; y++) {
                    System.arraycopy(sprite, y * CloudSprites.SPRITE_W, px, (oy + y) * w + ox, CloudSprites.SPRITE_W);
                }
            }
        }
        return new CloudAtlas(w, h, px);
    }

    /** Left edge of a slot, as a 0..1 texture coordinate. */
    public float u0(int variant, int bin) {
        return ((variant * CloudSprites.BINS + bin) % COLUMNS) * CloudSprites.SPRITE_W / (float) width;
    }

    public float v0(int variant, int bin) {
        return ((variant * CloudSprites.BINS + bin) / COLUMNS) * CloudSprites.SPRITE_H / (float) height;
    }

    public float du() {
        return CloudSprites.SPRITE_W / (float) width;
    }

    public float dv() {
        return CloudSprites.SPRITE_H / (float) height;
    }
}
