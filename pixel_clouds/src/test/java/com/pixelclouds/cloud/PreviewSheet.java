package com.pixelclouds.cloud;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Dev tool: draws generated clouds onto a sky so their look can be checked without Minecraft.
 * Run with {@code ./gradlew previewClouds}. Writes {@code build/preview/clouds.png}.
 */
public final class PreviewSheet {
    static int SCALE_OVERRIDE = 5;

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "build/preview/clouds.png");
        out.getParentFile().mkdirs();

        // Rows: day, dusk, night, storm. Each row shows the same four clouds.
        double[][] skies = {{1.0, 0, 0}, {0.05, 0, 0}, {-0.8, 0, 0}, {0.9, 1.0, 1.0}};
        int[] skyColours = {0x78A7FF, 0xD98A6E, 0x0B0D1A, 0x5B6067};
        int cellW = 100, cellH = 52, cols = 4;
        BufferedImage img = new BufferedImage(cols * cellW * SCALE_OVERRIDE, skies.length * cellH * SCALE_OVERRIDE, BufferedImage.TYPE_INT_RGB);

        CloudImage[] clouds = new CloudImage[cols];
        int[] widths = {34, 52, 70, 88};
        double[] flat = {0.0, 0.2, 0.4, 0.9};
        for (int c = 0; c < cols; c++) {
            List<Blob> blobs = CloudShapes.cumulus(new Random(7 + c * 31L), widths[c], flat[c], true);
            clouds[c] = CloudRaster.rasterise(blobs, 128, 64);
        }

        for (int row = 0; row < skies.length; row++) {
            int[] pal = CloudPalette.compute(skies[row][0], skies[row][1], skies[row][2], 0);
            for (int c = 0; c < cols; c++) {
                int ox = c * cellW, oy = row * cellH;
                fill(img, ox, oy, cellW, cellH, skyColours[row]);
                CloudImage ci = clouds[c];
                int dx = ox + (cellW - ci.width) / 2, dy = oy + (cellH - ci.height) / 2;
                for (int y = 0; y < ci.height; y++) {
                    for (int x = 0; x < ci.width; x++) {
                        byte t = ci.tone(x, y);
                        if (t != CloudImage.EMPTY) {
                            fill(img, dx + x, dy + y, 1, 1, pal[t]);
                        }
                    }
                }
            }
        }
        ImageIO.write(img, "png", out);
        System.out.println("Wrote " + out.getAbsolutePath());
    }

    static void fill(BufferedImage img, int x, int y, int w, int h, int rgb) {
        for (int yy = y * SCALE_OVERRIDE; yy < (y + h) * SCALE_OVERRIDE; yy++) {
            for (int xx = x * SCALE_OVERRIDE; xx < (x + w) * SCALE_OVERRIDE; xx++) {
                if (xx >= 0 && yy >= 0 && xx < img.getWidth() && yy < img.getHeight()) {
                    img.setRGB(xx, yy, rgb);
                }
            }
        }
    }
}
