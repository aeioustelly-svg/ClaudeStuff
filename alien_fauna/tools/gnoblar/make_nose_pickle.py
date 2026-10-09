#!/usr/bin/env python3
"""Draws the mod's item textures.

  nose_pickle.png   a pickled gnoblar nose: a gherkin that is also a nose: it curves up from a thick end with a pair of nostrils to a thin
                    stem, with a couple of bumps and a drop of brine. It is drawn from an ASCII map, three greens and a dark outline, in the muted vanilla style. (It was
                    once the vanilla sea pickle recoloured, which looked like a sea cucumber, so it is its own drawing now.)

    python3 -I tools/gnoblar/make_nose_pickle.py
"""
import os

from PIL import Image

OUT = "src/main/resources/assets/alien_fauna/textures/item/"

PALETTE = {
    ".": None,
    "o": (30, 40, 18),      # outline
    "d": (62, 78, 32),      # shadow
    "m": (98, 118, 46),     # body
    "l": (130, 150, 64),    # lit side
    "h": (180, 194, 112),   # highlight
    "w": (156, 164, 84),    # wart
    "n": (24, 30, 12),      # nostril
    "b": (206, 214, 128),   # brine
}

NOSE_PICKLE = [
    "................",
    "..........oo....",
    ".........ohlo...",
    "........ohmmlo..",
    "........odmmlo..",
    ".......odmmmlo..",
    ".......odmwmmlo.",
    "......odmmmmmlo.",
    ".....odmmmmmmlo.",
    "....odmwmmmmllo.",
    "...odmmmmmmmlo..",
    "..odmmmmmmmllo..",
    "..odmmmmmmmlo...",
    "..oddmnmmnmllo..",
    "...ooddmmllo....",
    ".....oooooo..b..",
]


def draw(rows, path):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), "an item texture is 16 x 16"
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            color = PALETTE[ch]
            if color:
                img.putpixel((x, y), (*color, 255))
    img.save(path)
    print("Wrote", path)


def main():
    os.makedirs(OUT, exist_ok=True)
    draw(NOSE_PICKLE, OUT + "nose_pickle.png")


if __name__ == "__main__":
    main()
