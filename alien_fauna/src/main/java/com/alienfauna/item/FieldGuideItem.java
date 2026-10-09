package com.alienfauna.item;

import com.alienfauna.client.GuideClient;
import com.alienfauna.guide.GuideEntries;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.TooltipFlag;
import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** The book that describes every creature of the mod. Using it opens the guide. */
public class FieldGuideItem extends Item {
    public FieldGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GuideClient.open(-1));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Using the book on a creature opens the guide at that creature's page. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        int index = GuideEntries.indexOf(target.getType());
        if (index < 0) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GuideClient.open(index));
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.alien_fauna.field_guide.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
