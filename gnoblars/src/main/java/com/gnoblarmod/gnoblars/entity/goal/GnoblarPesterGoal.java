package com.gnoblarmod.gnoblars.entity.goal;

import com.gnoblarmod.gnoblars.config.GnoblarConfig;
import com.gnoblarmod.gnoblars.entity.GnoblarEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Wild gnoblars trot after a nearby player and squeak at them. Annoying, never harmful: they do not
 * push, steal or hit. After a while they get distracted and leave the player alone for a bit.
 */
public class GnoblarPesterGoal extends Goal {
    private static final double NOTICE_RANGE = 12.0D;

    private final GnoblarEntity gnoblar;
    private Player player;
    private int timer;

    public GnoblarPesterGoal(GnoblarEntity gnoblar) {
        this.gnoblar = gnoblar;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!GnoblarConfig.pestering() || gnoblar.isTame() || !gnoblar.canPester()
                || gnoblar.getRandom().nextInt(40) != 0) {
            return false;
        }
        player = gnoblar.level().getNearestPlayer(gnoblar, NOTICE_RANGE);
        return player != null;
    }

    @Override
    public boolean canContinueToUse() {
        return timer > 0 && player != null && player.isAlive() && !player.isSpectator()
                && gnoblar.distanceToSqr(player) < 16.0D * 16.0D;
    }

    @Override
    public void start() {
        timer = 200 + gnoblar.getRandom().nextInt(200);
    }

    @Override
    public void stop() {
        player = null;
        gnoblar.getNavigation().stop();
        gnoblar.setPesterCooldown(300 + gnoblar.getRandom().nextInt(600));
    }

    @Override
    public void tick() {
        if (player == null) {
            return;
        }
        timer--;
        gnoblar.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (gnoblar.distanceToSqr(player) > 2.5D * 2.5D) {
            if (gnoblar.tickCount % 5 == 0) {
                gnoblar.getNavigation().moveTo(player, 1.15D);
            }
        } else {
            gnoblar.getNavigation().stop();
            if (timer % 60 == 0) {
                gnoblar.squeak();
            }
        }
    }
}
