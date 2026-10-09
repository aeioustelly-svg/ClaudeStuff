package com.dacianmod.draco.entity.goal;

import com.dacianmod.draco.entity.DracoEntity;
import java.util.EnumSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Climbs above the target, dives at it, then hovers briefly (taking extra damage) to recover. */
public class DracoDiveGoal extends Goal {
    private enum Phase { CLIMB, DIVE, RECOVER }

    private static final double DIVE_SPEED = 1.0D;

    private final DracoEntity draco;
    private Phase phase;
    private int ticks;

    public DracoDiveGoal(DracoEntity draco) {
        this.draco = draco;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = draco.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (draco.getDiveCooldown() > 0 || draco.isHowling() || draco.isLashing()) return false;
        double distSq = draco.distanceToSqr(target);
        return distSq > 25.0D && distSq < 28.0D * 28.0D && draco.getRandom().nextInt(10) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = draco.getTarget();
        return phase != null && target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        phase = Phase.CLIMB;
        ticks = 0;
    }

    @Override
    public void stop() {
        draco.setDiving(false);
        draco.setRecovering(false);
        if (draco.getDiveCooldown() <= 0) {
            draco.setDiveCooldown(100);
        }
        phase = null;
    }

    @Override
    public void tick() {
        LivingEntity target = draco.getTarget();
        if (target == null) return;
        ticks++;

        switch (phase) {
            case CLIMB -> {
                draco.getLookControl().setLookAt(target, 30.0F, 30.0F);
                draco.getMoveControl().setWantedPosition(target.getX(), target.getY() + 10.0D, target.getZ(), 1.0D);
                if (draco.getY() >= target.getY() + 6.0D || ticks > 60) {
                    phase = Phase.DIVE;
                    ticks = 0;
                    draco.setDiving(true);
                    draco.playSound(SoundEvents.PHANTOM_SWOOP, 1.5F, 0.8F);
                    draco.playSound(SoundEvents.WOLF_HOWL, 1.5F, 0.9F);
                }
            }
            case DIVE -> {
                Vec3 direction = target.getEyePosition().subtract(draco.position()).normalize();
                draco.setDeltaMovement(direction.scale(DIVE_SPEED));
                draco.hasImpulse = true;
                float yaw = (float) (Mth.atan2(direction.z, direction.x) * (180.0D / Math.PI)) - 90.0F;
                draco.setYRot(yaw);
                draco.yBodyRot = yaw;
                draco.yHeadRot = yaw;

                if (draco.getBoundingBox().inflate(0.6D).intersects(target.getBoundingBox())) {
                    float damage = (float) draco.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5F;
                    if (target.hurt(draco.damageSources().mobAttack(draco), damage)) {
                        target.knockback(0.8D, -direction.x, -direction.z);
                    }
                    beginRecovery(true);
                } else if (ticks > 30 || draco.horizontalCollision || draco.verticalCollision) {
                    beginRecovery(false);
                }
            }
            case RECOVER -> {
                draco.getMoveControl().setWantedPosition(draco.getX(), draco.getY() + 3.0D, draco.getZ(), 0.5D);
                if (ticks >= 30) {
                    draco.setRecovering(false);
                    phase = null;
                }
            }
        }
    }

    private void beginRecovery(boolean hit) {
        phase = Phase.RECOVER;
        ticks = 0;
        draco.setDiving(false);
        draco.setRecovering(true);
        draco.setDiveCooldown((hit ? 160 : 100) + draco.getRandom().nextInt(60));
    }
}
