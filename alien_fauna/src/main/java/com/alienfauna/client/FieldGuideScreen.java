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
 * The Field Guide, as a two-page spread built from the vanilla book page (the left page is the same
 * texture mirrored). The index has an introduction on the left and one model button per creature on
 * the right. A creature's spread has its name, its model (with Baby and Ball buttons) and a short
 * description on the left, and its habitat, behaviour, how to befriend it and what it drops on the
 * right, carrying on over further spreads if the text is long.
 */
public class FieldGuideScreen extends Screen {
    private static final ResourceLocation BOOK = new ResourceLocation("textures/gui/book.png");
    private static final int PAGE = 192, TEXT_W = 114, LINES_PER_PAGE = 13;

    private final int initialEntry;
    private int left;
    private int entry = -1;                 // -1 is the index
    private int spread;
    private LivingEntity model;
    private boolean baby, ball;
    private List<List<FormattedCharSequence>> textPages = List.of();
    private List<FormattedCharSequence> description = List.of();
    private List<FormattedCharSequence> intro = List.of();

    public FieldGuideScreen(int initialEntry) {
        super(Component.translatable("item.alien_fauna.field_guide"));
        this.initialEntry = initialEntry;
    }

    @Override
    protected void init() {
        left = (width - 2 * PAGE) / 2;
        intro = font.split(Component.translatable("guide.alien_fauna.intro"), TEXT_W);
        if (initialEntry >= 0 && entry < 0 && initialEntry < GuideEntries.ALL.size()) {
            openEntry(initialEntry);
        } else {
            rebuild();
        }
    }

    private int totalSpreads() {
        return entry < 0 ? 1 : 1 + (textPages.size() <= 1 ? 0 : (textPages.size() - 1 + 1) / 2);
    }

    private void rebuild() {
        clearWidgets();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, 196, 200, 20).build());
        addRenderableWidget(new PageButton(left + 43, 159, false, b -> turn(-1), true)).visible = entry >= 0 || spread > 0;
        addRenderableWidget(new PageButton(left + PAGE + 116, 159, true, b -> turn(1), true)).visible = spread < totalSpreads() - 1;
        if (entry < 0) {
            for (int i = 0; i < GuideEntries.ALL.size(); i++) {
                int index = i;
                GuideEntry e = GuideEntries.ALL.get(i);
                LivingEntity icon = e.type().get().create(minecraft.level);
                addRenderableWidget(new EntityButton(left + PAGE + 38 + (i % 3) * 38, 34 + (i / 3) * 38, 34, icon,
                        Component.translatable(e.titleKey()), 14, b -> openEntry(index)));
            }
        } else if (spread == 0 && model != null) {
            int x = left + 40;
            if (model instanceof AgeableMob) {
                addRenderableWidget(Button.builder(Component.translatable(baby ? "guide.alien_fauna.adult" : "guide.alien_fauna.baby"), b -> {
                    baby = !baby;
                    applyModel();
                    rebuild();
                }).bounds(x, 108, 52, 14).build());
            }
            if (GuideEntries.ALL.get(entry).hasBallForm()) {
                addRenderableWidget(Button.builder(Component.translatable(ball ? "guide.alien_fauna.standing" : "guide.alien_fauna.ball"), b -> {
                    ball = !ball;
                    applyModel();
                    rebuild();
                }).bounds(x + 56, 108, 52, 14).build());
            }
        }
    }

    private void turn(int direction) {
        spread += direction;
        if (spread < 0) {
            entry = -1;
            spread = 0;
            model = null;
        } else if (spread >= totalSpreads()) {
            spread = totalSpreads() - 1;
        }
        rebuild();
    }

    private void openEntry(int index) {
        entry = index;
        spread = 0;
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

    private void drawLines(GuiGraphics graphics, List<FormattedCharSequence> lines, int x, int y) {
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 9, 0, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        // right page: the vanilla texture; left page: the same texture mirrored
        graphics.blit(BOOK, left + PAGE, 2, 0, 0, PAGE, PAGE);
        graphics.blit(BOOK, left, 2, PAGE, 192, PAGE, 0, -PAGE, PAGE, 256, 256);
        int leftText = left + (PAGE - 36 - TEXT_W), rightText = left + PAGE + 36;

        Component indicator = Component.translatable("book.pageIndicator", spread + 1, totalSpreads());
        graphics.drawString(font, indicator, left + 2 * PAGE - 44 - font.width(indicator), 18, 0, false);

        if (entry < 0) {
            Component title = Component.translatable("guide.alien_fauna.title").withStyle(ChatFormatting.BOLD);
            graphics.drawString(font, title, left + (PAGE - font.width(title)) / 2, 22, 0, false);
            drawLines(graphics, intro, leftText, 38);
        } else {
            if (spread == 0) {
                Component title = Component.translatable(GuideEntries.ALL.get(entry).titleKey()).withStyle(ChatFormatting.BOLD);
                graphics.drawString(font, title, left + (PAGE - font.width(title)) / 2, 22, 0, false);
                if (model != null) {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, left + 96, 100, 30, left + 96 - mouseX, 60 - mouseY, model);
                }
                drawLines(graphics, description.subList(0, Math.min(3, description.size())), leftText, 126);
                if (!textPages.isEmpty()) {
                    drawLines(graphics, textPages.get(0), rightText, 32);
                }
            } else {
                int leftIndex = 2 * spread - 1, rightIndex = 2 * spread;
                if (leftIndex < textPages.size()) {
                    drawLines(graphics, textPages.get(leftIndex), leftText, 32);
                }
                if (rightIndex < textPages.size()) {
                    drawLines(graphics, textPages.get(rightIndex), rightText, 32);
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
