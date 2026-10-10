package com.aceliada.summon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Builds Dealul Bohii: a round blackstone arena on top of the Nether's bedrock roof with a hill of
 * skulls in the middle and a dark oak cross on its summit.
 *
 * <p>{@code centre} is the floor block in the middle of the arena (the top bedrock layer in the
 * Nether, y 127). Players stand one block above it. The layout is deterministic, so rebuilding over an
 * old arena restores it.
 */
public final class ArenaBuilder {
    public static final int FLOOR_RADIUS = 15;
    public static final int PLAYER_RING = 11;
    private static final double WALL_INNER = 15.5;
    private static final double WALL_OUTER = 17.5;
    private static final int WALL_HEIGHT = 5;
    private static final int PILLAR_HEIGHT = 7;
    private static final int CLEAR_RADIUS = 19;
    private static final int CLEAR_HEIGHT = 14;
    private static final double HILL_RADIUS = 5.3;

    private final ServerLevel level;
    private final BlockPos centre;
    private final List<BlockPos> needsShapeUpdate = new ArrayList<>();

    private ArenaBuilder(ServerLevel level, BlockPos centre) {
        this.level = level;
        this.centre = centre;
    }

    public static void build(ServerLevel level, BlockPos centre) {
        new ArenaBuilder(level, centre).build();
    }

    /** Height of the skull hill above the floor at this column, 0 outside the hill. */
    public static int hillHeight(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d >= HILL_RADIUS) {
            return 0;
        }
        double jitter = (hash(dx, dz, 11) % 5 - 2) * 0.18;
        return Math.max(0, (int) Math.round(5.0 - d * 0.9 + jitter));
    }

    /** Where the summoned players land, spread over the four paths. */
    public static BlockPos playerSpot(BlockPos centre, int index) {
        Direction[] order = {Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.NORTH};
        Direction dir = order[index % 4];
        int ring = PLAYER_RING - (index / 4) % 3;
        return centre.relative(dir, ring).above();
    }

    private void build() {
        clear();
        floor();
        wall();
        for (int i = 0; i < 8; i++) {
            pillar(Math.toRadians(22.5 + 45.0 * i));
        }
        for (int i = 0; i < 8; i++) {
            if (i == 2) {
                gate(Math.toRadians(90.0));
            } else {
                window(Math.toRadians(45.0 * i));
            }
        }
        hill();
        cross();
        for (BlockPos pos : needsShapeUpdate) {
            BlockState state = level.getBlockState(pos);
            level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), Block.UPDATE_CLIENTS);
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static int hash(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    private BlockPos at(int dx, int dy, int dz) {
        return centre.offset(dx, dy, dz);
    }

    private void set(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private void setConnecting(BlockPos pos, BlockState state) {
        set(pos, state);
        needsShapeUpdate.add(pos.immutable());
    }

    private static double dist(int dx, int dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The horizontal direction pointing away from the arena centre. */
    private static Direction outward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private BlockState wallBrick(int dx, int dy, int dz) {
        if (dy == 1) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        return hash(dx * 7 + dy, dz, 3) % 6 == 0
                ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    }

    private static boolean isWall(double d) {
        return d >= WALL_INNER && d < WALL_OUTER;
    }

    // ---- parts ------------------------------------------------------------------------------

    private void clear() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
            for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
                if (dist(dx, dz) > CLEAR_RADIUS) {
                    continue;
                }
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++) {
                    set(at(dx, dy, dz), air);
                }
            }
        }
    }

    private void floor() {
        for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
            for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
                double d = dist(dx, dz);
                if (d > WALL_OUTER + 0.5) {
                    continue;
                }
                set(at(dx, -1, dz), Blocks.BEDROCK.defaultBlockState());
                set(at(dx, 0, dz), floorBlock(dx, dz, d));
            }
        }
        // Soul campfires at the foot of the hill, on the diagonals.
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + 90 * i);
            int dx = (int) Math.round(Math.cos(a) * 6.0);
            int dz = (int) Math.round(Math.sin(a) * 6.0);
            set(at(dx, 1, dz), Blocks.SOUL_CAMPFIRE.defaultBlockState()
                    .setValue(CampfireBlock.FACING, outward(dx, dz)));
        }
    }

    private BlockState floorBlock(int dx, int dz, double d) {
        if (d < HILL_RADIUS + 1.5) {
            return Blocks.SOUL_SOIL.defaultBlockState();
        }
        if (d >= WALL_INNER - 0.5) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        boolean path = (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) && d < WALL_INNER - 0.5;
        boolean ring = d >= 8.5 && d < 10.0;
        if (path || ring) {
            if (hash(dx, dz, 5) % 7 == 0) {
                return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            }
            return path && !ring ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                    : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        }
        if (hash(dx, dz, 9) % 9 == 0) {
            return Blocks.SOUL_SOIL.defaultBlockState();
        }
        return Blocks.BEDROCK.defaultBlockState();
    }

    private void wall() {
        for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
            for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
                double d = dist(dx, dz);
                if (isWall(d)) {
                    for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                        set(at(dx, dy, dz), wallBrick(dx, dy, dz));
                    }
                    if (d >= WALL_INNER + 1.0) {
                        battlement(dx, dz);
                    }
                } else if (d >= WALL_INNER - 1.0 && d < WALL_INNER) {
                    // Upside-down stairs under the wall top as an inward overhang.
                    Direction out = outward(dx, dz);
                    set(at(dx, WALL_HEIGHT, dz), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, out).setValue(StairBlock.HALF, Half.TOP));
                }
            }
        }
    }

    private void battlement(int dx, int dz) {
        int step = Math.floorMod((int) Math.floor(Math.atan2(dz, dx) / (2 * Math.PI) * 48), 2);
        BlockPos pos = at(dx, WALL_HEIGHT + 1, dz);
        if (step == 0) {
            setConnecting(pos, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
        } else {
            set(pos, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }
    }

    private void pillar(double angle) {
        int px = (int) Math.round(Math.cos(angle) * 16.0);
        int pz = (int) Math.round(Math.sin(angle) * 16.0);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (int dy = 1; dy <= PILLAR_HEIGHT; dy++) {
                    BlockState state = dy == 4 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                            : dy == PILLAR_HEIGHT ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                            : Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
                    set(at(px + ox, dy, pz + oz), state);
                }
                boolean corner = ox != 0 && oz != 0;
                BlockPos top = at(px + ox, PILLAR_HEIGHT + 1, pz + oz);
                if (corner) {
                    setConnecting(top, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
                } else if (ox == 0 && oz == 0) {
                    set(top, Blocks.SOUL_CAMPFIRE.defaultBlockState());
                }
            }
        }
        // A bracket on the inner face of the pillar with a soul lantern hanging from it.
        Direction in = outward(px, pz).getOpposite();
        BlockPos face = at(px, 0, pz).relative(in, 2);
        set(face.above(PILLAR_HEIGHT - 1), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, in.getOpposite()).setValue(StairBlock.HALF, Half.TOP));
        setConnecting(face.above(PILLAR_HEIGHT - 2), Blocks.CHAIN.defaultBlockState());
        set(face.above(PILLAR_HEIGHT - 3), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** Cells of the wall within about one block either side of a line from the centre at this angle. */
    private List<int[]> wallCellsAt(double angle, double halfWidth) {
        List<int[]> cells = new ArrayList<>();
        double cx = Math.cos(angle);
        double cz = Math.sin(angle);
        for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
            for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
                double d = dist(dx, dz);
                if (!isWall(d) || dx * cx + dz * cz <= 0) {
                    continue;
                }
                double side = Math.abs(-dx * cz + dz * cx);
                if (side <= halfWidth) {
                    cells.add(new int[]{dx, dz});
                }
            }
        }
        return cells;
    }

    private void window(double angle) {
        for (int[] c : wallCellsAt(angle, 1.0)) {
            int dx = c[0];
            int dz = c[1];
            boolean outer = dist(dx, dz) >= WALL_INNER + 1.0;
            set(at(dx, 1, dz), Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            set(at(dx, 4, dz), Blocks.POLISHED_BASALT.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, outward(dx, dz).getClockWise().getAxis()));
            for (int dy = 2; dy <= 3; dy++) {
                if (outer) {
                    setConnecting(at(dx, dy, dz), Blocks.IRON_BARS.defaultBlockState());
                } else {
                    set(at(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private void gate(double angle) {
        for (int[] c : wallCellsAt(angle, 2.6)) {
            int dx = c[0];
            int dz = c[1];
            double side = Math.abs(-dx * Math.sin(angle) + dz * Math.cos(angle));
            boolean outer = dist(dx, dz) >= WALL_INNER + 1.0;
            boolean jamb = side > 1.6;
            for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                BlockPos pos = at(dx, dy, dz);
                if (jamb) {
                    set(pos, dy == WALL_HEIGHT ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                            : Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                } else if (dy == WALL_HEIGHT) {
                    set(pos, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
                } else if (dy == WALL_HEIGHT - 1 && !outer) {
                    // Upside-down stairs round off the top of the doorway on the inside.
                    set(pos, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, outward(dx, dz)).setValue(StairBlock.HALF, Half.TOP));
                } else if (outer) {
                    setConnecting(pos, Blocks.NETHER_BRICK_FENCE.defaultBlockState());
                } else {
                    set(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private void hill() {
        int r = (int) Math.ceil(HILL_RADIUS) + 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int h = hillHeight(dx, dz);
                for (int dy = 1; dy <= h; dy++) {
                    set(at(dx, dy, dz), hillBlock(dx, dy, dz, h));
                }
                if (dx == 0 && dz == 0) {
                    continue;
                }
                double d = dist(dx, dz);
                if (h > 0) {
                    set(at(dx, h + 1, dz), skullOrCandle(dx, dz));
                    sideSkulls(dx, dz, h);
                } else if (d < HILL_RADIUS + 2.0 && hash(dx, dz, 21) % 5 == 0
                        && level.getBlockState(at(dx, 1, dz)).isAir()) {
                    // Skulls rolled off the hill onto the floor.
                    set(at(dx, 1, dz), skull(dx, dz));
                }
            }
        }
    }

    private BlockState hillBlock(int dx, int dy, int dz, int h) {
        int roll = hash(dx * 3 + dy, dz, 13) % 10;
        if (dy == 1 && roll < 4) {
            return Blocks.SOUL_SOIL.defaultBlockState();
        }
        Direction.Axis axis = roll % 3 == 0 ? Direction.Axis.X : roll % 3 == 1 ? Direction.Axis.Z : Direction.Axis.Y;
        return Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private BlockState skull(int dx, int dz) {
        Block block = hash(dx, dz, 17) % 5 == 0 ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL;
        return block.defaultBlockState().setValue(SkullBlock.ROTATION, hash(dx, dz, 19) % 16);
    }

    private BlockState skullOrCandle(int dx, int dz) {
        if (hash(dx, dz, 23) % 14 == 0) {
            return Blocks.RED_CANDLE.defaultBlockState()
                    .setValue(CandleBlock.CANDLES, 1 + hash(dx, dz, 29) % 3)
                    .setValue(CandleBlock.LIT, true);
        }
        return skull(dx, dz);
    }

    /** Wall skulls on the exposed sides of the steps, staring outwards. */
    private void sideSkulls(int dx, int dz, int h) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int nx = dx + dir.getStepX();
            int nz = dz + dir.getStepZ();
            int nh = hillHeight(nx, nz);
            if (dist(nx, nz) <= dist(dx, dz)) {
                continue;
            }
            for (int dy = nh + 2; dy <= h; dy++) {
                BlockPos pos = at(nx, dy, nz);
                if (level.getBlockState(pos).isAir() && hash(nx * 5 + dy, nz, 31) % 3 == 0) {
                    Block block = hash(nx, nz + dy, 37) % 5 == 0 ? Blocks.WITHER_SKELETON_WALL_SKULL
                            : Blocks.SKELETON_WALL_SKULL;
                    set(pos, block.defaultBlockState().setValue(WallSkullBlock.FACING, dir));
                }
            }
        }
    }

    /** "La crucea din mormant": a dark oak cross on the summit, its arms facing the gate. */
    private void cross() {
        int base = hillHeight(0, 0) + 1;
        BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        for (int dy = 0; dy < 4; dy++) {
            setConnecting(at(0, base + dy, 0), fence);
        }
        setConnecting(at(-1, base + 2, 0), fence);
        setConnecting(at(1, base + 2, 0), fence);
    }
}
