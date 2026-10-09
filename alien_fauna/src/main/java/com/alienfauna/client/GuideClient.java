package com.alienfauna.client;

import net.minecraft.client.Minecraft;

/** Client-only entry point, so the common code never loads a screen class on a server. */
public class GuideClient {
    public static void open() {
        Minecraft.getInstance().setScreen(new FieldGuideScreen());
    }
}
