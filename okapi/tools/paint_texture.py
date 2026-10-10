#!/usr/bin/env python3
"""Paints the okapi entity texture from the baked model geometry.

Every texel gets its colour from its position on the un-rotated ("flat") model, so noise and stripes
run on across the edges of separate boxes and the coat fades into the pale face along the neck.
Vanilla style, checked against the cow, horse and llama textures: a few close, muted shades laid out
as irregular clusters that are elongated along the hair (along the body on the flanks, up and down on
the legs and neck). Contrast is low. A uniform repeated pattern and flat colour were both tried and
both look wrong. The stripes wobble, vary in thickness, start in different places and break up.
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
# The ears are flat planes (zero thickness), so both of their sides share one texture.
PARTS = {
    "body": (0, 0, 10, 10, 20),
    "neck": (62, 0, 4, 14, 4),
    "skull": (80, 0, 6, 6, 6),
    "muzzle": (80, 12, 4, 4, 6),
    "ossicone_a": (104, 0, 2, 3, 2),
    "ossicone_b": (104, 5, 2, 3, 2),
    "ear_a": (112, 0, 4, 5, 0),
    "ear_b": (112, 6, 4, 5, 0),
    "tongue": (80, 24, 2, 1, 12),
    "leg_fr": (0, 32, 4, 14, 4),
    "leg_fl": (16, 32, 4, 14, 4),
    "leg_rr": (32, 32, 4, 14, 4),
    "leg_rl": (48, 32, 4, 14, 4),
    "tail": (62, 20, 2, 9, 2),
}

# Three close shades each: dark (cooler shadow), base, light (warmer highlight). Vanilla animal
# textures read as fur through exactly this: low-contrast, irregular clusters of two or three
# neighbouring shades, elongated along the direction the hair lies. Kept muted.
COAT = ((66, 48, 45), (80, 59, 53), (93, 70, 61))
UNDER = ((50, 38, 37), (62, 47, 44), (74, 56, 51))
CREAM = ((170, 160, 138), (204, 194, 170), (226, 218, 194))
FACE = ((118, 107, 95), (131, 119, 105), (145, 132, 116))
MUZZLE = ((38, 31, 33), (54, 44, 45), (72, 58, 55))
HOOF = ((30, 26, 28), (42, 37, 37), (58, 51, 49))
TUFT = ((30, 24, 26), (42, 33, 33), (58, 46, 44))
OSSICONE = ((76, 57, 50), (96, 73, 63), (118, 92, 79))
EAR_INNER = ((132, 90, 88), (156, 110, 104), (178, 132, 122))
TONGUE = ((56, 60, 96), (74, 80, 122), (96, 102, 146))
EYE = (22, 18, 20)
NOSTRIL = (24, 20, 22)

FACE_LIGHT = {"top": 1.08, "front": 1.0, "side": 1.0, "rear": 0.95, "bottom": 0.86}


def cell(v):
    """Integer cell index of a texel centre (centres sit on half integers for whole-pixel boxes)."""
    return int(math.floor(v + 1e-3))


def h3(x, y, z, seed=0):
    """Deterministic hash noise in 0..1 for an integer lattice point."""
    n = (x * 374761393 + y * 668265263 + z * 2147483647 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n ^= n >> 16
    return (n & 0xFFFF) / 65535.0


def snoise(t, seed=0):
    """Smooth 1D noise in -1..1."""
    i = math.floor(t)
    f = t - i
    f = f * f * (3 - 2 * f)
    a, b = h3(i, 0, 0, seed), h3(i + 1, 0, 0, seed)
    return (a + (b - a) * f) * 2.0 - 1.0


def cluster_noise(pos, size, seed):
    """Clumpy noise: a coarse lattice (cell `size` = (sx, sy, sz)) plus a fine one. The coarse cells are
    elongated along the hair, so the result reads as short strands and tufts, not as speckle."""
    x, y, z = (cell(c) for c in pos)
    sx, sy, sz = size
    coarse = h3(x // sx, y // sy, z // sz, seed)
    fine = h3(x, y, z, seed + 7)
    return 0.62 * coarse + 0.38 * fine


def pick(palette, n, face, lo=0.22, hi=0.78):
    which = 0 if n < lo else 2 if n > hi else 1
    k = FACE_LIGHT[face]
    return tuple(min(255, int(v * k)) for v in palette[which])


def shade(palette, which, face):
    k = FACE_LIGHT[face]
    return tuple(min(255, int(v * k)) for v in palette[which])


def flat(palette, which):
    """No face lighting: for the flat plane, whose two coincident sides must look the same."""
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


def hindquarter_stripe(face, pos, seed):
    """Irregular cream stripe field over the haunch: the rows wobble along the body, some are one row
    thick and some two, each one starts at a different place and they break up now and then."""
    x, y, z = pos
    lateral = z if face == "side" else x
    f = y + 0.8 * snoise(z * 0.22, seed) + 0.3 * snoise(z * 0.7 + 4.0, seed + 3)
    band = int(math.floor(f - 3.0))
    if band < 0 or band > 5 or band % 4 not in (0, 1):
        return False
    group = band // 4
    if band % 4 == 1 and h3(group, 1, 0, seed + 5) > 0.62:
        return False                                    # this stripe is only one row thick
    if face == "side" and z < -3.0 + 4.0 * h3(band, 2, 0, seed + 9):
        return False                                    # ragged front end
    return h3(int(math.floor(lateral / 2.0)), band, 1, seed + 11) < 0.95


def paint_body(face, pos, seed):
    x, y, z = pos
    iy = cell(y)
    if face in ("side", "rear") and hindquarter_stripe(face, pos, seed):
        return pick(CREAM, cluster_noise(pos, (2, 2, 3), seed + 20), face, 0.20, 0.85)
    size = (2, 1, 3) if face == "top" else (2, 2, 3)
    n = cluster_noise(pos, size, seed + 30)
    if face == "bottom" or iy >= 9:
        return pick(UNDER, n, face)
    # a little lighter along the back, darker towards the belly
    n += 0.10 if iy <= 1 else -0.08 if iy >= 7 else 0.0
    return pick(COAT, n, face)


def paint_leg(name, face, pos, seed):
    x, y, z = pos
    gy = int(math.floor(24.0 - y - 1e-3))   # rows above the ground, 0 = hoof
    around = cell(x) + cell(z)
    n = cluster_noise(pos, (2, 3, 2), seed + 40)
    if gy <= 0:
        return pick(HOOF, n, face)
    if name in ("leg_rr", "leg_rl"):
        # irregular stripes from the hock to the hoof
        f = gy + 0.45 * snoise(around * 0.5, seed + 50)
        band = int(math.floor(f))
        if 1 <= band <= 11 and band % 3 != 0 and h3(around, band, 2, seed + 51) < 0.95:
            return pick(CREAM, cluster_noise(pos, (2, 2, 2), seed + 52), face, 0.25, 0.80)
        return pick(COAT, n - 0.10, face)
    # front legs: a pale sock with a ragged top edge and a dark band, plain above
    top = 5 + (1 if h3(cell(x), cell(z), 3, seed + 60) < 0.5 else 0) + (1 if h3(cell(z), cell(x), 4, seed + 61) < 0.3 else 0)
    if gy <= top and gy != 3:
        return pick(CREAM, cluster_noise(pos, (2, 2, 2), seed + 62), face, 0.25, 0.80)
    return pick(COAT, n - (0.12 if gy == 3 else 0.0), face)


def paint_tail(face, pos, local, seed):
    n = cluster_noise(pos, (2, 3, 2), seed + 70)
    if local[1] >= 6:
        return pick(TUFT, n, face)
    return pick(COAT, n, face)


def paint_neck(face, local, pos, seed):
    lx, ly, lz = local
    n = cluster_noise(pos, (2, 3, 2), seed + 80)
    # the pale head colour runs a little way down the neck with a ragged edge
    if ly <= 1 or (ly <= 3 and n > 0.55 + 0.12 * ly):
        return pick(FACE, cluster_noise(pos, (2, 2, 2), seed + 81), face)
    if face == "front":
        return pick(COAT, n + 0.12, face)
    return pick(COAT, n, face)


def paint_skull(face, local, pos, seed):
    lx, ly, lz = local          # lz: 0 at the front of the skull, 5 at the back; ly 0 at the top
    n = cluster_noise(pos, (2, 2, 2), seed + 90)
    if face == "side":
        if lz == 1 and ly in (2, 3):
            return EYE
        if 0 <= lz <= 2 and 1 <= ly <= 4:
            return pick(COAT, n - 0.15, face)                    # dark patch around the eye
        return pick(FACE, n, face, 0.25, 0.78)
    if face == "top":
        return pick(COAT, n, face) if lz >= 2 else pick(FACE, n, face)
    if face == "bottom":
        return pick(UNDER, n + 0.2, face)
    if face == "rear":
        return pick(COAT, n, face)
    return pick(FACE, n, face, 0.25, 0.78)


def paint_muzzle(face, local, pos, seed):
    lx, ly, lz = local          # lz: 0 at the nose, 5 where it meets the skull
    n = cluster_noise(pos, (2, 2, 2), seed + 100)
    if face == "front":
        if ly == 1 and lx in (0, 3):
            return NOSTRIL
        return pick(MUZZLE, n, face)
    if face == "bottom":
        return pick(FACE, n, face) if lz >= 2 else pick(MUZZLE, n, face)
    if face == "top":
        return pick(MUZZLE, n, face) if lz <= 1 else pick(FACE, n + 0.1, face)
    # sides: dark nose pad, a thin mouth line, paler cheek above
    if lz <= 1:
        return pick(MUZZLE, n, face)
    if ly == 3:
        return pick(MUZZLE, n - 0.1, face)
    return pick(FACE, n, face, 0.25, 0.78)


def paint_ossicone(face, local, pos, seed):
    lx, ly, lz = local
    n = cluster_noise(pos, (1, 2, 1), seed + 110)
    if ly == 0 or face == "top":
        return pick(TUFT, n, face)              # dark tip
    return pick(OSSICONE, n, face)


def paint_ear(local, pos, seed):
    lx, ly, lz = local          # lx 0..3 across, ly 0 at the tip; both sides of the plane look alike
    n = cluster_noise(pos, (2, 2, 2), seed + 120)
    which = 0 if n < 0.30 else 2 if n > 0.70 else 1
    if ly == 0:
        return COAT[0]                          # dark tip
    if 1 <= lx <= 2 and ly <= 4:
        return EAR_INNER[2 if lx == 1 else 1]
    return COAT[which]                          # brown hair along the rim


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
                seed = 1
                if name == "body":
                    c = paint_body(face, pos, seed)
                elif name.startswith("leg_"):
                    c = paint_leg(name, face, pos, seed)
                elif name == "tail":
                    c = paint_tail(face, pos, local, seed)
                elif name == "neck":
                    c = paint_neck(face, local, pos, seed)
                elif name == "skull":
                    c = paint_skull(face, local, pos, seed)
                elif name == "muzzle":
                    c = paint_muzzle(face, local, pos, seed)
                elif name.startswith("ossicone"):
                    c = paint_ossicone(face, local, pos, seed)
                elif name.startswith("ear"):
                    c = paint_ear(local, pos, seed)
                else:
                    c = paint_tongue(face)
                img[tv, tu] = (*[int(max(0, min(255, v))) for v in c], 255)

    Image.fromarray(img, "RGBA").save(OUT)
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
