package com.alienfauna;

import com.alienfauna.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Every player is handed the Field Guide once, the first time they join a world with the mod. */
@Mod.EventBusSubscriber(modid = AlienFauna.MODID)
public class GuideEvents {
    private static final String GIVEN = "alien_fauna_field_guide_given";

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) {
            return;
        }
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(ModItems.FIELD_GUIDE.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }
}
