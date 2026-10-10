#!/usr/bin/env python3
"""Paints the okapi entity texture from the baked model geometry.

Every texel gets its colour from its position on the un-rotated ("flat") model, so the stripes run
on across the body and the rear legs and the coat fades into the pale face along the neck. Vanilla
style: three muted shades per material (cooler shadows, warmer highlights), lighter tops, and
patterns that follow the material (short fur strokes, ordered dither for blends) instead of noise.
Placeholder art: repaint over the result in Blockbench if wanted.

    ./gradlew dumpModel
    python3 -I tools/paint_texture.py [model.json] [out.png]
"""
import json
import math
import sys

import numpy as np
from PIL import Image

MODEL = sys.argv[1] if len(sys.argv) > 1 else "build/preview/model.json"
OUT = sys.argv[2] if len(sys.argv) > 2 else "src/main/resources/assets/okapi/textures/entity/okapi.png"

# name: (u0, v0, w, h, d), the same numbers as the addBox calls in OkapiModel.
PARTS = {
    "body": (0, 0, 10, 10, 20),
    "neck": (62, 0, 4, 14, 4),
    "skull": (80, 0, 6, 6, 6),
    "muzzle": (80, 12, 4, 4, 6),
    "ossicone_a": (104, 0, 2, 3, 2),
    "ossicone_b": (104, 5, 2, 3, 2),
    "ear_a": (112, 0, 4, 5, 1),
    "ear_b": (112, 6, 4, 5, 1),
    "tongue": (80, 24, 2, 1, 12),
    "leg_fr": (0, 32, 4, 14, 4),
    "leg_fl": (16, 32, 4, 14, 4),
    "leg_rr": (32, 32, 4, 14, 4),
    "leg_rl": (48, 32, 4, 14, 4),
    "tail": (62, 20, 2, 9, 2),
}

# Three shades each: dark (cooler shadow), base, light (warmer highlight). Kept muted.
COAT = ((52, 38, 38), (76, 55, 50), (98, 73, 62))
BELLY = ((86, 66, 58), (110, 86, 72), (132, 106, 88))
CREAM = ((148, 138, 118), (200, 190, 166), (226, 218, 194))
FACE = ((96, 86, 78), (126, 114, 100), (150, 136, 118))
MUZZLE = ((40, 32, 34), (58, 46, 46), (78, 62, 58))
HOOF = ((30, 26, 28), (44, 38, 38), (60, 52, 50))
TUFT = ((30, 24, 26), (44, 34, 34), (62, 48, 46))
OSSICONE = ((70, 52, 46), (96, 72, 62), (120, 92, 78))
EAR_INNER = ((126, 86, 84), (156, 110, 104), (180, 134, 124))
TONGUE = ((56, 60, 96), (74, 80, 122), (96, 102, 146))
EYE = (22, 18, 20)
NOSTRIL = (24, 20, 22)

BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))
FACE_LIGHT = {"top": 1.10, "front": 1.0, "side": 1.0, "rear": 0.94, "bottom": 0.84}


def cell(v):
    """Integer cell index of a texel centre (centres sit on half integers for whole-pixel boxes)."""
    return int(math.floor(v + 1e-3))


def smoothstep(e0, e1, x):
    t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def dithered(weight, a, b):
    """True where a blend with the given weight (0..1) should show the second material."""
    return weight > (BAYER[a & 3][b & 3] + 0.5) / 16.0


def shade(palette, which, face):
    k = FACE_LIGHT[face]
    return tuple(min(255, int(v * k)) for v in palette[which])


def fur(palette, face, a, b):
    """Short strands: one light and one dark texel in every seven along a diagonal."""
    s = (a + 3 * b) % 7
    return shade(palette, 0 if s == 0 else 2 if s == 3 else 1, face)


def face_of(normal):
    nx, ny, nz = normal
    if ny < -0.5:
        return "top"
    if ny > 0.5:
        return "bottom"
    if nz < -0.5:
        return "front"
    if nz > 0.5:
        return "rear"
    return "side"


def plane_cells(face, pos):
    """Two cell indices that run along the face, for stroke and dither patterns."""
    x, y, z = (cell(c) for c in pos)
    if face in ("top", "bottom"):
        return z, x
    if face in ("front", "rear"):
        return x, y
    return z, y


def paint_body(face, pos):
    x, y, z = pos
    a, b = plane_cells(face, pos)
    iy = cell(y)
    # stripes on the hindquarters: two cream rows, two dark rows, fading out towards the shoulders
    if face in ("side", "rear") and 3 <= iy <= 8 and (iy - 3) % 4 in (0, 1):
        weight = 1.0 if face == "rear" else smoothstep(-1.0, 7.0, z)
        if dithered(weight, a, b):
            return fur(CREAM, face, a, b)
    if face == "bottom" or iy >= 9:
        return fur(BELLY, face, a, b)
    if face == "top" and abs(x) < 1.0:
        return fur(COAT, face, a, b) if (a + b) % 2 else shade(COAT, 0, face)   # dorsal line
    return fur(COAT, face, a, b)


def paint_leg(name, face, pos):
    x, y, z = pos
    a, b = plane_cells(face, pos)
    gy = int(math.floor(24.0 - y - 1e-3))   # rows above the ground, 0 = hoof
    if gy <= 0:
        return shade(HOOF, 1 if (a + b) % 2 else 0, face)
    if name in ("leg_rr", "leg_rl"):
        if gy <= 11 and gy % 3 != 0:
            return fur(CREAM, face, a, b)
        return fur(COAT, face, a, b) if gy > 11 else shade(COAT, 0 if gy % 3 == 0 else 1, face)
    # front legs: pale socks with a single dark band, dark above
    if gy <= 6 and gy != 3:
        return fur(CREAM, face, a, b)
    return fur(COAT, face, a, b)


def paint_tail(face, ty, a, b):
    if ty >= 6:
        return fur(TUFT, face, a, b)
    return fur(COAT, face, a, b)


def paint_neck(face, local):
    lx, ly, lz = local
    a, b = plane_cells(face, (lx, ly, lz))
    # coat on the lower neck, fading into the pale head colour towards the top
    if dithered(1.0 - ly / 9.0, a, b):
        return fur(FACE, face, a, b)
    if face == "front" or (face == "side" and lz <= 1 and ly > 6):
        return fur(BELLY, face, a, b)
    return fur(COAT, face, a, b)


def paint_skull(face, local):
    lx, ly, lz = local          # lz: 0 at the front of the skull, 5 at the back; ly 0 at the top
    a, b = plane_cells(face, (lx, ly, lz))
    if face == "side":
        if lz == 1 and ly in (2, 3):
            return EYE
        if 0 <= lz <= 2 and 1 <= ly <= 4:
            return shade(COAT, 0 if lz == 1 else 1, face)        # dark patch around the eye
    if face == "top":
        return fur(COAT, face, a, b) if lz >= 2 or dithered(0.5, a, b) else fur(FACE, face, a, b)
    if face == "bottom":
        return fur(BELLY, face, a, b)
    if face == "rear":
        return fur(COAT, face, a, b)
    return fur(FACE, face, a, b)


def paint_muzzle(face, local):
    lx, ly, lz = local          # lz: 0 at the nose, 5 where it meets the skull
    a, b = plane_cells(face, (lx, ly, lz))
    if face == "front":
        if ly == 1 and lx in (0, 3):
            return NOSTRIL
        return shade(MUZZLE, 1 if (a + b) % 2 else 0, face)
    if face == "bottom":
        return fur(CREAM, face, a, b) if lz >= 2 else shade(MUZZLE, 1, face)
    if lz <= 1:
        return shade(MUZZLE, 1 if (a + b) % 2 else 0, face)
    if lz == 2 and dithered(0.5, a, b):
        return shade(MUZZLE, 2, face)
    return fur(FACE, face, a, b)


def paint_ossicone(face, local):
    lx, ly, lz = local
    if ly == 0 or face == "top":
        return shade(TUFT, 1, face)             # dark tip
    return shade(OSSICONE, 2 if (lx + ly) % 2 else 1, face)


def paint_ear(face, local):
    lx, ly, lz = local          # lx 0..3 across, ly 0 at the tip
    if face == "front":
        if 1 <= lx <= 2 and 1 <= ly <= 4:
            return shade(EAR_INNER, 2 if lx == 1 else 1, face)
        return shade(CREAM, 1, face)           # pale hair along the rim
    if face == "rear":
        if ly == 0:
            return shade(COAT, 0, face)
        return fur(COAT, face, lx, ly)
    return shade(COAT, 0, face)


def paint_tongue(face):
    return shade(TONGUE, 2 if face == "top" else 0 if face == "bottom" else 1, face)


def part_of(u, v):
    for name, (u0, v0, w, h, d) in PARTS.items():
        if u0 <= u < u0 + 2 * (w + d) and v0 <= v < v0 + d + h:
            return name
    return None


def main():
    with open(MODEL) as f:
        data = json.load(f)
    W, H = data["texW"], data["texH"]
    img = np.zeros((H, W, 4), np.uint8)

    quads = []
    bounds = {}
    for quad in data["poses"]["flat"]:
        verts = [quad[i * 8:(i + 1) * 8] for i in range(4)]
        cu = sum(v[3] for v in verts) / 4.0
        cv = sum(v[4] for v in verts) / 4.0
        name = part_of(cu, cv)
        if name is None:
            raise ValueError(f"quad at uv {cu},{cv} belongs to no part")
        pts = np.array([v[:3] for v in verts])
        lo, hi = bounds.get(name, (pts.min(0), pts.max(0)))
        bounds[name] = (np.minimum(lo, pts.min(0)), np.maximum(hi, pts.max(0)))
        quads.append((name, verts))

    for name, verts in quads:
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
        face = face_of(verts[0][5:8])
        lo, hi = bounds[name]
        for tv in range(vmin, vmax):
            for tu in range(umin, umax):
                s = (tu + 0.5 - umin) / (umax - umin)
                t = (tv + 0.5 - vmin) / (vmax - vmin)
                pos = p00 + s * (p10 - p00) + t * (p01 - p00)
                # part-local cells: lx across (0 = lowest x), ly down from the top, lz back from the front
                local = (cell(pos[0] - lo[0]), cell(pos[1] - lo[1]), cell(pos[2] - lo[2]))
                if name == "body":
                    c = paint_body(face, pos)
                elif name.startswith("leg_"):
                    c = paint_leg(name, face, pos)
                elif name == "tail":
                    c = paint_tail(face, local[1], *plane_cells(face, pos))
                elif name == "neck":
                    c = paint_neck(face, local)
                elif name == "skull":
                    c = paint_skull(face, local)
                elif name == "muzzle":
                    c = paint_muzzle(face, local)
                elif name.startswith("ossicone"):
                    c = paint_ossicone(face, local)
                elif name.startswith("ear"):
                    c = paint_ear(face, local)
                else:
                    c = paint_tongue(face)
                img[tv, tu] = (*[int(max(0, min(255, v))) for v in c], 255)

    Image.fromarray(img, "RGBA").save(OUT)
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
