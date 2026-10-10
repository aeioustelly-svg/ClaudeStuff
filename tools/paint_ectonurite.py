#!/usr/bin/env python3
"""Paints the candidate Ectonurite textures from the baked model geometry.

Same technique as paint_texture.py: every texel takes its colour from its position on the 3D
model, so the black cracks run across separate boxes without breaking at the seams. Muted grey
skin in three shades (cooler shadow, base, warmer highlight), a cel-shaded dark strip along one
edge of each face as in the cartoon, an ordered-dither darkening towards the tail, one magenta
eye and black fracture lines. Hints (eye box, body span, claw start) come from the JSON that
EctonuriteDump writes.

    ./gradlew dumpEctonurite
    python3 -I tools/paint_ectonurite.py            # all three
    python3 -I tools/paint_ectonurite.py a          # one
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

IN_DIR = "build/preview"
OUT_DIR = "build/preview"

# Five skin shades from a cool shadow to a warm highlight, muted as vanilla palettes are.
GREY = ((92, 94, 108), (122, 123, 134), (150, 148, 152), (174, 170, 169), (198, 192, 186))
CRACK = (22, 18, 24)
EYE = ((150, 44, 150), (204, 78, 196), (238, 156, 228))
PUPIL = (58, 12, 70)

BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))

# Fracture lines in body units: x as a fraction of the torso half width, y as a fraction of the
# body span (0 = top of torso, 1 = tail tip). Shapes follow the reference art.
BODY_CRACKS = [
    [(-1.2, 0.06), (-0.2, 0.20), (0.5, 0.16), (1.2, 0.30)],
    [(-0.6, 0.34), (0.1, 0.50), (0.0, 0.62), (0.7, 0.74)],
    [(1.2, 0.46), (0.5, 0.52), (0.1, 0.50)],
]

# Face crack as offsets from the centre of the eye, so the eye sits on the line.
HEAD_CRACK = [(-5.0, -4.0), (-2.5, -1.8), (0.0, 0.0), (2.5, 1.4), (5.0, 2.6)]


def smoothstep(e0, e1, x):
    t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def _hash(ix, iy, iz):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def value_noise(x, y, z):
    """Smooth lattice noise in 0..1, so neighbouring texels stay related (no salt and pepper)."""
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = (v - math.floor(v) for v in (x, y, z))
    fx, fy, fz = (f * f * (3 - 2 * f) for f in (fx, fy, fz))
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                out += w * _hash(ix + dx, iy + dy, iz + dz)
    return out


def mist(x, y, z):
    """Ectoplasm mottling: two octaves, stretched along the body so it reads as flowing wisps."""
    return 0.7 * value_noise(x / 3.2, y / 12.0, z / 3.2) + 0.3 * value_noise(x / 1.8 + 17, y / 5.0 + 5, z / 1.8)


def dist_to_polyline(px, py, pts):
    best = 1e9
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        dx, dy = bx - ax, by - ay
        length2 = dx * dx + dy * dy
        t = 0.0 if length2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2))
        best = min(best, math.hypot(px - (ax + t * dx), py - (ay + t * dy)))
    return best


def paint_point(meta, pos, normal, s_px, w_px):
    x, y, z = pos
    nx, ny, nz = normal
    ex0, ex1, ey0, ey1, ez = meta["eye"]
    neck = meta["neckY"]
    top, bottom, half = meta["crack"]
    face = "top" if ny < -0.5 else "bottom" if ny > 0.5 else "front" if nz < -0.5 else "back" if nz > 0.5 else \
        "left" if nx < 0 else "right"
    in_head = y < neck

    # ---- the eye: magenta with a dark slit and a highlight ----
    if face == "front" and abs(z - ez) < 0.05:
        if ex0 <= x < ex1 and ey0 <= y < ey1:
            col, row = int(math.floor(x - ex0)), int(math.floor(y - ey0))
            w, h = ex1 - ex0, ey1 - ey0
            if col == (w - 1) // 2 and (h < 3 or row >= 1):
                return PUPIL
            if col == 0 and row == 0:
                return EYE[2]
            return EYE[1] if row < h - 1 else EYE[0]

    # ---- the crack that runs down the face; the eye above sits on top of it ----
    if in_head and face == "front":
        ecx, ecy = (ex0 + ex1) / 2, (ey0 + ey1) / 2
        line = [(ecx + dx, ecy + dy) for dx, dy in HEAD_CRACK]
        if dist_to_polyline(x, y, line) < 0.5:
            return CRACK

    # ---- hands: pale claws darkening to the tip ----
    if abs(x) >= meta["clawX"] and y >= meta["clawY"] - 0.01:
        t = y - meta["clawY"]
        return GREY[1] if t >= 5 else GREY[2] if t >= 3 else GREY[4]

    # ---- fracture lines on the body, painted by 3D position so they cross box edges ----
    span = bottom - top
    if face not in ("top", "bottom") and not in_head:
        for line in BODY_CRACKS:
            pts = [(px * half, top + py * span) for px, py in line]
            if dist_to_polyline(x, y, pts) < 0.5 and y < top + 0.84 * span:
                return CRACK

    # ---- skin: a lit level per face, mottled by flowing mist, darker towards the tail ----
    level = {"top": 3.3, "front": 2.5, "left": 2.4, "back": 1.9, "right": 1.6, "bottom": 1.0}[face]
    yn = (y - top) / span
    level += 0.4 * (1.0 - min(1.0, max(0.0, yn)))                      # chest and head catch more light
    level -= 1.5 * smoothstep(0.5, 1.0, yn)                            # the tail sinks into shadow
    level += (mist(x, y, z) - 0.5) * 3.0                               # wisps
    level -= 0.8 * max(-1.0, min(1.0, x / half))                       # lit from the left, shaded to the right
    if face in ("front", "back") and w_px >= 3:
        if s_px >= w_px - 1:
            level -= 1.2                                               # cel-shaded strip, right edge
        elif s_px == 0:
            level += 0.5                                               # rim light, left edge
    # contact shadow: one shade darker in a narrow band beside each crack, which gives them depth
    if face not in ("top", "bottom") and not in_head:
        for line in BODY_CRACKS:
            pts = [(px * half, top + py * span) for px, py in line]
            if dist_to_polyline(x, y, pts) < 1.7 and y < top + 0.84 * span:
                level -= 0.9
                break
    dither = 0.5 + 0.18 * ((BAYER[int(math.floor(x)) & 3][int(math.floor(y + z)) & 3] + 0.5) / 16.0 - 0.5)
    return GREY[max(0, min(len(GREY) - 1, int(math.floor(level + dither))))]


def paint(path_in, path_out):
    with open(path_in) as f:
        data = json.load(f)
    w, h = data["texW"], data["texH"]
    img = np.zeros((h, w, 4), np.uint8)

    for quad in data["poses"]["rest"]:
        verts = [quad[i * 8:(i + 1) * 8] for i in range(4)]
        us = [round(v[3]) for v in verts]
        vs = [round(v[4]) for v in verts]
        umin, umax, vmin, vmax = min(us), max(us), min(vs), max(vs)
        if umax == umin or vmax == vmin:
            continue                                        # zero-thickness face seen edge-on

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
                c = paint_point(data, pos, normal, tu - umin, umax - umin)
                img[tv, tu] = (*[int(max(0, min(255, v))) for v in c], 255)

    Image.fromarray(img, "RGBA").save(path_out)
    print("Wrote", path_out)


def main():
    ids = sys.argv[1:] or ["a", "b", "c"]
    os.makedirs(OUT_DIR, exist_ok=True)
    for i in ids:
        paint(os.path.join(IN_DIR, f"ectonurite_{i}.json"), os.path.join(OUT_DIR, f"ectonurite_{i}.png"))


if __name__ == "__main__":
    main()
