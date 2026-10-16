package com.pixelclouds.cloud;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Dev tool: renders frames of a cloud pulling apart (top row) and two clouds merging (bottom
 * row), as seen by a camera looking along +Z. Writes {@code build/preview/split_merge.png}.
 */
public final class SimPreview {
    private static final int FRAMES = 6;
    private static final int W = 150, H = 50, SCALE = 3;

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "build/preview/split_merge.png");
        out.getParentFile().mkdirs();
        BufferedImage img = new BufferedImage(FRAMES * W * SCALE, 2 * H * SCALE, BufferedImage.TYPE_INT_RGB);
        int[] palette = CloudPalette.compute(1, 0, 0, 0);

        CloudField.View view = new CloudField.View();
        view.y = 70;

        // Split: one long cloud, forced to start pulling apart.
        CloudField split = field();
        Cloud c = split.addForTest(0, 180, 0, CloudShapes.cumulus(new Random(12), 70, 0.3, true));
        split.startSplit(c);
        for (int f = 0; f < FRAMES; f++) {
            draw(img, split, f, 0, palette);
            for (int t = 0; t < 300; t++) {
                split.tick(view);
            }
        }

        // Merge: two clouds drifting into each other.
        CloudField merge = field();
        Cloud a = merge.addForTest(-50, 180, 0, CloudShapes.cumulus(new Random(3), 40, 0.2, true));
        Cloud b = merge.addForTest(50, 182, 6, CloudShapes.cumulus(new Random(4), 36, 0.2, true));
        a.driftX = 0.12;
        b.driftX = -0.12;
        for (int f = 0; f < FRAMES; f++) {
            draw(img, merge, f, 1, palette);
            for (int t = 0; t < 120; t++) {
                merge.tick(view);
            }
        }
        ImageIO.write(img, "png", out);
        System.out.println("Wrote " + out.getAbsolutePath());
    }

    private static CloudField field() {
        CloudField.Settings s = new CloudField.Settings();
        s.manageCount = false;
        return new CloudField(s, 1);
    }

    private static void draw(BufferedImage img, CloudField field, int frame, int row, int[] palette) {
        int ox = frame * W, oy = row * H;
        PreviewSheet.SCALE_OVERRIDE = SCALE;
        PreviewSheet.fill(img, ox, oy, W, H, 0x78A7FF);
        double px = field.settings().pixelSize;
        // Pan with the wind so the clouds stay in frame.
        double panX = field.clouds().isEmpty() ? 0 : averageX(field.clouds());
        for (Cloud cloud : field.clouds()) {
            CloudImage ci = cloud.image(126, 62);
            int cx = ox + W / 2 + (int) Math.round((cloud.x - panX) / px) + ci.minU;
            int cy = oy + H - 8 - (int) Math.round((cloud.y - 180) / px) + ci.minV;
            for (int y = 0; y < ci.height; y++) {
                for (int x = 0; x < ci.width; x++) {
                    byte t = ci.tone(x, y);
                    int sx = cx + x, sy = cy + y;
                    if (t != CloudImage.EMPTY && sx >= ox && sx < ox + W && sy >= oy && sy < oy + H) {
                        PreviewSheet.fill(img, sx, sy, 1, 1, palette[t]);
                    }
                }
            }
        }
        // Frame separator.
        PreviewSheet.fill(img, ox, oy, 1, H, 0x2B3A55);
    }

    private static double averageX(List<Cloud> clouds) {
        List<Double> xs = new ArrayList<>();
        for (Cloud c : clouds) {
            xs.add(c.x);
        }
        return xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
