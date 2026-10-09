#!/usr/bin/env python3
"""Paints the gnoblar entity texture from the baked model geometry.

Every texel gets its colour from its position on the 3D model, so patterns continue across the
edges of separate boxes. Vanilla style: three muted shades per material (cooler shadows, warmer
highlights), lighter tops, and patterns that follow the material (leathery wrinkles on skin,
stitched leather, woven cloth) instead of random noise. Placeholder art: repaint over the
result in Blockbench if wanted.

    ./gradlew dumpModel
    python3 -I tools/paint_texture.py [model.json] [out.png]
"""
import json
import math
import sys

import numpy as np
from PIL import Image

MODEL = sys.argv[1] if len(sys.argv) > 1 else "build/preview/model.json"
OUT = sys.argv[2] if len(sys.argv) > 2 else "src/main/resources/assets/gnoblars/textures/entity/gnoblar.png"

# Palettes: (dark = cooler shadow, base, light = warmer highlight), kept muted.
SKIN = ((66, 78, 70), (94, 110, 94), (120, 136, 110))
NOSE = ((74, 86, 76), (106, 120, 100), (134, 148, 118))
NOSE_TIP = ((112, 88, 84), (146, 114, 106), (172, 140, 128))
EAR_INNER = ((112, 82, 80), (146, 110, 104), (170, 136, 126))
HAIR = ((40, 38, 38), (62, 56, 52), (84, 76, 66))
LEATHER = ((66, 46, 34), (102, 72, 48), (134, 98, 64))
BELT = ((40, 28, 22), (62, 44, 32), (84, 62, 44))
SASH = ((46, 54, 78), (66, 78, 106), (90, 104, 134))
CLOTH = ((104, 90, 70), (142, 126, 98), (172, 156, 124))
IRIS = (214, 128, 40)
PUPIL = (28, 24, 24)
MOUTH = (44, 28, 30)
TUSK = (228, 220, 192)
BUCKLE = (156, 146, 120)
NOSTRIL = (34, 38, 36)
WART = (128, 122, 94)

FACE_LIGHT = {"top": 1.08, "side": 1.0, "bottom": 0.9}

# Axis-aligned boxes of the unrotated model (pixels, y points down, feet at y = 24).
# Order matters: the first box whose surface contains a quad owns it.
BOXES = [
    ("loincloth", (-2, 2), (21, 23), (-2, -2)),
    ("left_ear", (4, 10), (14, 14), (-3, 1)),
    ("right_ear", (-10, -4), (14, 14), (-3, 1)),
    ("hook", (-2, 2), (16, 18), (-9, -7)),
    ("nose", (-2, 2), (13, 16), (-9, -4)),
    ("left_arm", (3, 5), (16, 24), (-1, 1)),
    ("right_arm", (-5, -3), (16, 24), (-1, 1)),
    ("left_leg", (1, 3), (21, 24), (-1, 1)),
    ("right_leg", (-3, -1), (21, 24), (-1, 1)),
    ("body", (-3, 3), (16, 21), (-2, 2)),
    ("head", (-4, 4), (11, 17), (-4, 2)),
]


def cell(v):
    return int(math.floor(v + 0.5 + 1e-3))


def shade(palette, which, face):
    k = FACE_LIGHT[face]
    return tuple(min(255, int(v * k)) for v in palette[which])


def tint(color, face):
    k = FACE_LIGHT[face]
    return tuple(min(255, int(v * k)) for v in color)


def part_of(verts):
    pts = np.array([v[:3] for v in verts])
    lo, hi = pts.min(0), pts.max(0)
    eps = 0.02
    for name, bx, by, bz in BOXES:
        bounds = (bx, by, bz)
        if not all(lo[a] >= bounds[a][0] - eps and hi[a] <= bounds[a][1] + eps for a in range(3)):
            continue
        # on the surface: some axis where the whole quad sits at one of the box's limits
        for a in range(3):
            if hi[a] - lo[a] < eps and (abs(lo[a] - bounds[a][0]) < eps or abs(lo[a] - bounds[a][1]) < eps):
                return name
    raise ValueError(f"quad belongs to no box: {pts.tolist()}")


def skin(pos, face, palette=SKIN):
    """Leathery skin: a diagonal crease pattern in three shades."""
    x, y, z = (cell(c) for c in pos)
    k = (x * 3 + y * 5 + z * 7) % 9
    return shade(palette, 0 if k == 0 else 2 if k == 4 else 1, face)


def leather(pos, face, palette=LEATHER):
    """Stitched leather: dark seams on a regular grid, light flecks between."""
    x, y, z = (cell(c) for c in pos)
    k = (x * 2 + y * 3 + z * 5) % 7
    return shade(palette, 0 if k == 0 else 2 if k == 3 else 1, face)


def cloth(pos, face, palette=CLOTH):
    """Woven cloth: a checker of two shades."""
    x, y, z = (cell(c) for c in pos)
    return shade(palette, 1 if (x + y + z) % 2 else 2, face)


def paint(part, pos, normal):
    x, y, z = pos
    nx, ny, nz = normal
    face = "top" if ny < -0.5 else "bottom" if ny > 0.5 else "side"
    ax = abs(x)

    if part in ("left_ear", "right_ear"):
        if face == "bottom":
            return shade(EAR_INNER, 1 if (cell(ax) + cell(z)) % 2 else 2, face)
        return shade(SKIN, 0 if ax > 8.5 else 1 if (cell(ax) + cell(z)) % 3 else 2, "side")

    if part == "hook":
        if face == "bottom" and z < -8.0 and abs(ax - 1.5) < 0.1:
            return NOSTRIL
        return skin(pos, face, NOSE_TIP if face == "bottom" else NOSE)

    if part == "nose":
        if face == "top" and abs(x - 0.5) < 0.1 and abs(z + 6.5) < 0.1:
            return WART
        return skin(pos, face, NOSE)

    if part == "loincloth":
        if y > 22.0:
            return shade(CLOTH, 0, "side")      # frayed hem
        return cloth(pos, "side")

    if part in ("left_arm", "right_arm"):
        if y < 17.0:
            return leather(pos, face)            # shoulder strap of the vest
        if 22.0 < y < 23.0:
            return cloth(pos, face)              # wrist wrap
        if y >= 23.0:
            return skin(pos, face, ((46, 56, 50), (72, 86, 72), (98, 112, 88)))   # hands, darker
        return skin(pos, face)

    if part in ("left_leg", "right_leg"):
        if y >= 23.0:
            return cloth(pos, face)              # foot wrap
        if 22.0 < y < 23.0:
            return shade(BELT, 1, face)          # rope tie
        return skin(pos, face)

    if part == "body":
        if face == "bottom":
            return cloth(pos, face)
        if y > 20.0:
            if abs(x) < 1.0 and nz < -0.5:
                return BUCKLE
            return shade(BELT, 1 if (cell(x) + cell(z)) % 2 else 0, face)
        if nz < -0.5:                            # front
            if ax < 1.0 and y < 17.0:
                return skin(pos, face)           # open collar of the vest
            u = (x + 3.0) - 1.2 * (y - 16.0)
            if abs(u) <= 1.0:                    # sash from left shoulder to right hip
                return shade(SASH, 2 if u < -0.4 else 1 if u < 0.6 else 0, face)
        if nz > 0.5:                             # back: a thin sash strap
            u = (x + 3.0) + 1.2 * (y - 16.0) - 6.0
            if abs(u) <= 0.6:
                return shade(SASH, 0, face)
        if face == "top":
            return skin(pos, face)
        return leather(pos, face)

    # head
    if nz < -0.5 and abs(z + 4.0) < 0.1:         # face
        if 11.0 <= y < 12.0 and 1.0 <= ax <= 3.0:
            return shade(SKIN, 0, face)          # heavy brow
        if 12.0 <= y < 13.0 and 1.0 <= ax <= 3.0:
            return PUPIL if ax < 2.0 else IRIS   # eyes
        if 16.0 <= y < 17.0 and ax <= 3.0:
            return TUSK if ax >= 2.0 else MOUTH  # mouth with a tusk at each corner
    if face == "top" and z > 0.0 and ax <= 3.0 and cell(x) % 2 == 0:
        return shade(HAIR, 1, face)   # a few wisps combed back
    if nz > 0.5 and y < 13.0 and ax <= 3.0 and cell(x) % 2 == 0:
        return shade(HAIR, 0, face)
    return skin(pos, face)


def main():
    with open(MODEL) as f:
        data = json.load(f)
    size = data["texSize"]
    img = np.zeros((size, size, 4), np.uint8)

    for quad in data["poses"]["rest"]:
        verts = [quad[i * 8:(i + 1) * 8] for i in range(4)]
        us = [round(v[3]) for v in verts]
        vs = [round(v[4]) for v in verts]
        umin, umax, vmin, vmax = min(us), max(us), min(vs), max(vs)
        if umax == umin or vmax == vmin:
            continue

        def corner(u, v):
            for vert in verts:
                if round(vert[3]) == u and round(vert[4]) == v:
                    return np.array(vert[:3])
            raise ValueError("quad is not an axis-aligned UV rectangle")

        part = part_of(verts)
        p00, p10, p01 = corner(umin, vmin), corner(umax, vmin), corner(umin, vmax)
        normal = quad[5:8]
        for tv in range(vmin, vmax):
            for tu in range(umin, umax):
                s = (tu + 0.5 - umin) / (umax - umin)
                t = (tv + 0.5 - vmin) / (vmax - vmin)
                pos = p00 + s * (p10 - p00) + t * (p01 - p00)
                c = paint(part, pos, normal)
                img[tv, tu] = (*[int(max(0, min(255, v))) for v in c], 255)

    Image.fromarray(img, "RGBA").save(OUT)
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
