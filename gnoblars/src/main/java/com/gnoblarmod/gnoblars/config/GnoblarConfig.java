package com.gnoblarmod.gnoblars.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common config: lets players switch the annoying behaviour off. */
public class GnoblarConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue PESTERING;
    private static final ForgeConfigSpec.BooleanValue SCAVENGING;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        PESTERING = builder.comment("Wild gnoblars follow nearby players and squeak at them.")
                .define("pestering", true);
        SCAVENGING = builder.comment("Wild gnoblars pick up dropped items and carry them off (they hand them back for a gift).")
                .define("scavenging", true);
        SPEC = builder.build();
    }

    /** The config may not be loaded yet (for example in GameTests), so fall back to the defaults. */
    public static boolean pestering() {
        try {
            return PESTERING.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean scavenging() {
        try {
            return SCAVENGING.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
