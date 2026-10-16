package com.pixelclouds;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client settings, stored in {@code config/pixel_clouds-client.toml}. */
public final class PixelCloudsConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue ALTITUDE;
    public static final ForgeConfigSpec.IntValue ALTITUDE_SPREAD;
    public static final ForgeConfigSpec.IntValue RADIUS;
    public static final ForgeConfigSpec.DoubleValue PIXEL_SIZE;
    public static final ForgeConfigSpec.DoubleValue COVERAGE;
    public static final ForgeConfigSpec.BooleanValue SPLIT_AND_MERGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Replace the vanilla clouds with pixel-art clouds. When false the vanilla clouds return.")
                .define("enabled", true);
        ALTITUDE = b.comment("Average cloud height in blocks. Vanilla clouds sit at 192.")
                .defineInRange("altitude", 180, 64, 400);
        ALTITUDE_SPREAD = b.comment("Clouds are scattered this many blocks above and below the average height.")
                .defineInRange("altitudeSpread", 30, 0, 120);
        RADIUS = b.comment("How far away clouds are kept, in blocks. They fade out towards this distance.")
                .defineInRange("radius", 640, 128, 2048);
        PIXEL_SIZE = b.comment("Size of one cloud pixel in blocks. Larger values give bigger, chunkier clouds.")
                .defineInRange("pixelSize", 2.0, 0.5, 8.0);
        COVERAGE = b.comment("Multiplier for how much of the sky is covered. 0 gives a clear sky.")
                .defineInRange("coverage", 1.0, 0.0, 3.0);
        SPLIT_AND_MERGE = b.comment("Let clouds pull apart into two and merge when they drift into each other.")
                .define("splitAndMerge", true);
        SPEC = b.build();
    }

    private PixelCloudsConfig() {
    }
}
