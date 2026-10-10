package com.okapimod.okapi.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * What an okapi can reach with its tongue, and what it brings back. Everything here is renewable:
 * ripe crops are reset to their young stage, and leaves are only trimmed, never removed.
 */
public final class OkapiForaging {
    /** How far the tongue reaches from the okapi's eyes, in blocks. */
    public static final double REACH = 3.0D;

    public enum Kind { COCOA, SWEET_BERRIES, GLOW_BERRIES, LEAVES }

    private static final Map<Block, Item> SAPLINGS = Map.ofEntries(
            Map.entry(Blocks.OAK_LEAVES, Items.OAK_SAPLING),
            Map.entry(Blocks.SPRUCE_LEAVES, Items.SPRUCE_SAPLING),
            Map.entry(Blocks.BIRCH_LEAVES, Items.BIRCH_SAPLING),
            Map.entry(Blocks.JUNGLE_LEAVES, Items.JUNGLE_SAPLING),
            Map.entry(Blocks.ACACIA_LEAVES, Items.ACACIA_SAPLING),
            Map.entry(Blocks.DARK_OAK_LEAVES, Items.DARK_OAK_SAPLING),
            Map.entry(Blocks.MANGROVE_LEAVES, Items.MANGROVE_PROPAGULE),
            Map.entry(Blocks.CHERRY_LEAVES, Items.CHERRY_SAPLING),
            Map.entry(Blocks.AZALEA_LEAVES, Items.AZALEA),
            Map.entry(Blocks.FLOWERING_AZALEA_LEAVES, Items.FLOWERING_AZALEA));

    private OkapiForaging() {
    }

    @Nullable
    public static Kind kindOf(BlockState state) {
        if (state.is(Blocks.COCOA)) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE ? Kind.COCOA : null;
        }
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            return state.getValue(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE ? Kind.SWEET_BERRIES : null;
        }
        if (state.hasProperty(BlockStateProperties.BERRIES) && state.getValue(BlockStateProperties.BERRIES)
                && (state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT))) {
            return Kind.GLOW_BERRIES;
        }
        if (state.getBlock() instanceof LeavesBlock) {
            return Kind.LEAVES;
        }
        return null;
    }

    public static boolean isCrop(@Nullable Kind kind) {
        return kind != null && kind != Kind.LEAVES;
    }

    /**
     * Takes what a tongue can take from the block, resets or keeps the block as described above
     * and returns the harvest. Leaves usually give nothing.
     */
    public static List<ItemStack> harvest(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        List<ItemStack> loot = new ArrayList<>();
        Kind kind = kindOf(state);
        if (kind == null) {
            return loot;
        }
        switch (kind) {
            case COCOA -> {
                level.setBlock(pos, state.setValue(CocoaBlock.AGE, 0), Block.UPDATE_ALL);
                loot.add(new ItemStack(Items.COCOA_BEANS, 2 + random.nextInt(2)));
                level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.NEUTRAL, 0.8F, 0.8F);
            }
            case SWEET_BERRIES -> {
                level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_ALL);
                loot.add(new ItemStack(Items.SWEET_BERRIES, 2 + random.nextInt(2)));
                level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.NEUTRAL, 0.8F, 0.9F);
            }
            case GLOW_BERRIES -> {
                level.setBlock(pos, state.setValue(BlockStateProperties.BERRIES, false), Block.UPDATE_ALL);
                loot.add(new ItemStack(Items.GLOW_BERRIES));
                level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.NEUTRAL, 0.8F, 1.0F);
            }
            case LEAVES -> {
                nibble(level, pos, state);
                float roll = random.nextFloat();
                Block block = state.getBlock();
                if (roll < 0.10F && (block == Blocks.OAK_LEAVES || block == Blocks.DARK_OAK_LEAVES)) {
                    loot.add(new ItemStack(Items.APPLE));
                } else if (roll < 0.25F && SAPLINGS.containsKey(block)) {
                    loot.add(new ItemStack(SAPLINGS.get(block)));
                } else if (roll < 0.45F) {
                    loot.add(new ItemStack(Items.STICK));
                }
            }
        }
        return loot;
    }

    /** The cosmetic part of trimming a leaf: a few leaf particles and a rustle. */
    public static void nibble(ServerLevel level, BlockPos pos, BlockState state) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.3D, 0.3D, 0.3D, 0.05D);
        level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.7F, 1.3F);
    }

    /**
     * Picks the nearest ripe crop around the centre, or failing that a leaf block (when allowed).
     * Positions rejected by {@code skip} are ignored.
     */
    @Nullable
    public static BlockPos findTarget(Level level, BlockPos centre, int radius, boolean crops, boolean leaves,
                                      Predicate<BlockPos> skip) {
        BlockPos bestCrop = null;
        double bestCropDistance = Double.MAX_VALUE;
        BlockPos bestLeaf = null;
        double bestLeafDistance = Double.MAX_VALUE;
        int leafRadius = Math.min(radius, 5);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 5; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    Kind kind = kindOf(level.getBlockState(cursor));
                    if (kind == null) {
                        continue;
                    }
                    double distance = cursor.distSqr(centre);
                    if (isCrop(kind)) {
                        if (crops && distance < bestCropDistance && !skip.test(cursor)) {
                            bestCrop = cursor.immutable();
                            bestCropDistance = distance;
                        }
                    } else if (leaves && Math.abs(dx) <= leafRadius && Math.abs(dz) <= leafRadius
                            && dy >= 1 && dy <= 4 && distance < bestLeafDistance && !skip.test(cursor)) {
                        bestLeaf = cursor.immutable();
                        bestLeafDistance = distance;
                    }
                }
            }
        }
        return bestCrop != null ? bestCrop : bestLeaf;
    }

    public static boolean inReach(OkapiEntity okapi, BlockPos pos) {
        Vec3 eyes = okapi.getEyePosition();
        return eyes.distanceTo(Vec3.atCenterOf(pos)) <= REACH;
    }
}
