#!/usr/bin/env python3
"""Builds the item textures from vanilla ones, following the vanilla template for each kind of item.

* music_disc_la_crucea_din_mormant.png: the vanilla "13" disc with only the label recoloured blood red.
* drogul_zombie.png: a syringe drawn on the diagonal like vanilla tools, in vanilla palettes: the glass
  bottle's blues for the barrel, the zombie's skin greens for the liquid, the iron nugget's greys for
  the plunger and the needle.

Reads the vanilla textures from the Forge Gradle cache (run any Gradle task once first).

    python3 -I tools/make_item_textures.py
"""
import io
import os
import zipfile

from PIL import Image

JAR = os.path.expanduser("~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
OUT = "src/main/resources/assets/aceliada/textures/item"


def vanilla(path):
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + path))).convert("RGBA")


def disc():
    im = vanilla("item/music_disc_13.png")
    label = {
        (255, 216, 0): (122, 22, 20),    # yellow half of the label -> dark blood red
        (255, 255, 255): (178, 40, 34),  # white half -> brighter red
    }
    px = im.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if (r, g, b) in label:
                px[x, y] = label[(r, g, b)] + (a,)
    return im


# Palettes taken from vanilla textures (see the module docstring).
GLASS = [(93, 143, 194), (139, 173, 208), (179, 207, 236), (212, 229, 247)]
ZOMBIE = [(59, 98, 47), (78, 123, 54), (113, 149, 91)]
IRON = [(57, 60, 64), (88, 95, 104), (162, 176, 190), (217, 223, 231)]
OUTLINE = (40, 46, 56)


def syringe():
    """Plunger at the bottom left, needle at the top right.

    a = x + 15 - y runs along the syringe (0 at the bottom-left corner, 30 at the top-right) and
    p = x + y - 15 across it (negative is the upper-left side, which catches the light).
    """
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            a = x + 15 - y
            p = x + y - 15
            c = None
            if 1 <= a <= 3 and -3 <= p <= 3:            # thumb rest
                c = IRON[3] if p <= -2 else IRON[2] if p <= 1 else IRON[1]
                if p == 3 or a == 1:
                    c = IRON[0]
            elif 4 <= a <= 8 and -1 <= p <= 0:           # plunger rod
                c = IRON[2] if p == -1 else IRON[1]
            elif 9 <= a <= 10 and -3 <= p <= 3:          # finger grips
                c = IRON[3] if p < 0 else IRON[2] if p < 2 else IRON[1]
                if p == 3:
                    c = IRON[0]
            elif 11 <= a <= 23 and -2 <= p <= 2:         # barrel
                if p == -2:
                    c = GLASS[3] if a % 4 else GLASS[2]
                elif p == 2:
                    c = GLASS[0]
                elif a <= 20:                            # liquid, lit from the upper left
                    c = ZOMBIE[2] if p == -1 else ZOMBIE[1] if p == 0 else ZOMBIE[0]
                    if a in (13, 17) and p == -1:        # graduation marks
                        c = GLASS[3]
                else:                                    # empty glass above the liquid
                    c = GLASS[2] if p < 1 else GLASS[1]
            elif a == 24 and -2 <= p <= 2:               # end of the barrel
                c = GLASS[1] if p < 1 else GLASS[0]
            elif 25 <= a <= 26 and -1 <= p <= 0:         # needle hub
                c = IRON[2] if p == -1 else IRON[1]
            elif 27 <= a <= 30 and p == 0:               # needle
                c = IRON[3] if a == 30 else IRON[2]
            if c is not None:
                px[x, y] = c + (255,)
    # Dark outline on transparent neighbours of the lower-right side only, as vanilla shades tools.
    out = im.copy()
    opx = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] != 0:
                continue
            left = x > 0 and px[x - 1, y][3] and (x - 1) + y - 15 >= 2
            up = y > 0 and px[x, y - 1][3] and x + (y - 1) - 15 >= 2
            if (left or up) and x + 15 - y >= 9:
                opx[x, y] = OUTLINE + (255,)
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    disc().save(os.path.join(OUT, "music_disc_la_crucea_din_mormant.png"))
    syringe().save(os.path.join(OUT, "drogul_zombie.png"))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
