package com.pixelclouds.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A cloud described as a cluster of spheres, optionally cut off by a flat base.
 * Units are arbitrary: the cluster is roughly 2 wide, 1 deep and at most 1.2 tall,
 * centred on the origin horizontally. The sprite generator turns it into pixel art.
 */
public final class CloudModel {
    public enum Kind { CUMULUS, TOWER, FLAT, WISP, STORM }

    public record Sphere(float x, float y, float z, float r) {}

    public final Kind kind;
    public final List<Sphere> spheres = new ArrayList<>();
    /** Height of the flat base plane, or negative infinity for no cut. */
    public float baseY = Float.NEGATIVE_INFINITY;

    private CloudModel(Kind kind) {
        this.kind = kind;
    }

    public static CloudModel create(Kind kind, long seed) {
        Random rng = new Random(seed * 0x9E3779B97F4A7C15L + kind.ordinal());
        CloudModel m = new CloudModel(kind);
        switch (kind) {
            case CUMULUS -> m.buildCumulus(rng, 1.0f);
            case TOWER -> m.buildCumulus(rng, 1.45f);
            case FLAT -> m.buildFlat(rng);
            case WISP -> m.buildWisp(rng);
            case STORM -> m.buildStorm(rng);
        }
        return m;
    }

    private static float range(Random rng, float lo, float hi) {
        return lo + rng.nextFloat() * (hi - lo);
    }

    private void add(float x, float y, float z, float r) {
        spheres.add(new Sphere(x, y, z, r));
    }

    /** Domed puffs on a flat base, with a few small satellite bubbles near the top. */
    private void buildCumulus(Random rng, float tall) {
        float base = -0.42f;
        baseY = base;
        int n1 = 9 + rng.nextInt(3);
        for (int i = 0; i < n1; i++) {
            float x = lerp(-0.92f, 0.92f, (i + 0.5f) / n1) + range(rng, -0.06f, 0.06f);
            float zMax = 0.40f * (1f - 0.45f * x * x);
            float r = range(rng, 0.20f, 0.31f);
            add(x, base + r * 0.55f + range(rng, 0f, 0.03f), range(rng, -zMax, zMax), r);
        }
        int n2 = 6 + rng.nextInt(3);
        for (int i = 0; i < n2; i++) {
            float x = lerp(-0.68f, 0.68f, (i + 0.5f) / n2) + range(rng, -0.08f, 0.08f);
            float r = range(rng, 0.25f, 0.37f);
            float y = base + (0.18f + r * 0.7f) * (0.85f + 0.15f * tall) + range(rng, -0.02f, 0.04f);
            add(x, y, range(rng, -0.28f, 0.28f), r);
        }
        int n3 = 3 + rng.nextInt(2) + (tall > 1.2f ? 1 : 0);
        for (int i = 0; i < n3; i++) {
            float x = range(rng, -0.42f, 0.42f);
            float r = range(rng, 0.21f, 0.31f) * (0.9f + 0.1f * tall);
            float y = base + 0.30f * tall + 0.18f + r * 0.55f + range(rng, 0f, 0.06f);
            add(x, y, range(rng, -0.18f, 0.18f), r);
        }
        if (tall > 1.2f) {
            // a taller crown on top of the middle of the cloud
            for (int i = 0; i < 2; i++) {
                float r = range(rng, 0.20f, 0.26f);
                add(range(rng, -0.2f, 0.2f), 0.40f + r * 0.5f + 0.18f * i, range(rng, -0.1f, 0.1f), r);
            }
        }
        satellites(rng, 4 + rng.nextInt(3), 0.07f, 0.12f);
    }

    /** Low and wide. */
    private void buildFlat(Random rng) {
        float base = -0.20f;
        baseY = base;
        int n1 = 12 + rng.nextInt(5);
        for (int i = 0; i < n1; i++) {
            float x = lerp(-0.98f, 0.98f, (i + 0.5f) / n1) + range(rng, -0.05f, 0.05f);
            float zMax = 0.48f * (1f - 0.55f * x * x);
            float r = range(rng, 0.14f, 0.24f);
            add(x, base + r * 0.5f + range(rng, 0f, 0.03f), range(rng, -zMax, zMax), r);
        }
        int n2 = 6 + rng.nextInt(3);
        for (int i = 0; i < n2; i++) {
            float x = lerp(-0.75f, 0.75f, (i + 0.5f) / n2) + range(rng, -0.06f, 0.06f);
            float r = range(rng, 0.16f, 0.25f);
            add(x, base + 0.10f + r * 0.5f + range(rng, 0f, 0.04f), range(rng, -0.30f, 0.30f), r);
        }
        satellites(rng, 3 + rng.nextInt(3), 0.05f, 0.09f);
    }

    /** A thin beaded streak along a gentle arc. */
    private void buildWisp(Random rng) {
        int rows = 2;
        for (int row = 0; row < rows; row++) {
            int n = 16 + rng.nextInt(8);
            float bend = range(rng, -0.18f, 0.18f);
            float yOff = row * range(rng, 0.10f, 0.16f) - 0.05f;
            float xShift = range(rng, -0.15f, 0.15f);
            float extent = 1.05f - row * 0.2f;
            for (int i = 0; i < n; i++) {
                float t = (i + 0.5f) / n * 2f - 1f;
                float taper = 1f - 0.85f * t * t;
                float r = range(rng, 0.045f, 0.085f) * (0.45f + 0.55f * taper);
                float x = t * extent + xShift;
                float y = yOff + bend * (1f - t * t) + range(rng, -0.025f, 0.025f);
                add(x, y, range(rng, -0.12f, 0.12f), r);
            }
        }
    }

    /** Heavy and dark: wide base, bulky middle, spread anvil top. */
    private void buildStorm(Random rng) {
        float base = -0.45f;
        baseY = base;
        int n1 = 10 + rng.nextInt(3);
        for (int i = 0; i < n1; i++) {
            float x = lerp(-0.95f, 0.95f, (i + 0.5f) / n1) + range(rng, -0.05f, 0.05f);
            float zMax = 0.42f * (1f - 0.4f * x * x);
            float r = range(rng, 0.27f, 0.38f);
            add(x, base + r * 0.5f, range(rng, -zMax, zMax), r);
        }
        int n2 = 8;
        for (int i = 0; i < n2; i++) {
            float x = lerp(-0.8f, 0.8f, (i + 0.5f) / n2) + range(rng, -0.06f, 0.06f);
            float r = range(rng, 0.30f, 0.40f);
            add(x, -0.12f + range(rng, -0.05f, 0.06f), range(rng, -0.3f, 0.3f), r);
        }
        int n3 = 7 + rng.nextInt(2);
        for (int i = 0; i < n3; i++) {
            float x = lerp(-0.92f, 0.92f, (i + 0.5f) / n3) + range(rng, -0.05f, 0.05f);
            float r = range(rng, 0.26f, 0.34f);
            add(x, 0.22f + range(rng, -0.03f, 0.08f) - 0.2f * Math.abs(x), range(rng, -0.25f, 0.25f), r);
        }
        for (int i = 0; i < 3; i++) {
            float r = range(rng, 0.27f, 0.33f);
            add(range(rng, -0.25f, 0.25f), 0.38f + r * 0.4f, range(rng, -0.12f, 0.12f), r);
        }
        satellites(rng, 3, 0.07f, 0.11f);
    }

    /** Small bubbles sitting on the upper half of existing spheres. */
    private void satellites(Random rng, int count, float rMin, float rMax) {
        List<Sphere> parents = new ArrayList<>(spheres);
        for (int i = 0; i < count && !parents.isEmpty(); i++) {
            Sphere p = parents.get(rng.nextInt(parents.size()));
            if (p.y() + p.r() < baseY + 0.3f) {
                continue;
            }
            float ang = range(rng, 0f, (float) (Math.PI * 2));
            float up = range(rng, 0.25f, 0.9f);
            float flat = (float) Math.sqrt(1f - up * up);
            float r = range(rng, rMin, rMax);
            float d = p.r() + r * 0.4f;
            add(p.x() + d * flat * (float) Math.cos(ang), p.y() + d * up, p.z() + d * flat * (float) Math.sin(ang), r);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
