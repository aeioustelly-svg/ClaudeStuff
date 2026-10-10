package com.aceliada.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * A bone that shoots out of the floor, Sans style. Works like evoker fangs: after a warm-up shown by
 * bone dust on the floor it rises, hurts whatever touches it while it stands, and sinks again.
 */
public class BoneSpikeEntity extends Entity implements TraceableEntity {
    public static final int LIFE_TICKS = 22;
    public static final float DAMAGE = 5.0F;
    private static final byte EVENT_RISE = 4;

    private int warmupDelayTicks;
    private int lifeTicks = LIFE_TICKS;
    private boolean sentRiseEvent;
    private boolean clientRiseStarted;
    @Nullable
    private LivingEntity owner;
    @Nullable
    private UUID ownerUUID;
    private final Set<Integer> hit = new HashSet<>();

    public BoneSpikeEntity(EntityType<? extends BoneSpikeEntity> type, Level level) {
        super(type, level);
    }

    public void setup(double x, double y, double z, float yRot, int warmupDelay, @Nullable LivingEntity owner) {
        this.setPos(x, y, z);
        this.setYRot(yRot);
        this.warmupDelayTicks = warmupDelay;
        this.owner = owner;
        this.ownerUUID = owner == null ? null : owner.getUUID();
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        if (owner == null && ownerUUID != null && level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(ownerUUID) instanceof LivingEntity living) {
            owner = living;
        }
        return owner;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (clientRiseStarted && --lifeTicks < 0) {
                lifeTicks = 0;
            }
            return;
        }
        if (--warmupDelayTicks >= 0) {
            if (warmupDelayTicks % 3 == 0) {
                ((ServerLevel) level()).sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                        getX(), getY() + 0.05, getZ(), 3, 0.2, 0.0, 0.2, 0.0);
            }
            return;
        }
        if (!sentRiseEvent) {
            level().broadcastEntityEvent(this, EVENT_RISE);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_HURT, getSoundSource(), 0.6F,
                    1.4F + random.nextFloat() * 0.3F);
            sentRiseEvent = true;
        }
        // Dangerous while standing: after the first two ticks of rising and before it starts sinking.
        if (lifeTicks <= LIFE_TICKS - 2 && lifeTicks > 4) {
            for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.15, 0.0, 0.15))) {
                hurtOnce(victim);
            }
        }
        if (--lifeTicks < 0) {
            discard();
        }
    }

    private void hurtOnce(LivingEntity victim) {
        LivingEntity source = getOwner();
        if (!victim.isAlive() || victim.isInvulnerable() || victim == source || !hit.add(victim.getId())) {
            return;
        }
        if (source != null && source.isAlliedTo(victim)) {
            return;
        }
        DamageSource damage = source == null ? damageSources().magic() : damageSources().indirectMagic(this, source);
        victim.hurt(damage, DAMAGE);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_RISE) {
            clientRiseStarted = true;
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** How far the bone is out of the floor, 0 to 1. Quick to rise, quick to sink. */
    public float getRise(float partialTicks) {
        if (!clientRiseStarted) {
            return 0.0F;
        }
        float elapsed = LIFE_TICKS - lifeTicks + partialTicks;
        float rising = Math.min(1.0F, elapsed / 3.0F);
        float sinking = Math.min(1.0F, Math.max(0.0F, (lifeTicks - partialTicks) / 4.0F));
        return Math.min(rising, sinking);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        warmupDelayTicks = tag.getInt("Warmup");
        if (tag.hasUUID("Owner")) {
            ownerUUID = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Warmup", warmupDelayTicks);
        if (ownerUUID != null) {
            tag.putUUID("Owner", ownerUUID);
        }
    }
}
