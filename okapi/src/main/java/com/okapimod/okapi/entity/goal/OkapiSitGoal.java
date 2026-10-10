package com.okapimod.okapi.entity.goal;

import com.okapimod.okapi.entity.OkapiEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Sits while ordered to. Unlike the vanilla sit goal it does not also sit a tamed animal whose owner
 * cannot be found, so a companion keeps working when its owner is out of reach, and the goal can be
 * tested headlessly where the mock players are not part of the level.
 */
public class OkapiSitGoal extends Goal {
    private final OkapiEntity okapi;

    public OkapiSitGoal(OkapiEntity okapi) {
        this.okapi = okapi;
        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    public boolean canContinueToUse() {
        return okapi.isOrderedToSit();
    }

    @Override
    public boolean canUse() {
        return okapi.isTame() && !okapi.isInWaterOrBubble() && okapi.onGround() && okapi.isOrderedToSit();
    }

    @Override
    public void start() {
        okapi.getNavigation().stop();
        okapi.setInSittingPose(true);
    }

    @Override
    public void stop() {
        okapi.setInSittingPose(false);
    }
}
