package com.aceliada.network;

import com.aceliada.Aceliada;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AcelaNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Aceliada.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private AcelaNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(OpenDialoguePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenDialoguePacket::encode).decoder(OpenDialoguePacket::new)
                .consumerMainThread(OpenDialoguePacket::handle).add();
        CHANNEL.messageBuilder(CloseDialoguePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CloseDialoguePacket::encode).decoder(CloseDialoguePacket::new)
                .consumerMainThread(CloseDialoguePacket::handle).add();
        CHANNEL.messageBuilder(ChooseOptionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChooseOptionPacket::encode).decoder(ChooseOptionPacket::new)
                .consumerMainThread(ChooseOptionPacket::handle).add();
    }

    /** Sends to a connected player. Players without a connection (GameTest mocks) are skipped. */
    public static void sendTo(ServerPlayer player, Object packet) {
        if (player.connection != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
