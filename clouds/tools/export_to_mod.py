#!/usr/bin/env python3
"""Stage 4: pack the generated clouds and their broken-off pieces into one atlas for the mod.

The atlas stores shade indices, not colours: each pixel is grey at index * 60 (alpha 255), index 0 is fully
transparent. The mod reads the indices back and paints the atlas with the palette for the time of day and the
weather, so one texture serves dawn, day, dusk, night and storms.

    python3 -I clouds/tools/export_to_mod.py [sprites_dir] [mod_resources_dir]

Writes <mod_resources_dir>/assets/pixel_clouds/textures/clouds/atlas.png and
<mod_resources_dir>/assets/pixel_clouds/clouds.json (atlas rectangles of every cloud and piece, and each piece's
offset in pixels from its cloud's top-left corner).
"""
import json
import os
import sys

import numpy as np
from PIL import Image

SPRITES = sys.argv[1] if len(sys.argv) > 1 else "clouds/sprites"
RES = sys.argv[2] if len(sys.argv) > 2 else "clouds/mod/src/main/resources"
ATLAS_W = 256
STEP = 60       # grey level per shade index


def load(name):
    return np.asarray(Image.open(os.path.join(SPRITES, "generated", name)))


def main():
    with open(os.path.join(SPRITES, "generated", "clouds.json")) as f:
        manifest = json.load(f)
    layers = []         # (array, owner, piece entry or None)
    for name in sorted(manifest):
        e = manifest[name]
        layers.append((load(e["file"]), name, None))
        for b in e["bits"]:
            layers.append((load(b["file"]), name, b))

    # Shelf packing, tallest first, one empty pixel between sprites so sampling never bleeds.
    order = sorted(range(len(layers)), key=lambda i: -layers[i][0].shape[0])
    rects = {}
    x = y = shelf = 0
    for i in order:
        h, w = layers[i][0].shape
        if x + w > ATLAS_W:
            x, y, shelf = 0, y + shelf + 1, 0
        rects[i] = (x, y, w, h)
        x += w + 1
        shelf = max(shelf, h)
    height = 1
    while height < y + shelf:
        height *= 2

    atlas = np.zeros((height, ATLAS_W, 4), np.uint8)
    for i, (a, _, _) in enumerate(layers):
        x, y, w, h = rects[i]
        atlas[y:y + h, x:x + w, :3] = (a * STEP)[..., None]
        atlas[y:y + h, x:x + w, 3] = np.where(a > 0, 255, 0)

    clouds = []
    for i, (a, name, bit) in enumerate(layers):
        x, y, w, h = rects[i]
        if bit is None:
            clouds.append({"name": name, "kind": manifest[name]["kind"], "x": x, "y": y, "w": w, "h": h, "bits": []})
        else:
            clouds[-1]["bits"].append({"x": x, "y": y, "w": w, "h": h, "dx": bit["x"], "dy": bit["y"]})

    tex_dir = os.path.join(RES, "assets", "pixel_clouds", "textures", "clouds")
    os.makedirs(tex_dir, exist_ok=True)
    Image.fromarray(atlas, "RGBA").save(os.path.join(tex_dir, "atlas.png"))
    with open(os.path.join(RES, "assets", "pixel_clouds", "clouds.json"), "w") as f:
        json.dump({"atlas": [ATLAS_W, height], "step": STEP, "clouds": clouds}, f, indent=1)
    print(f"atlas {ATLAS_W} x {height}, {len(clouds)} clouds, {sum(len(c['bits']) for c in clouds)} pieces")


if __name__ == "__main__":
    main()
