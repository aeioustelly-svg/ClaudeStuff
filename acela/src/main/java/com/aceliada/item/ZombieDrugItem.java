package com.aceliada.item;

import com.aceliada.registry.ModEffects;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Drogul Zombie: a syringe dropped by Acela. Hold use to inject it. Two minutes of strength, speed,
 * resistance, regeneration, night vision and fire resistance, during which undead mobs leave the user
 * alone. The first seconds hit hard: nausea and hunger.
 */
public class ZombieDrugItem extends Item {
    public static final int DURATION = 20 * 120;
    private static final int USE_TICKS = 24;

    public ZombieDrugItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!level.isClientSide) {
            applyEffects(user);
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE,
                    SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        if (user instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        }
        if (user instanceof Player player) {
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    public static void applyEffects(LivingEntity user) {
        user.addEffect(new MobEffectInstance(ModEffects.DROGUL_ZOMBIE.get(), DURATION, 0));
        user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 1));
        user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 1));
        user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 1));
        user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION / 4, 1));
        user.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION, 0));
        user.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, DURATION, 0));
        user.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 8, 0));
        user.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 15, 2));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.DARK_GREEN));
    }
}
