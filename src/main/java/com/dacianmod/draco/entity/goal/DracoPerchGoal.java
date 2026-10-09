package com.dacianmod.draco.entity.goal;

import com.dacianmod.draco.entity.DracoEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Sitting for a flier: glide down to the ground, then settle into the coiled perch pose.
 * Gravity is switched back on while perched and off again when the Draco is released.
 */
public class DracoPerchGoal extends Goal {
    private final DracoEntity draco;

    public DracoPerchGoal(DracoEntity draco) {
        this.draco = draco;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return draco.isTame() && draco.isOrderedToSit() && !draco.isInWaterOrBubble();
    }

    @Override
    public boolean canContinueToUse() {
        return draco.isOrderedToSit();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        draco.getNavigation().stop();
        draco.setTarget(null);
        draco.setNoGravity(false);
    }

    @Override
    public void stop() {
        draco.setInSittingPose(false);
        draco.setNoGravity(true);
    }

    @Override
    public void tick() {
        draco.setNoGravity(false);
        Vec3 motion = draco.getDeltaMovement();
        if (draco.onGround()) {
            draco.setInSittingPose(true);
            draco.setDeltaMovement(0.0D, motion.y, 0.0D);
        } else {
            // sink gently instead of dropping like a stone
            draco.setDeltaMovement(motion.x * 0.8D, Math.max(motion.y, -0.25D), motion.z * 0.8D);
        }
    }
}
