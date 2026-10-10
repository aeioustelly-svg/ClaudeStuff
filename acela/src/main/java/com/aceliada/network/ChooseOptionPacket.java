package com.aceliada.network;

import com.aceliada.entity.AcelaEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client to server: the player picked one of the four answers. */
public record ChooseOptionPacket(int entityId, int option) {
    private static final double MAX_DISTANCE = 64.0;

    public ChooseOptionPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(option);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        if (player.level().getEntity(entityId) instanceof AcelaEntity acela
                && acela.isParticipant(player)
                && acela.distanceToSqr(player) < MAX_DISTANCE * MAX_DISTANCE) {
            acela.onDialogueChoice(player, option);
        }
    }
}
