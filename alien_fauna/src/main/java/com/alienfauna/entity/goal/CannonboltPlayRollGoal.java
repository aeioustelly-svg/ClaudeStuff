package com.alienfauna.entity.goal;

import com.alienfauna.entity.CannonboltEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Wild and tame Cannonbolts now and then curl up and roll somewhere for the fun of it. It hurts
 * nothing. Afterwards the Cannonbolt is briefly dizzy, then not curious about rolling again for
 * a good while.
 */
public class CannonboltPlayRollGoal extends Goal {
    private final CannonboltEntity mob;
    private int ticks;

    public CannonboltPlayRollGoal(CannonboltEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null || mob.isInSittingPose() || mob.isDizzy() || mob.isBaby()
                || mob.getRollCooldown() > 0 || !mob.onGround() || mob.isInWaterOrBubble()
                || mob.isLeashed() || mob.isPassenger()) {
            return false;
        }
        return mob.getRandom().nextInt(reducedTickDelay(240)) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null && !mob.isInSittingPose() && !mob.getNavigation().isDone() && ticks < 160;
    }

    @Override
    public void start() {
        ticks = 0;
        Vec3 pos = LandRandomPos.getPos(mob, 16, 4);
        if (pos == null) {
            mob.setRollCooldown(100);
            return;
        }
        mob.startRolling();
        mob.getNavigation().moveTo(pos.x, pos.y, pos.z, 2.2D);
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        if (mob.isRolling()) {
            mob.stopRolling(40);
        }
        mob.setRollCooldown(600 + mob.getRandom().nextInt(600));
    }
}
