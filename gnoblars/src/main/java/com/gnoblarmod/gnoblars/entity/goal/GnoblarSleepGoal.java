package com.gnoblarmod.gnoblars.entity.goal;

import com.gnoblarmod.gnoblars.entity.GnoblarEntity;
import com.gnoblarmod.gnoblars.entity.GnoblarMode;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * At night a gnoblar that is not following anybody looks for a gnoblar bed (a hay block with a carpet on top)
 * within ten blocks, climbs on and curls up until morning. Camps have beds, so the camp gnoblars sleep too.
 */
public class GnoblarSleepGoal extends Goal {
    private static final int RANGE = 10;
    /** A bed it cannot reach (behind a wall, across water) is given up after this long, and not tried again for a minute. */
    private static final int GIVE_UP_TICKS = 300;

    private final GnoblarEntity gnoblar;
    private BlockPos bed;
    private boolean asleep;
    private int elapsed;

    public GnoblarSleepGoal(GnoblarEntity gnoblar) {
        this.gnoblar = gnoblar;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    public static boolean isNight(Level level) {
        return isNight(level.getDayTime());
    }

    public static boolean isNight(long dayTime) {
        long time = dayTime % 24000L;
        return time >= 13000L && time < 23000L;
    }

    /**
     * A hay block with a carpet on top. There is no need to check for headroom: a gnoblar lying on the carpet is under 2 blocks
     * above the hay, so even the ridge of a tent just over it leaves it room.
     */
    public static boolean isBed(Level level, BlockPos hay) {
        return level.getBlockState(hay).is(Blocks.HAY_BLOCK) && level.getBlockState(hay.above()).is(BlockTags.WOOL_CARPETS);
    }

    private boolean taken(BlockPos hay) {
        return !gnoblar.level().getEntitiesOfClass(GnoblarEntity.class, new net.minecraft.world.phys.AABB(hay.above()).inflate(0.4D),
                other -> other != gnoblar && other.isNapping()).isEmpty();
    }

    @Override
    public boolean canUse() {
        if (!gnoblar.canLookForABed() || !isNight(gnoblar.level()) || gnoblar.isPassenger() || gnoblar.isScared() || gnoblar.isPartying()
                || gnoblar.getMode() == GnoblarMode.SIT || (gnoblar.isTame() && gnoblar.getMode() == GnoblarMode.FOLLOW)
                || gnoblar.getRandom().nextInt(60) != 0) {
            return false;
        }
        BlockPos origin = gnoblar.blockPosition();
        double best = Double.MAX_VALUE;
        bed = null;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-RANGE, -3, -RANGE), origin.offset(RANGE, 3, RANGE))) {
            if (isBed(gnoblar.level(), pos) && !taken(pos)) {
                double distance = pos.distSqr(origin);
                if (distance < best) {
                    best = distance;
                    bed = pos.immutable();
                }
            }
        }
        return bed != null;
    }

    @Override
    public boolean canContinueToUse() {
        return bed != null && isNight(gnoblar.level()) && isBed(gnoblar.level(), bed)
                && (!asleep || gnoblar.isNapping()) && !gnoblar.isScared() && (asleep || elapsed < GIVE_UP_TICKS);
    }

    @Override
    public void start() {
        asleep = false;
        elapsed = 0;
        gnoblar.getNavigation().moveTo(bed.getX() + 0.5D, bed.getY() + 1.0D, bed.getZ() + 0.5D, 0.9D);
    }

    @Override
    public void stop() {
        if (!asleep && elapsed >= GIVE_UP_TICKS) {
            gnoblar.setNapCooldown(1200);   // it could not reach the bed: leave it alone for a minute
        }
        gnoblar.setNapping(false);
        gnoblar.getNavigation().stop();
        asleep = false;
        bed = null;
    }

    @Override
    public void tick() {
        // The selector can tick a goal once more after it ended itself, so tolerate a lost bed.
        if (bed == null) {
            return;
        }
        double x = bed.getX() + 0.5D;
        double z = bed.getZ() + 0.5D;
        elapsed++;
        if (!asleep) {
            double dx = gnoblar.getX() - x;
            double dz = gnoblar.getZ() - z;
            if (dx * dx + dz * dz < 0.8D && Math.abs(gnoblar.getY() - (bed.getY() + 1.0D)) < 1.5D) {
                asleep = true;
                gnoblar.getNavigation().stop();
                gnoblar.setNapping(true);
            } else if (gnoblar.getNavigation().isDone()) {
                gnoblar.getNavigation().moveTo(x, bed.getY() + 1.0D, z, 0.9D);
            }
        }
        if (asleep) {
            gnoblar.setPos(x, bed.getY() + 1.0625D, z);   // on top of the carpet
            gnoblar.setDeltaMovement(0.0D, 0.0D, 0.0D);
            if (gnoblar.tickCount % 50 == 0 && gnoblar.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CLOUD, x, bed.getY() + 1.6D, z, 2, 0.1D, 0.05D, 0.1D, 0.0D);
            }
        }
    }
}
