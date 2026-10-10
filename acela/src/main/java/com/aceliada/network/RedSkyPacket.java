package com.aceliada.network;

import com.aceliada.client.RedSky;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client: turn the sky red (from the summoning) or back to normal (when the fight ends). */
public record RedSkyPacket(boolean on) {
    public RedSkyPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(on);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RedSky.set(on));
    }
}
