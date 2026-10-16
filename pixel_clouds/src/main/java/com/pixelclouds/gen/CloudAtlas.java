package com.pixelclouds.gen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Bakes every cloud variant at every viewing elevation and packs the sprites into one texture.
 * All sprites share one pixel size, so a small cloud simply has fewer pixels than a big one.
 */
public final class CloudAtlas {
    public static final int ATLAS_WIDTH = 2048;
    private static final int MARGIN = 8;

    private static final CloudKind[] KIND_ORDER = {
            CloudKind.CUMULUS, CloudKind.TOWER, CloudKind.PUFF, CloudKind.BANK,
            CloudKind.CLUSTER, CloudKind.WISP, CloudKind.ANVIL, CloudKind.STRATUS};
    private static final int[] KIND_COUNT = {6, 4, 4, 4, 3, 3, 3, 3};

    private static final int VARIANTS;
    private static final int[] KIND_FIRST = new int[KIND_ORDER.length];

    static {
        int total = 0;
        for (int i = 0; i < KIND_ORDER.length; i++) {
            KIND_FIRST[i] = total;
            total += KIND_COUNT[i];
        }
        VARIANTS = total;
    }

    public final int width;
    public final int height;
    /** Packed 0xAABBGGRR. */
    public final int[] pixels;

    private final int[] slotX;
    private final int[] slotY;
    private final int[] canvasW;
    private final int[] canvasH;
    private final int[] bodyW;
    private final int[] bodyH;

    private CloudAtlas(int width, int height, int[] pixels, int[] slotX, int[] slotY, int[] canvasW, int[] canvasH,
                       int[] bodyW, int[] bodyH) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.slotX = slotX;
        this.slotY = slotY;
        this.canvasW = canvasW;
        this.canvasH = canvasH;
        this.bodyW = bodyW;
        this.bodyH = bodyH;
    }

    public static int variantCount() {
        return VARIANTS;
    }

    public static int first(CloudKind kind) {
        return KIND_FIRST[kind.ordinal()];
    }

    public static int count(CloudKind kind) {
        return KIND_COUNT[kind.ordinal()];
    }

    public static CloudKind kindOf(int variant) {
        for (int i = KIND_ORDER.length - 1; i >= 0; i--) {
            if (variant >= KIND_FIRST[i]) {
                return KIND_ORDER[i];
            }
        }
        return KIND_ORDER[0];
    }

    public static CloudAtlas build() {
        final int[][][] sprites = new int[VARIANTS][CloudSprites.BINS][];
        final int[] cw = new int[VARIANTS];
        final int[] ch = new int[VARIANTS];
        final int[] bw = new int[VARIANTS];
        final int[] bh = new int[VARIANTS];

        IntStream.range(0, VARIANTS).parallel().forEach(v -> {
            CloudKind kind = kindOf(v);
            long seed = 1000L + v * 7919L;
            CloudVolume vol = CloudShapes.build(kind, seed);
            if (vol.isEmpty()) {
                throw new IllegalStateException("empty cloud volume for variant " + v);
            }
            int w = vol.maxX - vol.minX + 1 + 2 * MARGIN;
            int h = CloudSprites.canvasHeight(vol, MARGIN);
            cw[v] = w;
            ch[v] = h;
            bw[v] = vol.maxX - vol.minX + 1;
            bh[v] = vol.maxY - vol.minY + 1;
            for (int b = 0; b < CloudSprites.BINS; b++) {
                sprites[v][b] = CloudSprites.render(vol, CloudSprites.ELEVATIONS[b], w, h, (int) seed);
            }
        });

        // Shelf packing, tallest first.
        record Item(int variant, int bin) {}
        List<Item> items = new ArrayList<>();
        for (int v = 0; v < VARIANTS; v++) {
            for (int b = 0; b < CloudSprites.BINS; b++) {
                items.add(new Item(v, b));
            }
        }
        items.sort(Comparator.<Item>comparingInt(i -> -ch[i.variant()]).thenComparingInt(Item::variant).thenComparingInt(Item::bin));
        int[] sx = new int[VARIANTS * CloudSprites.BINS];
        int[] sy = new int[VARIANTS * CloudSprites.BINS];
        int curX = 0;
        int curY = 0;
        int shelf = 0;
        for (Item it : items) {
            int w = cw[it.variant()];
            int h = ch[it.variant()];
            if (curX + w > ATLAS_WIDTH) {
                curY += shelf;
                curX = 0;
                shelf = 0;
            }
            int slot = it.variant() * CloudSprites.BINS + it.bin();
            sx[slot] = curX;
            sy[slot] = curY;
            curX += w;
            shelf = Math.max(shelf, h);
        }
        int atlasHeight = curY + shelf;

        int[] px = new int[ATLAS_WIDTH * atlasHeight];
        for (int v = 0; v < VARIANTS; v++) {
            for (int b = 0; b < CloudSprites.BINS; b++) {
                int slot = v * CloudSprites.BINS + b;
                for (int y = 0; y < ch[v]; y++) {
                    System.arraycopy(sprites[v][b], y * cw[v], px, (sy[slot] + y) * ATLAS_WIDTH + sx[slot], cw[v]);
                }
            }
        }
        return new CloudAtlas(ATLAS_WIDTH, atlasHeight, px, sx, sy, cw, ch, bw, bh);
    }

    /** Sprite size in pixels, shared by all elevations of a variant. */
    public int canvasWidth(int variant) {
        return canvasW[variant];
    }

    public int canvasHeight(int variant) {
        return canvasH[variant];
    }

    /** Width and height of the cloud's body itself in pixels, excluding the transparent margin. */
    public int bodyWidth(int variant) {
        return bodyW[variant];
    }

    public int bodyHeight(int variant) {
        return bodyH[variant];
    }

    public int slotX(int variant, int bin) {
        return slotX[variant * CloudSprites.BINS + bin];
    }

    public int slotY(int variant, int bin) {
        return slotY[variant * CloudSprites.BINS + bin];
    }

    public float u0(int variant, int bin) {
        return slotX(variant, bin) / (float) width;
    }

    public float v0(int variant, int bin) {
        return slotY(variant, bin) / (float) height;
    }

    public float du(int variant) {
        return canvasW[variant] / (float) width;
    }

    public float dv(int variant) {
        return canvasH[variant] / (float) height;
    }
}
