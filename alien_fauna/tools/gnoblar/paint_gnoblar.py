#!/usr/bin/env python3
"""Paints the gnoblar entity texture from the baked model geometry.

Every texel gets its colour from its position on the 3D model, so patterns continue across the
edges of separate boxes. Vanilla style: three muted shades per material (cooler shadows, warmer
highlights), lighter tops, and patterns that follow the material (leathery wrinkles on skin,
stitched leather, woven cloth) instead of random noise. Placeholder art: repaint over the
result in Blockbench if wanted.

    ./gradlew dumpGnoblar
    python3 -I tools/gnoblar/paint_gnoblar.py [model.json] [output directory]

Paints one texture per variant (see VARIANTS; the names must match GnoblarVariant.java), plus for each a grey mask of the part
of the outfit that dye colours (gnoblar_sash_<id>.png, tinted in the game), and one mud overlay for all (gnoblar_mud.png). The geometry is the same for all,
so only the colours, the sash and the chest patch differ. Wart cubes share one texture patch, so a wart looks the same on
every variant of a skin colour; which cube shows is decided by the model.
"""
import json
import math
import sys

import numpy as np
from PIL import Image

MODEL = sys.argv[1] if len(sys.argv) > 1 else "build/preview/gnoblar.json"
OUT_DIR = sys.argv[2] if len(sys.argv) > 2 else "src/main/resources/assets/alien_fauna/textures/entity/"

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

# The looks. Each palette is (dark, base, light), kept muted. sash_dir: +1 sash from the left shoulder to the right hip,
# -1 the other way, 0 no sash (a stitched patch on the chest instead).
VARIANTS = {
    "green": dict(skin=((66, 78, 70), (94, 110, 94), (120, 136, 110)), leather=((66, 46, 34), (102, 72, 48), (134, 98, 64)),
                  sash=((46, 54, 78), (66, 78, 106), (90, 104, 134)), cloth=((104, 90, 70), (142, 126, 98), (172, 156, 124)),
                  iris=(214, 128, 40), sash_dir=1),
    "mossy": dict(skin=((44, 58, 48), (66, 84, 62), (90, 108, 76)), leather=((52, 44, 30), (82, 68, 44), (112, 92, 58)),
                  sash=((52, 66, 52), (72, 92, 70), (96, 116, 88)), cloth=((88, 84, 62), (120, 116, 84), (150, 146, 106)),
                  iris=(220, 190, 70), sash_dir=1),
    "rusty": dict(skin=((92, 64, 42), (138, 96, 56), (170, 126, 74)), leather=((54, 38, 28), (82, 58, 40), (110, 80, 54)),
                  sash=((86, 36, 32), (122, 52, 44), (152, 74, 60)), cloth=((120, 104, 84), (156, 138, 110), (186, 168, 138)),
                  iris=(120, 150, 60), sash_dir=1),
    "bark": dict(skin=((70, 56, 44), (104, 86, 64), (134, 114, 86)), leather=((46, 42, 40), (70, 64, 60), (96, 88, 82)),
                 sash=((120, 92, 44), (160, 126, 60), (190, 156, 86)), cloth=((96, 100, 92), (130, 134, 124), (160, 164, 152)),
                 iris=(200, 90, 40), sash_dir=-1),
    "pickle": dict(skin=((78, 84, 44), (112, 120, 62), (142, 150, 84)), leather=((70, 50, 34), (106, 76, 50), (138, 102, 66)),
                   sash=((74, 58, 86), (98, 80, 112), (124, 104, 138)), cloth=((110, 96, 72), (146, 130, 100), (176, 160, 128)),
                   iris=(190, 60, 44), sash_dir=-1),
    "sooty": dict(skin=((44, 50, 48), (66, 74, 70), (90, 98, 90)), leather=((38, 30, 24), (58, 46, 36), (80, 64, 50)),
                  sash=((86, 36, 32), (122, 52, 44), (152, 74, 60)), cloth=((98, 54, 46), (134, 76, 62), (164, 100, 82)),
                  iris=(230, 170, 50), sash_dir=0),
}


def lighten(palette, add):
    return tuple(tuple(min(255, c + a) for c, a in zip(shade_rgb, add)) for shade_rgb in palette)


def apply_variant(v):
    """Sets the colour globals every painting function reads."""
    global SKIN, NOSE, HAND, HAIR, LEATHER, BELT, SASH, CLOTH, IRIS, WART_SHADES, SASH_DIR
    SKIN = v["skin"]
    NOSE = lighten(SKIN, (30, 32, 20))
    HAND = tuple(tuple(int(c * 0.72) for c in shade_rgb) for shade_rgb in SKIN)
    LEATHER = v["leather"]
    BELT = tuple(tuple(int(c * 0.6) for c in shade_rgb) for shade_rgb in LEATHER)
    SASH = v["sash"]
    CLOTH = v["cloth"]
    IRIS = v["iris"]
    SASH_DIR = v["sash_dir"]
    WART_SHADES = lighten(SKIN, (34, 18, -4))


apply_variant(VARIANTS["green"])

# Axis-aligned boxes of the unrotated model (pixels, y points down, feet at y = 24).
# Order matters: the first box whose surface contains a quad owns it.
BOXES = [
    ("loincloth", (-2, 2), (21, 23), (-2, -1)),
    ("lear_a", (4, 7), (10, 14), (-1, 0)),
    ("lear_b", (7, 9), (9, 12), (-1, 0)),
    ("lear_c", (9, 10), (8, 10), (-1, 0)),
    ("rear_a", (-7, -4), (10, 14), (-1, 0)),
    ("rear_b", (-9, -7), (9, 12), (-1, 0)),
    ("rear_c", (-10, -9), (8, 10), (-1, 0)),
    ("wart_nose", (0, 1), (9, 10), (-6, -5)),
    ("wart_nose_side", (2, 3), (12, 13), (-6, -5)),
    ("wart_cheek", (3, 4), (13, 14), (-5, -4)),
    ("wart_forehead", (-3, -2), (8, 9), (-3, -2)),
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


def skin(pos, face, palette=None):
    """Leathery skin: a diagonal crease pattern in three shades."""
    palette = palette or SKIN
    x, y, z = (cell(c) for c in pos)
    k = (x * 3 + y * 5 + z * 7) % 11
    return shade(palette, 0 if k == 0 else 2 if k == 5 else 1, face)


def leather(pos, face, palette=None):
    """Stitched leather: dark seams on a regular grid, light flecks between."""
    palette = palette or LEATHER
    x, y, z = (cell(c) for c in pos)
    k = (x * 2 + y * 3 + z * 5) % 7
    return shade(palette, 0 if k == 0 else 2 if k == 3 else 1, face)


def cloth(pos, face, palette=None):
    """Woven cloth: a checker of two shades."""
    palette = palette or CLOTH
    x, y, z = (cell(c) for c in pos)
    return shade(palette, 1 if (x + y + z) % 2 else 2, face)


def darker(color, k):
    return tuple(int(v * k) for v in color)


def paint_ear(part, pos, normal):
    """Pointed ear, a 1 px box of which only the FRONT face is painted, as vanilla does for a chicken's leg.
    Every other face stays transparent, so the ear is one flat sheet. The sheet is seen from the front and from
    behind (mirrored), so it is two-toned the same way on both: a dark skin rim along the top and tip, a skin
    row under it, then pink with a darker vein climbing to the tip. One nick is bitten out of the lower edge."""
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
        return shade(SKIN, 0, "side")                # rim and tip
    if y < top + 2.0 and abs(y - vein) > 0.1:
        return shade(SKIN, 2 if (cell(ax) + cell(y)) % 2 else 1, "side")   # skin row under the rim
    if abs(y - vein) < 0.1:
        return shade(EAR_INNER, 0, "side")           # vein
    return shade(EAR_INNER, 2 if bottom_row else 1, "side")


HEAD_PARTS = ("head", "nose")
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
        return skin(pos, face, HAND)

    if part.startswith("wart"):
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
            return skin(pos, face, HAND) if (cell(x) + cell(z)) % 2 else shade(HAND, 0, face)
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
            u = (x * SASH_DIR + 4.0) - 1.6 * (y - 16.0)
            if SASH_DIR and abs(u) <= 1.0:          # sash across the chest, the way round this variant wears it
                return shade(SASH, 2 if u < -0.4 else 1 if u < 0.6 else 0, face)
            if not SASH_DIR and ax <= 3.0 and 17.0 <= y <= 19.0:
                edge = ax > 2.0 or y < 18.0          # no sash: a stitched patch on the chest instead
                return shade(BELT, 1, face) if edge else shade(LEATHER, 2, face)
            if abs(ax - 3.5) < 0.1 and abs(y - 18.5) < 0.1:
                return BUCKLE                       # a rivet
        if nz > 0.5:                                # back
            u = (x * SASH_DIR + 4.0) + 1.6 * (y - 16.0) - 8.0
            if SASH_DIR and abs(u) <= 0.6:
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


GREY = ((150, 150, 150), (200, 200, 200), (240, 240, 240))
MUD = ((62, 46, 32), (88, 66, 46), (112, 86, 58))


def hash100(x, y, z):
    """A fixed pseudo-random 0..99 per block position: splotches that do not change between runs."""
    return (cell(x) * 73 + cell(y) * 151 + cell(z) * 257 + cell(x) * cell(z) * 13) % 100


def paint_sash_mask(part, pos, normal):
    """The part of the outfit that dye colours, in grey, to be tinted when drawn: the sash and its back strap, or for the
    variant without a sash (sooty) the wrist and foot wraps."""
    x, y, z = pos
    nx, ny, nz = normal
    face = "top" if ny < -0.5 else "bottom" if ny > 0.5 else "side"
    ax = abs(x)
    if SASH_DIR and part == "body" and face != "bottom":
        if y > 20.0 or (nx > 0.5 and y > 19.0 and abs(z) < 1.0):
            return None                              # belt and pouch stay as they are
        if nz < -0.5:
            if ax < 1.0:
                return None                          # collar and lacing
            u = (x * SASH_DIR + 4.0) - 1.6 * (y - 16.0)
            if abs(u) <= 1.0:
                return shade(GREY, 2 if u < -0.4 else 1 if u < 0.6 else 0, face)
        if nz > 0.5:
            u = (x * SASH_DIR + 4.0) + 1.6 * (y - 16.0) - 8.0
            if abs(u) <= 0.6:
                return shade(GREY, 0, face)
        return None
    if not SASH_DIR:
        if part in ("left_arm", "right_arm") and 22.0 < y < 23.0:
            return cloth(pos, face, GREY)
        if part in ("left_leg", "right_leg") and y >= 23.0 and face != "bottom":
            return cloth(pos, face, GREY)
    return None


def paint_mud(part, pos, normal):
    """Splotches of mud on the legs, hands, belly and chin. Everything else stays transparent."""
    x, y, z = pos
    nx, ny, nz = normal
    face = "top" if ny < -0.5 else "bottom" if ny > 0.5 else "side"
    h = hash100(x, y, z)
    tone = h % 3
    if part in ("left_leg", "right_leg"):
        if face != "top" and (h < 75 if y >= 22.0 else h < 30):
            return shade(MUD, tone, face)
    elif part in ("left_arm", "right_arm"):
        if face != "top" and (h < 70 if y >= 21.5 else h < 22):
            return shade(MUD, tone, face)
    elif part == "body":
        if face != "top" and y > 19.5 and h < 45:
            return shade(MUD, tone, face)
    elif part == "nose":
        wy = y
        if face != "top" and wy > 14.5 and h < 55:
            return shade(MUD, tone, face)
    elif part == "head":
        if nz < -0.5 and abs(z + 4.0) < 0.1 and y > 13.5 and abs(x) > 2.0 and h < 45:
            return shade(MUD, tone, face)      # a smear on each cheek
    elif part == "loincloth":
        if nz < -0.5 and y > 21.5 and h < 60:
            return shade(MUD, tone, "side")
    return None


def paint_image(data, painter=None):
    painter = painter or paint
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
                c = painter(part, pos, normal)
                if c is None:
                    continue              # transparent: a nick or gap in a flat part
                img[tv, tu] = (*[int(max(0, min(255, v))) for v in c], 255)
    return img


def main():
    with open(MODEL) as f:
        data = json.load(f)
    for name, variant in VARIANTS.items():
        apply_variant(variant)
        path = f"{OUT_DIR.rstrip('/')}/gnoblar_{name}.png"
        Image.fromarray(paint_image(data), "RGBA").save(path)
        print("Wrote", path)
        sash_path = f"{OUT_DIR.rstrip('/')}/gnoblar_sash_{name}.png"
        Image.fromarray(paint_image(data, paint_sash_mask), "RGBA").save(sash_path)
        print("Wrote", sash_path)
    mud_path = f"{OUT_DIR.rstrip('/')}/gnoblar_mud.png"
    Image.fromarray(paint_image(data, paint_mud), "RGBA").save(mud_path)
    print("Wrote", mud_path)


if __name__ == "__main__":
    main()
