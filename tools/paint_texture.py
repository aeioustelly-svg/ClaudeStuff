#!/usr/bin/env python3
"""Paints the Draco entity texture from the baked model geometry.

Every texel gets its colour from its position on the 3D model (not from its place on the
unfolded sheet), so fur fades into scales along the neck and colours stay continuous across the
edges of separate boxes. Placeholder art: repaint over the result in Blockbench if wanted.

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

FUR_BASE = np.array([108, 98, 84], float)
FUR_BACK = np.array([78, 70, 62], float)
CREAM = np.array([200, 184, 140], float)
SCALE_BASE = np.array([76, 90, 60], float)
SCALE_DARK = np.array([48, 62, 44], float)
BRONZE = np.array([178, 124, 46], float)
CLOTH_RED = np.array([150, 30, 28], float)
CLOTH_OCHRE = np.array([206, 154, 52], float)
EYE_OUTER = np.array([214, 150, 24], float)
EYE_INNER = np.array([244, 206, 70], float)
NOSE = np.array([24, 20, 20], float)
LIP = np.array([66, 44, 44], float)
TOOTH = np.array([238, 230, 208], float)
EAR_INNER = np.array([138, 70, 70], float)


def hash01(a, b, salt=0):
    h = (int(a) * 73856093) ^ (int(b) * 19349663) ^ (salt * 83492791)
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return (h & 0xFFFF) / 65535.0


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


def paint_point(pos, normal, tu, tv):
    x, y, z = pos
    hy = y - AXIS_Y                     # negative = towards the back/top of the animal
    nx, ny, nz = normal

    # ---- cloth streamer ----
    if z >= CLOTH_Z - 0.01:
        stripe = int(math.floor((z - CLOTH_Z) / 2.0)) % 2
        base = CLOTH_OCHRE if stripe == 0 else CLOTH_RED
        return base * (0.86 + 0.14 * hash01(tu, tv, 5))

    in_head = z < HEAD_Z + 0.01

    # ---- head details placed by 3D position ----
    if in_head:
        on_front = nz < -0.5
        if on_front and z < HEAD_Z - 4.9 and z > HEAD_Z - 5.1:
            # skull front face: eyes sit either side above the snout
            for sign in (-1, 1):
                cx = x * sign
                if 1.0 <= cx <= 3.0 and -2.0 <= hy <= -1.0:
                    return EYE_INNER if cx < 2.0 else EYE_OUTER
                if 1.0 <= cx <= 3.0 and -3.0 <= hy < -2.0:
                    return FUR_BACK * 0.75            # brow
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
            return TOOTH if int(math.floor(z)) % 3 == 0 else LIP
        if hy < -3.0 and z > HEAD_Z - 2.1:
            # ears
            if nz < -0.5 and hy > -5.0 and 1.0 <= abs(x) <= 2.0:
                # pink only in the hollow, on the column nearest the middle of the head
                return EAR_INNER * (0.9 + 0.1 * hash01(tu, tv, 9))
            return FUR_BACK * (0.85 + 0.3 * hash01(tu, tv, 4))

    # ---- fur / scale mix ----
    belly = 1.0 if ny > 0.5 else smoothstep(0.5, 2.8, hy)
    is_fur = hash01(tu, tv, 1) < fur_weight(z)

    if is_fur:
        back = 1.0 - smoothstep(-2.5, 0.5, hy)               # darker saddle along the back
        base = FUR_BASE * (1 - back) + FUR_BACK * back
        if hash01(tu, tv, 2) < belly:
            base = CREAM * (0.88 + 0.12 * hash01(tu, tv, 3))
        return base * (0.86 + 0.28 * hash01(tu, tv, 6))

    base = SCALE_BASE
    if (tu + tv) % 4 == 0:
        base = SCALE_DARK * 1.1
    elif (tu - tv) % 6 == 0:
        base = SCALE_BASE * 0.82
    if hash01(tu, tv, 7) < 0.06:
        base = BRONZE
    if hash01(tu, tv, 8) < belly * 0.92:
        base = CREAM * (0.9 + 0.1 * hash01(tu, tv, 3)) * (0.92 if (tu % 3 == 0) else 1.0)
    return base


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
                img[tv, tu] = (*np.clip(c, 0, 255).astype(np.uint8), 255)

    Image.fromarray(img, "RGBA").save(OUT)
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
