package com.aceliada.client;

import com.aceliada.entity.AcelaEntity;
import com.aceliada.network.AcelaNetwork;
import com.aceliada.network.ChooseOptionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * An Undertale-style text box: black with a white border, the question typed out letter by letter
 * with a low blip, then four answers picked with the arrow keys and Enter (or 1 to 4, or the mouse).
 * The world stays visible behind it, so the blinded player still sees Acela glowing.
 */
public class DialogueScreen extends Screen {
    private static final int BOX_HEIGHT = 92;
    private static final int LINE = 14;
    private static final int WHITE = 0xFFFFFF;
    private static final int YELLOW = 0xFFFF40;
    private static final int RED = 0xFF2020;

    private final int entityId;
    private final String question = "* " + AcelaEntity.QUESTION;
    private int revealed;
    private int selected;

    public DialogueScreen(int entityId) {
        super(Component.literal("Acela"));
        this.entityId = entityId;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private boolean fullyTyped() {
        return revealed >= question.length();
    }

    @Override
    public void tick() {
        if (fullyTyped()) {
            return;
        }
        char c = question.charAt(revealed++);
        if (!Character.isWhitespace(c) && minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.55F, 0.7F));
        }
    }

    private int boxWidth() {
        return Math.min(width - 32, 340);
    }

    private int boxLeft() {
        return (width - boxWidth()) / 2;
    }

    private int boxTop() {
        return height - BOX_HEIGHT - 16;
    }

    private int optionTop(int index) {
        return boxTop() + 30 + index * LINE;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x0 = boxLeft();
        int y0 = boxTop();
        int w = boxWidth();
        graphics.fill(x0 - 3, y0 - 3, x0 + w + 3, y0 + BOX_HEIGHT + 3, 0xFFFFFFFF);
        graphics.fill(x0, y0, x0 + w, y0 + BOX_HEIGHT, 0xFF000000);
        graphics.drawString(font, question.substring(0, Math.min(revealed, question.length())), x0 + 10, y0 + 10, WHITE, false);
        if (!fullyTyped()) {
            return;
        }
        int hovered = optionAt(mouseX, mouseY);
        if (hovered >= 0) {
            selected = hovered;
        }
        for (int i = 0; i < AcelaEntity.OPTIONS.size(); i++) {
            int y = optionTop(i);
            boolean on = i == selected;
            if (on) {
                graphics.drawString(font, "♥", x0 + 14, y, RED, false);
            }
            graphics.drawString(font, (i + 1) + ". " + AcelaEntity.OPTIONS.get(i), x0 + 26, y, on ? YELLOW : WHITE, false);
        }
    }

    private int optionAt(double mouseX, double mouseY) {
        if (!fullyTyped() || mouseX < boxLeft() || mouseX > boxLeft() + boxWidth()) {
            return -1;
        }
        for (int i = 0; i < AcelaEntity.OPTIONS.size(); i++) {
            int y = optionTop(i);
            if (mouseY >= y - 2 && mouseY < y + LINE - 2) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!fullyTyped()) {
            revealed = question.length();
            return true;
        }
        int count = AcelaEntity.OPTIONS.size();
        switch (key) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> selected = (selected + count - 1) % count;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> selected = (selected + 1) % count;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_Z -> choose(selected);
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4 -> choose(key - GLFW.GLFW_KEY_1);
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        if (minecraft != null && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN
                || key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_S)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.3F));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!fullyTyped()) {
            revealed = question.length();
            return true;
        }
        int option = optionAt(mouseX, mouseY);
        if (option >= 0) {
            choose(option);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void choose(int option) {
        AcelaNetwork.CHANNEL.sendToServer(new ChooseOptionPacket(entityId, option));
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.5F));
            minecraft.setScreen(null);
        }
    }
}
