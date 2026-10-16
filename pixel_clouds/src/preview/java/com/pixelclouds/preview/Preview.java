package com.pixelclouds.preview;

import com.pixelclouds.gen.CloudAtlas;
import com.pixelclouds.gen.CloudSprites;
import com.pixelclouds.sky.CloudPalette;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Dev-only: writes PNG contact sheets of the generated sprites, lit with a CPU copy of the
 * fragment shader. Run with plain javac/java (see the notes in NOTES.md), no Minecraft needed.
 *
 * Usage: Preview OUT_DIR
 */
public final class Preview {
    private static final float T0 = 0.28f;
    private static final float T1 = 0.50f;
    private static final float T2 = 0.72f;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        long t0 = System.nanoTime();
        CloudAtlas atlas = CloudAtlas.build();
        System.out.printf("atlas %dx%d built in %d ms%n", atlas.width, atlas.height, (System.nanoTime() - t0) / 1_000_000);

        // 1. Raw normal/occlusion atlas, drawn opaque on grey so the data is visible.
        BufferedImage raw = new BufferedImage(atlas.width, atlas.height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < atlas.height; y++) {
            for (int x = 0; x < atlas.width; x++) {
                int p = atlas.pixels[y * atlas.width + x];
                raw.setRGB(x, y, (p >>> 24) == 0 ? 0x303038 : 0xFF000000 | ((p & 0xFF) << 16) | (((p >> 8) & 0xFF) << 8) | ((p >> 16) & 0xFF));
            }
        }
        ImageIO.write(raw, "png", new File(out, "atlas_raw.png"));

        // 2. Each variant across all elevations, lit for a mid-afternoon sun.
        for (int v = 0; v < CloudAtlas.VARIANTS; v++) {
            int w = CloudSprites.SPRITE_W;
            int h = CloudSprites.SPRITE_H;
            BufferedImage img = new BufferedImage(w * CloudSprites.BINS, h, BufferedImage.TYPE_INT_RGB);
            fill(img, 0x7F94D8);
            CloudPalette pal = CloudPalette.compute(0.55f, false, 0f, 0f);
            for (int b = 0; b < CloudSprites.BINS; b++) {
                drawSprite(img, atlas, v, b, b * w, 0, pal, new float[]{-0.45f, 0.6f, 0.66f}, 1f, 0f);
            }
            ImageIO.write(upscale(img, 2), "png", new File(out, "variant_" + v + ".png"));
        }

        // 3. One cumulus in several lighting situations at the level elevation, from the palette keys.
        String[] names = {"noon", "evening", "sunset", "dusk", "night", "morning", "rain", "thunder"};
        float[] sunY = {0.8f, 0.2f, 0.0f, -0.1f, -0.4f, 0.0f, 0.5f, 0.5f};
        boolean[] morning = {false, false, false, false, false, true, false, false};
        float[] rain = {0, 0, 0, 0, 0, 0, 1, 1};
        float[] thunder = {0, 0, 0, 0, 0, 0, 0, 1};
        int[] sky = {0x7F94D8, 0x9A8CC0, 0xB080A8, 0x4A4274, 0x161C34, 0xA890C8, 0x68707C, 0x3A404C};
        int[] variants = {0, 3, 5, 10};
        int w = CloudSprites.SPRITE_W;
        int h = CloudSprites.SPRITE_H;
        BufferedImage sheet = new BufferedImage(w * names.length, h * variants.length, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < names.length; i++) {
            CloudPalette pal = CloudPalette.compute(sunY[i], morning[i], rain[i], thunder[i]);
            // Light direction in sprite space: sun to the left of the view and slightly behind the cloud at dusk.
            float[] l = i == 2 || i == 5 ? new float[]{-0.8f, 0.2f, -0.55f} : i == 4 ? new float[]{0.4f, 0.5f, 0.75f} : new float[]{-0.5f, 0.3f + sunY[i] * 0.4f, 0.65f};
            for (int j = 0; j < variants.length; j++) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        sheet.setRGB(i * w + x, j * h + y, sky[i]);
                    }
                }
                drawSprite(sheet, atlas, variants[j], 4, i * w, j * h, pal, l, 1f, j == 3 ? -0.1f : 0f);
            }
        }
        ImageIO.write(upscale(sheet, 2), "png", new File(out, "lighting.png"));
        System.out.println("wrote " + out);
    }

    private static void fill(BufferedImage img, int rgb) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.setRGB(x, y, rgb);
            }
        }
    }

    private static BufferedImage upscale(BufferedImage src, int k) {
        BufferedImage dst = new BufferedImage(src.getWidth() * k, src.getHeight() * k, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < dst.getHeight(); y++) {
            for (int x = 0; x < dst.getWidth(); x++) {
                dst.setRGB(x, y, src.getRGB(x / k, y / k));
            }
        }
        return dst;
    }

    /** CPU twin of assets/pixel_clouds/shaders/core/pixel_cloud.fsh. Keep the two in step. */
    private static void drawSprite(BufferedImage img, CloudAtlas atlas, int variant, int bin, int ox, int oy,
                                   CloudPalette pal, float[] lightRaw, float fade, float bias) {
        float ll = (float) Math.sqrt(lightRaw[0] * lightRaw[0] + lightRaw[1] * lightRaw[1] + lightRaw[2] * lightRaw[2]);
        float lx = lightRaw[0] / ll;
        float ly = lightRaw[1] / ll;
        float lz = lightRaw[2] / ll;
        int slot = variant * CloudSprites.BINS + bin;
        int sx = (slot % CloudAtlas.COLUMNS) * CloudSprites.SPRITE_W;
        int sy = (slot / CloudAtlas.COLUMNS) * CloudSprites.SPRITE_H;
        for (int y = 0; y < CloudSprites.SPRITE_H; y++) {
            for (int x = 0; x < CloudSprites.SPRITE_W; x++) {
                int p = atlas.pixels[(sy + y) * atlas.width + sx + x];
                float a = (p >>> 24) / 255f;
                if (a < 0.5f) {
                    continue;
                }
                float nx = (p & 0xFF) / 255f * 2f - 1f;
                float ny = ((p >> 8) & 0xFF) / 255f * 2f - 1f;
                float ao = ((p >> 16) & 0xFF) / 255f;
                float nz = (float) Math.sqrt(Math.max(0f, 1f - nx * nx - ny * ny));
                float lit = clamp(((nx * lx + ny * ly + nz * 0.5f * lz) * 0.5f + 0.5f), 0f, 1f);
                lit *= 0.62f + 0.38f * ao;
                lit += bias;
                float back = clamp(-lz, 0f, 1f);
                float nl = (float) Math.sqrt(nx * nx + ny * ny) + 1e-5f;
                float ll2 = (float) Math.sqrt(lx * lx + ly * ly) + 1e-5f;
                float towards = clamp((nx * lx + ny * ly) / (nl * ll2), 0f, 1f);
                float rim = back * (float) Math.pow(1f - nz, 1.5) * 1.6f * (0.35f + 0.65f * towards);
                lit += rim * 0.4f;
                float[] c = lit < T0 ? pal.shade[0] : lit < T1 ? pal.shade[1] : lit < T2 ? pal.shade[2] : pal.shade[3];
                if (rim > 0.40f) {
                    c = pal.rim;
                }
                int rgb = (Math.round(c[0] * 255) << 16) | (Math.round(c[1] * 255) << 8) | Math.round(c[2] * 255);
                img.setRGB(ox + x, oy + y, rgb);
            }
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
