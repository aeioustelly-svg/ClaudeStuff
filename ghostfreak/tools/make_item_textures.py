#!/usr/bin/env python3
"""Builds the item and block textures from vanilla textures, as the project guidelines ask (same
family means same template, new material painted in).

  ectoplasm_bottle.png   vanilla potion bottle: the same bottle, with only the liquid recoloured
  spectral_lantern.png   vanilla soul lantern (block and item): the blue flame shifted to magenta,
                         the frame left alone. The block texture keeps its flicker animation.

    python3 -I tools/make_item_textures.py [client-extra.jar]
"""
import colorsys
import io
import os
import sys
import zipfile

import numpy as np
from PIL import Image

JAR = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
OUT = "src/main/resources/assets/ghostfreak/textures/"
T = "assets/minecraft/textures/"


def load(zf, name):
    return Image.open(io.BytesIO(zf.read(T + name))).convert("RGBA")


def tint(img, dark, light):
    """Recolour a mostly grey image by brightness. Alpha is kept."""
    a = np.array(img).astype(float)
    lum = (a[..., 0] * 0.3 + a[..., 1] * 0.59 + a[..., 2] * 0.11) / 255.0
    solid = a[..., 3] > 0
    lo, hi = lum[solid].min(), lum[solid].max()
    t = ((lum - lo) / (hi - lo + 1e-9))[..., None]
    a[..., :3] = np.array(dark) + (np.array(light) - np.array(dark)) * t
    return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")


def shift_flame(img, hue=0.84):
    """Moves saturated blue and cyan pixels (the soul flame) to magenta, keeping their brightness."""
    a = np.array(img).astype(float)
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            r, g, b, alpha = a[y, x]
            if alpha == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if 0.4 < h < 0.7 and s > 0.25:
                r, g, b = colorsys.hsv_to_rgb(hue, min(1.0, s * 0.85), v)
                a[y, x, :3] = (r * 255.0, g * 255.0, b * 255.0)
    return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")


def main():
    os.makedirs(OUT + "item", exist_ok=True)
    os.makedirs(OUT + "block", exist_ok=True)
    with zipfile.ZipFile(JAR) as zf:
        # the bottle is vanilla's, the liquid is vanilla's overlay layer painted a pale ectoplasm violet
        bottle = load(zf, "item/potion.png")
        liquid = tint(load(zf, "item/potion_overlay.png"), (120, 92, 150), (214, 190, 226))
        bottle.alpha_composite(liquid)
        bottle.save(OUT + "item/ectoplasm_bottle.png")

        shift_flame(load(zf, "item/soul_lantern.png")).save(OUT + "item/spectral_lantern.png")
        shift_flame(load(zf, "block/soul_lantern.png")).save(OUT + "block/spectral_lantern.png")
        with open(OUT + "block/spectral_lantern.png.mcmeta", "wb") as f:
            f.write(zf.read(T + "block/soul_lantern.png.mcmeta"))
    print("Wrote item and block textures to", OUT)


if __name__ == "__main__":
    main()
