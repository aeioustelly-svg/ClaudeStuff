package com.dacianmod.draco.preview.ectonurite;

/**
 * Hands out texOffs for boxes in the order they are requested (simple shelf packing), so the
 * candidate models do not need hand-computed UV layouts. A box of size dx, dy, dz occupies
 * 2 * (dx + dz) by (dz + dy) texels, which is the vanilla cube layout.
 */
final class TexPacker {
    private final int width;
    private int x;
    private int y;
    private int rowHeight;

    TexPacker(int width) {
        this.width = width;
    }

    int[] next(int dx, int dy, int dz) {
        int w = 2 * (dx + dz);
        int h = dz + dy;
        if (x + w > width) {
            x = 0;
            y += rowHeight;
            rowHeight = 0;
        }
        int[] offs = {x, y};
        x += w;
        rowHeight = Math.max(rowHeight, h);
        return offs;
    }
}
