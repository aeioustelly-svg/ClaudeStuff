package com.dacianmod.draco.registry;

import com.dacianmod.draco.DacianDraco;
import com.dacianmod.draco.item.DacianFeltMaterial;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DacianDraco.MODID);

    public static final RegistryObject<Item> SHED_SKIN = ITEMS.register("shed_skin",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> DACIAN_FELT_CAP = ITEMS.register("dacian_felt_cap",
            () -> new ArmorItem(DacianFeltMaterial.INSTANCE, ArmorItem.Type.HELMET, new Item.Properties()));

    public static final RegistryObject<ForgeSpawnEggItem> DRACO_SPAWN_EGG = ITEMS.register("draco_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.DRACO, 0x4F5B45, 0xB07A2E, new Item.Properties()));
}
