package com.pixelclouds.cloud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The client-side cloud simulation. Clouds drift with a slowly turning wind, form out of nothing
 * by swelling from tiny puffs, evaporate the same way, pull apart into two clouds and merge when
 * they drift into each other. Coverage and shape follow the weather: rain brings more, wider,
 * flatter and lower clouds.
 *
 * <p>Splits and merges are laid out along the camera's horizontal right vector, which is the
 * axis the sprites are drawn along. From the player's point of view the pieces therefore stay
 * where they were on screen when a cloud is split in two or two clouds become one.
 *
 * <p>This class has no Minecraft dependencies, so it can be tested and previewed headlessly.
 */
public final class CloudField {
    /** Size of one atlas slot. No cloud image is ever larger than this. */
    public static final int MAX_WIDTH = 128;
    public static final int MAX_HEIGHT = 64;

    private static final double GROW_RATE = 0.02;
    private static final double SHRINK_RATE = 0.016;
    private static final double SPLIT_DRIFT = 0.02;
    private static final int SPLIT_TICKS = 1200;

    public static final class Settings {
        public double radius = 640;
        public double pixelSize = 2.0;
        public double altitude = 180;
        public double altitudeSpread = 30;
        public double coverage = 1.0;
        public int maxClouds = 256;
        public boolean splitAndMerge = true;
        /** Spawn and evaporate clouds to follow the weather. Only tests turn this off. */
        boolean manageCount = true;
    }

    /** What the simulation needs to know about the camera and the weather each tick. */
    public static final class View {
        public double x, y, z;
        /** Horizontal unit vector to the camera's right. */
        public double rightX = 1, rightZ = 0;
        public double rain, thunder;
    }

    private final Random rnd;
    private final List<Cloud> clouds = new ArrayList<>();
    private final List<Cloud> unmodifiable = Collections.unmodifiableList(clouds);
    private Settings settings;
    private int nextId;
    private int ticks;
    private boolean filled;
    private double lastX, lastZ;
    private double windAngle;
    private double windSpeed = 0.035;
    private int lastDissolve;

    public CloudField(Settings settings, long seed) {
        this.settings = settings;
        this.rnd = new Random(seed);
        this.windAngle = rnd.nextDouble() * Math.PI * 2;
    }

    public List<Cloud> clouds() {
        return unmodifiable;
    }

    public Settings settings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        if (settings.radius != this.settings.radius || settings.altitude != this.settings.altitude
                || settings.pixelSize != this.settings.pixelSize) {
            filled = false;
        }
        this.settings = settings;
    }

    public double windX() {
        return Math.cos(windAngle) * windSpeed;
    }

    public double windZ() {
        return Math.sin(windAngle) * windSpeed;
    }

    public void tick(View view) {
        ticks++;
        double storm = clamp(view.rain * 0.8 + view.thunder * 0.4, 0, 1);
        double r = settings.radius;

        if (settings.manageCount && (!filled || sq(view.x - lastX) + sq(view.z - lastZ) > sq(r * 0.5))) {
            fill(view, storm);
        }
        lastX = view.x;
        lastZ = view.z;

        updateWind(storm);
        double wx = windX(), wz = windZ();
        int target = targetCount(storm);

        Iterator<Cloud> it = clouds.iterator();
        while (it.hasNext()) {
            Cloud c = it.next();
            c.prevX = c.x;
            c.prevY = c.y;
            c.prevZ = c.z;
            c.x += wx * c.speedFactor + c.driftX;
            c.z += wz * c.speedFactor + c.driftZ;
            c.targetY = Math.min(c.targetY, altitudeCeiling(storm));
            c.y += clamp(c.targetY - c.y, -0.02, 0.02);
            c.age++;
            evolve(c);
            boolean gone = c.blobs.isEmpty() || sq(c.x - view.x) + sq(c.z - view.z) > sq(r * 1.05);
            if (gone) {
                it.remove();
            }
        }

        if (settings.splitAndMerge) {
            if (ticks % 10 == 0) {
                mergeNearby(view);
            }
            if (ticks % 10 == 5) {
                splitApart(view);
            }
        }

        if (!settings.manageCount) {
            return;
        }
        int live = 0;
        for (Cloud c : clouds) {
            if (!c.dissolving) {
                live++;
            }
        }
        if (live < target) {
            double rate = (target - live) * 0.004;
            int spawns = live < target * 0.6 ? 3 : 1;
            for (int i = 0; i < spawns; i++) {
                if (rnd.nextDouble() < rate && clouds.size() < settings.maxClouds) {
                    spawnTicking(view, storm);
                }
            }
        } else if (live > target * 1.2 && ticks - lastDissolve > 40) {
            List<Cloud> candidates = new ArrayList<>();
            for (Cloud c : clouds) {
                if (!c.dissolving && c.splitting == 0) {
                    candidates.add(c);
                }
            }
            if (!candidates.isEmpty()) {
                candidates.get(rnd.nextInt(candidates.size())).dissolving = true;
                lastDissolve = ticks;
            }
        }
    }

    int targetCount(double storm) {
        double coverage = clamp((0.16 + 0.55 * storm) * settings.coverage, 0, 0.9);
        double width = (storm > 0.5 ? 80 : 55) * settings.pixelSize;
        double footprint = width * width * 0.6;
        double area = Math.PI * settings.radius * settings.radius;
        return (int) Math.min(settings.maxClouds, Math.round(coverage * area / footprint));
    }

    private void updateWind(double storm) {
        windAngle += rnd.nextGaussian() * 0.0006;
        double wanted = 0.03 + 0.05 * storm;
        windSpeed += (wanted - windSpeed) * 0.002;
    }

    private double altitudeCeiling(double storm) {
        return settings.altitude + settings.altitudeSpread - storm * 20;
    }

    private void fill(View view, double storm) {
        clouds.clear();
        filled = true;
        int target = targetCount(storm);
        for (int i = 0; i < target; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rnd.nextDouble()) * settings.radius;
            Cloud c = create(view.x + Math.cos(a) * d, view.z + Math.sin(a) * d, storm, true);
            c.age = 2000 + rnd.nextInt(2000);
        }
    }

    private void spawnTicking(View view, double storm) {
        double r = settings.radius;
        if (rnd.nextDouble() < 0.6) {
            // At the edge, mostly upwind, where the distance fade hides the pop-in.
            double a = rnd.nextDouble() < 0.6
                    ? Math.atan2(-windZ(), -windX()) + (rnd.nextDouble() - 0.5) * Math.PI
                    : rnd.nextDouble() * Math.PI * 2;
            double d = r * (0.86 + rnd.nextDouble() * 0.14);
            Cloud c = create(view.x + Math.cos(a) * d, view.z + Math.sin(a) * d, storm, true);
            c.age = 2000;
        } else {
            // In open sky: grows out of a few tiny puffs.
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = r * (0.15 + rnd.nextDouble() * 0.65);
            create(view.x + Math.cos(a) * d, view.z + Math.sin(a) * d, storm, false);
        }
    }

    private Cloud create(double x, double z, double storm, boolean grown) {
        double y = settings.altitude + (rnd.nextDouble() * 2 - 1) * settings.altitudeSpread;
        y = Math.min(y, altitudeCeiling(storm));
        Cloud c = new Cloud(nextId++, x, y, z);
        c.speedFactor = 0.85 + rnd.nextDouble() * 0.3;
        double flat = clamp(rnd.nextDouble() * 0.25 + storm * 0.75, 0, 1);
        int width = storm > 0.5
                ? 50 + rnd.nextInt(56)
                : 24 + rnd.nextInt(52);
        c.blobs.addAll(CloudShapes.cumulus(rnd, width, flat, grown));
        clouds.add(c);
        return c;
    }

    private void evolve(Cloud c) {
        boolean changed = false;
        if (c.dissolving) {
            for (Blob b : c.blobs) {
                b.targetR = 0;
            }
        } else if (rnd.nextDouble() < 0.003 && !c.blobs.isEmpty()) {
            // Billowing: one puff slowly swells or sags.
            Blob b = c.blobs.get(rnd.nextInt(c.blobs.size()));
            b.targetR = clamp(b.targetR * (0.85 + rnd.nextDouble() * 0.3), 2.0, 14.0);
        }

        Iterator<Blob> it = c.blobs.iterator();
        while (it.hasNext()) {
            Blob b = it.next();
            if (b.r < b.targetR) {
                b.r = Math.min(b.targetR, b.r + GROW_RATE);
                changed = true;
            } else if (b.r > b.targetR) {
                b.r = Math.max(b.targetR, b.r - SHRINK_RATE);
                changed = true;
            }
            if (c.splitting > 0 && b.du != 0) {
                b.u += b.du;
                changed = true;
            }
            if (b.targetR <= 0 && b.r < 0.6) {
                it.remove();
                changed = true;
            }
        }

        if (c.splitting > 0) {
            c.splitting--;
            if (c.splitting == 0 || c.maxU() - c.minU() > MAX_WIDTH - 4) {
                c.splitting = 0;
                for (Blob b : c.blobs) {
                    b.du = 0;
                }
            }
        }

        // Re-rasterising is the costly part, so each cloud redraws at most every fifth tick.
        if (changed && (ticks + c.id) % 5 == 0) {
            c.markDirty();
        }
    }

    private void splitApart(View view) {
        List<Cloud> born = new ArrayList<>();
        Iterator<Cloud> it = clouds.iterator();
        while (it.hasNext()) {
            Cloud c = it.next();
            if (c.dissolving) {
                continue;
            }
            if (c.splitting == 0) {
                if (canStartSplit(c) && rnd.nextDouble() < 0.004) {
                    startSplit(c);
                }
                continue;
            }
            int[] label = CloudRaster.components(c.blobs, 0.92);
            int count = 0;
            for (int l : label) {
                count = Math.max(count, l + 1);
            }
            if (count < 2) {
                continue;
            }
            it.remove();
            for (int k = 0; k < count; k++) {
                List<Blob> part = new ArrayList<>();
                for (int i = 0; i < label.length; i++) {
                    if (label[i] == k) {
                        part.add(c.blobs.get(i));
                    }
                }
                born.add(piece(c, part, view));
            }
        }
        clouds.addAll(born);
    }

    private boolean canStartSplit(Cloud c) {
        if (c.age < 600 || c.blobs.size() < 6) {
            return false;
        }
        for (Blob b : c.blobs) {
            if (Math.abs(b.r - b.targetR) > 0.5) {
                return false;
            }
        }
        return c.maxU() - c.minU() >= 40;
    }

    void startSplit(Cloud c) {
        double lo = c.minU(), hi = c.maxU();
        double line = (lo + hi) / 2 + rnd.nextGaussian() * (hi - lo) * 0.1;
        for (Blob b : c.blobs) {
            b.du = b.u < line ? -SPLIT_DRIFT : SPLIT_DRIFT;
        }
        c.splitting = SPLIT_TICKS;
    }

    private Cloud piece(Cloud parent, List<Blob> part, View view) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE, area = 0;
        for (Blob b : part) {
            lo = Math.min(lo, b.u - b.r);
            hi = Math.max(hi, b.u + b.r);
            area += b.targetR * b.targetR;
        }
        double centre = (lo + hi) / 2;
        double px = settings.pixelSize;
        Cloud c = new Cloud(nextId++, parent.x + view.rightX * centre * px, parent.y, parent.z + view.rightZ * centre * px);
        c.prevX = parent.prevX + (c.x - parent.x);
        c.prevY = parent.prevY;
        c.prevZ = parent.prevZ + (c.z - parent.z);
        c.targetY = parent.targetY;
        c.speedFactor = parent.speedFactor;
        // Keep easing apart after the split.
        double away = Math.signum(centre) * 0.006;
        c.driftX = parent.driftX + view.rightX * away;
        c.driftZ = parent.driftZ + view.rightZ * away;
        c.age = parent.age;
        for (Blob b : part) {
            b.u -= centre;
            b.du = 0;
            c.blobs.add(b);
        }
        // A lone wisp that broke off evaporates.
        c.dissolving = area < 20;
        return c;
    }

    private void mergeNearby(View view) {
        double px = settings.pixelSize;
        double fx = -view.rightZ, fz = view.rightX;
        for (int i = 0; i < clouds.size(); i++) {
            Cloud a = clouds.get(i);
            if (!mergeable(a)) {
                continue;
            }
            for (int j = i + 1; j < clouds.size(); j++) {
                Cloud b = clouds.get(j);
                if (!mergeable(b)) {
                    continue;
                }
                double dx = b.x - a.x, dz = b.z - a.z, dy = b.y - a.y;
                double along = dx * view.rightX + dz * view.rightZ;
                double depth = dx * fx + dz * fz;
                if (Math.abs(depth) > 24 || Math.abs(dy) > 6 * px) {
                    continue;
                }
                double reach = (a.halfWidth() + b.halfWidth()) * px * 0.8;
                if (Math.abs(along) > reach) {
                    continue;
                }
                double du = along / px, dv = -dy / px;
                double lo = Math.min(a.minU(), b.minU() + du);
                double hi = Math.max(a.maxU(), b.maxU() + du);
                double top = Math.min(a.minV(), b.minV() + dv);
                double bottom = Math.max(a.maxV(), b.maxV() + dv);
                if (hi - lo > MAX_WIDTH - 6 || bottom - top > MAX_HEIGHT - 4) {
                    continue;
                }
                if (rnd.nextDouble() > 0.3) {
                    continue;
                }
                merge(a, b, du, dv, depth, (lo + hi) / 2, view);
                clouds.remove(j);
                return;
            }
        }
    }

    private static boolean mergeable(Cloud c) {
        return !c.dissolving && c.splitting == 0 && c.age > 200;
    }

    private void merge(Cloud a, Cloud b, double du, double dv, double depth, double centre, View view) {
        double px = settings.pixelSize;
        for (Blob blob : b.blobs) {
            Blob moved = blob.copy();
            moved.u += du;
            moved.v += dv;
            a.blobs.add(moved);
        }
        for (Blob blob : a.blobs) {
            blob.u -= centre;
        }
        // Meet halfway in depth, so neither half jumps far towards or away from the camera.
        double fx = -view.rightZ, fz = view.rightX;
        double shiftX = view.rightX * centre * px + fx * depth / 2;
        double shiftZ = view.rightZ * centre * px + fz * depth / 2;
        a.x += shiftX;
        a.z += shiftZ;
        a.prevX += shiftX;
        a.prevZ += shiftZ;
        a.driftX = (a.driftX + b.driftX) / 2;
        a.driftZ = (a.driftZ + b.driftZ) / 2;
        a.speedFactor = (a.speedFactor + b.speedFactor) / 2;
        a.markDirty();
    }

    /** Test hook: adds a fully grown cloud with the given puffs. */
    Cloud addForTest(double x, double y, double z, List<Blob> blobs) {
        Cloud c = new Cloud(nextId++, x, y, z);
        c.speedFactor = 1;
        c.age = 1000;
        c.blobs.addAll(blobs);
        clouds.add(c);
        filled = true;
        return c;
    }

    static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : x > hi ? hi : x;
    }

    private static double sq(double x) {
        return x * x;
    }
}
