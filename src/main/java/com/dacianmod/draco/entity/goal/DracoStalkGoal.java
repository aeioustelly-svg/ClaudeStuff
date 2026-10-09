package com.dacianmod.draco.entity.goal;

import com.dacianmod.draco.entity.DracoEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Fallback while no attack is running: circle the target at range and above it. Keeping that
 * distance is what lets the howl (4 to 14 blocks) and the dive (more than 5 blocks) trigger.
 */
public class DracoStalkGoal extends Goal {
    private static final double RADIUS = 9.0D;

    private final DracoEntity draco;

    public DracoStalkGoal(DracoEntity draco) {
        this.draco = draco;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = draco.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = draco.getTarget();
        if (target == null) return;
        draco.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double angle = (draco.tickCount + draco.getId() * 30) * 0.05D;
        double height = 4.0D + Math.sin(draco.tickCount * 0.1D);
        draco.getMoveControl().setWantedPosition(
                target.getX() + Math.cos(angle) * RADIUS,
                target.getY() + height,
                target.getZ() + Math.sin(angle) * RADIUS,
                1.0D);
    }
}
