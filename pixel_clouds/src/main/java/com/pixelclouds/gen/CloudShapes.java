package com.pixelclouds.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds cloud density volumes. A cloud starts as a handful of large lobes, then gets smaller lobes grown on
 * the surfaces of bigger ones (and smaller again on those), the way a pixel artist builds a cumulus. After that
 * come loose satellites and trailing fragments, so pieces visibly break away, and an erosion pass that carves
 * notches and hooks into the edges.
 */
public final class CloudShapes {
    /** A blob's kernel reaches 0.575 of its radius at the ISO level, so visible radius = reach * 0.575. */
    private static final float VISIBLE = 0.575f;
    private static final int PAD = 6;

    private record Blob(float x, float y, float z, float rv, float ax, float ay, float az, float w, boolean coarse) {}

    private CloudShapes() {}

    public static CloudVolume build(CloudKind kind, long seed) {
        Builder b = new Builder(new Random(seed * 0x9E3779B97F4A7C15L + kind.ordinal() * 7919L), (int) (seed * 31 + kind.ordinal()));
        switch (kind) {
            case CUMULUS -> b.cumulus();
            case TOWER -> b.tower();
            case PUFF -> b.puff();
            case BANK -> b.bank();
            case CLUSTER -> b.cluster();
            case WISP -> b.wisp();
            case ANVIL -> b.anvil();
            case STRATUS -> b.stratus();
        }
        return b.bake();
    }

    private static final class Builder {
        final Random rng;
        final int noiseSeed;
        final List<Blob> blobs = new ArrayList<>();
        final List<Blob> mains = new ArrayList<>();
        boolean baseClip = true;
        /** How strongly edges are eaten away. */
        float erode = 0.30f;
        /** Extra roughness of the surface. */
        float rough = 0.06f;

        Builder(Random rng, int noiseSeed) {
            this.rng = rng;
            this.noiseSeed = noiseSeed;
        }

        float range(float lo, float hi) {
            return lo + rng.nextFloat() * (hi - lo);
        }

        void lobe(float x, float y, float z, float rv, float ax, float ay, float az, float w, boolean coarse) {
            Blob b = new Blob(x, y, z, rv, ax, ay, az, w, coarse);
            blobs.add(b);
            if (coarse) {
                mains.add(b);
            }
        }

        void lobe(float x, float y, float z, float rv, boolean coarse) {
            lobe(x, y, z, rv, 1f, range(0.88f, 1.08f), 1f, 1f, coarse);
        }

        /** Smaller lobes on the upper and outer surface of a parent, optionally nested one level deeper. */
        void children(float px, float py, float pz, float prv, int n, float lo, float hi, float upBias, int depth,
                      float grandProb) {
            for (int i = 0; i < n; i++) {
                float dx = (float) rng.nextGaussian();
                float dy = (float) rng.nextGaussian() + upBias;
                float dz = (float) rng.nextGaussian() * 0.8f;
                if (dy < -0.15f) {
                    dy = -dy * 0.3f;
                }
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4f;
                dx /= len;
                dy /= len;
                dz /= len;
                float rv = prv * range(lo, hi);
                float d = prv * range(0.74f, 0.9f);
                float cx = px + dx * d;
                float cy = py + dy * d;
                float cz = pz + dz * d;
                // Never poke through the flat base.
                if (baseClip && cy < rv * 0.4f) {
                    cy = rv * 0.4f;
                }
                lobe(cx, cy, cz, rv, range(0.8f, 1.3f), range(0.75f, 1.2f), range(0.85f, 1.2f), 1f, false);
                if (depth > 0 && rng.nextFloat() < grandProb && rv > 4f) {
                    children(cx, cy, cz, rv, 2 + rng.nextInt(3), 0.38f, 0.6f, upBias, depth - 1, grandProb * 0.5f);
                }
            }
        }

        /** Tiny loose bubbles around the top and sides, some in short trails, so the edge feels alive. */
        void satellites(int count, float spreadLo, float spreadHi, float rvLo, float rvHi) {
            if (mains.isEmpty()) {
                return;
            }
            for (int i = 0; i < count; i++) {
                Blob p = mains.get(rng.nextInt(mains.size()));
                float ang = range(0f, (float) (Math.PI * 2));
                float up = range(-0.1f, 1f);
                float flat = (float) Math.sqrt(Math.max(0f, 1f - up * up));
                float dist = p.rv() * range(spreadLo, spreadHi);
                float sx = p.x() + (float) Math.cos(ang) * flat * dist;
                float sy = p.y() + up * dist;
                float sz = p.z() + (float) Math.sin(ang) * flat * dist * 0.7f;
                if (baseClip && sy < 4f) {
                    sy = 4f + range(0f, 6f);
                }
                float rv = range(rvLo, rvHi);
                lobe(sx, sy, sz, rv, range(0.8f, 1.4f), range(0.7f, 1.1f), 1f, 1f, false);
                if (rng.nextFloat() < 0.4f) {
                    // a smaller piece following it, drifting off
                    lobe(sx + range(2f, 7f), sy + range(-3f, 4f), sz, rv * range(0.5f, 0.75f), false);
                }
            }
        }

        /** A line of shrinking flat lobes streaming downwind from a point: a piece pulling away. */
        void trail(float x, float y, float z, float dirX, float dirY, int count, float rv0, float shrink) {
            float rv = rv0;
            for (int i = 0; i < count; i++) {
                lobe(x, y, z, rv, range(1.6f, 2.6f), range(0.5f, 0.75f), range(0.8f, 1f), 0.9f, false);
                float step = rv * range(1.3f, 1.9f);
                x += dirX * step;
                y += dirY * step + range(-1f, 1f);
                z += range(-1.5f, 1.5f);
                rv *= shrink * range(0.85f, 1.05f);
                if (rv < 1.6f) {
                    break;
                }
            }
        }

        // ---- recipes ----

        void cumulus() {
            float w = range(70f, 130f);
            int n = 2 + rng.nextInt(3);
            float[] xs = new float[n];
            float[] rvs = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = lerp(-0.36f * w, 0.36f * w, (i + 0.5f + range(-0.35f, 0.35f)) / n);
                float xn = Math.abs(xs[i]) / (0.5f * w);
                rvs[i] = w * range(0.12f, 0.20f) * (1.2f - 0.55f * xn);
                lobe(xs[i], rvs[i] * range(0.62f, 0.95f), range(-0.1f * w, 0.1f * w), rvs[i], true);
            }
            for (int i = 0; i + 1 < n; i++) {
                float r = Math.min(rvs[i], rvs[i + 1]);
                lobe((xs[i] + xs[i + 1]) / 2, r * 0.55f, range(-4f, 4f), r * 0.8f, true);
            }
            List<Blob> base = new ArrayList<>(mains);
            for (Blob m : base) {
                children(m.x(), m.y(), m.z(), m.rv(), 5 + rng.nextInt(5), 0.34f, 0.6f, 0.55f, 1, 0.65f);
            }
            feet(w, 3 + rng.nextInt(3));
            satellites(4 + rng.nextInt(6), 1.15f, 1.9f, 1.6f, 5f);
            Blob m = base.get(rng.nextInt(base.size()));
            trail(m.x() + m.rv() * 0.8f, m.y() + m.rv() * 0.2f, m.z(), 1f, 0.15f, 3 + rng.nextInt(3), m.rv() * 0.35f, 0.7f);
        }

        void tower() {
            float w = range(50f, 78f);
            float h = w * range(0.95f, 1.4f);
            float lean = range(-0.45f, 0.45f);
            int m = 4;
            float topX = 0;
            float topY = 0;
            float topRv = 0;
            for (int i = 0; i < m; i++) {
                float t = i / (float) (m - 1);
                float rv = w * (0.28f - 0.09f * (float) Math.pow(t, 1.2)) * range(0.9f, 1.15f);
                float y = lerp(rv * 0.75f, h - rv * 1.1f, t);
                float x = lean * t * w * 0.7f + range(-0.07f, 0.07f) * w;
                lobe(x, y, range(-5f, 5f), rv, true);
                topX = x;
                topY = y;
                topRv = rv;
            }
            // a crown of several lobes instead of one round head
            int crown = 3 + rng.nextInt(2);
            for (int i = 0; i < crown; i++) {
                float rv = topRv * range(0.6f, 0.95f);
                float ang = (i / (float) crown) * 6.2832f + range(0f, 1f);
                lobe(topX + (float) Math.cos(ang) * topRv * 0.7f, topY + topRv * range(0.25f, 0.6f), (float) Math.sin(ang) * topRv * 0.5f, rv, true);
            }
            int shoulders = 2 + rng.nextInt(3);
            for (int i = 0; i < shoulders; i++) {
                float t = range(0.05f, 0.5f);
                float side = i % 2 == 0 ? 1f : -1f;
                float rv = w * range(0.15f, 0.24f);
                lobe(side * w * range(0.24f, 0.42f) + lean * t * w * 0.5f, rv * range(0.7f, 1.2f) + t * h * 0.35f, range(-6f, 6f), rv, true);
            }
            List<Blob> base = new ArrayList<>(mains);
            for (Blob b : base) {
                children(b.x(), b.y(), b.z(), b.rv(), 5 + rng.nextInt(4), 0.34f, 0.58f, 0.5f, 1, 0.6f);
            }
            satellites(5 + rng.nextInt(6), 1.2f, 2f, 1.6f, 5.5f);
        }

        void puff() {
            float w = range(40f, 64f);
            int n = rng.nextBoolean() ? 1 : 2;
            for (int i = 0; i < n; i++) {
                float rv = w * range(0.2f, 0.3f);
                lobe((i - (n - 1) / 2f) * w * 0.32f, rv * range(0.65f, 0.9f), range(-3f, 3f), rv, true);
            }
            List<Blob> base = new ArrayList<>(mains);
            for (Blob b : base) {
                children(b.x(), b.y(), b.z(), b.rv(), 4 + rng.nextInt(3), 0.38f, 0.62f, 0.5f, 1, 0.5f);
            }
            satellites(2 + rng.nextInt(3), 1.2f, 2f, 1.5f, 3.5f);
            erode = 0.22f;
        }

        void bank() {
            float w = range(140f, 210f);
            int n = 6 + rng.nextInt(4);
            float[] xs = new float[n];
            float[] rvs = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = lerp(-0.45f * w, 0.45f * w, (i + 0.5f + range(-0.3f, 0.3f)) / n);
                float xn = Math.abs(xs[i]) / (0.5f * w);
                rvs[i] = w * range(0.05f, 0.095f) * (1.25f - 0.6f * xn);
                lobe(xs[i], rvs[i] * range(0.6f, 0.9f), range(-0.07f * w, 0.07f * w), rvs[i], 1.15f, 0.9f, 1f, 1f, true);
            }
            for (int i = 0; i + 1 < n; i++) {
                float r = Math.min(rvs[i], rvs[i + 1]);
                lobe((xs[i] + xs[i + 1]) / 2, r * 0.5f, range(-3f, 3f), r * 0.85f, true);
            }
            List<Blob> base = new ArrayList<>(mains);
            for (Blob m : base) {
                children(m.x(), m.y(), m.z(), m.rv(), 3 + rng.nextInt(3), 0.36f, 0.6f, 0.5f, 1, 0.5f);
            }
            satellites(6 + rng.nextInt(6), 1.2f, 2.1f, 1.6f, 5f);
            Blob m = base.get(base.size() - 1);
            trail(m.x() + m.rv(), m.y(), m.z(), 1f, 0.05f, 4, m.rv() * 0.5f, 0.74f);
        }

        void cluster() {
            float w = range(120f, 190f);
            int n = 6 + rng.nextInt(5);
            for (int i = 0; i < n; i++) {
                float t = (i + 0.5f + range(-0.4f, 0.4f)) / n;
                float x = lerp(-0.45f * w, 0.45f * w, t);
                float rv = range(9f, 19f) * (1f - 0.35f * Math.abs(t * 2 - 1));
                float y = rv * 0.7f + range(0f, 7f) * (rng.nextFloat() < 0.35f ? 2f : 1f);
                float z = range(-0.12f * w, 0.12f * w);
                lobe(x, y, z, rv, range(0.95f, 1.5f), 0.9f, 1f, 1f, true);
                children(x, y, z, rv, 2 + rng.nextInt(3), 0.4f, 0.6f, 0.5f, 0, 0f);
            }
            satellites(4 + rng.nextInt(4), 1.3f, 2.4f, 1.5f, 4f);
            erode = 0.26f;
        }

        void wisp() {
            baseClip = false;
            erode = 0.12f;
            rough = 0.04f;
            float w = range(120f, 200f);
            int streaks = 2 + rng.nextInt(2);
            for (int s = 0; s < streaks; s++) {
                int n = 14 + rng.nextInt(9);
                float bend = range(-0.07f, 0.07f) * w;
                float yOff = s * range(7f, 13f) + range(-2f, 2f);
                float xShift = range(-0.12f, 0.12f) * w;
                float extent = (0.46f - s * 0.08f) * w;
                for (int i = 0; i < n; i++) {
                    float t = (i + 0.5f) / n * 2f - 1f;
                    float taper = 1f - 0.85f * t * t;
                    float rv = range(3.2f, 5.6f) * (0.4f + 0.6f * taper);
                    lobe(t * extent + xShift, yOff + bend * (1f - t * t) + range(-1.5f, 1.5f), range(-5f, 5f), rv,
                            range(2.2f, 3.8f), 0.6f, 0.8f, 1.7f, true);
                }
            }
            // a few stray beads off the streaks
            satellites(3 + rng.nextInt(3), 1.2f, 2.5f, 1.4f, 2.6f);
        }

        void anvil() {
            float w = range(110f, 160f);
            float h = w * range(0.62f, 0.85f);
            for (int i = 0; i < 4; i++) {
                float t = i / 3f;
                float rv = w * (0.2f - 0.05f * t) * range(0.92f, 1.1f);
                lobe(range(-3f, 3f) + t * w * 0.05f, lerp(rv * 0.7f, h * 0.7f, t), range(-4f, 4f), rv, true);
            }
            int top = 3 + rng.nextInt(2);
            for (int i = 0; i < top; i++) {
                float t = (i + 0.5f) / top * 2f - 1f;
                float rv = w * range(0.1f, 0.15f);
                lobe(t * w * 0.34f, h * range(0.82f, 0.92f) - Math.abs(t) * 6f, range(-6f, 6f), rv, range(1.7f, 2.4f), 0.55f, 1f, 1f, true);
            }
            for (int i = 0; i < 3; i++) {
                float t = (i + 0.5f) / 3f * 2f - 1f;
                float rv = w * range(0.1f, 0.15f);
                lobe(t * w * 0.28f, rv * 0.7f, range(-8f, 8f), rv, 1.3f, 0.9f, 1f, 1f, true);
            }
            List<Blob> base = new ArrayList<>(mains);
            for (Blob b : base) {
                children(b.x(), b.y(), b.z(), b.rv(), 4 + rng.nextInt(4), 0.34f, 0.58f, b.y() > h * 0.6f ? 0.9f : 0.35f, 1, 0.55f);
            }
            satellites(6 + rng.nextInt(6), 1.2f, 2.1f, 1.8f, 6f);
            trail(w * 0.4f, h * 0.8f, 0f, 1f, -0.05f, 5, w * 0.05f, 0.75f);
        }

        void stratus() {
            float w = range(200f, 270f);
            int n = 8 + rng.nextInt(6);
            float[] xs = new float[n];
            float[] rvs = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = lerp(-0.46f * w, 0.46f * w, (i + 0.5f + range(-0.3f, 0.3f)) / n);
                rvs[i] = range(8f, 16f);
                lobe(xs[i], rvs[i] * range(0.55f, 0.8f) + range(0f, 4f), range(-0.08f * w, 0.08f * w), rvs[i], 1.5f, 0.78f, 1.2f, 1f, true);
            }
            for (int i = 0; i + 1 < n; i++) {
                float r = Math.min(rvs[i], rvs[i + 1]);
                lobe((xs[i] + xs[i + 1]) / 2, r * 0.45f, range(-3f, 3f), r * 0.85f, 1.4f, 0.8f, 1f, 1f, true);
            }
            List<Blob> base = new ArrayList<>(mains);
            for (Blob m : base) {
                children(m.x(), m.y(), m.z(), m.rv(), 2 + rng.nextInt(3), 0.36f, 0.55f, 0.5f, 0, 0f);
            }
            satellites(8 + rng.nextInt(6), 1.3f, 2.4f, 1.5f, 4.5f);
            erode = 0.32f;
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        /** Small low lumps along the base so the underside is not a ruler line. */
        void feet(float w, int n) {
            for (int i = 0; i < n; i++) {
                float rv = w * range(0.035f, 0.07f);
                float side = rng.nextBoolean() ? 1f : -1f;
                lobe(side * w * range(0.28f, 0.5f), rv * 0.65f, range(-0.1f * w, 0.1f * w), rv, 1.2f, 0.85f, 1f, 1f, false);
            }
        }

        // ---- baking ----

        CloudVolume bake() {
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            for (Blob b : blobs) {
                float rx = b.rv() / VISIBLE * b.ax();
                float ry = b.rv() / VISIBLE * b.ay();
                float rz = b.rv() / VISIBLE * b.az();
                minX = Math.min(minX, b.x() - rx);
                maxX = Math.max(maxX, b.x() + rx);
                minY = Math.min(minY, b.y() - ry);
                maxY = Math.max(maxY, b.y() + ry);
                minZ = Math.min(minZ, b.z() - rz);
                maxZ = Math.max(maxZ, b.z() + rz);
            }
            if (baseClip) {
                minY = 0f;
            }
            float offX = -minX + PAD;
            float offY = -minY + (baseClip ? 0f : PAD);
            float offZ = -minZ + PAD;
            int nx = (int) Math.ceil(maxX - minX) + 2 * PAD;
            int ny = (int) Math.ceil(maxY - minY) + PAD + (baseClip ? 0 : PAD);
            int nz = (int) Math.ceil(maxZ - minZ) + 2 * PAD;
            CloudVolume v = new CloudVolume(nx, ny, nz);

            for (Blob b : blobs) {
                splat(v, b, offX, offY, offZ);
            }

            // Flat underside.
            if (baseClip) {
                for (int z = 0; z < nz; z++) {
                    for (int y = 0; y < 4; y++) {
                        float t = Math.max(0f, Math.min(1f, (y + 0.5f - 0.3f) / 3f));
                        float f = t * t * (3f - 2f * t);
                        int row = (z * ny + y) * nx;
                        for (int x = 0; x < nx; x++) {
                            v.full[row + x] *= f;
                            v.coarse[row + x] *= f;
                        }
                    }
                }
            }

            // Roughen, then eat the edges with a warped noise so notches, hooks and loose pieces appear.
            int s1 = noiseSeed * 3 + 1;
            int s2 = noiseSeed * 3 + 2;
            int s3 = noiseSeed * 3 + 3;
            for (int z = 0; z < nz; z++) {
                for (int y = 0; y < ny; y++) {
                    int row = (z * ny + y) * nx;
                    for (int x = 0; x < nx; x++) {
                        float d = v.full[row + x];
                        if (d < 0.02f) {
                            continue;
                        }
                        float px = x + 0.5f;
                        float py = y + 0.5f;
                        float pz = z + 0.5f;
                        float bump = (Noise.value(s1, px * 0.14f, py * 0.14f, pz * 0.14f) - 0.5f) * rough * 2f
                                + (Noise.value(s1 + 17, px * 0.09f, py * 0.09f, pz * 0.09f) - 0.5f) * rough * 1.4f;
                        float wx = (Noise.value(s2, px * 0.11f, py * 0.11f, pz * 0.11f) - 0.5f) * 9f;
                        float wy = (Noise.value(s2 + 5, px * 0.11f, py * 0.11f, pz * 0.11f) - 0.5f) * 9f;
                        float wz = (Noise.value(s2 + 9, px * 0.11f, py * 0.11f, pz * 0.11f) - 0.5f) * 9f;
                        float e = Noise.value(s3, (px + wx) * 0.105f, (py + wy) * 0.105f, (pz + wz) * 0.105f);
                        // Strongest on thin, outer density; solid cores stay intact.
                        float outer = 1f - Math.min(1f, d / 1.05f);
                        float eat = erode * Math.max(0f, e - 0.35f) * 1.9f * (0.35f + outer);
                        v.full[row + x] = Math.max(0f, d + bump - eat);
                    }
                }
            }
            v.measure();
            return v;
        }

        private void splat(CloudVolume v, Blob b, float offX, float offY, float offZ) {
            float cx = b.x() + offX;
            float cy = b.y() + offY;
            float cz = b.z() + offZ;
            float rx = b.rv() / VISIBLE * b.ax();
            float ry = b.rv() / VISIBLE * b.ay();
            float rz = b.rv() / VISIBLE * b.az();
            int x0 = Math.max(0, (int) Math.floor(cx - rx));
            int x1 = Math.min(v.nx - 1, (int) Math.ceil(cx + rx));
            int y0 = Math.max(0, (int) Math.floor(cy - ry));
            int y1 = Math.min(v.ny - 1, (int) Math.ceil(cy + ry));
            int z0 = Math.max(0, (int) Math.floor(cz - rz));
            int z1 = Math.min(v.nz - 1, (int) Math.ceil(cz + rz));
            for (int z = z0; z <= z1; z++) {
                float dz = (z + 0.5f - cz) / rz;
                for (int y = y0; y <= y1; y++) {
                    float dy = (y + 0.5f - cy) / ry;
                    int row = (z * v.ny + y) * v.nx;
                    for (int x = x0; x <= x1; x++) {
                        float dx = (x + 0.5f - cx) / rx;
                        float d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 >= 1f) {
                            continue;
                        }
                        float k = 1f - d2;
                        float val = k * k * k * b.w();
                        v.full[row + x] += val;
                        if (b.coarse()) {
                            v.coarse[row + x] += val;
                        }
                    }
                }
            }
        }
    }
}
