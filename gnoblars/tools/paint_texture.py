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
NOSE = ((92, 106, 90), (128, 144, 112), (158, 172, 132))
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
WART_SHADES = ((112, 98, 72), (142, 126, 90), (172, 152, 108))

FACE_LIGHT = {"top": 1.08, "side": 1.0, "bottom": 0.9}

# Axis-aligned boxes of the unrotated model (pixels, y points down, feet at y = 24).
# Order matters: the first box whose surface contains a quad owns it.
BOXES = [
    ("loincloth", (-2, 2), (21, 23), (-3, -2)),
    ("lear_a", (4, 7), (10, 14), (-1, 0)),
    ("lear_b", (7, 9), (9, 12), (-1, 0)),
    ("lear_c", (9, 10), (8, 10), (-1, 0)),
    ("rear_a", (-7, -4), (10, 14), (-1, 0)),
    ("rear_b", (-9, -7), (9, 12), (-1, 0)),
    ("rear_c", (-10, -9), (8, 10), (-1, 0)),
    ("wart", (0, 1), (9, 10), (-6, -5)),
    ("nose", (-2, 2), (10, 16), (-7, -4)),
    ("neck", (-1, 1), (15, 16), (-2, 0)),
    ("left_arm", (4, 6), (17, 24), (-1, 1)),
    ("right_arm", (-6, -4), (17, 24), (-1, 1)),
    ("left_leg", (1, 3), (21, 24), (-1, 1)),
    ("right_leg", (-3, -1), (21, 24), (-1, 1)),
    ("body", (-4, 4), (16, 21), (-2, 2)),
    ("head", (-4, 4), (9, 15), (-4, 2)),
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
                # and it must span the box's whole face, or a neighbour's face on the same plane (a leg's side
                # at the edge of the arm's range) would be taken for this box's
                others = [b for b in range(3) if b != a]
                if all(abs((hi[b] - lo[b]) - (bounds[b][1] - bounds[b][0])) < 2 * eps for b in others):
                    return name
    raise ValueError(f"quad belongs to no box: {pts.tolist()}")


def skin(pos, face, palette=SKIN):
    """Leathery skin: a diagonal crease pattern in three shades."""
    x, y, z = (cell(c) for c in pos)
    k = (x * 3 + y * 5 + z * 7) % 11
    return shade(palette, 0 if k == 0 else 2 if k == 5 else 1, face)


def leather(pos, face, palette=LEATHER):
    """Stitched leather: dark seams on a regular grid, light flecks between."""
    x, y, z = (cell(c) for c in pos)
    k = (x * 2 + y * 3 + z * 5) % 7
    return shade(palette, 0 if k == 0 else 2 if k == 3 else 1, face)


def cloth(pos, face, palette=CLOTH):
    """Woven cloth: a checker of two shades."""
    x, y, z = (cell(c) for c in pos)
    return shade(palette, 1 if (x + y + z) % 2 else 2, face)


def darker(color, k):
    return tuple(int(v * k) for v in color)


def paint_ear(part, pos, normal):
    """Pointed ear, a 1 px box of which only the FRONT face is painted, as vanilla does for a chicken's leg.
    Every other face stays transparent, so the ear is one flat sheet (pink, with a vein climbing to the tip)
    and no two visible faces share a place. One nick is bitten out of the lower edge."""
    if normal[2] > -0.5:
        return None                                  # back and edges: transparent
    piece = part[-1]
    x, y, z = pos
    ax = abs(x)
    top, bottom = {"a": (10, 14), "b": (9, 12), "c": (8, 10)}[piece]
    vein = {"a": 12.5, "b": 10.5, "c": 8.5}[piece]
    top_row = y < top + 1.0
    bottom_row = y > bottom - 1.0
    if piece == "b" and bottom_row and 8.0 < ax < 9.0:
        return None                                  # the nick
    if piece == "c" or top_row:
        return shade(EAR_INNER, 0, "side")
    if abs(y - vein) < 0.1:
        return shade(EAR_INNER, 0, "side")
    return shade(EAR_INNER, 2 if bottom_row else 1, "side")


HEAD_PARTS = ("head", "nose", "wart")
HEAD_RAISE = 2.0     # the face rows below are written for a head 2 px lower than it now sits


def paint(part, pos, normal):
    x, y, z = pos
    if part in HEAD_PARTS or part[1:4] == "ear":
        y += HEAD_RAISE
    nx, ny, nz = normal
    face = "top" if ny < -0.5 else "bottom" if ny > 0.5 else "side"
    ax = abs(x)

    if part[1:4] == "ear" and len(part) == 6:
        return paint_ear(part, pos, normal)

    if part == "neck":
        return skin(pos, face, ((46, 56, 50), (72, 86, 72), (98, 112, 88)))

    if part == "wart":
        return shade(WART_SHADES, 2 if face == "top" else 0 if face == "bottom" else 1, "side")

    if part == "nose":
        wy = y - HEAD_RAISE                          # true height; the face rows above are written for a lower head
        if face == "bottom":
            return shade(NOSE, 0, face)
        if nz < -0.5 and abs(z + 7.0) < 0.1:         # the front: smooth, a highlight at the top, a shaded tip
            if wy < 11.0 and ax < 1.0:
                return shade(NOSE, 2, face)
            if wy > 15.0:
                return shade(NOSE, 0, face)
            return skin(pos, face, NOSE)
        if abs(nx) > 0.5:                            # the sides: a nostril with a flare crease above it
            if abs(z + 6.5) < 0.1 and abs(wy - 14.5) < 0.1:
                return NOSTRIL
            if abs(z + 6.5) < 0.1 and abs(wy - 13.5) < 0.1:
                return shade(NOSE, 0, face)
            if wy > 15.0:
                return shade(NOSE, 0, face)
        return skin(pos, face, NOSE)

    if part == "loincloth":
        if nz > -0.5:
            return None                             # only the front face is painted (like a chicken's leg)
        if y > 22.0:
            return shade(CLOTH, 0, "side")          # frayed hem
        if ax < 1.0 and nz < -0.5:
            return shade(SASH, 1, "side")           # a stripe in the sash's colour on the front
        return cloth(pos, "side")

    if part in ("left_arm", "right_arm"):
        if y < 18.0:
            return leather(pos, face)               # shoulder strap of the vest
        if 22.0 < y < 23.0:
            return cloth(pos, face)                 # wrist wrap
        if y >= 23.0:
            hand = ((46, 56, 50), (72, 86, 72), (98, 112, 88))
            return skin(pos, face, hand) if (cell(x) + cell(z)) % 2 else shade(hand, 0, face)
        if part == "left_arm" and nz < -0.5 and abs(z + 1.0) < 0.1 and (
                (abs(x - 4.5) < 0.1 and abs(y - 20.5) < 0.1) or (abs(x - 5.5) < 0.1 and abs(y - 21.5) < 0.1)):
            return (168, 158, 134)                  # an old scar
        return skin(pos, face)

    if part in ("left_leg", "right_leg"):
        if y >= 23.0:
            return cloth(pos, face) if face != "bottom" else shade(BELT, 0, face)   # foot wrap and sole
        if 22.0 < y < 23.0:
            return shade(BELT, 1, face)             # rope tie
        return skin(pos, face)

    if part == "body":
        if face == "bottom":
            return cloth(pos, face)
        if y > 20.0:
            if abs(x) < 1.0 and nz < -0.5:
                return BUCKLE
            return shade(BELT, 1 if (cell(x) + cell(z)) % 2 else 0, face)
        if nx > 0.5 and y > 19.0 and abs(z) < 1.0:
            return shade(LEATHER, 0 if y < 19.9 and abs(z + 0.5) < 0.1 else 2, face)   # belt pouch flap
        if nz < -0.5:                               # front
            if ax < 1.0 and y < 17.0:
                return skin(pos, face)              # open collar of the vest
            if ax < 1.0:
                return shade(BELT, 0 if cell(y) % 2 else 2, face)    # lacing
            u = (x + 4.0) - 1.6 * (y - 16.0)
            if abs(u) <= 1.0:                       # sash from left shoulder to right hip
                return shade(SASH, 2 if u < -0.4 else 1 if u < 0.6 else 0, face)
            if abs(ax - 3.5) < 0.1 and abs(y - 18.5) < 0.1:
                return BUCKLE                       # a rivet
        if nz > 0.5:                                # back
            u = (x + 4.0) + 1.6 * (y - 16.0) - 8.0
            if abs(u) <= 0.6:
                return shade(SASH, 0, face)         # thin sash strap
            if -2.0 <= x <= 2.0 and 17.0 <= y <= 19.0:
                edge = abs(x) > 1.0 or y < 18.0
                return shade(BELT, 1, face) if edge else shade(LEATHER, 2, face)   # a stitched patch
        if face == "top":
            return skin(pos, face)
        return leather(pos, face)

    # head
    jaw = 0.9 if y >= 16.0 else 1.0
    if nz < -0.5 and abs(z + 4.0) < 0.1:            # face
        if 11.0 <= y < 12.0 and ax >= 1.0:
            return shade(SKIN, 0, face)             # heavy brow
        if 12.0 <= y < 13.0:
            if 2.0 <= ax < 4.0:
                return PUPIL if ax < 3.0 else IRIS   # eyes, set wide
            if ax < 2.0 and ax >= 1.0:
                return shade(SKIN, 0, face)
        if 13.0 <= y < 14.0 and 2.0 <= ax < 4.0:
            return shade(SKIN, 0, face)             # bags under the eyes
        if 14.0 <= y < 15.0 and ax >= 3.0:
            return shade(SKIN, 0, face)             # a crease down each cheek
        if 16.0 <= y < 17.0 and ax <= 3.0:
            return TUSK if ax >= 2.0 else MOUTH     # mouth with a tusk at each corner
        if 16.0 <= y < 17.0:
            return shade(SKIN, 0, face)
    if face == "side" and abs(ax - 4.0) < 0.1 and 14.0 <= y < 15.0 and -3.0 <= z < -1.0:
        return shade(SKIN, 0, face)                 # the ear hole
    if face == "top" and z > 0.0 and ax <= 3.0 and cell(x) % 2 == 0:
        return shade(HAIR, 1, face)                 # a few wisps combed back
    if nz > 0.5 and y < 13.0 and ax <= 3.0 and cell(x) % 2 == 0:
        return shade(HAIR, 0, face)
    color = skin(pos, face)
    return darker(color, jaw) if jaw < 1.0 else color


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
                if c is None:
                    continue              # transparent: a nick or gap in a flat part
                img[tv, tu] = (*[int(max(0, min(255, v))) for v in c], 255)

    Image.fromarray(img, "RGBA").save(OUT)
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
