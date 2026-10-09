package com.gnoblarmod.gnoblars.event;

import com.gnoblarmod.gnoblars.Gnoblars;
import com.gnoblarmod.gnoblars.entity.GnoblarEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Gnoblars.MODID)
public class GnoblarEvents {
    /** Sneak and use a block with an empty hand to put a gnoblar down from your back. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
                && !player.level().isClientSide) {
            GnoblarEntity.putDownPassengers(player);
        }
    }
}
