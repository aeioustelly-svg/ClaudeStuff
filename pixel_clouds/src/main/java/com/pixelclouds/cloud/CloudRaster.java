package com.pixelclouds.cloud;

import java.util.List;

/**
 * Turns a cloud's puffs into a flat pixel-art image.
 *
 * <p>Each puff is shaded as if it were a ball lit from the upper left, and the front-most puff
 * wins at every texel. Quantising that light into a few tones gives the look of the reference
 * art: white caps on the upper left of each puff, pale blue bodies, and a darker rim along the
 * underside. Nothing here is 3D at render time: the result is a single flat image per cloud.
 */
public final class CloudRaster {
    private static final double LX;
    private static final double LY;
    private static final double LZ;

    static {
        double x = -0.45, y = -0.62, z = 0.64;
        double len = Math.sqrt(x * x + y * y + z * z);
        LX = x / len;
        LY = y / len;
        LZ = z / len;
    }

    static final double LIGHT_THRESHOLD = 0.66;
    static final double BODY_THRESHOLD = 0.08;
    /** How much brighter the top of a cloud is than its base. */
    static final double TOP_BIAS = 0.26;

    private CloudRaster() {
    }

    public static CloudImage rasterise(List<Blob> blobs, int maxWidth, int maxHeight) {
        double minUf = Double.MAX_VALUE, maxUf = -Double.MAX_VALUE;
        double minVf = Double.MAX_VALUE, maxVf = -Double.MAX_VALUE;
        int live = 0;
        for (Blob b : blobs) {
            if (b.r < 0.5) {
                continue;
            }
            live++;
            minUf = Math.min(minUf, b.u - b.r);
            maxUf = Math.max(maxUf, b.u + b.r);
            minVf = Math.min(minVf, b.v - b.r);
            maxVf = Math.max(maxVf, b.v + b.r);
        }
        if (live == 0) {
            return new CloudImage(0, 0, 1, 1, new byte[1]);
        }

        int minU = (int) Math.floor(minUf) - 1;
        int maxU = (int) Math.ceil(maxUf) + 1;
        int minV = (int) Math.floor(minVf) - 1;
        int maxV = (int) Math.ceil(maxVf) + 1;
        int width = maxU - minU;
        int height = maxV - minV;
        if (width > maxWidth) {
            minU += (width - maxWidth) / 2;
            width = maxWidth;
        }
        if (height > maxHeight) {
            // Keep the base, which carries the shading that reads as a cloud, and crop the top.
            minV += height - maxHeight;
            height = maxHeight;
        }

        double top = minVf;
        double span = Math.max(1.0, maxVf - minVf);
        float[] light = new float[width * height];
        boolean[] inside = new boolean[width * height];

        for (int y = 0; y < height; y++) {
            double py = minV + y + 0.5;
            for (int x = 0; x < width; x++) {
                double px = minU + x + 0.5;
                double best = -Double.MAX_VALUE;
                Blob winner = null;
                double wdx = 0, wdy = 0;
                for (Blob b : blobs) {
                    if (b.r < 0.5) {
                        continue;
                    }
                    double dx = px - b.u;
                    double dy = py - b.v;
                    double d2 = dx * dx + dy * dy;
                    double r2 = b.r * b.r;
                    if (d2 >= r2) {
                        continue;
                    }
                    double h = b.depth() + Math.sqrt(r2 - d2);
                    if (h > best) {
                        best = h;
                        winner = b;
                        wdx = dx;
                        wdy = dy;
                    }
                }
                if (winner == null) {
                    continue;
                }
                double nx = wdx / winner.r;
                double ny = wdy / winner.r;
                double nz = Math.sqrt(Math.max(0.0, 1.0 - nx * nx - ny * ny));
                double l = nx * LX + ny * LY + nz * LZ;
                l += TOP_BIAS * (0.5 - (py - top) / span);
                int i = y * width + x;
                light[i] = (float) l;
                inside[i] = true;
            }
        }

        fillHoles(inside, light, width, height);
        byte[] tones = new byte[width * height];
        for (int x = 0; x < width; x++) {
            // Count filled texels below each texel to find the underside rim.
            int below = -1;
            for (int y = height - 1; y >= 0; y--) {
                int i = y * width + x;
                if (!inside[i]) {
                    below = -1;
                    continue;
                }
                below++;
                double l = light[i];
                byte t;
                if (l > LIGHT_THRESHOLD) {
                    t = CloudImage.LIGHT;
                } else if (l > BODY_THRESHOLD) {
                    t = CloudImage.BODY;
                } else {
                    t = CloudImage.SHADOW;
                }
                if (below == 0) {
                    t = CloudImage.DEEP;
                } else if (below == 1 && t != CloudImage.LIGHT) {
                    t = CloudImage.SHADOW;
                }
                tones[i] = t;
            }
        }
        despeckle(tones, width, height);
        return new CloudImage(minU, minV, width, height, tones);
    }

    /** Sky enclosed by puffs looks like a hole punched in the cloud, so it is filled in shadow. */
    private static void fillHoles(boolean[] inside, float[] light, int width, int height) {
        boolean[] outside = new boolean[width * height];
        int[] stack = new int[4 * width * height + 2 * (width + height)];
        int top = 0;
        for (int x = 0; x < width; x++) {
            stack[top++] = x;
            stack[top++] = (height - 1) * width + x;
        }
        for (int y = 0; y < height; y++) {
            stack[top++] = y * width;
            stack[top++] = y * width + width - 1;
        }
        while (top > 0) {
            int i = stack[--top];
            if (inside[i] || outside[i]) {
                continue;
            }
            outside[i] = true;
            int x = i % width, y = i / width;
            if (x > 0) stack[top++] = i - 1;
            if (x < width - 1) stack[top++] = i + 1;
            if (y > 0) stack[top++] = i - width;
            if (y < height - 1) stack[top++] = i + width;
        }
        for (int i = 0; i < inside.length; i++) {
            if (!inside[i] && !outside[i]) {
                inside[i] = true;
                light[i] = (float) BODY_THRESHOLD;
            }
        }
    }

    /** Lone texels of a tone read as noise in pixel art, so they take their neighbours' tone. */
    private static void despeckle(byte[] tones, int width, int height) {
        byte[] src = tones.clone();
        int[] count = new int[CloudImage.TONES];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                byte t = src[i];
                if (t == CloudImage.EMPTY) {
                    continue;
                }
                java.util.Arrays.fill(count, 0);
                boolean lonely = true;
                int filled = 0;
                for (int k = 0; k < 4; k++) {
                    int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0);
                    int ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                        continue;
                    }
                    byte n = src[ny * width + nx];
                    if (n == CloudImage.EMPTY) {
                        continue;
                    }
                    filled++;
                    count[n]++;
                    if (n == t) {
                        lonely = false;
                    }
                }
                if (lonely && filled >= 2) {
                    int bestTone = t;
                    int bestCount = 0;
                    for (int n = 1; n < CloudImage.TONES; n++) {
                        if (count[n] > bestCount) {
                            bestCount = count[n];
                            bestTone = n;
                        }
                    }
                    tones[i] = (byte) bestTone;
                }
            }
        }
    }

    /** Puffs that touch form one piece. Returns a component index per blob. */
    static int[] components(List<Blob> blobs, double slack) {
        int n = blobs.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (blobs.get(i).overlaps(blobs.get(j), slack)) {
                    int a = find(parent, i);
                    int b = find(parent, j);
                    if (a != b) {
                        parent[a] = b;
                    }
                }
            }
        }
        int[] label = new int[n];
        int[] map = new int[n];
        java.util.Arrays.fill(map, -1);
        int next = 0;
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            if (map[root] < 0) {
                map[root] = next++;
            }
            label[i] = map[root];
        }
        return label;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
