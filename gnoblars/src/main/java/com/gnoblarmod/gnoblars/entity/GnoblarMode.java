package com.gnoblarmod.gnoblars.entity;

/** What a friend does when its owner is not asking for anything: the usual pet choices of follow, sit and wander. */
public enum GnoblarMode {
    FOLLOW("follow"),
    SIT("sit"),
    WANDER("wander");

    private final String id;

    GnoblarMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Follow, then sit, then wander, then follow again. */
    public GnoblarMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static GnoblarMode byName(String name) {
        for (GnoblarMode mode : values()) {
            if (mode.id.equals(name)) {
                return mode;
            }
        }
        return FOLLOW;
    }
}
