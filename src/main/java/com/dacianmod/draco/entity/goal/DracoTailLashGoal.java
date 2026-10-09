package com.dacianmod.draco.entity.goal;

import com.dacianmod.draco.entity.DracoEntity;
import java.util.EnumSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A close-range sweep of the cloth tail that hurts and briefly blinds. */
public class DracoTailLashGoal extends Goal {
    private static final double REACH = 3.5D;
    private static final int HIT_TICK = 8;
    private static final int DURATION = 16;

    private final DracoEntity draco;
    private int ticks;

    public DracoTailLashGoal(DracoEntity draco) {
        this.draco = draco;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = draco.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (draco.getLashCooldown() > 0 || draco.isDiving() || draco.isHowling() || draco.isRecovering()) return false;
        return draco.distanceToSqr(target) < 4.5D * 4.5D;
    }

    @Override
    public boolean canContinueToUse() {
        return ticks < DURATION;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        ticks = 0;
        draco.setLashing(true);
        draco.getNavigation().stop();
    }

    @Override
    public void stop() {
        draco.setLashing(false);
        draco.setLashCooldown(60);
        ticks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = draco.getTarget();
        if (target != null) {
            draco.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        ticks++;
        if (ticks == HIT_TICK) {
            sweep();
        }
    }

    private void sweep() {
        float damage = (float) draco.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7F;
        draco.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.7F);
        for (LivingEntity entity : draco.level().getEntitiesOfClass(LivingEntity.class,
                draco.getBoundingBox().inflate(REACH), e -> e != draco)) {
            if (entity instanceof DracoEntity || entity instanceof Wolf) continue;
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            if (entity.hurt(draco.damageSources().mobAttack(draco), damage)) {
                Vec3 away = entity.position().subtract(draco.position()).normalize();
                entity.knockback(0.6D, -away.x, -away.z);
                entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), draco);
            }
        }
    }
}
