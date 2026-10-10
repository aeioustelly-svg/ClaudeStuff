package com.ghostfreakmod.ghostfreak.entity.goal;

import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Follow mode: stays within a few blocks of the owner, passing through walls to do it. */
public class GhostfreakFollowOwnerGoal extends Goal {
    private static final double START_SQR = 6.0D * 6.0D;
    private static final double STOP_SQR = 3.0D * 3.0D;
    private static final double TELEPORT_SQR = 40.0D * 40.0D;

    private final GhostfreakEntity mob;

    public GhostfreakFollowOwnerGoal(GhostfreakEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = mob.getOwner();
        return mob.isTame() && mob.getMode() == GhostfreakEntity.MODE_FOLLOW && owner != null
                && !owner.isSpectator() && mob.distanceToSqr(owner) > START_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity owner = mob.getOwner();
        return mob.isTame() && mob.getMode() == GhostfreakEntity.MODE_FOLLOW && owner != null
                && mob.distanceToSqr(owner) > STOP_SQR;
    }

    @Override
    public void tick() {
        LivingEntity owner = mob.getOwner();
        if (owner == null) {
            return;
        }
        if (mob.distanceToSqr(owner) > TELEPORT_SQR) {
            mob.moveTo(owner.getX() + (mob.getRandom().nextDouble() - 0.5D) * 2.0D, owner.getY() + 1.0D,
                    owner.getZ() + (mob.getRandom().nextDouble() - 0.5D) * 2.0D);
            return;
        }
        mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());
        mob.getMoveControl().setWantedPosition(owner.getX(), owner.getY() + 1.2D, owner.getZ(), 1.2D);
    }
}
