package com.pixelclouds.cloud;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the puff layout of a new cloud: a row of small puffs on a roughly flat base, larger
 * puffs above them, a few towers in the middle and the odd small puff on top. A flatness of 1
 * gives the low, wide shapes of an overcast or stormy sky.
 */
public final class CloudShapes {
    private CloudShapes() {
    }

    public static List<Blob> cumulus(Random rnd, int width, double flatness, boolean grown) {
        List<Blob> blobs = new ArrayList<>();
        double half = width / 2.0;
        double scale = clamp(width / 48.0, 0.65, 1.4);
        double lift = 1.0 - 0.6 * flatness;

        // Base: small puffs side by side, bottoms close to v = 0.
        double u = -half;
        while (u <= half) {
            double edge = 1.0 - Math.pow(Math.abs(u) / half, 2);
            double r = (4.0 + rnd.nextDouble() * 3.0) * scale * (0.55 + 0.45 * edge);
            r = Math.max(3.0, r);
            blobs.add(new Blob(u, -r * 0.55 + rnd.nextDouble() * 0.8, r, r));
            u += r * (1.0 + rnd.nextDouble() * 0.4);
        }

        // Body: larger puffs sitting on the base, biggest near the middle.
        int body = Math.max(2, (int) Math.round(width / 13.0));
        for (int i = 0; i < body; i++) {
            double bu = clamp(rnd.nextGaussian() * half * 0.42, -half * 0.72, half * 0.72);
            double r = (5.0 + rnd.nextDouble() * 5.0) * scale * (1.0 - 0.4 * Math.abs(bu) / half);
            double bv = -(r * 0.85 + rnd.nextDouble() * 3.0) * lift;
            blobs.add(new Blob(bu, bv, r, r));
        }

        // Towers: the billowing tops, rarer on flat clouds.
        int towers = (int) Math.round((1.0 - flatness) * (1.0 + width / 32.0) * (0.6 + rnd.nextDouble() * 0.6));
        List<Blob> tops = new ArrayList<>();
        for (int i = 0; i < towers; i++) {
            double tu = clamp(rnd.nextGaussian() * half * 0.32, -half * 0.55, half * 0.55);
            double r = (6.0 + rnd.nextDouble() * 6.0) * scale;
            double tv = -(r + 5.0 + rnd.nextDouble() * 6.0) * lift;
            Blob t = new Blob(tu, tv, r, r);
            blobs.add(t);
            tops.add(t);
        }

        // Small puffs perched on the towers (or on the body when there are none).
        List<Blob> perches = tops.isEmpty() ? new ArrayList<>(blobs.subList(blobs.size() - body, blobs.size())) : tops;
        int accents = 1 + rnd.nextInt(3);
        for (int i = 0; i < accents; i++) {
            Blob host = perches.get(rnd.nextInt(perches.size()));
            double angle = -Math.PI / 2 + (rnd.nextDouble() - 0.5) * Math.PI * 0.9;
            double r = (2.0 + rnd.nextDouble() * 2.0) * scale;
            double dist = host.r * 0.85;
            blobs.add(new Blob(host.u + Math.cos(angle) * dist, host.v + Math.sin(angle) * dist, r, r));
        }

        if (!grown) {
            for (Blob b : blobs) {
                b.r = 0.6;
            }
        }
        return blobs;
    }

    static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : x > hi ? hi : x;
    }
}
