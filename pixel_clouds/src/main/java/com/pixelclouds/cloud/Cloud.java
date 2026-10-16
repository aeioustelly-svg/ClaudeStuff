package com.pixelclouds.cloud;

import java.util.ArrayList;
import java.util.List;

/**
 * One cloud: a world-space anchor and a set of puffs in its own texel space. The sprite is always
 * drawn facing the camera, like a particle, with texel {@code u} along the camera's right and
 * {@code v} down the camera's up.
 */
public final class Cloud {
    public final int id;
    public double x, y, z;
    public double prevX, prevY, prevZ;
    /** Per-cloud drift on top of the wind, so clouds catch up with each other and merge. */
    double driftX, driftZ;
    double speedFactor;
    double targetY;
    final List<Blob> blobs = new ArrayList<>();
    int age;
    boolean dissolving;
    /** Ticks left of pulling apart, 0 when not splitting. */
    int splitting;

    private CloudImage image;
    private int version;
    private boolean shapeDirty = true;

    Cloud(int id, double x, double y, double z) {
        this.id = id;
        this.x = this.prevX = x;
        this.y = this.prevY = y;
        this.z = this.prevZ = z;
        this.targetY = y;
    }

    public List<Blob> blobs() {
        return blobs;
    }

    public int age() {
        return age;
    }

    public boolean isDissolving() {
        return dissolving;
    }

    public boolean isSplitting() {
        return splitting > 0;
    }

    /** Increments whenever the image changes, so the renderer knows when to repaint. */
    public int version() {
        return version;
    }

    void markDirty() {
        shapeDirty = true;
    }

    /** Re-rasterises when the puffs have changed since the last call. */
    public CloudImage image(int maxWidth, int maxHeight) {
        if (shapeDirty || image == null) {
            image = CloudRaster.rasterise(blobs, maxWidth, maxHeight);
            shapeDirty = false;
            version++;
        }
        return image;
    }

    /** Horizontal half-extent of the puffs, in texels. */
    double halfWidth() {
        double lo = 0, hi = 0;
        for (Blob b : blobs) {
            lo = Math.min(lo, b.u - b.r);
            hi = Math.max(hi, b.u + b.r);
        }
        return Math.max(-lo, hi);
    }

    double minU() {
        double lo = Double.MAX_VALUE;
        for (Blob b : blobs) {
            lo = Math.min(lo, b.u - Math.max(b.r, b.targetR));
        }
        return lo;
    }

    double maxU() {
        double hi = -Double.MAX_VALUE;
        for (Blob b : blobs) {
            hi = Math.max(hi, b.u + Math.max(b.r, b.targetR));
        }
        return hi;
    }

    double minV() {
        double lo = Double.MAX_VALUE;
        for (Blob b : blobs) {
            lo = Math.min(lo, b.v - Math.max(b.r, b.targetR));
        }
        return lo;
    }

    double maxV() {
        double hi = -Double.MAX_VALUE;
        for (Blob b : blobs) {
            hi = Math.max(hi, b.v + Math.max(b.r, b.targetR));
        }
        return hi;
    }

    public double lerpX(float pt) {
        return prevX + (x - prevX) * pt;
    }

    public double lerpY(float pt) {
        return prevY + (y - prevY) * pt;
    }

    public double lerpZ(float pt) {
        return prevZ + (z - prevZ) * pt;
    }
}
