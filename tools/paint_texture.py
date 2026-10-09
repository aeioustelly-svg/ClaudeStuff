#!/usr/bin/env python3
"""Paints the Draco entity texture from the baked model geometry.

Every texel gets its colour from its position on the 3D model (not from its place on the
unfolded sheet), so fur fades into scales along the neck and colours stay continuous across the
edges of separate boxes. Vanilla style: a few muted shades per material (cooler shadows, warmer
highlights), lighter tops, and patterns that follow the material (fur strokes, staggered scales)
instead of random noise. Placeholder art: repaint over the result in Blockbench if wanted.

    ./gradlew dumpModel
    python3 tools/paint_texture.py [model.json] [out.png]
"""
import json
import math
import sys

import numpy as np
from PIL import Image

MODEL = sys.argv[1] if len(sys.argv) > 1 else "build/preview/model.json"
OUT = sys.argv[2] if len(sys.argv) > 2 else "src/main/resources/assets/dacian_draco/textures/entity/draco.png"

# Model-space layout (pixels, y points down): the body axis sits at y = AXIS_Y.
AXIS_Y = 17.0
HEAD_Z = -14.5          # back of the skull
CLOTH_Z = 26.5          # where the cloth streamer starts

# Palettes: three shades each (dark = cooler shadow, base, light = warmer highlight), kept muted.
SCALE = ((54, 68, 56), (84, 98, 66), (118, 126, 82))
BRONZE = ((106, 78, 48), (150, 112, 58), (182, 142, 78))
CREAM = ((152, 140, 108), (190, 176, 134), (216, 204, 162))
FUR = ((84, 78, 74), (120, 108, 92), (152, 138, 116))
FUR_BACK = ((66, 62, 62), (98, 88, 78), (126, 112, 96))
FUR_CREAM = ((160, 148, 122), (198, 184, 152), (220, 208, 176))
CLOTH_RED = ((98, 34, 34), (140, 46, 40), (170, 66, 54))
CLOTH_OCHRE = ((140, 106, 56), (184, 144, 76), (212, 176, 104))
EYE = ((176, 120, 30), (208, 152, 40), (236, 200, 84))
NOSE = (30, 26, 28)
LIP = (78, 56, 54)
TOOTH = (232, 224, 200)
EAR_INNER = (150, 92, 84)

BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))
FACE_LIGHT = {"top": 1.08, "side": 1.0, "bottom": 0.92}


def cell(v):
    """Integer cell index of a texel centre, unique for consecutive texels."""
    return int(math.floor(v + 0.5 + 1e-3))


def smoothstep(e0, e1, x):
    t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def fur_weight(z):
    """1 on the head, fading through the neck, gone by the front of the body."""
    if z <= HEAD_Z:
        return 1.0
    if z <= -9.5:
        return 1.0 - 0.4 * (z - HEAD_Z) / 5.0
    if z <= -3.5:
        return 0.6 - 0.35 * (z + 9.5) / 6.0
    return 0.25 * (1.0 - smoothstep(-3.5, 3.0, z))


def radius_at(z):
    """Rough half-thickness of the body at z, used to find the lower flank."""
    if z < -3.5:
        return 2.5
    for limit, r in ((3.5, 3.0), (9.5, 2.5), (15.5, 2.0), (21.5, 1.5)):
        if z < limit:
            return r
    return 1.0


def shade(palette, which, face):
    c = palette[which]
    k = FACE_LIGHT[face]
    return tuple(min(255, int(v * k)) for v in c)


def paint_point(pos, normal, tu, tv):
    x, y, z = pos
    hy = y - AXIS_Y                     # negative = towards the back/top of the animal
    nx, ny, nz = normal
    face = "top" if ny < -0.5 else "bottom" if ny > 0.5 else "side"
    za = cell(z)
    cc = cell(x) if face != "side" else cell(hy)

    # ---- cloth streamer: two muted colours in bands, with a darker lower edge ----
    if z >= CLOTH_Z - 0.01:
        palette = CLOTH_OCHRE if (za // 2) % 2 == 0 else CLOTH_RED
        which = 0 if (face == "bottom" or hy > 1.0) else 2 if face == "top" else 1
        return palette[which]

    in_head = z < HEAD_Z + 0.01

    # ---- head details placed by 3D position ----
    if in_head:
        on_front = nz < -0.5
        if on_front and HEAD_Z - 5.1 < z < HEAD_Z - 4.9:
            # skull front face: eyes sit either side above the snout, with a dark brow
            for sign in (-1, 1):
                cx = x * sign
                if 1.0 <= cx <= 3.0 and -2.0 <= hy <= -1.0:
                    return EYE[2] if cx < 2.0 else EYE[1]
                if 1.0 <= cx <= 3.0 and -3.0 <= hy < -2.0:
                    return FUR_BACK[0]
        if on_front and z < HEAD_Z - 8.9:
            # snout front: nose on top, teeth at the corners
            if -0.5 <= hy <= 0.5 and abs(x) <= 1.0:
                return NOSE
            if hy >= 1.5 and 1.0 <= abs(x) <= 2.0:
                return TOOTH
            if hy >= 1.5:
                return LIP
        if z < HEAD_Z - 4.9 and 1.5 <= hy <= 2.6 and abs(nx) > 0.5:
            # lip line along the side of the snout, with a tooth now and then
            return TOOTH if za % 3 == 0 else LIP
        if hy < -3.0 and z > HEAD_Z - 2.1:
            # ears: fur outside, a pink hollow on the inner column
            if nz < -0.5 and hy > -5.0 and 1.0 <= abs(x) <= 2.0:
                return EAR_INNER
            return shade(FUR_BACK, 1 if (za + cc) % 3 else 0, face)

    # ---- fur / scale mix: ordered dither, so the transition is a pattern and not noise ----
    r = radius_at(z)
    belly = face == "bottom" or hy > 0.35 * r
    back = face == "top" or hy < -0.35 * r
    is_fur = fur_weight(z) > (BAYER[cc & 3][za & 3] + 0.5) / 16.0

    if is_fur:
        palette = FUR_CREAM if belly else FUR_BACK if back else FUR
        stroke = (za + 3 * cc) % 7           # short diagonal strands along the body
        return shade(palette, 0 if stroke == 0 else 2 if stroke == 3 else 1, face)

    # scales: 2x2 cells, every second row staggered, lit from the top left
    sz = za // 2
    stagger = sz % 2
    lx, ly = za % 2, (cc + stagger) % 2
    which = 2 if (lx == 0 and ly == 0) else 0 if (lx == 1 and ly == 1) else 1
    if belly:
        palette = CREAM
    elif face == "top" and abs(x) < 1.5 and sz % 3 == 0:
        palette = BRONZE                      # dorsal crest of bronze scales
    else:
        palette = SCALE
    return shade(palette, which, face)


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

        p00, p10, p01 = corner(umin, vmin), corner(umax, vmin), corner(umin, vmax)
        normal = quad[5:8]
        for tv in range(vmin, vmax):
            for tu in range(umin, umax):
                s = (tu + 0.5 - umin) / (umax - umin)
                t = (tv + 0.5 - vmin) / (vmax - vmin)
                pos = p00 + s * (p10 - p00) + t * (p01 - p00)
                c = paint_point(pos, normal, tu, tv)
                img[tv, tu] = (*[int(max(0, min(255, v))) for v in c], 255)

    Image.fromarray(img, "RGBA").save(OUT)
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
