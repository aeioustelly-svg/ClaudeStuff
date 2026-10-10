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

# Three shades each: dark (cooler), base, light (warmer). Muted, as vanilla palettes are.
GREY = ((108, 110, 122), (162, 160, 160), (190, 185, 180))
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
# Head crack in pixels relative to the neck line (x, y): from the crown down to the brow.
HEAD_CRACK = [(-1.0, -9.0), (-1.2, -6.5), (-2.0, -4.5), (-3.5, -4.0)]


def smoothstep(e0, e1, x):
    t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


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

    # ---- the eye: magenta with a dark slit and a highlight, under a dark brow ----
    if face == "front" and abs(z - ez) < 0.05:
        if ex0 <= x < ex1 and ey0 <= y < ey1:
            col, row = int(math.floor(x - ex0)), int(math.floor(y - ey0))
            w, h = ex1 - ex0, ey1 - ey0
            if col == (w - 1) // 2 and (h < 3 or row >= 1):
                return PUPIL
            if col == 0 and row == 0:
                return EYE[2]
            return EYE[1] if row < h - 1 else EYE[0]
        if ex0 <= x < ex1 and ey0 - 1 <= y < ey0:
            return GREY[0]

    # ---- hands: pale claws darkening to the tip ----
    if abs(x) >= meta["clawX"] and y >= meta["clawY"] - 0.01:
        t = y - meta["clawY"]
        return GREY[0] if t >= 5 else GREY[1] if t >= 3 else GREY[2]

    # ---- fracture lines, painted by 3D position so they cross box edges ----
    if face not in ("top", "bottom"):
        if in_head:
            if dist_to_polyline(x, y - neck, HEAD_CRACK) < 0.5:
                return CRACK
        else:
            span = bottom - top
            for line in BODY_CRACKS:
                pts = [(px * half, top + py * span) for px, py in line]
                if dist_to_polyline(x, y, pts) < 0.5 and y < top + 0.84 * span:
                    return CRACK

    # ---- skin: light from the top front left, one dark strip along the shadow edge ----
    tone = 2 if face == "top" else 0 if face == "bottom" else 1
    if face in ("front", "back") and w_px >= 3 and s_px >= w_px - 1:
        tone = 0                                           # cel-shaded strip on the right edge

    # the tail deepens into shadow over a narrow ordered-dither band, then stays dark
    fade = smoothstep(0.62, 0.82, (y - top) / (bottom - top)) if not in_head else 0.0
    if fade > (BAYER[int(math.floor(x)) & 3][int(math.floor(y)) & 3] + 0.5) / 16.0 and face != "top":
        tone = 0
    return GREY[tone]


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
