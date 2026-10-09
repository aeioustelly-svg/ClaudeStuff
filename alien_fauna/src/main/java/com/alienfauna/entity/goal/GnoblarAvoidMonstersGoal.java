package com.alienfauna.entity.goal;

import com.alienfauna.entity.GnoblarEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Monster;

/** Gnoblars are cowards: they run from every hostile mob and wave their arms about while doing it. */
public class GnoblarAvoidMonstersGoal extends AvoidEntityGoal<Monster> {
    private final GnoblarEntity gnoblar;

    public GnoblarAvoidMonstersGoal(GnoblarEntity gnoblar) {
        super(gnoblar, Monster.class, 8.0F, 1.0D, 1.35D);
        this.gnoblar = gnoblar;
    }

    @Override
    public void start() {
        super.start();
        gnoblar.setScared(true);
    }

    @Override
    public void stop() {
        super.stop();
        gnoblar.setScared(false);
    }
}
