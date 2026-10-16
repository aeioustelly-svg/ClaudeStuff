package com.pixelclouds.gen;

/**
 * A cloud as a 3D density field, one voxel per sprite pixel. x is right, y is up, z points towards a viewer at
 * zero elevation. Two grids are kept: the full density, and a coarse one holding only the large lobes, which gives
 * the large-scale form that the lighting leans on.
 */
public final class CloudVolume {
    /** Density at which the cloud surface sits. */
    public static final float ISO = 0.32f;

    public final int nx;
    public final int ny;
    public final int nz;
    public final float[] full;
    public final float[] coarse;

    /** Bounds of the voxels at or above ISO, inclusive. */
    public int minX;
    public int maxX;
    public int minY;
    public int maxY;
    public int minZ;
    public int maxZ;

    public CloudVolume(int nx, int ny, int nz) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.full = new float[nx * ny * nz];
        this.coarse = new float[nx * ny * nz];
    }

    public int index(int x, int y, int z) {
        return (z * ny + y) * nx + x;
    }

    /** Nearest-voxel lookup at a continuous position, zero outside. */
    public float nearest(float[] grid, double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        if (ix < 0 || iy < 0 || iz < 0 || ix >= nx || iy >= ny || iz >= nz) {
            return 0f;
        }
        return grid[(iz * ny + iy) * nx + ix];
    }

    /** Trilinear lookup, treating voxel centres as lying at +0.5. Zero outside. */
    public float sample(float[] grid, double x, double y, double z) {
        double fx = x - 0.5;
        double fy = y - 0.5;
        double fz = z - 0.5;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        int z0 = (int) Math.floor(fz);
        float tx = (float) (fx - x0);
        float ty = (float) (fy - y0);
        float tz = (float) (fz - z0);
        float c00 = lerp(at(grid, x0, y0, z0), at(grid, x0 + 1, y0, z0), tx);
        float c10 = lerp(at(grid, x0, y0 + 1, z0), at(grid, x0 + 1, y0 + 1, z0), tx);
        float c01 = lerp(at(grid, x0, y0, z0 + 1), at(grid, x0 + 1, y0, z0 + 1), tx);
        float c11 = lerp(at(grid, x0, y0 + 1, z0 + 1), at(grid, x0 + 1, y0 + 1, z0 + 1), tx);
        return lerp(lerp(c00, c10, ty), lerp(c01, c11, ty), tz);
    }

    private float at(float[] grid, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny || z >= nz) {
            return 0f;
        }
        return grid[(z * ny + y) * nx + x];
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Finds the bounds of the solid part. */
    public void measure() {
        minX = nx;
        minY = ny;
        minZ = nz;
        maxX = -1;
        maxY = -1;
        maxZ = -1;
        for (int z = 0; z < nz; z++) {
            for (int y = 0; y < ny; y++) {
                int row = (z * ny + y) * nx;
                for (int x = 0; x < nx; x++) {
                    if (full[row + x] >= ISO) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }
    }

    public boolean isEmpty() {
        return maxX < 0;
    }
}
