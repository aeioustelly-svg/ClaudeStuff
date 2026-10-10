package com.okapimod.okapi.registry;

import com.okapimod.okapi.OkapiMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OkapiMod.MODID);

    public static final RegistryObject<ForgeSpawnEggItem> OKAPI_SPAWN_EGG = ITEMS.register("okapi_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.OKAPI, 0x4A342E, 0xD9CDB0, new Item.Properties()));
}
