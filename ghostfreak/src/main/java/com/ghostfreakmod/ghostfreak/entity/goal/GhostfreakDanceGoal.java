package com.ghostfreakmod.ghostfreak.entity.goal;

import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** While music plays it holds still and dances, and forgets whoever it was after. */
public class GhostfreakDanceGoal extends Goal {
    private final GhostfreakEntity mob;

    public GhostfreakDanceGoal(GhostfreakEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return mob.isDancing();
    }

    @Override
    public boolean canContinueToUse() {
        return mob.isDancing();
    }

    @Override
    public void start() {
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        mob.setTarget(null);
    }
}
