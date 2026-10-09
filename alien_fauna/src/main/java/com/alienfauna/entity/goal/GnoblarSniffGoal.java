package com.alienfauna.entity.goal;

import com.alienfauna.entity.GnoblarEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A friend wanders to nearby soft ground, sniffs about with its big nose and digs up a scrap of
 * something useful (see data/alien_fauna/loot_tables/gameplay/gnoblar_sniffing.json).
 */
public class GnoblarSniffGoal extends Goal {
    private static final int SNIFF_TICKS = 60;
    private static final int GIVE_UP_TICKS = 240;

    private final GnoblarEntity gnoblar;
    private BlockPos spot;
    private int sniffed;
    private int elapsed;

    public GnoblarSniffGoal(GnoblarEntity gnoblar) {
        this.gnoblar = gnoblar;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean diggable(BlockPos pos) {
        BlockState state = gnoblar.level().getBlockState(pos);
        boolean soft = state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY);
        return soft && gnoblar.level().getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean canUse() {
        if (!gnoblar.isTame() || gnoblar.isPassenger() || gnoblar.isOrderedToSit() || gnoblar.getSniffCooldown() > 0
                || !gnoblar.onGround() || gnoblar.getRandom().nextInt(80) != 0) {
            return false;
        }
        BlockPos origin = gnoblar.blockPosition();
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos pos = origin.offset(gnoblar.getRandom().nextInt(9) - 4,
                    gnoblar.getRandom().nextInt(3) - 2, gnoblar.getRandom().nextInt(9) - 4);
            if (diggable(pos)) {
                spot = pos;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && !gnoblar.isOrderedToSit() && elapsed < GIVE_UP_TICKS && diggable(spot);
    }

    @Override
    public void start() {
        elapsed = 0;
        sniffed = 0;
        gnoblar.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY() + 1.0D, spot.getZ() + 0.5D, 1.0D);
    }

    @Override
    public void stop() {
        gnoblar.setSniffing(false);
        gnoblar.getNavigation().stop();
        spot = null;
    }

    @Override
    public void tick() {
        // The selector can tick a goal once more after it ended itself, so tolerate a lost spot.
        if (spot == null) {
            return;
        }
        elapsed++;
        gnoblar.getLookControl().setLookAt(spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D);
        double dx = gnoblar.getX() - (spot.getX() + 0.5D);
        double dz = gnoblar.getZ() - (spot.getZ() + 0.5D);
        if (dx * dx + dz * dz > 1.6D) {
            if (gnoblar.getNavigation().isDone()) {
                gnoblar.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY() + 1.0D, spot.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        gnoblar.getNavigation().stop();
        gnoblar.setSniffing(true);
        sniffed++;
        if (sniffed % 10 == 0 && gnoblar.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(spot)),
                    spot.getX() + 0.5D, spot.getY() + 1.05D, spot.getZ() + 0.5D, 6, 0.2D, 0.05D, 0.2D, 0.02D);
        }
        if (sniffed >= SNIFF_TICKS) {
            gnoblar.finishSniff();
            gnoblar.setSniffing(false);
            spot = null;   // ends the goal on the next canContinueToUse check
        }
    }
}
