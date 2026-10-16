package com.pixelclouds.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.pixelclouds.PixelClouds;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Cloud sprites stacked in one texture. Sprite files are listed in clouds.txt and read from textures/cloud/.
 * Texel red holds the shading band (50 = deep, 100 = lavender, 150 = light, 200 = white), alpha is 255 where solid.
 */
final class SpriteAtlas {
    final int width;
    final int height;
    final int[] pixels;
    final int count;
    private final int[] top;
    private final int[] w;
    private final int[] h;
    /** Opaque bounding box per sprite: minX, minY, maxX, maxY. */
    private final int[][] body;

    private SpriteAtlas(int width, int height, int[] pixels, int[] top, int[] w, int[] h, int[][] body) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.count = w.length;
        this.top = top;
        this.w = w;
        this.h = h;
        this.body = body;
    }

    static SpriteAtlas load(ResourceManager manager) throws Exception {
        List<String> names = new ArrayList<>();
        ResourceLocation list = new ResourceLocation(PixelClouds.MODID, "clouds.txt");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                manager.getResourceOrThrow(list).open(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    names.add(line);
                }
            }
        }
        List<NativeImage> images = new ArrayList<>();
        try {
            int maxW = 1;
            int totalH = 0;
            for (String n : names) {
                ResourceLocation id = new ResourceLocation(PixelClouds.MODID, "textures/cloud/" + n);
                try (InputStream in = manager.getResourceOrThrow(id).open()) {
                    NativeImage img = NativeImage.read(in);
                    images.add(img);
                    maxW = Math.max(maxW, img.getWidth());
                    totalH += img.getHeight();
                }
            }
            if (images.isEmpty()) {
                throw new IllegalStateException("clouds.txt lists no sprites");
            }
            int n = images.size();
            int[] px = new int[maxW * totalH];
            int[] top = new int[n];
            int[] w = new int[n];
            int[] h = new int[n];
            int[][] body = new int[n][];
            int y0 = 0;
            for (int i = 0; i < n; i++) {
                NativeImage img = images.get(i);
                top[i] = y0;
                w[i] = img.getWidth();
                h[i] = img.getHeight();
                int minX = w[i];
                int minY = h[i];
                int maxX = 0;
                int maxY = 0;
                for (int y = 0; y < h[i]; y++) {
                    for (int x = 0; x < w[i]; x++) {
                        int p = img.getPixelRGBA(x, y);
                        px[(y0 + y) * maxW + x] = p;
                        if ((p >>> 24) > 127) {
                            minX = Math.min(minX, x);
                            minY = Math.min(minY, y);
                            maxX = Math.max(maxX, x);
                            maxY = Math.max(maxY, y);
                        }
                    }
                }
                body[i] = new int[]{minX, minY, maxX, maxY};
                y0 += h[i];
            }
            return new SpriteAtlas(maxW, totalH, px, top, w, h, body);
        } finally {
            images.forEach(NativeImage::close);
        }
    }

    int spriteWidth(int s) {
        return w[s];
    }

    int spriteHeight(int s) {
        return h[s];
    }

    /** Visible width and height of the opaque part, in sprite pixels. */
    int bodyWidth(int s) {
        return body[s][2] - body[s][0] + 1;
    }

    int bodyHeight(int s) {
        return body[s][3] - body[s][1] + 1;
    }

    float u0(int s) {
        return 0f;
    }

    float u1(int s) {
        return w[s] / (float) width;
    }

    float v0(int s) {
        return top[s] / (float) height;
    }

    float v1(int s) {
        return (top[s] + h[s]) / (float) height;
    }
}
