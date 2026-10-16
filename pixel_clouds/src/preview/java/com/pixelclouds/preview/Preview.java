package com.pixelclouds.preview;

import com.pixelclouds.gen.CloudAtlas;
import com.pixelclouds.gen.CloudKind;
import com.pixelclouds.gen.CloudSprites;
import com.pixelclouds.sky.CloudPalette;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Dev-only: writes PNG contact sheets of the generated sprites, lit with {@link ShadeCpu}.
 * Run with plain javac/java (see README.md), no Minecraft needed.
 *
 * Usage: Preview OUT_DIR [scale]
 */
public final class Preview {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        int scale = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        long t0 = System.nanoTime();
        CloudAtlas atlas = CloudAtlas.build();
        System.out.printf("atlas %dx%d (%d variants) built in %d ms%n", atlas.width, atlas.height,
                CloudAtlas.variantCount(), (System.nanoTime() - t0) / 1_000_000);

        CloudPalette day = CloudPalette.compute(0.6f, false, 0f, 0f);
        float[] dayLight = norm(-0.55f, 0.6f, 0.6f);
        float[] dayHaze = {0.72f, 0.82f, 0.96f};

        // One sheet per kind: every variant at every other elevation.
        for (CloudKind kind : CloudKind.values()) {
            int first = CloudAtlas.first(kind);
            int count = CloudAtlas.count(kind);
            int[] bins = {0, 2, 4, 6, 8};
            int maxW = 0;
            int maxH = 0;
            for (int v = first; v < first + count; v++) {
                maxW = Math.max(maxW, atlas.canvasWidth(v));
                maxH = Math.max(maxH, atlas.canvasHeight(v));
            }
            BufferedImage img = new BufferedImage(maxW * bins.length * scale, maxH * count * scale, BufferedImage.TYPE_INT_RGB);
            fill(img, 0x6C94E8);
            for (int i = 0; i < count; i++) {
                for (int j = 0; j < bins.length; j++) {
                    draw(img, atlas, first + i, bins[j], j * maxW * scale, i * maxH * scale, scale, day, dayLight, 0.5f, 0f, dayHaze);
                }
            }
            ImageIO.write(img, "png", new File(out, "kind_" + kind.name().toLowerCase() + ".png"));
        }

        // A few clouds across the day.
        String[] names = {"noon", "evening", "sunset", "dusk", "night", "morning", "rain", "thunder"};
        float[] sunY = {0.8f, 0.2f, 0.0f, -0.1f, -0.4f, 0.0f, 0.5f, 0.5f};
        boolean[] morning = {false, false, false, false, false, true, false, false};
        float[] rain = {0, 0, 0, 0, 0, 0, 1, 1};
        float[] thunder = {0, 0, 0, 0, 0, 0, 0, 1};
        int[] sky = {0x6C94E8, 0x9A8CC0, 0xB080A8, 0x4A4274, 0x161C34, 0xA890C8, 0x68707C, 0x3A404C};
        int[] picks = {CloudAtlas.first(CloudKind.CUMULUS), CloudAtlas.first(CloudKind.TOWER) + 1, CloudAtlas.first(CloudKind.BANK)};
        int cw = 0;
        int ch = 0;
        for (int v : picks) {
            cw = Math.max(cw, atlas.canvasWidth(v));
            ch = Math.max(ch, atlas.canvasHeight(v));
        }
        BufferedImage sheet = new BufferedImage(cw * names.length * scale, ch * picks.length * scale, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < names.length; i++) {
            CloudPalette pal = CloudPalette.compute(sunY[i], morning[i], rain[i], thunder[i]);
            float[] l = (i == 2 || i == 5) ? norm(-0.8f, 0.2f, -0.55f) : i == 4 ? norm(0.4f, 0.5f, 0.75f) : norm(-0.5f, 0.3f + sunY[i] * 0.4f, 0.65f);
            for (int j = 0; j < picks.length; j++) {
                for (int y = 0; y < ch * scale; y++) {
                    for (int x = 0; x < cw * scale; x++) {
                        sheet.setRGB(i * cw * scale + x, j * ch * scale + y, sky[i]);
                    }
                }
                draw(sheet, atlas, picks[j], 4, i * cw * scale, j * ch * scale, scale, pal, l, 0.5f, 0f, rgb(sky[i]));
            }
        }
        ImageIO.write(sheet, "png", new File(out, "lighting.png"));
        System.out.println("wrote " + out);
    }

    static void draw(BufferedImage img, CloudAtlas atlas, int variant, int bin, int ox, int oy, int scale,
                     CloudPalette pal, float[] light, float bias, float haze, float[] hazeColour) {
        int w = atlas.canvasWidth(variant);
        int h = atlas.canvasHeight(variant);
        int sx = atlas.slotX(variant, bin);
        int sy = atlas.slotY(variant, bin);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float[] c = ShadeCpu.shade(atlas.pixels[(sy + y) * atlas.width + sx + x], light, bias, haze, pal, hazeColour);
                if (c == null) {
                    continue;
                }
                int rgb = (Math.round(c[0] * 255) << 16) | (Math.round(c[1] * 255) << 8) | Math.round(c[2] * 255);
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        int px = ox + x * scale + dx;
                        int py = oy + y * scale + dy;
                        if (px < img.getWidth() && py < img.getHeight()) {
                            img.setRGB(px, py, rgb);
                        }
                    }
                }
            }
        }
    }

    private static float[] norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    private static void fill(BufferedImage img, int rgb) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.setRGB(x, y, rgb);
            }
        }
    }
}
