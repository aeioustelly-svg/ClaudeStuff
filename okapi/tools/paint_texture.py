#!/usr/bin/env python3
"""Paints the okapi entity texture from the baked model geometry.

Every texel gets its colour from its position on the un-rotated ("flat") model, so the stripes run
on across the body and the rear legs and the coat fades into the pale face along the neck. Vanilla
style: flat areas of a few muted shades (cooler shadows, warmer highlights), lighter tops, and each
body part painted for what it is. No repeated fur pattern and no speckle: dither is used only to
soften the edge between two regions.
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
# The ears and the tongue are flat planes (zero thickness), so both of their sides share one texture.
# The tongue is two crossed planes, so it stays visible from the side and from above.
PARTS = {
    "body": (0, 0, 10, 10, 20),
    "neck": (62, 0, 4, 14, 4),
    "skull": (80, 0, 6, 6, 6),
    "muzzle": (80, 12, 4, 4, 6),
    "ossicone_a": (104, 0, 2, 3, 2),
    "ossicone_b": (104, 5, 2, 3, 2),
    "ear_a": (112, 0, 4, 5, 0),
    "ear_b": (112, 6, 4, 5, 0),
    "tongue_h": (80, 24, 2, 0, 12),
    "tongue_v": (80, 38, 0, 2, 12),
    "leg_fr": (0, 32, 4, 14, 4),
    "leg_fl": (16, 32, 4, 14, 4),
    "leg_rr": (32, 32, 4, 14, 4),
    "leg_rl": (48, 32, 4, 14, 4),
    "tail": (62, 20, 2, 9, 2),
}

# Three shades each: dark (cooler shadow), base, light (warmer highlight). Kept muted.
COAT = ((54, 39, 38), (78, 57, 51), (100, 75, 63))
BELLY = ((72, 54, 48), (92, 69, 58), (112, 86, 72))
CREAM = ((150, 140, 120), (200, 190, 166), (226, 218, 194))
FACE = ((104, 94, 84), (132, 120, 106), (154, 141, 124))
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


def flat(palette, which):
    """No face lighting: for the flat planes, whose two coincident sides must look the same."""
    return palette[which]


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
    """Two cell indices that run along the face, used only to place the dither at transitions."""
    x, y, z = (cell(c) for c in pos)
    if face in ("top", "bottom"):
        return z, x
    if face in ("front", "rear"):
        return x, y
    return z, y


def paint_body(face, pos):
    x, y, z = pos
    iy = cell(y)
    # Hindquarter stripes with hard edges: two cream rows (light above, base below) then two dark rows.
    # The pattern steps down one row towards the rump, so the stripes curve over the haunch.
    row = iy - (1 if z >= 5.0 else 0)
    if face in ("side", "rear") and z >= 1.0 and 3 <= row <= 8 and (row - 3) % 4 in (0, 1):
        return shade(CREAM, 2 if (row - 3) % 4 == 0 else 1, face)
    if face == "bottom" or iy >= 9:
        return shade(COAT, 0, face)
    return shade(COAT, 1, face)


def paint_leg(name, face, pos):
    x, y, z = pos
    a, b = plane_cells(face, pos)
    gy = int(math.floor(24.0 - y - 1e-3))   # rows above the ground, 0 = hoof
    if gy <= 0:
        return shade(HOOF, 1, face)
    if name in ("leg_rr", "leg_rl"):
        # striped from the hock to the hoof, plain above
        if gy <= 11 and gy % 3 != 0:
            return shade(CREAM, 2 if gy % 3 == 1 else 1, face)
        return shade(COAT, 0 if gy <= 11 else 1, face)
    # front legs: a pale sock with one dark band, plain above
    if gy <= 6 and gy != 3:
        return shade(CREAM, 2 if gy == 6 else 1, face)
    if gy == 3:
        return shade(COAT, 0, face)
    return shade(COAT, 1, face)


def paint_tail(face, ty):
    if ty >= 6:
        return shade(TUFT, 1, face)
    return shade(COAT, 1, face)


def paint_neck(face, local, pos):
    lx, ly, lz = local
    a, b = plane_cells(face, pos)
    # the pale head colour runs a little way down the neck, with a dithered edge
    if ly <= 1 or (ly == 2 and dithered(0.5, a, b)):
        return shade(FACE, 1, face)
    if face == "front":
        return shade(BELLY, 2, face)
    if face == "top":
        return shade(COAT, 2, face)
    return shade(COAT, 1, face)


def paint_skull(face, local, pos):
    lx, ly, lz = local          # lz: 0 at the front of the skull, 5 at the back; ly 0 at the top
    if face == "side":
        if lz == 1 and ly in (2, 3):
            return EYE
        if 0 <= lz <= 2 and 1 <= ly <= 4:
            return shade(COAT, 0 if lz == 1 else 1, face)        # dark patch around the eye
        if ly == 5:
            return shade(CREAM, 0, face)                         # pale jaw line
        return shade(FACE, 1, face)
    if face == "top":
        return shade(COAT, 1, face) if lz >= 2 else shade(FACE, 1, face)
    if face == "bottom":
        return shade(BELLY, 2, face)
    if face == "rear":
        return shade(COAT, 1, face)
    return shade(FACE, 1, face)


def paint_muzzle(face, local, pos):
    lx, ly, lz = local          # lz: 0 at the nose, 5 where it meets the skull
    if face == "front":
        if ly == 1 and lx in (0, 3):
            return NOSTRIL
        return shade(MUZZLE, 1, face)
    if face == "bottom":
        return shade(CREAM, 0, face) if lz >= 2 else shade(MUZZLE, 1, face)
    if face == "top":
        return shade(MUZZLE, 1, face) if lz <= 1 else shade(FACE, 2, face)
    # sides: dark nose pad, a thin mouth line, pale cheek above
    if lz <= 1:
        return shade(MUZZLE, 1, face)
    if ly == 3:
        return shade(MUZZLE, 0, face)
    return shade(FACE, 1, face)


def paint_ossicone(face, local):
    lx, ly, lz = local
    if ly == 0 or face == "top":
        return shade(TUFT, 1, face)             # dark tip
    return shade(OSSICONE, 1, face)


def paint_ear(local):
    lx, ly, lz = local          # lx 0..3 across, ly 0 at the tip; both sides of the plane look alike
    if ly == 0:
        return flat(COAT, 0)                    # dark tip
    if 1 <= lx <= 2 and ly <= 4:
        return flat(EAR_INNER, 2 if lx == 1 else 1)
    return flat(CREAM, 1)                       # pale hair along the rim


def paint_tongue(local):
    lx, ly, lz = local          # lz: 0 at the tip
    return flat(TONGUE, 2 if lz <= 1 else 1)


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
        if len({round(v[3]) for v in verts}) == 1 or len({round(v[4]) for v in verts}) == 1:
            continue                    # the empty sides of a flat plane
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
                    c = paint_tail(face, local[1])
                elif name == "neck":
                    c = paint_neck(face, local, pos)
                elif name == "skull":
                    c = paint_skull(face, local, pos)
                elif name == "muzzle":
                    c = paint_muzzle(face, local, pos)
                elif name.startswith("ossicone"):
                    c = paint_ossicone(face, local)
                elif name.startswith("ear"):
                    c = paint_ear(local)
                else:
                    c = paint_tongue(local)
                img[tv, tu] = (*[int(max(0, min(255, v))) for v in c], 255)

    Image.fromarray(img, "RGBA").save(OUT)
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
