package com.pixelclouds.client;

/** Orientation of one cloud's sprite quad for a given camera. */
final class Billboard {
    float dist;
    /** Screen-right (horizontal) and screen-up axes of the quad. */
    float rX;
    float rZ;
    float uX;
    float uY;
    float uZ;
    /** Light direction in the sprite's frame: right, up, towards the viewer. */
    float lr;
    float lu;

    /**
     * @param rx,ry,rz  cloud position relative to the camera
     * @param lightX,lightY  world light direction (unit, no z component)
     * @return false if the cloud sits on the camera
     */
    boolean set(float rx, float ry, float rz, float lightX, float lightY) {
        dist = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (dist < 1e-3f) {
            return false;
        }
        float cx = -rx / dist;
        float cy = -ry / dist;
        float cz = -rz / dist;
        float horizontal = (float) Math.sqrt(cx * cx + cz * cz);
        if (horizontal < 1e-3f) {
            rX = 1f;
            rZ = 0f;
        } else {
            rX = cz / horizontal;
            rZ = -cx / horizontal;
        }
        uX = cy * rZ;
        uY = cz * rX - cx * rZ;
        uZ = -cy * rX;

        lr = lightX * rX;
        lu = lightX * uX + lightY * uY;
        return true;
    }

    /** World light direction (x, y) blended from sun to moon as the sun sets. Never zero length. */
    static float[] lightDirection(float sunX, float sunY) {
        float toMoon = smooth(-0.04f, -0.22f, sunY);
        float x = sunX * (1f - 2f * toMoon);
        float y = sunY * (1f - 2f * toMoon) + toMoon * (1f - toMoon);
        float len = (float) Math.sqrt(x * x + y * y);
        return new float[]{x / len, y / len};
    }

    /** Fade from dissolving next to the camera and at the edge of the draw range. */
    static float distanceFade(float dist, float bodyWidth, float range) {
        float half = bodyWidth * 0.5f;
        return smooth(half * 0.30f, half * 0.80f, dist) * (1f - smooth(range * 0.85f, range, dist));
    }

    static float haze(float dist, float range) {
        return 0.62f * smooth(range * 0.3f, range, dist);
    }

    static float smooth(float e0, float e1, float v) {
        float t = (v - e0) / (e1 - e0);
        t = t < 0f ? 0f : Math.min(t, 1f);
        return t * t * (3f - 2f * t);
    }
}
