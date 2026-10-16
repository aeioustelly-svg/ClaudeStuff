package com.pixelclouds.cloud;

/**
 * One round puff of a cloud, in the cloud's own texel space: {@code u} grows to the right of the
 * sprite and {@code v} grows downwards, like texture coordinates. The radius eases towards
 * {@code targetR}, which is how puffs swell when a cloud forms and shrink when it evaporates.
 */
public final class Blob {
    public double u;
    public double v;
    public double r;
    public double targetR;
    /** Drift in texels per tick, used while a cloud pulls apart. */
    public double du;

    public Blob(double u, double v, double r, double targetR) {
        this.u = u;
        this.v = v;
        this.r = r;
        this.targetR = targetR;
    }

    public Blob copy() {
        Blob b = new Blob(u, v, r, targetR);
        b.du = du;
        return b;
    }

    /** Puffs whose base sits lower are drawn in front, which gives the stacked cumulus look. */
    double depth() {
        return 0.55 * (v + r);
    }

    boolean overlaps(Blob o, double slack) {
        double dx = u - o.u;
        double dy = v - o.v;
        double reach = (r + o.r) * slack;
        return dx * dx + dy * dy < reach * reach;
    }
}
