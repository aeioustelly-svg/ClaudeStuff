package com.pixelclouds.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.pixelclouds.PixelClouds;
import com.pixelclouds.cloud.Cloud;
import com.pixelclouds.cloud.CloudField;
import com.pixelclouds.cloud.CloudImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * One texture holding every visible cloud, one 128x64 slot each. A slot is repainted only when
 * its cloud's shape, the sky palette or the cloud's fade step changes, and only that slot is
 * uploaded.
 *
 * <p>Fading is an ordered 4x4 dither baked into the slot, so clouds dissolve pixel by pixel
 * instead of turning translucent.
 */
final class CloudAtlas implements AutoCloseable {
    static final int SLOTS_X = 16;
    static final int SLOTS_Y = 16;
    static final int WIDTH = SLOTS_X * CloudField.MAX_WIDTH;
    static final int HEIGHT = SLOTS_Y * CloudField.MAX_HEIGHT;
    /** One transparent texel around each image keeps neighbouring slots from bleeding in. */
    static final int MAX_IMAGE_WIDTH = CloudField.MAX_WIDTH - 2;
    static final int MAX_IMAGE_HEIGHT = CloudField.MAX_HEIGHT - 2;
    static final int FADE_STEPS = 16;

    private static final int[] BAYER = {
            0, 8, 2, 10,
            12, 4, 14, 6,
            3, 11, 1, 9,
            15, 7, 13, 5,
    };

    static final class Slot {
        final int index;
        int version = -1;
        int palette = -1;
        int fade = -1;
        int frame;
        CloudImage image;

        Slot(int index) {
            this.index = index;
        }

        int texX() {
            return (index % SLOTS_X) * CloudField.MAX_WIDTH + 1;
        }

        int texY() {
            return (index / SLOTS_X) * CloudField.MAX_HEIGHT + 1;
        }
    }

    final ResourceLocation location = new ResourceLocation(PixelClouds.MOD_ID, "dynamic/cloud_atlas");
    private final DynamicTexture texture;
    private final NativeImage pixels;
    private final Map<Integer, Slot> slots = new HashMap<>();
    private final Deque<Integer> free = new ArrayDeque<>();
    private int frame;

    CloudAtlas() {
        texture = new DynamicTexture(WIDTH, HEIGHT, true);
        pixels = texture.getPixels();
        pixels.fillRect(0, 0, WIDTH, HEIGHT, 0);
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(location, texture);
        for (int i = 0; i < SLOTS_X * SLOTS_Y; i++) {
            free.add(i);
        }
    }

    void beginFrame() {
        frame++;
    }

    /**
     * Makes sure the cloud's slot shows its current image with the given palette and fade.
     *
     * @return the slot, or null when the atlas is full
     */
    Slot update(Cloud cloud, int[] palette, int paletteVersion, int fade) {
        Slot slot = slots.get(cloud.id);
        if (slot == null) {
            Integer index = free.poll();
            if (index == null) {
                return null;
            }
            slot = new Slot(index);
            slots.put(cloud.id, slot);
        }
        slot.frame = frame;
        CloudImage image = cloud.image(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
        if (slot.version != cloud.version() || slot.palette != paletteVersion || slot.fade != fade) {
            int oldW = slot.image == null ? 0 : slot.image.width;
            int oldH = slot.image == null ? 0 : slot.image.height;
            slot.image = image;
            slot.version = cloud.version();
            slot.palette = paletteVersion;
            slot.fade = fade;
            paint(slot, image, palette, fade, Math.max(oldW, image.width), Math.max(oldH, image.height));
        }
        return slot;
    }

    /** Frees slots of clouds that were not drawn this frame. */
    void endFrame() {
        Iterator<Slot> it = slots.values().iterator();
        while (it.hasNext()) {
            Slot slot = it.next();
            if (slot.frame != frame) {
                free.add(slot.index);
                it.remove();
            }
        }
    }

    private void paint(Slot slot, CloudImage image, int[] palette, int fade, int clearW, int clearH) {
        int sx = slot.texX(), sy = slot.texY();
        int[] abgr = new int[palette.length];
        for (int i = 1; i < palette.length; i++) {
            int rgb = palette[i];
            abgr[i] = 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
        }
        for (int y = 0; y < clearH; y++) {
            for (int x = 0; x < clearW; x++) {
                int colour = 0;
                if (x < image.width && y < image.height) {
                    byte t = image.tone(x, y);
                    // Dither in cloud space so the pattern stays put as the image bounds change.
                    int bx = (x + image.minU) & 3, by = (y + image.minV) & 3;
                    if (t != CloudImage.EMPTY && BAYER[by * 4 + bx] < fade) {
                        colour = abgr[t];
                    }
                }
                pixels.setPixelRGBA(sx + x, sy + y, colour);
            }
        }
        texture.bind();
        pixels.upload(0, sx, sy, sx, sy, clearW, clearH, false, false, false, false);
    }

    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(location);
    }
}
