package com.alienfauna.entity.goal;

import com.alienfauna.config.GnoblarConfig;
import com.alienfauna.entity.GnoblarEntity;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Wild gnoblars pick up one item from any dropped stack and carry it around. The item is not lost:
 * a gift makes the gnoblar hand it back, it drops it when bored, and it drops it if it dies.
 */
public class GnoblarScavengeGoal extends Goal {
    private static final double RANGE = 8.0D;

    private final GnoblarEntity gnoblar;
    private ItemEntity target;

    public GnoblarScavengeGoal(GnoblarEntity gnoblar) {
        this.gnoblar = gnoblar;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!GnoblarConfig.scavenging() || gnoblar.isTame() || gnoblar.isHoarding()
                || gnoblar.getRandom().nextInt(10) != 0) {
            return false;
        }
        List<ItemEntity> items = gnoblar.level().getEntitiesOfClass(ItemEntity.class,
                gnoblar.getBoundingBox().inflate(RANGE, 2.0D, RANGE),
                item -> item.isAlive() && !item.hasPickUpDelay() && !item.getItem().isEmpty());
        target = items.stream()
                .min(Comparator.comparingDouble(gnoblar::distanceToSqr))
                .orElse(null);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && !gnoblar.isHoarding();
    }

    @Override
    public void start() {
        gnoblar.getNavigation().moveTo(target, 1.1D);
    }

    @Override
    public void stop() {
        target = null;
        gnoblar.getNavigation().stop();
    }

    @Override
    public void tick() {
        // The selector can tick a goal once more after it ended itself, so tolerate a lost target.
        if (target == null || !target.isAlive()) {
            return;
        }
        gnoblar.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (gnoblar.distanceToSqr(target) < 1.5D) {
            ItemStack stack = target.getItem();
            ItemStack one = stack.split(1);
            if (stack.isEmpty()) {
                target.discard();
            } else {
                target.setItem(stack);
            }
            gnoblar.hoard(one);
            target = null;
        } else if (gnoblar.tickCount % 10 == 0) {
            gnoblar.getNavigation().moveTo(target, 1.1D);
        }
    }
}
