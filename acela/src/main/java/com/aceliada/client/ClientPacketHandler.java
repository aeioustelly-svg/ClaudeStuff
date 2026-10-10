package com.aceliada.client;

import net.minecraft.client.Minecraft;

/** Client-only reactions to the mod's packets. Only reached through DistExecutor. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void openDialogue(int entityId) {
        Minecraft.getInstance().setScreen(new DialogueScreen(entityId));
    }

    public static void closeDialogue() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DialogueScreen) {
            minecraft.setScreen(null);
        }
    }
}
