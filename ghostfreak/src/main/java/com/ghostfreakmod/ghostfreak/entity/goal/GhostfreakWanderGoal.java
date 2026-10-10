package com.ghostfreakmod.ghostfreak.entity.goal;

import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Drifts between random spots. Wild ones favour darker spots. Tamed ones only do it in wander
 * mode, and stay put in follow mode when the owner is near.
 */
public class GhostfreakWanderGoal extends Goal {
    private final GhostfreakEntity mob;
    private Vec3 destination;
    private int ticks;

    public GhostfreakWanderGoal(GhostfreakEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.isTame() && mob.getMode() != GhostfreakEntity.MODE_WANDER) {
            return false;
        }
        if (mob.getRandom().nextInt(30) != 0) {
            return false;
        }
        destination = pick();
        return destination != null;
    }

    @Override
    public boolean canContinueToUse() {
        return destination != null && ticks < 200 && mob.distanceToSqr(destination) > 2.0D
                && !(mob.isTame() && mob.getMode() != GhostfreakEntity.MODE_WANDER);
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        ticks++;
        if (destination != null) {
            mob.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 0.7D);
        }
    }

    @Override
    public void stop() {
        destination = null;
    }

    private Vec3 pick() {
        BlockPos here = mob.blockPosition();
        int best = Integer.MAX_VALUE;
        Vec3 choice = null;
        for (int i = 0; i < 6; i++) {
            BlockPos pos = here.offset(mob.getRandom().nextInt(17) - 8, mob.getRandom().nextInt(7) - 3,
                    mob.getRandom().nextInt(17) - 8);
            if (!mob.level().getBlockState(pos).getCollisionShape(mob.level(), pos).isEmpty()) {
                continue;
            }
            int light = mob.isTame() ? 0 : mob.level().getMaxLocalRawBrightness(pos);
            if (light < best) {
                best = light;
                choice = Vec3.atCenterOf(pos);
            }
        }
        return choice;
    }
}
