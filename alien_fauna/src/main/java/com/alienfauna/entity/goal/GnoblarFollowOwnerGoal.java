package com.alienfauna.entity.goal;

import com.alienfauna.entity.GnoblarEntity;
import com.alienfauna.entity.GnoblarMode;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;

/** Only follows while the owner has it in follow mode; in wander mode it stays around where it was told to. */
public class GnoblarFollowOwnerGoal extends FollowOwnerGoal {
    private final GnoblarEntity gnoblar;

    public GnoblarFollowOwnerGoal(GnoblarEntity gnoblar, double speed, float startDistance, float stopDistance) {
        super(gnoblar, speed, startDistance, stopDistance, false);
        this.gnoblar = gnoblar;
    }

    @Override
    public boolean canUse() {
        return gnoblar.getMode() == GnoblarMode.FOLLOW && !gnoblar.isPassenger() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return gnoblar.getMode() == GnoblarMode.FOLLOW && !gnoblar.isPassenger() && super.canContinueToUse();
    }
}
