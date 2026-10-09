package com.alienfauna.entity.goal;

import com.alienfauna.entity.CannonboltEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Curls into a ball and rolls at the target, hitting it hard. Then it stands dizzy for a few
 * seconds (and takes extra damage), which is what keeps a fight with a Cannonbolt fair.
 * It only ever has a target when something hurt it or its owner.
 */
public class CannonboltRollAttackGoal extends Goal {
    private static final int WIND_UP = 2;
    private static final int GIVE_UP = 120;

    private final CannonboltEntity mob;
    private int ticks;
    private boolean hit;
    private boolean finished;

    public CannonboltRollAttackGoal(CannonboltEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || mob.isBaby() || mob.isDizzy() || mob.isInSittingPose()
                || mob.getRollCooldown() > 0 || !mob.onGround()) {
            return false;
        }
        double distance = mob.distanceToSqr(target);
        return distance > 9.0D && distance < 24.0D * 24.0D && mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return !finished && target != null && target.isAlive() && ticks <= GIVE_UP && !mob.isInSittingPose();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        ticks = 0;
        hit = false;
        finished = false;
        mob.startRolling();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        mob.stopRolling(hit ? 60 : 40);
        mob.setRollCooldown(80 + mob.getRandom().nextInt(60));
    }

    @Override
    public void tick() {
        // The selector may tick a goal once more after it ended itself.
        LivingEntity target = mob.getTarget();
        if (finished || target == null) {
            return;
        }
        ticks++;
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (ticks <= WIND_UP) {
            mob.getNavigation().stop();
            return;
        }
        if (ticks % 4 == 0 || mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(target, 2.6D);
        }
        double reach = mob.getBbWidth() / 2.0D + target.getBbWidth() / 2.0D + 0.6D;
        if (mob.distanceToSqr(target) <= reach * reach) {
            hit = mob.rollImpact(target);
            finished = true;
        }
    }
}
