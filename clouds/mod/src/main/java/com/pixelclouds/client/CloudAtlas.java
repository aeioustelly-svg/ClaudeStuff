package com.pixelclouds.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.pixelclouds.PixelClouds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The cloud sprites, packed by clouds/tools/export_to_mod.py. The atlas file holds shade indices (grey level =
 * index * step), which are kept in memory; the texture the game draws is painted from them with the current
 * palette, so the same sprites serve every time of day and weather.
 */
public final class CloudAtlas {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PixelClouds.MOD_ID, "clouds_dynamic");
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(PixelClouds.MOD_ID, "textures/clouds/atlas.png");
    private static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath(PixelClouds.MOD_ID, "clouds.json");

    /** A rectangle in the atlas, in pixels. */
    public record Rect(int x, int y, int w, int h) {}

    /** A piece that has broken off a cloud, starting dx, dy pixels from the cloud's top-left corner. */
    public record Piece(Rect rect, int dx, int dy) {}

    public record Sprite(Rect rect, List<Piece> pieces) {}

    public final int width;
    public final int height;
    public final List<Sprite> sprites;
    private final byte[] indices;
    private final DynamicTexture texture;
    private int[] painted;

    private CloudAtlas(int width, int height, List<Sprite> sprites, byte[] indices) {
        this.width = width;
        this.height = height;
        this.sprites = sprites;
        this.indices = indices;
        this.texture = new DynamicTexture(width, height, false);
        Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
    }

    public static CloudAtlas load() {
        var resources = Minecraft.getInstance().getResourceManager();
        try (InputStream layoutIn = resources.open(LAYOUT); InputStream atlasIn = resources.open(ATLAS)) {
            JsonObject layout = JsonParser.parseReader(new InputStreamReader(layoutIn, StandardCharsets.UTF_8)).getAsJsonObject();
            int step = layout.get("step").getAsInt();
            List<Sprite> sprites = new ArrayList<>();
            for (JsonElement e : layout.getAsJsonArray("clouds")) {
                JsonObject c = e.getAsJsonObject();
                List<Piece> pieces = new ArrayList<>();
                JsonArray bits = c.getAsJsonArray("bits");
                for (JsonElement b : bits) {
                    JsonObject o = b.getAsJsonObject();
                    pieces.add(new Piece(rect(o), o.get("dx").getAsInt(), o.get("dy").getAsInt()));
                }
                sprites.add(new Sprite(rect(c), List.copyOf(pieces)));
            }
            try (NativeImage image = NativeImage.read(atlasIn)) {
                int w = image.getWidth(), h = image.getHeight();
                byte[] indices = new byte[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int abgr = image.getPixelRGBA(x, y);
                        int alpha = abgr >>> 24, grey = abgr & 0xFF;
                        indices[y * w + x] = (byte) (alpha == 0 ? 0 : Math.round(grey / (float) step));
                    }
                }
                return new CloudAtlas(w, h, List.copyOf(sprites), indices);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not load the pixel cloud atlas", e);
        }
    }

    private static Rect rect(JsonObject o) {
        return new Rect(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("w").getAsInt(), o.get("h").getAsInt());
    }

    /** Paints the texture with colours[index] (ARGB) for shade indices 1 to 4. Skipped if nothing changed. */
    public void paint(int[] colours) {
        if (painted != null && java.util.Arrays.equals(painted, colours)) {
            return;
        }
        painted = colours.clone();
        NativeImage pixels = texture.getPixels();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = indices[y * width + x];
                int argb = index == 0 ? 0 : colours[index];
                // NativeImage stores ABGR.
                int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                pixels.setPixelRGBA(x, y, abgr);
            }
        }
        texture.upload();
    }
}
