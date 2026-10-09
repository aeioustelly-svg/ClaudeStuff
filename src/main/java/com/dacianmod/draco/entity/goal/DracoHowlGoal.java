package com.dacianmod.draco.entity.goal;

import com.dacianmod.draco.entity.DracoEntity;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A ranged howl: a forward cone that slows and weakens everything caught in it. */
public class DracoHowlGoal extends Goal {
    private static final double RANGE = 14.0D;
    private static final double CONE_COS = Math.cos(Math.toRadians(40.0D));
    private static final int WIND_UP = 15;
    private static final int DURATION = 30;

    private final DracoEntity draco;
    private int ticks;

    public DracoHowlGoal(DracoEntity draco) {
        this.draco = draco;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = draco.getTarget();
        if (target == null || !target.isAlive() || draco.isOrderedToSit()) return false;
        if (draco.getHowlCooldown() > 0 || draco.isDiving() || draco.isLashing() || draco.isRecovering()) return false;
        double distSq = draco.distanceToSqr(target);
        return distSq > 16.0D && distSq < RANGE * RANGE
                && draco.hasLineOfSight(target)
                && draco.getRandom().nextInt(15) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return ticks < DURATION && draco.getTarget() != null && draco.getTarget().isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        ticks = 0;
        draco.setHowling(true);
        draco.getNavigation().stop();
        draco.playSound(SoundEvents.WOLF_HOWL, 2.5F, 0.55F);
    }

    @Override
    public void stop() {
        draco.setHowling(false);
        draco.setHowlCooldown(300);
        ticks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = draco.getTarget();
        if (target == null) return;
        ticks++;
        draco.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (ticks == WIND_UP) {
            release(target);
        }
    }

    private void release(LivingEntity target) {
        Vec3 origin = draco.getEyePosition();
        Vec3 direction = target.getEyePosition().subtract(origin).normalize();

        for (LivingEntity entity : draco.level().getEntitiesOfClass(LivingEntity.class,
                draco.getBoundingBox().inflate(RANGE), e -> e != draco)) {
            if (entity instanceof DracoEntity || entity instanceof Wolf || draco.isAlliedTo(entity)) continue;
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            Vec3 toEntity = entity.getEyePosition().subtract(origin);
            if (toEntity.length() > RANGE || toEntity.normalize().dot(direction) < CONE_COS) continue;
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), draco);
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), draco);
        }

        if (draco.level() instanceof ServerLevel server) {
            for (double d = 2.0D; d < RANGE; d += 3.0D) {
                Vec3 p = origin.add(direction.scale(d));
                server.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }
}
