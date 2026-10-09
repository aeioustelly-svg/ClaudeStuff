package com.alienfauna.registry;

import com.alienfauna.AlienFauna;
import com.alienfauna.item.FieldGuideItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AlienFauna.MODID);

    /** The book that describes every creature. Given once on first joining, and craftable. */
    public static final RegistryObject<Item> FIELD_GUIDE = ITEMS.register("field_guide",
            () -> new FieldGuideItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<ForgeSpawnEggItem> CANNONBOLT_SPAWN_EGG = ITEMS.register("cannonbolt_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CANNONBOLT, 0xE4E4E0, 0xC8A22A, new Item.Properties()));
}
