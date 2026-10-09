package com.alienfauna.entity.goal;

import com.alienfauna.entity.GnoblarEntity;
import com.alienfauna.entity.GnoblarMode;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cake is a party: any gnoblar that smells one within a few blocks wanders over and takes a bite. Everyone near the cake
 * then cheers and dances for ten seconds. The bite wears the cake down like any other, so it is gone after seven.
 */
public class GnoblarCakeGoal extends Goal {
    private static final int RANGE = 8;
    /** A cake it cannot reach is given up after this long. */
    private static final int GIVE_UP_TICKS = 200;

    private final GnoblarEntity gnoblar;
    private BlockPos cake;
    private int elapsed;

    public GnoblarCakeGoal(GnoblarEntity gnoblar) {
        this.gnoblar = gnoblar;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!gnoblar.canEatCake() || gnoblar.isPassenger() || gnoblar.isNapping() || gnoblar.isScared()
                || gnoblar.getMode() == GnoblarMode.SIT || gnoblar.getRandom().nextInt(40) != 0) {
            return false;
        }
        BlockPos origin = gnoblar.blockPosition();
        double best = Double.MAX_VALUE;
        cake = null;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-RANGE, -2, -RANGE), origin.offset(RANGE, 2, RANGE))) {
            if (gnoblar.level().getBlockState(pos).is(Blocks.CAKE)) {
                double distance = pos.distSqr(origin);
                if (distance < best) {
                    best = distance;
                    cake = pos.immutable();
                }
            }
        }
        return cake != null;
    }

    @Override
    public boolean canContinueToUse() {
        return cake != null && gnoblar.level().getBlockState(cake).is(Blocks.CAKE) && !gnoblar.isPassenger()
                && !gnoblar.isScared() && elapsed < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        elapsed = 0;
        gnoblar.getNavigation().moveTo(cake.getX() + 0.5D, cake.getY(), cake.getZ() + 0.5D, 1.1D);
    }

    @Override
    public void stop() {
        if (cake != null) {
            gnoblar.setCakeCooldown(200);   // gave up on a cake it could not reach
        }
        cake = null;
        gnoblar.getNavigation().stop();
    }

    @Override
    public void tick() {
        // The selector can tick a goal once more after it ended itself, so tolerate a lost cake.
        if (cake == null) {
            return;
        }
        elapsed++;
        gnoblar.getLookControl().setLookAt(cake.getX() + 0.5D, cake.getY() + 0.3D, cake.getZ() + 0.5D);
        double dx = gnoblar.getX() - (cake.getX() + 0.5D);
        double dz = gnoblar.getZ() - (cake.getZ() + 0.5D);
        if (dx * dx + dz * dz < 2.2D && Math.abs(gnoblar.getY() - cake.getY()) < 2.0D) {
            BlockPos eaten = cake;
            cake = null;                      // ends the goal on the next canContinueToUse check
            biteCake(eaten);
        } else if (gnoblar.getNavigation().isDone()) {
            gnoblar.getNavigation().moveTo(cake.getX() + 0.5D, cake.getY(), cake.getZ() + 0.5D, 1.1D);
        }
    }

    private void biteCake(BlockPos pos) {
        BlockState state = gnoblar.level().getBlockState(pos);
        if (!state.is(Blocks.CAKE)) {
            return;
        }
        int bites = state.getValue(CakeBlock.BITES);
        if (bites < 6) {
            gnoblar.level().setBlock(pos, state.setValue(CakeBlock.BITES, bites + 1), 3);
        } else {
            gnoblar.level().removeBlock(pos, false);
        }
        gnoblar.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.4F);
        gnoblar.setCakeCooldown(100);
        gnoblar.celebrateCake(pos);
    }
}
