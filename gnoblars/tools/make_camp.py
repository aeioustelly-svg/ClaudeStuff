#!/usr/bin/env python3
"""Builds the gnoblar camp structure (data/gnoblars/structures/camp.nbt) block by block, and draws previews.

The camp is a 15 x 8 x 15 patch: a trodden clearing with a cooking pot over a fire in a ring of stones, two hide
tents (one a bedroom with the loot chest, one a store with a barrel), a scrap heap, a bone-and-pumpkin totem, a
drying rack, log seats and a few gnoblars. Layer 0 is foundation and layer 1 is the ground, so the structure is
placed two blocks into the terrain (start_height -2 in the structure's worldgen JSON).

    python3 -I tools/make_camp.py [output.nbt] [preview.png]
"""
import gzip
import random
import struct
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/data/gnoblars/structures/camp.nbt"
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else "build/preview/camp.png"
W, H, L = 15, 8, 15
DATA_VERSION = 3465   # Minecraft 1.20.1
LOOT = "gnoblars:chests/camp"

rng = random.Random(1745)
grid = {}          # (x, y, z) -> (name, props)
block_nbt = {}     # (x, y, z) -> dict for block entities
gnoblars = []      # (x, y, z, variant, yaw)


def put(x, y, z, name, **props):
    if 0 <= x < W and 0 <= y < H and 0 <= z < L:
        grid[(x, y, z)] = ("minecraft:" + name, {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()})


def fill(x1, y1, z1, x2, y2, z2, name, **props):
    for x in range(x1, x2 + 1):
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                put(x, y, z, name, **props)


# ---- ground: a rough disc of foundation and a trodden surface -------------------------------------
CX, CZ = 7, 7
for x in range(W):
    for z in range(L):
        d = ((x - CX) ** 2 + (z - CZ) ** 2) ** 0.5
        if d <= 7.2 - rng.random() * 0.9:
            put(x, 0, z, "dirt")
            r = rng.random()
            if d < 3.6:
                put(x, 1, z, "dirt_path")
            elif r < 0.45:
                put(x, 1, z, "coarse_dirt")
            elif r < 0.65:
                put(x, 1, z, "podzol")
            elif r < 0.80:
                put(x, 1, z, "gravel")
            elif r < 0.90:
                put(x, 1, z, "mud")
            else:
                put(x, 1, z, "dirt")

# clear the air above, so trees and bushes do not grow through the camp
for x in range(W):
    for z in range(L):
        if (x, 1, z) in grid:
            for y in range(2, H):
                put(x, y, z, "air")

# ---- the fire: campfire under a water-filled cauldron, in a ring of stones ----------------------------
put(7, 2, 7, "campfire", lit=True, facing="north", signal_fire=False, waterlogged=False)
put(7, 3, 7, "water_cauldron", level=3)
for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
    put(7 + dx, 2, 7 + dz, rng.choice(["cobblestone", "mossy_cobblestone", "stone"]))
for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
    put(7 + dx, 2, 7 + dz, "cobblestone_wall", up=True, north="none", south="none", east="none", west="none")

# log seats round the fire
put(4, 2, 7, "stripped_spruce_log", axis="z")
put(4, 2, 8, "stripped_spruce_log", axis="z")
put(10, 2, 6, "stripped_spruce_log", axis="z")
put(7, 2, 10, "stripped_spruce_log", axis="x")
put(8, 2, 10, "stripped_spruce_log", axis="x")


def tent(x0, z0, length, along, open_end, hide, trim, chest=None, barrel=None):
    """An A-frame of stepped hide blocks, 5 wide and 3 high. `along` is the axis the ridge runs on."""
    def cell(u, y, v):          # u across, v along
        return (x0 + u, y, z0 + v) if along == "z" else (x0 + v, y, z0 + u)

    for v in range(length):
        for u, y, blk in ((0, 2, hide), (4, 2, hide), (1, 3, hide), (3, 3, hide)):
            put(*cell(u, y, v), blk)
        put(*cell(2, 4, v), "spruce_slab", type="bottom")
        for u in (1, 2, 3):
            put(*cell(u, 2, v), "air")
        put(*cell(2, 3, v), "air")
    # closed back end, open front with a framed doorway
    back = 0 if open_end != 0 else length - 1
    front = length - 1 if back == 0 else 0
    for u, y in ((1, 2), (2, 2), (3, 2), (2, 3)):
        put(*cell(u, y, back), hide)
    put(*cell(1, 2, front), "spruce_fence")
    put(*cell(3, 2, front), "spruce_fence")
    put(*cell(1, 3, front), "spruce_fence")
    put(*cell(3, 3, front), "spruce_fence")
    put(*cell(2, 3, front), "spruce_planks")
    return cell


# tent A: the bedroom, ridge along z, open to the fire (east)
cell = tent(1, 3, 5, "z", 0, "brown_wool", "spruce_planks")
# the doorway of tent A is its far end (z = 7); the fire is east, so give it a side door by opening the east side
for y in (2,):
    put(*cell(4, y, 2), "air")
    put(*cell(4, 3, 2), "air")
# a gnoblar bed: a hay block with a carpet on top (gnoblars climb on at night and sleep)
put(3, 2, 5, "hay_block")
put(3, 3, 5, "brown_carpet")
put(3, 2, 4, "chest", facing="south", type="single", waterlogged=False)
block_nbt[(3, 2, 4)] = {"id": "minecraft:chest", "LootTable": LOOT}
put(3, 2, 6, "barrel", facing="up", open=False)
block_nbt[(3, 2, 6)] = {"id": "minecraft:barrel", "LootTable": LOOT}
put(3, 3, 6, "lantern", hanging=True)

# tent B: the store, ridge along x, south of the fire
cell = tent(8, 10, 5, "x", 4, "light_gray_wool", "spruce_planks")
put(*cell(1, 2, 1), "barrel", facing="up", open=False)
put(*cell(1, 2, 2), "barrel", facing="up", open=False)
block_nbt[cell(1, 2, 2)] = {"id": "minecraft:barrel", "LootTable": LOOT}
put(*cell(2, 2, 2), "hay_block")
put(*cell(2, 3, 2), "red_carpet")
put(*cell(3, 2, 3), "composter", level=3)

# ---- the scrap heap: junk that gnoblars find precious ----------------------------------------------
heap = ["gravel", "coarse_dirt", "oak_planks", "cobblestone", "iron_bars", "mossy_cobblestone", "cobweb", "dead_bush"]
for x in range(10, 14):
    for z in range(1, 5):
        if (x - 11.5) ** 2 + (z - 2.5) ** 2 < 5.2:
            put(x, 2, z, rng.choice(heap[:5]))
for x in range(11, 13):
    for z in range(2, 4):
        put(x, 3, z, rng.choice(["gravel", "cobblestone", "oak_planks", "coarse_dirt"]))
put(11, 4, 2, "cauldron")
put(12, 4, 3, "dead_bush")
put(10, 3, 2, "chain", axis="y")
put(13, 3, 1, "barrel", facing="north", open=False)
put(9, 2, 3, "iron_bars", north=False, south=False, east=False, west=False)

# ---- the totem: bones topped with a carved pumpkin (a face with a big nose) ------------------------------
for y in (2, 3, 4):
    put(12, y, 8, "bone_block", axis="y")
put(12, 5, 8, "carved_pumpkin", facing="west")
put(13, 2, 8, "spruce_fence")

# ---- a drying rack (south-west corner) ----------------------------------------------------------
put(1, 2, 10, "spruce_fence")
put(1, 3, 10, "spruce_fence")
put(3, 2, 10, "spruce_fence")
put(3, 3, 10, "spruce_fence")
put(2, 3, 10, "spruce_fence")
put(2, 2, 10, "dried_kelp_block")
put(13, 2, 11, "hay_block")

# a few lanterns on posts
put(5, 2, 9, "spruce_fence")
put(5, 3, 9, "lantern", hanging=False)
put(9, 2, 5, "spruce_fence")
put(9, 3, 5, "lantern", hanging=False)

# ---- ground under everything: a built cell without earth below it gets a foundation ----------------------------
for (x, y, z), (name, _) in list(grid.items()):
    if y >= 2 and name != "minecraft:air":
        if (x, 1, z) not in grid:
            put(x, 1, z, "coarse_dirt")
        if (x, 0, z) not in grid:
            put(x, 0, z, "dirt")

# ---- the residents -------------------------------------------------------------------------------
gnoblars.extend([(4.5, 2.0, 5.5, "bark", 90.0), (8.5, 2.0, 5.5, "green", 180.0), (6.5, 2.0, 9.5, "mossy", 0.0),
                 (11.5, 2.0, 6.5, "rusty", 270.0), (9.5, 2.0, 12.5, "pickle", 45.0)])

# ---- NBT writer (just enough for a structure file) ----------------------------------------------------
def tag(kind, payload):
    return kind, payload


def w_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def w_payload(kind, value):
    if kind == 1:
        return struct.pack(">b", value)
    if kind == 3:
        return struct.pack(">i", value)
    if kind == 4:
        return struct.pack(">q", value)
    if kind == 5:
        return struct.pack(">f", value)
    if kind == 6:
        return struct.pack(">d", value)
    if kind == 8:
        return w_string(value)
    if kind == 9:                         # list: (element kind, [values])
        ek, items = value
        return struct.pack(">bi", ek, len(items)) + b"".join(w_payload(ek, i) for i in items)
    if kind == 10:                        # compound: dict name -> (kind, value)
        out = b""
        for name, (k, v) in value.items():
            out += struct.pack(">b", k) + w_string(name) + w_payload(k, v)
        return out + b"\x00"
    raise ValueError(kind)


def compound(**kw):
    return kw


def build():
    palette = {}
    blocks = []
    for (x, y, z), (name, props) in sorted(grid.items()):
        key = (name, tuple(sorted(props.items())))
        if key not in palette:
            palette[key] = len(palette)
        entry = {"pos": (9, (3, [x, y, z])), "state": (3, palette[key])}
        if (x, y, z) in block_nbt:
            entry["nbt"] = (10, {k: (8, v) for k, v in block_nbt[(x, y, z)].items()})
        blocks.append((10, entry))
    pal = []
    for (name, props), _ in sorted(palette.items(), key=lambda kv: kv[1]):
        e = {"Name": (8, name)}
        if props:
            e["Properties"] = (10, {k: (8, v) for k, v in props})
        pal.append(e)
    ents = []
    for x, y, z, variant, yaw in gnoblars:
        nbt = {"id": (8, "gnoblars:gnoblar"), "Variant": (8, variant), "PersistenceRequired": (1, 1),
               "Rotation": (9, (5, [yaw, 0.0]))}
        ents.append({"pos": (9, (6, [x, y, z])), "blockPos": (9, (3, [int(x), int(y), int(z)])), "nbt": (10, nbt)})
    root = {"DataVersion": (3, DATA_VERSION),
            "size": (9, (3, [W, H, L])),
            "palette": (9, (10, pal)),
            "blocks": (9, (10, [b for _, b in blocks])),
            "entities": (9, (10, ents))}
    return b"\x0a" + w_string("") + w_payload(10, root)


def preview():
    from PIL import Image, ImageDraw
    colors = {"dirt": (96, 68, 48), "coarse_dirt": (112, 82, 58), "podzol": (84, 62, 36), "gravel": (128, 124, 122),
              "mud": (60, 50, 44), "dirt_path": (150, 120, 70), "campfire": (230, 120, 30), "water_cauldron": (50, 80, 170),
              "cobblestone": (120, 120, 120), "mossy_cobblestone": (96, 120, 96), "stone": (130, 130, 130),
              "cobblestone_wall": (110, 110, 110), "brown_wool": (110, 74, 40), "light_gray_wool": (160, 160, 156),
              "spruce_slab": (104, 78, 46), "spruce_planks": (114, 84, 52), "spruce_fence": (92, 66, 36),
              "stripped_spruce_log": (140, 106, 64), "hay_block": (200, 170, 40), "brown_carpet": (120, 80, 50), "red_carpet": (150, 40, 40),
              "chest": (170, 110, 30), "barrel": (130, 90, 50), "lantern": (255, 220, 120), "bone_block": (230, 226, 206),
              "carved_pumpkin": (220, 130, 20), "dried_kelp_block": (40, 56, 36), "composter": (110, 80, 40),
              "cauldron": (60, 60, 64), "oak_planks": (160, 130, 80), "iron_bars": (150, 150, 160), "cobweb": (230, 230, 230),
              "dead_bush": (130, 100, 60), "chain": (70, 74, 90)}
    s = 24
    top = Image.new("RGB", (W * s, L * s), (30, 30, 34))
    d = ImageDraw.Draw(top)
    for x in range(W):
        for z in range(L):
            col = None
            for y in range(H - 1, -1, -1):
                b = grid.get((x, y, z))
                if b and b[0] != "minecraft:air":
                    col = colors.get(b[0][10:], (200, 0, 200))
                    shade = 0.75 + 0.25 * y / H
                    col = tuple(int(c * shade) for c in col)
                    break
            if col:
                d.rectangle([x * s, z * s, x * s + s - 1, z * s + s - 1], fill=col)
    for gx, gy, gz, _, _ in gnoblars:
        d.ellipse([gx * s - 6, gz * s - 6, gx * s + 6, gz * s + 6], fill=(60, 200, 80), outline=(0, 0, 0))
    side = Image.new("RGB", (W * s, H * s), (30, 30, 34))
    d = ImageDraw.Draw(side)
    for x in range(W):
        for y in range(H):
            col = None
            for z in range(L):
                b = grid.get((x, y, z))
                if b and b[0] != "minecraft:air":
                    col = colors.get(b[0][10:], (200, 0, 200))
                    break
            if col:
                d.rectangle([x * s, (H - 1 - y) * s, x * s + s - 1, (H - 1 - y) * s + s - 1], fill=col)
    out = Image.new("RGB", (W * s * 2 + 10, max(L, H) * s), (20, 20, 24))
    out.paste(top, (0, 0))
    out.paste(side, (W * s + 10, 0))
    import os
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    out.save(PREVIEW)
    print("Wrote", PREVIEW)


if __name__ == "__main__":
    data = build()
    with open(OUT, "wb") as f:
        f.write(gzip.compress(data))
    print("Wrote", OUT, f"({len(grid)} blocks, {len(gnoblars)} gnoblars)")
    preview()
