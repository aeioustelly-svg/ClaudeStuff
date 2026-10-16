package com.pixelclouds.gen;

/** The families of cloud shape the generator knows. Each has its own recipe in {@link CloudShapes}. */
public enum CloudKind {
    /** Fair-weather heap: a few lobes on a flat base, covered in smaller bumps. */
    CUMULUS,
    /** Tall, narrow and sheared, with a cauliflower crown. */
    TOWER,
    /** A small lone puff. */
    PUFF,
    /** A long, low bank of lobes. */
    BANK,
    /** A loose field of small separate puffs, like altocumulus. */
    CLUSTER,
    /** Thin high streaks. */
    WISP,
    /** A storm tower with a spread-out top. */
    ANVIL,
    /** A very wide, low, ragged sheet. */
    STRATUS
}
