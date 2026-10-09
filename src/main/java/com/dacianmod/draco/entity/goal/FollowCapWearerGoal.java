package com.dacianmod.draco.entity.goal;

import com.dacianmod.draco.entity.DracoEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Circles above a player wearing a Dacian felt cap, like a living standard, and sheds faster. */
public class FollowCapWearerGoal extends Goal {
    private static final double START_RANGE = 24.0D;
    private static final double STOP_RANGE = 32.0D;

    private final DracoEntity draco;
    private Player wearer;

    public FollowCapWearerGoal(DracoEntity draco) {
        this.draco = draco;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (draco.getTarget() != null || draco.isTame()) return false;
        Player best = null;
        double bestDistSq = START_RANGE * START_RANGE;
        for (Player player : draco.level().players()) {
            if (player.isSpectator() || !DracoEntity.isWearingCap(player)) continue;
            double distSq = draco.distanceToSqr(player);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = player;
            }
        }
        wearer = best;
        return wearer != null;
    }

    @Override
    public boolean canContinueToUse() {
        return draco.getTarget() == null
                && wearer != null
                && wearer.isAlive()
                && !wearer.isSpectator()
                && DracoEntity.isWearingCap(wearer)
                && draco.distanceToSqr(wearer) < STOP_RANGE * STOP_RANGE;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        wearer = null;
    }

    @Override
    public void tick() {
        if (wearer == null) return;
        draco.boostShedding();
        double angle = (draco.tickCount + draco.getId() * 40) * 0.04D;
        double bob = Math.sin(draco.tickCount * 0.08D) * 1.2D;
        Vec3 goal = wearer.position().add(Math.cos(angle) * 4.5D, 3.5D + bob, Math.sin(angle) * 4.5D);
        draco.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 0.9D);
        draco.getLookControl().setLookAt(wearer, 20.0F, 20.0F);
    }
}
