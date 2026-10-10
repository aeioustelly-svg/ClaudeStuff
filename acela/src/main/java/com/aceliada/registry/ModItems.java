package com.aceliada.registry;

import com.aceliada.Aceliada;
import net.minecraft.world.item.RecordItem;
import com.aceliada.item.ZombieDrugItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Aceliada.MODID);

    /** Length of la_crucea_din_mormant.ogg (2:23.7) in ticks. */
    public static final int DISC_LENGTH_TICKS = 2875;

    public static final RegistryObject<Item> LA_CRUCEA_DIN_MORMANT_DISC = ITEMS.register("music_disc_la_crucea_din_mormant",
            () -> new RecordItem(13, ModSounds.LA_CRUCEA_DIN_MORMANT,
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE), DISC_LENGTH_TICKS));

    public static final RegistryObject<Item> DROGUL_ZOMBIE = ITEMS.register("drogul_zombie",
            () -> new ZombieDrugItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    public static final RegistryObject<ForgeSpawnEggItem> ACELA_SPAWN_EGG = ITEMS.register("acela_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.ACELA, 0x0E0B10, 0xC21414, new Item.Properties()));
}
