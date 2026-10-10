#!/usr/bin/env python3
"""Paints the Ghostfreak textures from the baked model geometry.

Every texel takes its colour from its position on the 3D model (not from its place on the
unfolded sheet), so the black cracks run across separate boxes without breaking at the seams.
Vanilla style: a few muted shades per material, flat cel-shaded strips along one edge of each
face, and patterns that follow the material instead of random speckle.

  ghostfreak.png        the body: five-shade grey skin mottled by flowing mist, one connected black
                        crack network from the head across the body, crease lines round the arms,
                        and black and white banded tentacles
  ghostfreak_eyes.png   only the eye pixels, drawn full-bright by the eyes layer so the eye glows
                        while the rest of the body is transparent

    ./gradlew dumpModel
    python3 -I tools/paint_texture.py [model.json]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

MODEL = sys.argv[1] if len(sys.argv) > 1 else "build/preview/model.json"
OUT_DIR = "src/main/resources/assets/ghostfreak/textures/entity"

# Five skin shades from a cool shadow to a warm highlight, muted as vanilla palettes are.
GREY = ((92, 94, 108), (122, 123, 134), (150, 148, 152), (174, 170, 169), (198, 192, 186))
CRACK = (22, 18, 24)
EYE = ((150, 44, 150), (204, 78, 196), (238, 156, 228))
PUPIL = (58, 12, 70)
BLACK_BAND = ((16, 14, 22), (32, 30, 40), (58, 56, 70))
WHITE_BAND = ((148, 146, 158), (204, 200, 206), (236, 232, 234))

BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))

# Fracture network in model-space pixels (y points down). Branches share their end points
# exactly, so the lines are one connected crack from the top of the head, through the eye, down the
# neck and across the body. Branches stop short of the torso edge (x = 4), because the arms start
# there and would pick up stray dots.
NETWORK = [
    [(-3, -13.5), (0, -11.2), (2, -9.5), (2.5, -7), (1, -5.5), (0.5, -5), (-0.5, -2)],
    [(-0.5, -2), (-2, -0.5), (-3.2, 0.2)],
    [(-0.5, -2), (1.5, 1), (3.2, 1.9)],
    [(1.5, 1), (1, 5), (2, 8), (1, 11), (0, 13)],
    [(0, 13), (-2, 15), (-3.5, 17)],
    [(0, 13), (2, 16), (3.5, 19)],
    [(2, 8), (3.2, 8.6)],
    [(1, 5), (-1.5, 6.5), (-3.2, 6.8)],
]


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


def face_of(normal):
    nx, ny, nz = normal
    return "top" if ny < -0.5 else "bottom" if ny > 0.5 else "front" if nz < -0.5 else "back" if nz > 0.5 else \
        "left" if nx < 0 else "right"


def eye_colour(meta, pos, face):
    """The eye: magenta with a dark slit and a highlight. None outside the eye."""
    x, y, z = pos
    ex0, ex1, ey0, ey1, ez = meta["eye"]
    if face == "front" and abs(z - ez) < 0.05 and ex0 <= x < ex1 and ey0 <= y < ey1:
        col, row = int(math.floor(x - ex0)), int(math.floor(y - ey0))
        w, h = ex1 - ex0, ey1 - ey0
        if col == (w - 1) // 2 and (h < 3 or row >= 1):
            return PUPIL
        if col == 0 and row == 0:
            return EYE[2]
        return EYE[1] if row < h - 1 else EYE[0]
    return None


def paint_tentacle(meta, pos, face, s_px, w_px):
    """Alternating black and white bands, one per five-pixel segment, lit from above."""
    x, y, z = pos
    band = int(math.floor((y - meta["tentacleY"]) / 5.0 + 1e-3)) % 2
    palette = BLACK_BAND if band == 0 else WHITE_BAND
    tone = 2 if face == "top" else 0 if face == "bottom" else 1
    if face in ("front", "back", "left", "right") and w_px >= 2 and s_px >= w_px - 1:
        tone = 0                                           # cel-shaded strip on the shadow edge
    elif s_px == 0 and tone == 1:
        tone = 2                                           # rim light on the other edge
    return palette[tone]


def paint_point(meta, pos, normal, s_px, w_px, tv):
    x, y, z = pos
    face = face_of(normal)
    neck = meta["neckY"]
    top, bottom, half = meta["crack"]
    in_head = y < neck

    if tv >= meta["tentacleV"]:
        return paint_tentacle(meta, pos, face, s_px, w_px)

    eye = eye_colour(meta, pos, face)
    if eye is not None:
        return eye

    # ---- hands: pale claws darkening to the tip ----
    if abs(x) >= meta["clawX"] and y >= meta["clawY"] - 0.01:
        t = y - meta["clawY"]
        return GREY[1] if t >= 5 else GREY[2] if t >= 3 else GREY[4]

    # ---- lines around the arms: two bands that wrap right round, the lower one stepping down on
    # the sides and back, like the creases in the reference ----
    if abs(x) >= 3.99 and -4 <= y < meta["clawY"] and face not in ("top", "bottom"):
        if 3 <= y < 4:
            return CRACK
        if (8 <= y < 9 and face == "front") or (9 <= y < 10 and face != "front"):
            return CRACK

    # ---- fracture network, painted by 3D position so it crosses box edges (the eye is drawn over it) ----
    span = bottom - top
    if face not in ("top", "bottom") and y < top + 0.84 * span:
        for line in NETWORK:
            if dist_to_polyline(x, y, line) < 0.55:
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
        if any(dist_to_polyline(x, y, line) < 1.7 for line in NETWORK):
            level -= 0.9
    dither = 0.5 + 0.18 * ((BAYER[int(math.floor(x)) & 3][int(math.floor(y + z)) & 3] + 0.5) / 16.0 - 0.5)
    return GREY[max(0, min(len(GREY) - 1, int(math.floor(level + dither))))]


def main():
    with open(MODEL) as f:
        data = json.load(f)
    w, h = data["texW"], data["texH"]
    body = np.zeros((h, w, 4), np.uint8)
    eyes = np.zeros((h, w, 4), np.uint8)

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
                c = paint_point(data, pos, normal, tu - umin, umax - umin, tv)
                body[tv, tu] = (*[int(max(0, min(255, v))) for v in c], 255)
                glow = eye_colour(data, pos, face_of(normal))
                if glow is not None and glow != PUPIL:
                    eyes[tv, tu] = (*glow, 255)

    os.makedirs(OUT_DIR, exist_ok=True)
    Image.fromarray(body, "RGBA").save(os.path.join(OUT_DIR, "ghostfreak.png"))
    Image.fromarray(eyes, "RGBA").save(os.path.join(OUT_DIR, "ghostfreak_eyes.png"))
    print("Wrote", OUT_DIR)


if __name__ == "__main__":
    main()
