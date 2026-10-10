package com.ghostfreakmod.ghostfreak.entity.goal;

import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A wild Ghostfreak does not like light. In light it turns see-through and drifts, through walls
 * if need be, towards a darker spot, and it will not attack from a lit area.
 */
public class GhostfreakAvoidLightGoal extends Goal {
    private static final int CANDIDATES = 14;

    private final GhostfreakEntity mob;
    private Vec3 destination;
    private int repick;

    public GhostfreakAvoidLightGoal(GhostfreakEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !mob.isTame() && mob.isInLight();
    }

    @Override
    public boolean canContinueToUse() {
        return !mob.isTame() && mob.isInLight();
    }

    @Override
    public void start() {
        mob.setFleeingLight(true);
        pick();
    }

    @Override
    public void tick() {
        if (destination == null || --repick <= 0 || mob.distanceToSqr(destination) < 2.0D) {
            pick();
        }
        mob.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.0D);
    }

    @Override
    public void stop() {
        mob.setFleeingLight(false);
        destination = null;
    }

    /** Samples nearby spots and heads for the darkest. With none darker, it drifts off in a random direction. */
    private void pick() {
        repick = 40;
        BlockPos here = mob.blockPosition();
        int best = mob.lightAt();
        Vec3 choice = null;
        for (int i = 0; i < CANDIDATES; i++) {
            BlockPos pos = here.offset(mob.getRandom().nextInt(21) - 10, mob.getRandom().nextInt(9) - 4,
                    mob.getRandom().nextInt(21) - 10);
            int light = mob.level().getMaxLocalRawBrightness(pos);
            if (light < best && mob.level().getBlockState(pos).getCollisionShape(mob.level(), pos).isEmpty()) {
                best = light;
                choice = Vec3.atCenterOf(pos);
            }
        }
        if (choice == null) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
            choice = mob.position().add(Math.cos(angle) * 8.0D, mob.getRandom().nextInt(5) - 2, Math.sin(angle) * 8.0D);
        }
        destination = choice;
    }
}
