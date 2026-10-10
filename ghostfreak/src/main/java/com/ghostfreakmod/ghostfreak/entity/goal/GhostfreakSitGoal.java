package com.ghostfreakmod.ghostfreak.entity.goal;

import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Sitting for a flier: it hovers in place and does nothing else until told otherwise. */
public class GhostfreakSitGoal extends Goal {
    private final GhostfreakEntity mob;

    public GhostfreakSitGoal(GhostfreakEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return mob.isTame() && mob.isOrderedToSit();
    }

    @Override
    public boolean canContinueToUse() {
        return mob.isTame() && mob.isOrderedToSit();
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }
}
