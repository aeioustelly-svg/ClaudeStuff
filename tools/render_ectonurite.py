#!/usr/bin/env python3
"""Software-renders the candidate Ectonurite models with their painted textures.

Writes to build/preview/: one sheet per candidate (poses by views) and `ectonurite_compare.png`,
which shows all three at the same scale. Not the game's renderer: no lighting, and the
translucency a ghost would get in game is not shown.

    ./gradlew dumpEctonurite
    python3 -I tools/paint_ectonurite.py
    python3 -I tools/render_ectonurite.py
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # -I drops the script folder
import render_preview as rp  # noqa: E402

DIR = "build/preview"
IDS = ("a", "b", "c")
NAMES = {"a": "A Faithful", "b": "B Wisp", "c": "C Stalker"}
VIEWS = [("front", 180, 4), ("3/4", 215, 14), ("side", 90, 4), ("back", 0, 4)]
BG_PIXEL = 16.0


def load(i):
    with open(os.path.join(DIR, f"ectonurite_{i}.json")) as f:
        data = json.load(f)
    tex = np.array(Image.open(os.path.join(DIR, f"ectonurite_{i}.png")).convert("RGBA"))
    return data, tex


def fit_scale(quads, size):
    pts = np.concatenate([q[0] for q in quads])
    ext = pts.max(0) - pts.min(0)
    return 0.9 * min(size[0] / (max(ext[0], ext[2]) + 4), size[1] / (ext[1] + 4))


def main():
    models = {i: load(i) for i in IDS}

    for i, (data, tex) in models.items():
        cells = []
        size = (300, 420)
        scale = fit_scale(rp.load_quads(data, "idle"), size)
        for pose in ("idle", "drift", "look"):
            quads = rp.load_quads(data, pose)
            for name, yaw, pitch in VIEWS:
                cells.append((f"{NAMES[i]}  {pose} / {name}", rp.render(quads, tex, yaw, pitch, scale, size)))
        rp.sheet(cells, 4, size, os.path.join(DIR, f"ectonurite_{i}_sheet.png"))

        t = Image.fromarray(tex, "RGBA")
        bg = Image.new("RGBA", t.size, (70, 70, 76, 255))
        bg.alpha_composite(t)
        k = 512 // max(t.size)
        bg.resize((t.size[0] * k, t.size[1] * k), Image.NEAREST).convert("RGB").save(
            os.path.join(DIR, f"ectonurite_{i}_texture.png"))

    # same scale for all three, so sizes can be compared; a 32 px bar marks the height of a player
    size = (380, 520)
    cells = []
    for pose, yaw, pitch in (("idle", 180, 4), ("idle", 215, 14), ("drift", 215, 14)):
        for i, (data, tex) in models.items():
            quads = rp.load_quads(data, pose)
            cells.append((f"{NAMES[i]}  {pose} / yaw {yaw}", rp.render(quads, tex, yaw, pitch, 8.0, size)))
    rp.sheet(cells, 3, size, os.path.join(DIR, "ectonurite_compare.png"))


if __name__ == "__main__":
    main()
