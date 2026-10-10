package com.ghostfreakmod.ghostfreak.registry;

import com.ghostfreakmod.ghostfreak.GhostfreakMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, GhostfreakMod.MODID);

    /**
     * A soul lantern that burns at light level 7, dim enough for a Ghostfreak (it avoids light
     * level 9 and above), so it lights a base without driving them off.
     */
    public static final RegistryObject<Block> SPECTRAL_LANTERN = BLOCKS.register("spectral_lantern",
            () -> new LanternBlock(BlockBehaviour.Properties.copy(Blocks.SOUL_LANTERN).lightLevel(state -> 7)));
}
