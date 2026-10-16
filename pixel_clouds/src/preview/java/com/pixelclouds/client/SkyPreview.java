package com.pixelclouds.client;

import com.pixelclouds.gen.CloudAtlas;
import com.pixelclouds.gen.CloudSprites;
import com.pixelclouds.sky.CloudPalette;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Dev-only software "camera". Places the clouds with the same CloudField and Billboard code the game uses,
 * rasterises the sprite quads with a CPU copy of the fragment shader and writes whole-sky PNGs, so layout,
 * orientation, lighting and weather can be judged without Minecraft. Package com.pixelclouds.client on purpose,
 * to reach the package-private classes.
 *
 * Usage: SkyPreview OUT_DIR
 */
public final class SkyPreview {
    private static final int W = 960;
    private static final int H = 540;
    private static final float FOV = 70f;

    private record Scene(String name, double x, double y, double z, float yaw, float pitch, float sunAngle,
                         float rain, float thunder, double seconds) {}

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        CloudAtlas atlas = CloudAtlas.build();
        Scene[] scenes = {
                new Scene("1_noon", 0, 70, 0, 0, 10, 0.12f, 0, 0, 0),
                new Scene("2_sunset_west", 0, 70, 0, 90, 6, 1.42f, 0, 0, 500),
                new Scene("3_dawn_east", 0, 70, 0, 270, 8, (float) (Math.PI * 2 - 1.45), 0, 0, 900),
                new Scene("4_from_peak", 0, 262, 0, 40, -24, 0.4f, 0, 0, 0),
                new Scene("5_overhead", 0, 70, 0, 0, 62, 0.3f, 0, 0, 0),
                new Scene("6_rain", 0, 70, 0, 0, 12, 0.3f, 1, 0, 0),
                new Scene("7_thunder", 0, 70, 0, 180, 10, 0.3f, 1, 1, 100),
                new Scene("8_night", 0, 70, 0, 90, 14, (float) Math.PI + 0.55f, 0, 0, 0),
                new Scene("9_dusk_from_peak", 0, 255, 0, 90, -12, 1.5f, 0, 0, 300),
        };
        for (Scene s : scenes) {
            BufferedImage img = render(atlas, s);
            ImageIO.write(img, "png", new File(out, s.name + ".png"));
        }
    }

    private static BufferedImage render(CloudAtlas atlas, Scene s) {
        float sunX = -(float) Math.sin(s.sunAngle);
        float sunY = (float) Math.cos(s.sunAngle);
        CloudPalette pal = CloudPalette.compute(sunY, sunX > 0f, s.rain, s.thunder);
        float[] light = Billboard.lightDirection(sunX, sunY);
        float[][] sky = skyColours(sunY, sunX > 0f, s.rain, s.thunder);

        double yawR = Math.toRadians(s.yaw);
        double pitchR = Math.toRadians(s.pitch);
        double[] f = {-Math.sin(yawR) * Math.cos(pitchR), Math.sin(pitchR), Math.cos(yawR) * Math.cos(pitchR)};
        double[] right = norm(new double[]{-f[2], 0, f[0]});
        double[] up = cross(right, f);
        double tanHalf = Math.tan(Math.toRadians(FOV) / 2);
        double aspect = (double) W / H;

        float[] color = new float[W * H * 3];
        float[] depth = new float[W * H];
        java.util.Arrays.fill(depth, Float.MAX_VALUE);
        double[][] rays = new double[W * H][];

        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                double nx = ((px + 0.5) / W * 2 - 1) * tanHalf * aspect;
                double ny = (1 - (py + 0.5) / H * 2) * tanHalf;
                double[] d = norm(new double[]{f[0] + right[0] * nx + up[0] * ny,
                        f[1] + right[1] * nx + up[1] * ny, f[2] + right[2] * nx + up[2] * ny});
                rays[py * W + px] = d;
                float[] c;
                if (d[1] < -0.002) {
                    double t = (62 - s.y) / d[1];
                    float fog = (float) Math.min(1, t / 900.0);
                    float dayLight = Math.max(0.25f, Math.min(1f, sunY * 1.5f + 0.4f));
                    float k = dayLight * (s.rain > 0 ? 0.7f : 1f);
                    c = new float[]{0.30f * k, 0.47f * k, 0.26f * k};
                    for (int i = 0; i < 3; i++) {
                        c[i] = c[i] + (sky[1][i] - c[i]) * fog;
                    }
                    depth[py * W + px] = (float) t;
                } else {
                    float h = (float) Math.max(0, Math.min(1, d[1] * 1.6));
                    c = new float[3];
                    for (int i = 0; i < 3; i++) {
                        c[i] = sky[1][i] + (sky[0][i] - sky[1][i]) * (float) Math.pow(h, 0.7);
                    }
                    double sd = d[0] * sunX + d[1] * sunY;
                    double md = -sd;
                    if (sd > 0.9992 && sunY > -0.06) {
                        c = new float[]{1f, 0.96f, 0.82f};
                    } else if (md > 0.9994 && sunY < 0.1) {
                        c = new float[]{0.9f, 0.92f, 0.98f};
                    }
                }
                System.arraycopy(c, 0, color, (py * W + px) * 3, 3);
            }
        }

        CloudField field = new CloudField();
        float range = 1100f;
        field.update(s.x, s.y, s.z, s.seconds, s.rain, s.thunder, 0.55f, 1.3f, 2f, 190f, range);
        System.out.println(s.name + ": " + field.count + " clouds placed");
        Billboard bb = new Billboard();
        float canvasPerWidth = (CloudSprites.SPRITE_W / CloudSprites.PX_PER_UNIT) / 2f;
        for (int i = 0; i < field.count; i++) {
            CloudField.Cloud c = field.clouds[i];
            float rx = (float) (c.x - s.x);
            float ry = (float) (c.y - s.y);
            float rz = (float) (c.z - s.z);
            float dist = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            float fade = c.presence * Billboard.distanceFade(dist, c.width, range);
            if (fade <= 0.01f || !bb.set(rx, ry, rz, light[0], light[1])) {
                continue;
            }
            double cosAng = (rx * f[0] + ry * f[1] + rz * f[2]) / dist;
            double angRadius = Math.atan2(c.width, dist);
            if (Math.acos(Math.max(-1, Math.min(1, cosAng))) > Math.toRadians(FOV) * 0.95 + angRadius) {
                continue;
            }
            float haze = Billboard.haze(dist, range);
            float halfW = c.width * 0.5f * canvasPerWidth;
            float halfH = halfW * CloudSprites.SPRITE_H / CloudSprites.SPRITE_W;
            for (int k = 0; k < 2; k++) {
                int bin = bb.bin + k;
                float q = k == 0 ? 1f - bb.mix : bb.mix;
                if (q <= 0.01f) {
                    continue;
                }
                raster(atlas, c, bb, bin, rx, ry, rz, halfW, halfH, fade * q, haze, pal, sky[1], rays, color, depth, f);
            }
        }

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < W * H; i++) {
            int r = Math.round(clamp(color[i * 3]) * 255);
            int g = Math.round(clamp(color[i * 3 + 1]) * 255);
            int b = Math.round(clamp(color[i * 3 + 2]) * 255);
            img.setRGB(i % W, i / W, (r << 16) | (g << 8) | b);
        }
        return img;
    }

    private static void raster(CloudAtlas atlas, CloudField.Cloud c, Billboard bb, int bin,
                               float rx, float ry, float rz, float halfW, float halfH, float fade, float haze,
                               CloudPalette pal, float[] hazeColour, double[][] rays, float[] color, float[] depth,
                               double[] f) {
        int slot = c.variant * CloudSprites.BINS + bin;
        int sx = (slot % CloudAtlas.COLUMNS) * CloudSprites.SPRITE_W;
        int sy = (slot / CloudAtlas.COLUMNS) * CloudSprites.SPRITE_H;

        float ll = (float) Math.sqrt(bb.lr * bb.lr + bb.lu * bb.lu + bb.lc * bb.lc);
        float lx = bb.lr / ll;
        float ly = bb.lu / ll;
        float lz = bb.lc / ll;

        double[] right = norm(new double[]{-f[2], 0, f[0]});
        double[] up = cross(right, f);
        double tanHalf = Math.tan(Math.toRadians(FOV) / 2);
        double aspect = (double) W / H;
        int minX = W;
        int maxX = -1;
        int minY = H;
        int maxY = -1;
        boolean behind = false;
        for (int k = 0; k < 4; k++) {
            float a = (k == 0 || k == 3) ? -halfW : halfW;
            float b = (k < 2) ? halfH : -halfH;
            double px = rx + bb.rX * a + bb.uX * b;
            double py = ry + bb.uY * b;
            double pz = rz + bb.rZ * a + bb.uZ * b;
            double zc = px * f[0] + py * f[1] + pz * f[2];
            if (zc < 0.5) {
                behind = true;
                break;
            }
            double xc = px * right[0] + py * right[1] + pz * right[2];
            double yc = px * up[0] + py * up[1] + pz * up[2];
            int sxp = (int) ((xc / zc / (tanHalf * aspect) + 1) / 2 * W);
            int syp = (int) ((1 - yc / zc / tanHalf) / 2 * H);
            minX = Math.min(minX, sxp);
            maxX = Math.max(maxX, sxp);
            minY = Math.min(minY, syp);
            maxY = Math.max(maxY, syp);
        }
        if (behind) {
            minX = 0;
            maxX = W - 1;
            minY = 0;
            maxY = H - 1;
        }
        minX = Math.max(0, minX - 1);
        maxX = Math.min(W - 1, maxX + 1);
        minY = Math.max(0, minY - 1);
        maxY = Math.min(H - 1, maxY + 1);

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                double[] d = rays[py * W + px];
                double denom = d[0] * bb.cx + d[1] * bb.cy + d[2] * bb.cz;
                if (denom > -1e-4) {
                    continue;
                }
                double t = (rx * bb.cx + ry * bb.cy + rz * bb.cz) / denom;
                if (t <= 0 || t >= depth[py * W + px]) {
                    continue;
                }
                double lx2 = d[0] * t - rx;
                double ly2 = d[1] * t - ry;
                double lz2 = d[2] * t - rz;
                double a = lx2 * bb.rX + lz2 * bb.rZ;
                double b = lx2 * bb.uX + ly2 * bb.uY + lz2 * bb.uZ;
                if (Math.abs(a) >= halfW || Math.abs(b) >= halfH) {
                    continue;
                }
                int tx = (int) ((a / halfW + 1) / 2 * CloudSprites.SPRITE_W);
                int ty = (int) ((halfH - b) / (2 * halfH) * CloudSprites.SPRITE_H);
                int p = atlas.pixels[(sy + ty) * atlas.width + sx + tx];
                if ((p >>> 24) < 128) {
                    continue;
                }
                float bayer = (BAYER[(py & 3) * 4 + (px & 3)] + 0.5f) / 16f;
                if ((bin & 1) == 1) {
                    bayer = 1f - bayer;
                }
                if (bayer >= fade) {
                    continue;
                }
                float nx = (p & 0xFF) / 255f * 2f - 1f;
                float ny = ((p >> 8) & 0xFF) / 255f * 2f - 1f;
                float ao = ((p >> 16) & 0xFF) / 255f;
                float nz = (float) Math.sqrt(Math.max(0f, 1f - nx * nx - ny * ny));
                float lit = clamp((nx * lx + ny * ly + nz * 0.5f * lz) * 0.5f + 0.5f);
                lit *= 0.62f + 0.38f * ao;
                lit += (c.bias - 0.5f) * 0.7f;
                float back = clamp(-lz);
                float nl = (float) Math.sqrt(nx * nx + ny * ny) + 1e-5f;
                float ll2 = (float) Math.sqrt(lx * lx + ly * ly) + 1e-5f;
                float towards = clamp((nx * lx + ny * ly) / (nl * ll2));
                float rim = back * (float) Math.pow(1f - nz, 1.5) * 1.6f * (0.35f + 0.65f * towards);
                lit += rim * 0.4f;
                float[] col = lit < 0.28f ? pal.shade[0] : lit < 0.50f ? pal.shade[1] : lit < 0.72f ? pal.shade[2] : pal.shade[3];
                if (rim > 0.40f) {
                    col = pal.rim;
                }
                int o = (py * W + px) * 3;
                for (int i = 0; i < 3; i++) {
                    color[o + i] = col[i] + (hazeColour[i] - col[i]) * haze;
                }
                depth[py * W + px] = (float) t;
            }
        }
    }

    private static final float[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private static float clamp(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** {zenith, horizon} approximations of vanilla's sky and fog colours. */
    private static float[][] skyColours(float sunY, boolean morning, float rain, float thunder) {
        float[][] zen = {rgb(0x070A1C), rgb(0x1E2250), morning ? rgb(0x5A60A8) : rgb(0x3A4A94), rgb(0x5C80D4), rgb(0x6C94E8)};
        float[][] hor = {rgb(0x14203C), rgb(0x6A4A78), morning ? rgb(0xF4B0A8) : rgb(0xE8A488), rgb(0xE8C8B4), rgb(0xB8D0F4)};
        float[] key = {-0.3f, -0.08f, 0f, 0.15f, 0.5f};
        int lo = 0;
        while (lo < 3 && sunY >= key[lo + 1]) {
            lo++;
        }
        float t = clamp((sunY - key[lo]) / (key[lo + 1] - key[lo]));
        float[] z = new float[3];
        float[] h = new float[3];
        float day = clamp((sunY + 0.15f) / 0.45f);
        float w = clamp(rain * 0.85f);
        float dim = (0.3f + 0.7f * day) * (1f - 0.4f * thunder);
        float[] grey = {0.42f * dim, 0.46f * dim, 0.52f * dim};
        for (int i = 0; i < 3; i++) {
            z[i] = zen[lo][i] + (zen[lo + 1][i] - zen[lo][i]) * t;
            h[i] = hor[lo][i] + (hor[lo + 1][i] - hor[lo][i]) * t;
            z[i] += (grey[i] - z[i]) * w;
            h[i] += (grey[i] * 1.1f - h[i]) * w;
        }
        return new float[][]{z, h};
    }

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }
}
