package com.ghostfreakmod.ghostfreak.registry;

import com.ghostfreakmod.ghostfreak.GhostfreakMod;
import com.ghostfreakmod.ghostfreak.item.EctoplasmBottleItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GhostfreakMod.MODID);

    public static final RegistryObject<Item> ECTOPLASM_BOTTLE = ITEMS.register("ectoplasm_bottle",
            () -> new EctoplasmBottleItem(new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)));

    public static final RegistryObject<Item> SPECTRAL_LANTERN = ITEMS.register("spectral_lantern",
            () -> new BlockItem(ModBlocks.SPECTRAL_LANTERN.get(), new Item.Properties()));

    public static final RegistryObject<ForgeSpawnEggItem> GHOSTFREAK_SPAWN_EGG = ITEMS.register("ghostfreak_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.GHOSTFREAK, 0x9E9C9C, 0xC850C0, new Item.Properties()));
}
