package com.alienfauna.entity.goal;

import com.alienfauna.entity.GnoblarEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** While a cake party lasts the gnoblar stays where it is and dances (the dance is the model's pose). */
public class GnoblarPartyGoal extends Goal {
    private final GnoblarEntity gnoblar;

    public GnoblarPartyGoal(GnoblarEntity gnoblar) {
        this.gnoblar = gnoblar;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return gnoblar.isPartying() && !gnoblar.isPassenger() && !gnoblar.isScared();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        gnoblar.getNavigation().stop();
    }
}
