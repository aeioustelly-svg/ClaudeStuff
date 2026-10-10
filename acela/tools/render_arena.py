#!/usr/bin/env python3
"""Draws Dealul Bohii from the blocks the real ArenaBuilder placed, no Minecraft needed.

The arena GameTest writes build/preview/arena.json (every non-air block with its state) when run
through `./gradlew runGameTestServer`. This script draws it isometrically, with each block coloured
by the average of its vanilla texture and partial blocks (stairs, slabs, walls, fences, bars,
skulls, lanterns, candles, campfires) given rough shapes. It shows layout and massing, not the
game's lighting.

    ./gradlew runGameTestServer
    python3 -I tools/render_arena.py
"""
import io
import json
import math
import os
import zipfile

import numpy as np
from PIL import Image, ImageDraw

DUMP = "build/preview/arena.json"
OUT = "build/preview"
JAR = os.path.expanduser("~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")

SPECIAL = {
    "skeleton_skull": (206, 204, 188), "skeleton_wall_skull": (206, 204, 188),
    "wither_skeleton_skull": (46, 46, 46), "wither_skeleton_wall_skull": (46, 46, 46),
    "soul_lantern": (96, 170, 178), "soul_campfire": (92, 200, 210),
}
TEXTURE_ALIASES = {
    "polished_blackstone_brick_stairs": "polished_blackstone_bricks",
    "polished_blackstone_brick_slab": "polished_blackstone_bricks",
    "polished_blackstone_brick_wall": "polished_blackstone_bricks",
    "dark_oak_fence": "dark_oak_planks",
    "nether_brick_fence": "nether_bricks",
}


class Colours:
    def __init__(self):
        self.jar = zipfile.ZipFile(JAR)
        self.names = set(self.jar.namelist())
        self.cache = {}

    def texture(self, name):
        path = f"assets/minecraft/textures/block/{name}.png"
        if path not in self.names:
            return None
        im = np.array(Image.open(io.BytesIO(self.jar.read(path))).convert("RGBA").crop((0, 0, 16, 16)), float)
        opaque = im[..., 3] > 128
        return tuple(int(c) for c in im[..., :3][opaque].mean(0)) if opaque.any() else None

    def get(self, block, face):
        key = (block, face)
        if key in self.cache:
            return self.cache[key]
        colour = SPECIAL.get(block)
        if colour is None:
            base = TEXTURE_ALIASES.get(block, block)
            candidates = [f"{base}_top", base] if face == "top" else [f"{base}_side", base, f"{base}_top"]
            if block == "soul_campfire":
                candidates = ["campfire_log"]
            for c in candidates:
                colour = self.texture(c)
                if colour:
                    break
        self.cache[key] = colour or (255, 0, 255)
        return self.cache[key]


def shapes(block, props):
    """Boxes (x0, y0, z0, x1, y1, z1) in block units for the block's rough shape."""
    full = [(0, 0, 0, 1, 1, 1)]
    if block.endswith("_stairs"):
        top = props.get("half") == "top"
        slab = (0, 0.5, 0, 1, 1, 1) if top else (0, 0, 0, 1, 0.5, 1)
        y0, y1 = (0, 0.5) if top else (0.5, 1)
        back = {"north": (0, y0, 0, 1, y1, 0.5), "south": (0, y0, 0.5, 1, y1, 1),
                "west": (0, y0, 0, 0.5, y1, 1), "east": (0.5, y0, 0, 1, y1, 1)}[props["facing"]]
        return [slab, back]
    if block.endswith("_slab"):
        return [(0, 0, 0, 1, 0.5, 1)] if props.get("type") == "bottom" else [(0, 0.5, 0, 1, 1, 1)] \
            if props.get("type") == "top" else full
    if block.endswith("_wall"):
        return [(0.25, 0, 0.25, 0.75, 1, 0.75)]
    if block.endswith("_fence"):
        boxes = [(0.375, 0, 0.375, 0.625, 1, 0.625)]
        arms = {"north": (0.44, 0.4, 0, 0.56, 0.9, 0.4), "south": (0.44, 0.4, 0.6, 0.56, 0.9, 1),
                "west": (0, 0.4, 0.44, 0.4, 0.9, 0.56), "east": (0.6, 0.4, 0.44, 1, 0.9, 0.56)}
        return boxes + [b for d, b in arms.items() if props.get(d) == "true"]
    if block == "iron_bars":
        ew = props.get("east") == "true" or props.get("west") == "true"
        ns = props.get("north") == "true" or props.get("south") == "true"
        boxes = []
        if ew:
            boxes.append((0, 0, 0.45, 1, 1, 0.55))
        if ns:
            boxes.append((0.45, 0, 0, 0.55, 1, 1))
        return boxes or [(0.45, 0, 0.45, 0.55, 1, 0.55)]
    if block == "chain":
        return [(0.45, 0, 0.45, 0.55, 1, 0.55)]
    if block.endswith("wall_skull"):
        f = props["facing"]
        return [{"north": (0.25, 0.25, 0.5, 0.75, 0.75, 1), "south": (0.25, 0.25, 0, 0.75, 0.75, 0.5),
                 "west": (0.5, 0.25, 0.25, 1, 0.75, 0.75), "east": (0, 0.25, 0.25, 0.5, 0.75, 0.75)}[f]]
    if block.endswith("_skull"):
        return [(0.25, 0, 0.25, 0.75, 0.5, 0.75)]
    if block.endswith("lantern"):
        return [(0.31, 0.06, 0.31, 0.69, 0.56, 0.69)]
    if block.endswith("candle"):
        return [(0.4, 0, 0.4, 0.6, 0.45, 0.6)]
    if block.endswith("campfire"):
        return [(0, 0, 0, 1, 0.45, 1)]
    return full


def render(blocks, colours, scale, keep, size, centre_offset=(0, 0)):
    W, H = size
    img = Image.new("RGB", (W, H), (58, 22, 20))
    draw = ImageDraw.Draw(img)
    cos30, sin30 = math.cos(math.radians(30)), math.sin(math.radians(30))

    # Only the arena: whatever the test world has around it is left out.
    def project(x, y, z):
        return (W / 2 + (x - z) * cos30 * scale + centre_offset[0],
                H / 2 + (x + z) * sin30 * scale - y * scale + centre_offset[1])

    boxes = []
    for x, y, z, block, props in blocks:
        if x * x + z * z > 18.5 * 18.5 or not keep(x, y, z):
            continue
        for b in shapes(block, props):
            boxes.append((x + b[0], y + b[1], z + b[2], x + b[3], y + b[4], z + b[5], block))
    boxes.sort(key=lambda b: (b[0] + b[3] + b[2] + b[5]) / 2 + (b[1] + b[4]) / 2 * 0.98)
    for x0, y0, z0, x1, y1, z1, block in boxes:
        top = colours.get(block, "top")
        side = colours.get(block, "side")
        faces = [
            ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], top, 1.0),
            ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], side, 0.78),   # south
            ([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], side, 0.62),   # east
        ]
        for pts, c, shade in faces:
            draw.polygon([project(*p) for p in pts], fill=tuple(int(v * shade) for v in c),
                         outline=tuple(int(v * shade * 0.8) for v in c))
    return img


def main():
    with open(DUMP) as f:
        blocks = json.load(f)
    colours = Colours()
    os.makedirs(OUT, exist_ok=True)
    everything = lambda x, y, z: True
    render(blocks, colours, 14, everything, (1100, 760), (0, 40)).save(os.path.join(OUT, "arena.png"))
    # Cut away the near half of the wall so the inside shows.
    cutaway = lambda x, y, z: not (x + z > 14 and x * x + z * z > 14 * 14 and y > 0)
    render(blocks, colours, 14, cutaway, (1100, 760), (0, 40)).save(os.path.join(OUT, "arena_cutaway.png"))
    hill = lambda x, y, z: x * x + z * z <= 7.5 * 7.5 and y >= 0
    render(blocks, colours, 46, hill, (1000, 800), (0, 120)).save(os.path.join(OUT, "arena_hill.png"))
    print("Wrote", OUT + "/arena.png, arena_cutaway.png, arena_hill.png")


if __name__ == "__main__":
    main()
