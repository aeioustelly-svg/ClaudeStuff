package com.alienfauna.client;

import net.minecraft.client.Minecraft;

/** Client-only entry point, so the common code never loads a screen class on a server. */
public class GuideClient {
    /** Opens the guide at the index, or at a creature's page when entry is 0 or more. */
    public static void open(int entry) {
        Minecraft.getInstance().setScreen(new FieldGuideScreen(entry));
    }
}
