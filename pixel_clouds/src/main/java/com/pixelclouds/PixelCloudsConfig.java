package com.pixelclouds;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-side tuning, written to config/pixel_clouds-client.toml. */
public final class PixelCloudsConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue COVERAGE;
    public static final ForgeConfigSpec.DoubleValue SIZE;
    public static final ForgeConfigSpec.DoubleValue WIND_SPEED;
    public static final ForgeConfigSpec.IntValue HEIGHT;
    public static final ForgeConfigSpec.IntValue RANGE;
    public static final ForgeConfigSpec.BooleanValue CLOUD_FOG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Draw pixel clouds. When false, or when the shader fails to load, vanilla clouds are used.")
                .define("enabled", true);
        COVERAGE = b.comment("How much of the sky holds clouds in fair weather (0 = clear, 1 = crowded).")
                .defineInRange("coverage", 0.55, 0.0, 1.0);
        SIZE = b.comment("Scales the size of every cloud.")
                .defineInRange("size", 1.3, 0.4, 2.5);
        WIND_SPEED = b.comment("How fast clouds drift, in blocks per second.")
                .defineInRange("windSpeed", 2.0, 0.0, 40.0);
        HEIGHT = b.comment("Base altitude of the fair-weather cloud layer. Clouds sit between this and about 45 blocks above it.")
                .defineInRange("height", 190, 70, 300);
        RANGE = b.comment("How far away clouds are drawn, in blocks.")
                .defineInRange("range", 1100, 300, 3000);
        CLOUD_FOG = b.comment("Thicken the fog while the camera is inside a cloud.")
                .define("cloudFog", true);
        SPEC = b.build();
    }

    private PixelCloudsConfig() {}
}
