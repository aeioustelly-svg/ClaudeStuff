#!/usr/bin/env python3
"""Builds the mod's item and armour textures from vanilla textures, as the project guidelines ask
(same family means same template, new material painted in).

  mamaliga.png            vanilla bowl, with the pixels vanilla recolours for mushroom stew recoloured as polenta
  dacian_felt_cap.png     vanilla leather cap shape and shading, lightened and tinted felt cream, red band
  dacian_felt_layer_1.png vanilla leather armour layer, same treatment (worn model)
  shed_skin.png           vanilla phantom membrane shape and shading, on a dark olive to cream ramp

    python3 tools/make_item_textures.py [client-extra.jar]
"""
import io
import os
import sys
import zipfile

import numpy as np
from PIL import Image

JAR = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
OUT = "src/main/resources/assets/dacian_draco/textures/"
T = "assets/minecraft/textures/"


def load(zf, name):
    return Image.open(io.BytesIO(zf.read(T + name))).convert("RGBA")


def luminance(a):
    return (a[..., 0] * 0.3 + a[..., 1] * 0.59 + a[..., 2] * 0.11) / 255.0


def ramp(img, dark, light):
    """Recolour by brightness: darkest pixel -> dark, brightest -> light. Alpha is kept."""
    a = np.array(img).astype(float)
    solid = a[..., 3] > 0
    lum = luminance(a)
    lo, hi = lum[solid].min(), lum[solid].max()
    t = ((lum - lo) / (hi - lo + 1e-9))[..., None]
    a[..., :3] = np.array(dark) + (np.array(light) - np.array(dark)) * t
    return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")


def felt(img):
    return ramp(img, (128, 116, 92), (232, 222, 192))      # cooler shadow, warmer highlight


def band(img):
    return ramp(img, (104, 40, 36), (164, 72, 60))          # muted red


def main():
    with zipfile.ZipFile(JAR) as zf:
        # mamaliga: only the pixels where vanilla stew differs from the bowl are recoloured
        bowl, stew = load(zf, "item/bowl.png"), load(zf, "item/mushroom_stew.png")
        polenta = {(0xbe, 0x78, 0x5e): (0xd6, 0xa8, 0x2c), (0xcd, 0x8c, 0x6f): (0xf0, 0xc8, 0x48),
                   (0xcc, 0x99, 0x78): (0xfa, 0xe0, 0x78), (0x45, 0x32, 0x0d): (0x45, 0x32, 0x0d)}
        mamaliga = bowl.copy()
        for y in range(16):
            for x in range(16):
                if bowl.getpixel((x, y)) != stew.getpixel((x, y)):
                    s = stew.getpixel((x, y))[:3]
                    mamaliga.putpixel((x, y), polenta.get(s, s) + (255,))
        mamaliga.save(OUT + "item/mamaliga.png")

        cap = felt(load(zf, "item/leather_helmet.png"))
        cap.alpha_composite(band(load(zf, "item/leather_helmet_overlay.png")))
        cap.save(OUT + "item/dacian_felt_cap.png")

        layer = felt(load(zf, "models/armor/leather_layer_1.png"))
        layer.alpha_composite(band(load(zf, "models/armor/leather_layer_1_overlay.png")))
        layer.save(OUT + "models/armor/dacian_felt_layer_1.png")

        ramp(load(zf, "item/phantom_membrane.png"), (66, 82, 58), (212, 206, 164)).save(OUT + "item/shed_skin.png")
    print("Wrote item and armour textures to", OUT)


if __name__ == "__main__":
    main()
