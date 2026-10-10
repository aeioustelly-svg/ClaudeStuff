package com.ghostfreakmod.ghostfreak.entity.goal;

import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Closes in on the target in a straight line, phasing through anything in the way, and lashes at
 * it with the tentacles from just over three blocks away.
 */
public class GhostfreakAttackGoal extends Goal {
    private static final double REACH_SQR = 3.4D * 3.4D;

    private final GhostfreakEntity mob;
    private int cooldown;

    public GhostfreakAttackGoal(GhostfreakEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return valid(mob.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return valid(mob.getTarget());
    }

    private boolean valid(LivingEntity target) {
        return target != null && target.isAlive() && !mob.isCalm() && !mob.isOrderedToSit()
                && (mob.isTame() || !mob.isInLight());
    }

    @Override
    public void start() {
        cooldown = 10;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;                                   // the selector can tick a goal once more after it has ended
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (mob.distanceToSqr(target) > REACH_SQR * 0.5D) {
            mob.getMoveControl().setWantedPosition(target.getX(), target.getY(0.5D), target.getZ(), 1.3D);
        }
        if (cooldown > 0) {
            cooldown--;
        } else if (mob.distanceToSqr(target) <= REACH_SQR) {
            mob.doHurtTarget(target);
            cooldown = 20;
        }
    }

    @Override
    public void stop() {
        cooldown = 0;
    }
}
