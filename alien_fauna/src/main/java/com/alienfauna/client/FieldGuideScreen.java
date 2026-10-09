package com.alienfauna.client;

import com.alienfauna.entity.CannonboltEntity;
import com.alienfauna.guide.GuideEntries;
import com.alienfauna.guide.GuideEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Field Guide: the vanilla book page, an index of creatures, and for each creature a first page
 * with its model (it follows the mouse, with buttons for the baby and the ball) and a short
 * description, then pages for its habitat, behaviour, how to befriend it and what it drops.
 */
public class FieldGuideScreen extends Screen {
    private static final ResourceLocation BOOK = new ResourceLocation("textures/gui/book.png");
    private static final int TEXT_X = 36, TEXT_W = 114, LINES_PER_PAGE = 13;

    private int left;
    private int entry = -1;                 // -1 is the index
    private int page;
    private LivingEntity model;
    private boolean baby, ball;
    private List<List<FormattedCharSequence>> textPages = List.of();
    private List<FormattedCharSequence> description = List.of();

    public FieldGuideScreen() {
        super(Component.translatable("item.alien_fauna.field_guide"));
    }

    @Override
    protected void init() {
        left = (width - 192) / 2;
        rebuild();
    }

    private int totalPages() {
        return entry < 0 ? 1 : 1 + textPages.size();
    }

    private void rebuild() {
        clearWidgets();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, 196, 200, 20).build());
        addRenderableWidget(new PageButton(left + 43, 159, false, b -> turn(-1), true)).visible = entry >= 0;
        addRenderableWidget(new PageButton(left + 116, 159, true, b -> turn(1), true)).visible = page < totalPages() - 1;
        if (entry < 0) {
            for (int i = 0; i < GuideEntries.ALL.size(); i++) {
                int index = i;
                addRenderableWidget(Button.builder(Component.translatable(GuideEntries.ALL.get(i).titleKey()), b -> openEntry(index))
                        .bounds(left + 39, 40 + i * 22, 110, 20).build());
            }
        } else if (page == 0 && model != null) {
            GuideEntry e = GuideEntries.ALL.get(entry);
            int x = left + 40;
            if (model instanceof AgeableMob) {
                addRenderableWidget(Button.builder(Component.translatable(baby ? "guide.alien_fauna.adult" : "guide.alien_fauna.baby"), b -> {
                    baby = !baby;
                    applyModel();
                    rebuild();
                }).bounds(x, 108, 52, 14).build());
            }
            if (e.hasBallForm()) {
                addRenderableWidget(Button.builder(Component.translatable(ball ? "guide.alien_fauna.standing" : "guide.alien_fauna.ball"), b -> {
                    ball = !ball;
                    applyModel();
                    rebuild();
                }).bounds(x + 56, 108, 52, 14).build());
            }
        }
    }

    private void turn(int direction) {
        page += direction;
        if (page < 0) {
            entry = -1;
            page = 0;
            model = null;
        } else if (page >= totalPages()) {
            page = totalPages() - 1;
        }
        rebuild();
    }

    private void openEntry(int index) {
        entry = index;
        page = 0;
        baby = false;
        ball = false;
        GuideEntry e = GuideEntries.ALL.get(index);
        model = e.type().get().create(minecraft.level);
        applyModel();
        description = font.split(Component.translatable(e.textKey(e.sections().get(0))), TEXT_W);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int s = 1; s < e.sections().size(); s++) {
            String section = e.sections().get(s);
            lines.add(Component.translatable("guide.alien_fauna.heading." + section).withStyle(ChatFormatting.BOLD).getVisualOrderText());
            lines.addAll(font.split(Component.translatable(e.textKey(section)), TEXT_W));
            lines.add(FormattedCharSequence.EMPTY);
        }
        textPages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += LINES_PER_PAGE) {
            textPages.add(lines.subList(i, Math.min(lines.size(), i + LINES_PER_PAGE)));
        }
        rebuild();
    }

    private void applyModel() {
        if (model instanceof AgeableMob mob) {
            mob.setBaby(baby);
        }
        if (model instanceof CannonboltEntity cannonbolt) {
            cannonbolt.setDisplayBall(ball);
        }
    }

    @Override
    public void tick() {
        if (model != null) {
            model.tickCount++;
            if (model instanceof CannonboltEntity cannonbolt) {
                cannonbolt.tickDisplay();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.blit(BOOK, left, 2, 0, 0, 192, 192);
        Component pageNumber = Component.translatable("book.pageIndicator", page + 1, totalPages());
        graphics.drawString(font, pageNumber, left + 192 - 44 - font.width(pageNumber), 18, 0, false);
        if (entry < 0) {
            Component title = Component.translatable("guide.alien_fauna.title");
            graphics.drawString(font, title, left + (192 - font.width(title)) / 2, 22, 0, false);
        } else if (page == 0) {
            Component title = Component.translatable(GuideEntries.ALL.get(entry).titleKey());
            graphics.drawString(font, title, left + (192 - font.width(title)) / 2, 32, 0, false);
            if (model != null) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, left + 96, 100, 30, left + 96 - mouseX, 60 - mouseY, model);
            }
            for (int i = 0; i < Math.min(3, description.size()); i++) {
                graphics.drawString(font, description.get(i), left + TEXT_X, 126 + i * 9, 0, false);
            }
        } else {
            List<FormattedCharSequence> lines = textPages.get(page - 1);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), left + TEXT_X, 32 + i * 9, 0, false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
