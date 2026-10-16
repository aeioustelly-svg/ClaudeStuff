package com.pixelclouds.client;

import net.minecraft.client.renderer.ShaderInstance;

/** Holds the cloud shader once it has compiled. Stays null if loading failed, which falls back to vanilla clouds. */
final class PixelCloudShader {
    private static ShaderInstance instance;

    private PixelCloudShader() {}

    static ShaderInstance get() {
        return instance;
    }

    static void set(ShaderInstance shader) {
        instance = shader;
    }
}
