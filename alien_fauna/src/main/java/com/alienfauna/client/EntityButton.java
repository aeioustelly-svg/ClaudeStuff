package com.alienfauna.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** A square button that shows a small model of a creature, with its name as the tooltip. */
public class EntityButton extends Button {
    private final LivingEntity entity;
    private final int scale;

    public EntityButton(int x, int y, int size, LivingEntity entity, Component name, int scale, OnPress onPress) {
        super(x, y, size, size, name, onPress, DEFAULT_NARRATION);
        this.entity = entity;
        this.scale = scale;
        setTooltip(Tooltip.create(name));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int border = isHoveredOrFocused() ? 0xFF3C3C3C : 0xFF8B7B5B;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFFD9C9A0);
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, getX() + width / 2, getY() + height - 4, scale, 0.0F, 0.0F, entity);
    }
}
