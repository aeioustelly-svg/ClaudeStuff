package com.aceliada.entity.goal;

import com.aceliada.entity.AcelaEntity;
import com.aceliada.entity.BoneSpikeEntity;
import com.aceliada.registry.ModEntities;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Acela's special attacks, one at a time with a cooldown in between:
 * <ul>
 * <li>bone wave: a row of bones bursting out of the floor towards the target (three rows below half health)</li>
 * <li>bone cage: a ring of bones around the target with one in the middle, so standing still hurts</li>
 * <li>gravity slam: the target is lifted into the air and thrown down onto waiting bones</li>
 * <li>smoke blast: he draws on the pipe, aims, and blows a line of smoke that hurts and blinds</li>
 * </ul>
 */
public class AcelaSpecialAttackGoal extends Goal {
    public enum Attack {
        BONE_WAVE(30), BONE_CAGE(36), GRAVITY_SLAM(44), SMOKE_BLAST(40);

        final int duration;

        Attack(int duration) {
            this.duration = duration;
        }
    }

    public static final float BLAST_DAMAGE = 10.0F;
    private static final double MAX_RANGE = 24.0;
    private static final int BLAST_AIM_TICK = 12;
    private static final int BLAST_FIRE_TICK = 26;
    private static final int SLAM_DROP_TICK = 16;

    private final AcelaEntity acela;
    @Nullable
    private Attack current;
    @Nullable
    private Attack last;
    @Nullable
    private Attack forced;
    @Nullable
    private LivingEntity target;
    private int tick;
    private Vec3 aim = Vec3.ZERO;

    public AcelaSpecialAttackGoal(AcelaEntity acela) {
        this.acela = acela;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** For tests: the next attack will be this one. */
    public void force(Attack attack) {
        this.forced = attack;
    }

    @Override
    public boolean canUse() {
        LivingEntity t = acela.getTarget();
        return acela.isFighting() && acela.getSpecialCooldown() <= 0 && t != null && t.isAlive()
                && acela.distanceToSqr(t) < MAX_RANGE * MAX_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return current != null && tick < current.duration && acela.isFighting() && target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        target = acela.getTarget();
        current = forced != null ? forced : pick();
        forced = null;
        last = current;
        tick = 0;
        acela.getNavigation().stop();
    }

    private Attack pick() {
        Attack[] all = Attack.values();
        Attack choice;
        do {
            choice = all[acela.getRandom().nextInt(all.length)];
        } while (choice == last);
        return choice;
    }

    @Override
    public void stop() {
        if (target != null && current == Attack.GRAVITY_SLAM) {
            target.removeEffect(MobEffects.LEVITATION);
        }
        current = null;
        target = null;
        boolean tired = acela.getHealth() < acela.getMaxHealth() * 0.5F;
        acela.setSpecialCooldown((tired ? 40 : 70) + acela.getRandom().nextInt(50));
    }

    @Override
    public void tick() {
        // The goal selector may tick a goal once more after it decided to end; do nothing then.
        if (current == null || target == null) {
            return;
        }
        acela.getLookControl().setLookAt(target, 30.0F, 30.0F);
        switch (current) {
            case BONE_WAVE -> boneWave();
            case BONE_CAGE -> boneCage();
            case GRAVITY_SLAM -> gravitySlam();
            case SMOKE_BLAST -> smokeBlast();
        }
        tick++;
    }

    // ---- attacks ----------------------------------------------------------------------------

    private void boneWave() {
        if (tick != 4) {
            return;
        }
        acela.swing(InteractionHand.MAIN_HAND);
        double base = Mth.atan2(target.getZ() - acela.getZ(), target.getX() - acela.getX());
        boolean tired = acela.getHealth() < acela.getMaxHealth() * 0.5F;
        double[] offsets = tired ? new double[]{-0.35, 0.0, 0.35} : new double[]{0.0};
        for (double offset : offsets) {
            double angle = base + offset;
            for (int i = 0; i < 16; i++) {
                double d = 1.5 + i * 1.2;
                spike(acela.getX() + Math.cos(angle) * d, acela.getZ() + Math.sin(angle) * d,
                        target.getY(), angle, 4 + i);
            }
        }
    }

    private void boneCage() {
        if (tick != 2) {
            return;
        }
        acela.swing(InteractionHand.MAIN_HAND);
        Vec3 c = target.position();
        spike(c.x, c.z, c.y, 0.0, 16);
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI * 2.0 * i / 10.0;
            spike(c.x + Math.cos(angle) * 1.6, c.z + Math.sin(angle) * 1.6, c.y, angle, 16);
        }
    }

    private void gravitySlam() {
        if (tick == 0) {
            acela.swing(InteractionHand.MAIN_HAND);
            target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, SLAM_DROP_TICK, 9, false, false));
            acela.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR,
                    acela.getSoundSource(), 1.0F, 0.6F);
        } else if (tick == SLAM_DROP_TICK) {
            target.removeEffect(MobEffects.LEVITATION);
            target.setDeltaMovement(0.0, -2.5, 0.0);
            target.hurtMarked = true;
            Vec3 c = target.position();
            double floor = groundBelow(c.x, c.y, c.z, 16);
            if (Double.isNaN(floor)) {
                return;
            }
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2.0 * i / 8.0;
                spike(c.x + Math.cos(angle) * 1.2, c.z + Math.sin(angle) * 1.2, floor, angle, 4);
            }
        }
    }

    private void smokeBlast() {
        if (!(acela.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 mouth = acela.getEyePosition().add(acela.getLookAngle().scale(0.6));
        if (tick == 0) {
            level.playSound(null, acela.getX(), acela.getY(), acela.getZ(), SoundEvents.FIRECHARGE_USE,
                    acela.getSoundSource(), 1.0F, 0.5F);
        }
        if (tick < BLAST_FIRE_TICK) {
            int puffs = 1 + tick / 6;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, mouth.x, mouth.y, mouth.z, puffs, 0.15, 0.15, 0.15, 0.01);
        }
        if (tick == BLAST_AIM_TICK) {
            aim = target.getEyePosition().subtract(mouth).normalize();
        }
        if (tick > BLAST_AIM_TICK && tick < BLAST_FIRE_TICK && tick % 2 == 0) {
            for (double d = 1.0; d < MAX_RANGE; d += 1.5) {
                Vec3 p = mouth.add(aim.scale(d));
                level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (tick == BLAST_FIRE_TICK) {
            fireBlast(level, mouth);
        }
    }

    private void fireBlast(ServerLevel level, Vec3 mouth) {
        level.playSound(null, acela.getX(), acela.getY(), acela.getZ(), SoundEvents.BLAZE_SHOOT,
                acela.getSoundSource(), 1.5F, 0.5F);
        Vec3 end = mouth.add(aim.scale(MAX_RANGE));
        for (double d = 0.5; d < MAX_RANGE; d += 0.5) {
            Vec3 p = mouth.add(aim.scale(d));
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.005);
        }
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(mouth, end).inflate(1.5))) {
            if (victim == acela || !victim.isAlive()) {
                continue;
            }
            if (distanceToSegment(victim.getBoundingBox().getCenter(), mouth, end) < 1.3) {
                victim.hurt(acela.damageSources().mobAttack(acela), BLAST_DAMAGE);
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0));
                victim.knockback(0.8, -aim.x, -aim.z);
            }
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / ab.lengthSqr(), 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** Spawns a bone on the first floor at or below {@code y} (searching a few blocks), if inside the arena. */
    private void spike(double x, double z, double y, double angle, int warmup) {
        Vec3 pos = new Vec3(x, y, z);
        if (!acela.insideArena(pos)) {
            return;
        }
        double floor = groundBelow(x, y + 1.0, z, 6);
        if (Double.isNaN(floor)) {
            return;
        }
        BoneSpikeEntity spike = ModEntities.BONE_SPIKE.get().create(acela.level());
        if (spike == null) {
            return;
        }
        spike.setup(x, floor, z, (float) (angle * Mth.RAD_TO_DEG), warmup, acela);
        acela.level().addFreshEntity(spike);
    }

    /** Top of the first solid block at or below y, within {@code range} blocks, or NaN. */
    private double groundBelow(double x, double y, double z, int range) {
        Level level = acela.level();
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < range; i++) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    && !level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) {
                return pos.getY();
            }
            pos.move(0, -1, 0);
        }
        return Double.NaN;
    }
}
