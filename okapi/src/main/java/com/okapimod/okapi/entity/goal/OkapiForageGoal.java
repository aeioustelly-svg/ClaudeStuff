package com.okapimod.okapi.entity.goal;

import com.okapimod.okapi.entity.OkapiEntity;
import com.okapimod.okapi.entity.OkapiForaging;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Reaches for food with the tongue. A tamed okapi walks to ripe crops and leaves within its range,
 * harvests them into its pack and carries on. A wild okapi only trims a leaf that is already within
 * reach, for show.
 */
public class OkapiForageGoal extends Goal {
    private static final int SEARCH_RADIUS = 8;
    private static final int WILD_RADIUS = 2;
    private static final int GIVE_UP_TICKS = 240;
    /** Ticks from the tongue coming out to the bite. */
    private static final int BITE_TICK = 14;
    private static final int FINISH_TICK = 26;
    private static final double OWNER_LEASH = 24.0D;

    private final OkapiEntity okapi;
    private final Set<Long> unreachable = new HashSet<>();
    private long forgetUnreachableAt;
    private BlockPos target;
    private int walkTicks;
    private int reachTicks;
    private boolean bitten;

    public OkapiForageGoal(OkapiEntity okapi) {
        this.okapi = okapi;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (okapi.isBaby() || okapi.isInSittingPose() || okapi.isOrderedToSit() || okapi.getForageCooldown() > 0
                || okapi.isInWaterOrBubble()) {
            return false;
        }
        if (okapi.tickCount > forgetUnreachableAt) {
            unreachable.clear();
            forgetUnreachableAt = okapi.tickCount + 3600L;
        }
        boolean working = okapi.isTame();
        if (working) {
            LivingEntity owner = okapi.getOwner();
            if (owner != null && okapi.distanceToSqr(owner) > OWNER_LEASH * OWNER_LEASH) {
                return false;
            }
            if (!okapi.packHasRoom()) {
                return false;
            }
        }
        BlockPos found = OkapiForaging.findTarget(okapi.level(), okapi.blockPosition(),
                working ? SEARCH_RADIUS : WILD_RADIUS, working, true, pos -> unreachable.contains(pos.asLong()));
        if (found == null || (!working && !OkapiForaging.inReach(okapi, found))) {
            okapi.setForageCooldown(working ? 100 : 400 + okapi.getRandom().nextInt(600));
            return false;
        }
        this.target = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && !okapi.isInSittingPose() && okapi.isAlive();
    }

    @Override
    public void start() {
        walkTicks = 0;
        reachTicks = 0;
        bitten = false;
    }

    @Override
    public void stop() {
        okapi.setTongueOut(false);
        okapi.getNavigation().stop();
        target = null;
    }

    @Override
    public void tick() {
        if (target == null || !(okapi.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(target);
        if (OkapiForaging.kindOf(state) == null) {
            giveUp(false);
            return;
        }

        Vec3 centre = Vec3.atCenterOf(target);
        if (!OkapiForaging.inReach(okapi, target)) {
            okapi.setTongueOut(false);
            if (reachTicks > 0 || !okapi.isTame() || ++walkTicks > GIVE_UP_TICKS) {
                giveUp(true);
                return;
            }
            if (walkTicks % 20 == 1) {
                okapi.getNavigation().moveTo(centre.x, centre.y, centre.z, 1.0D);
            }
            return;
        }

        okapi.getNavigation().stop();
        okapi.getLookControl().setLookAt(centre.x, centre.y, centre.z, 40.0F, 40.0F);
        reachTicks++;
        if (reachTicks == 1) {
            okapi.setTongueOut(true);
        }
        if (reachTicks == BITE_TICK && !bitten) {
            bitten = true;
            bite(level, state);
        }
        if (reachTicks >= FINISH_TICK) {
            okapi.setTongueOut(false);
            okapi.setForageCooldown(okapi.isTame() ? 60 + okapi.getRandom().nextInt(100)
                    : 600 + okapi.getRandom().nextInt(600));
            target = null;
        }
    }

    private void bite(ServerLevel level, BlockState state) {
        okapi.playSound(SoundEvents.GENERIC_EAT, 0.5F, 0.7F + okapi.getRandom().nextFloat() * 0.2F);
        if (!okapi.isTame()) {
            OkapiForaging.nibble(level, target, state);
            return;
        }
        List<ItemStack> loot = OkapiForaging.harvest(level, target, state, okapi.getRandom());
        for (ItemStack stack : loot) {
            okapi.stash(stack);
        }
    }

    private void giveUp(boolean remember) {
        if (remember && target != null) {
            unreachable.add(target.asLong());
        }
        okapi.setForageCooldown(40);
        okapi.setTongueOut(false);
        target = null;
    }
}
