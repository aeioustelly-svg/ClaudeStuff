package com.ghostfreakmod.ghostfreak.client;

/**
 * Hands out texOffs for boxes in the order they are requested (simple shelf packing), so the
 * model does not need a hand-computed UV layout. A box of size dx, dy, dz occupies
 * 2 * (dx + dz) by (dz + dy) texels, which is the vanilla cube layout.
 */
final class TexPacker {
    private final int width;
    private int x;
    private int y;
    private int rowHeight;

    TexPacker(int width, int startY) {
        this.width = width;
        this.y = startY;
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
