package com.okapimod.okapi.entity.goal;

import com.okapimod.okapi.entity.OkapiEntity;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Brings the haul home: into the nearest barrel (the okapi's basket) or, when there is none, to the
 * owner. Starts when the pack is getting full or nothing new has been picked for a while.
 */
public class OkapiDeliverGoal extends Goal {
    private static final int BARREL_RADIUS = 16;
    private static final int IDLE_TICKS = 400;
    private static final int GIVE_UP_TICKS = 400;
    private static final double ARRIVAL_SQR = 2.6D * 2.6D;

    private final OkapiEntity okapi;
    @Nullable private BlockPos barrel;
    @Nullable private LivingEntity owner;
    private int ticks;
    private boolean done;

    public OkapiDeliverGoal(OkapiEntity okapi) {
        this.okapi = okapi;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (!okapi.isTame() || okapi.isBaby() || okapi.isInSittingPose() || okapi.isOrderedToSit()
                || okapi.getDeliverCooldown() > 0 || okapi.getPack().isEmpty()) {
            return false;
        }
        boolean due = okapi.packCount() >= OkapiEntity.DELIVER_AT || !okapi.packHasRoom()
                || okapi.getTicksSinceHarvest() > IDLE_TICKS;
        if (!due) {
            return false;
        }
        barrel = okapi.level().getGameTime() < okapi.getBarrelBlockedUntil() ? null : findBarrel();
        owner = null;
        if (barrel == null) {
            LivingEntity candidate = okapi.getOwner();
            if (candidate != null && okapi.distanceToSqr(candidate) < 48.0D * 48.0D) {
                owner = candidate;
            }
        }
        if (barrel == null && owner == null) {
            okapi.setDeliverCooldown(200);
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !done && (barrel != null || owner != null) && !okapi.isInSittingPose() && okapi.isAlive();
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
    }

    @Override
    public void stop() {
        okapi.getNavigation().stop();
        barrel = null;
        owner = null;
    }

    @Override
    public void tick() {
        if (done || (barrel == null && owner == null)) {
            return;
        }
        Vec3 destination = barrel != null ? Vec3.atCenterOf(barrel) : owner.position();
        if (++ticks > GIVE_UP_TICKS) {
            okapi.setDeliverCooldown(300);
            done = true;
            return;
        }
        okapi.getLookControl().setLookAt(destination.x, destination.y + 0.5D, destination.z);
        if (okapi.distanceToSqr(destination.x, destination.y, destination.z) > ARRIVAL_SQR) {
            if (ticks % 10 == 1) {
                okapi.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0D);
            }
            return;
        }
        okapi.getNavigation().stop();
        if (barrel != null) {
            putInBarrel();
        } else {
            okapi.handPackTo(owner);
        }
        okapi.setTicksSinceHarvest(0);
        okapi.setDeliverCooldown(100);
        done = true;
    }

    private void putInBarrel() {
        BlockEntity entity = okapi.level().getBlockEntity(barrel);
        if (!(entity instanceof BarrelBlockEntity container)) {
            okapi.setBarrelBlockedUntil(okapi.level().getGameTime() + 1200L);
            return;
        }
        boolean leftovers = false;
        List<ItemStack> items = okapi.getPack().removeAllItems();
        for (ItemStack stack : items) {
            ItemStack rest = HopperBlockEntity.addItem(null, container, stack, null);
            if (!rest.isEmpty()) {
                okapi.getPack().addItem(rest);
                leftovers = true;
            }
        }
        okapi.playSound(SoundEvents.ITEM_PICKUP, 0.5F, 0.9F);
        if (leftovers) {
            // The barrel is full: carry on to the owner next time.
            okapi.setBarrelBlockedUntil(okapi.level().getGameTime() + 1200L);
        }
    }

    @Nullable
    private BlockPos findBarrel() {
        Level level = okapi.level();
        BlockPos centre = okapi.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -BARREL_RADIUS; dx <= BARREL_RADIUS; dx++) {
                for (int dz = -BARREL_RADIUS; dz <= BARREL_RADIUS; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.isLoaded(cursor) || !level.getBlockState(cursor).is(Blocks.BARREL)) {
                        continue;
                    }
                    double distance = cursor.distSqr(centre);
                    if (distance < bestDistance) {
                        best = cursor.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }
}
