#!/usr/bin/env python3
"""Paints the Cannonbolt entity texture (128x128) from scratch.

Mirrors the boxes in CannonboltModel.createBodyLayer: every entry in BOXES has the same texture
offset and size as the cube with that name there, so change both together.

White fur with a black hood, outlined amber eyes and a thick black stripe; yellow plates with a
dark crescent and thick black borders (round discs with transparent corners on the arms and
legs, rounded domes on the shoulders, bands across the back); steel-grey hands and feet with dark
claws. Placeholder art: repaint over it in Blockbench if wanted. Running this overwrites the png.

    python3 -I tools/paint_cannonbolt.py [out.png]
"""
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/alien_fauna/textures/entity/cannonbolt.png"

WHITE = ((176, 177, 182), (214, 214, 212), (238, 237, 232))
YELLOW = ((152, 112, 24), (200, 164, 38), (230, 198, 74))
STEEL = ((70, 72, 86), (104, 106, 120), (140, 142, 156))
BLACK = (24, 24, 28)
AMBER = (228, 150, 36)
AMBER_LIGHT = (250, 196, 84)
CLAW = (28, 28, 34)


def faces(u, v, w, h, d):
    """Texture rectangles (x, y, width, height) of the six faces of a box at (u, v)."""
    return {
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
    }


def dither(x, y, a, b):
    return a if (x + y) % 2 == 0 else b


def fur(face, x, y, W, H, tx, ty):
    """White fur: strokes of three texels running down, staggered between columns."""
    if face == "top":
        return WHITE[2]
    if face == "bottom":
        return WHITE[0]
    if y >= H - max(2, H // 5):
        return WHITE[0]
    if tx % 4 == 0 and (ty + tx // 2) % 8 < 3:
        return WHITE[0]
    if tx % 4 == 2 and (ty + tx // 2 + 4) % 8 < 2:
        return WHITE[2]
    return WHITE[1]


# ---- torso ----

def eye(x, y):
    """Four wide, four tall: a one-pixel black outline round a 2x2 amber centre."""
    if not (0 <= x < 4 and 0 <= y < 4):
        return None
    if y in (0, 3) or x in (0, 3):
        return BLACK
    return AMBER_LIGHT if (x, y) == (1, 1) else AMBER


def body(face, x, y, W, H, tx, ty):
    """The face is on the torso: black hood and cowl, outlined eyes, a thick centre stripe above
    and below the frown (not touching it), and a wide frown with a lip shadow."""
    if face == "top":
        return BLACK
    if face == "bottom":
        return STEEL[0]
    if face in ("left", "right"):
        if y < 6:
            return BLACK
        if y == 6:
            return dither(x, y, BLACK, STEEL[1])
        return fur(face, x, y, W, H, tx, ty)
    if y < 2:
        return BLACK
    if face != "front":
        return dither(x, y, STEEL[1], WHITE[1]) if y == 2 else fur(face, x, y, W, H, tx, ty)
    mid = W // 2                                    # stripe columns mid-1 and mid
    if y == 2:
        return BLACK if mid - 1 <= x <= mid else dither(x, y, STEEL[1], BLACK)
    if x in (0, W - 1) and y <= 8 or x in (1, W - 2) and y <= 4:
        return BLACK
    if mid - 1 <= x <= mid and (y <= 6 or y >= 11):
        return BLACK
    for ex in (2, W - 6):
        e = eye(x - ex, y - 3)
        if e is not None:
            return e
    if y == 8 and mid - 3 <= x <= mid + 2:
        return BLACK
    if y == 9:
        if x in (mid - 4, mid + 3):
            return BLACK
        if mid - 3 <= x <= mid + 2:
            return STEEL[2]
    if y == 10 and x in (mid - 5, mid + 4):
        return BLACK
    return fur(face, x, y, W, H, tx, ty)


def band(face, x, y, W, H, tx, ty):
    """A band across the back: light top, dark crescent underneath, black bottom edge."""
    if face == "top":
        return YELLOW[2]
    if face == "bottom":
        return BLACK
    if y == H - 1:
        return BLACK
    if y == H - 2:
        return YELLOW[0]
    return YELLOW[1]


# ---- limbs ----

def arm(face, x, y, W, H, tx, ty):
    if face == "top":
        return BLACK
    if face == "bottom":
        return STEEL[0]
    if y < 1:
        return BLACK
    if y >= H - 4:
        return STEEL[2] if y < H - 1 else STEEL[1]
    if y == H - 5:
        return dither(x, y, STEEL[1], WHITE[1])
    return fur(face, x, y, W, H, tx, ty)


def leg(face, x, y, W, H, tx, ty):
    if face == "top":
        return WHITE[1]
    if face == "bottom":
        return STEEL[0]
    if y >= H - 2:
        return STEEL[1] if y == H - 2 else STEEL[0]
    return fur(face, x, y, W, H, tx, ty)


def disc(face, x, y, W, H, tx, ty):
    """A round yellow plate with a thick black border. The big faces are drawn as a disc with the
    corners left transparent, and the thin edge faces are black only where the disc touches them."""
    if min(W, H) < 5:
        long_side, pos = (W, x) if W >= H else (H, y)
        return BLACK if abs(pos + 0.5 - long_side / 2) <= long_side * 0.3 else None
    cx, cy = W / 2.0, H / 2.0
    r = min(W, H) / 2.0
    d2 = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
    if d2 > r * r:
        return None
    if d2 > (r - (1.0 if r < 4.5 else 1.6)) ** 2:
        return BLACK
    lit = (x + 0.5 - cx) + (y + 0.5 - cy)
    if lit < -r * 0.55:
        return YELLOW[2]
    if lit > r * 0.6:
        return YELLOW[0]
    return YELLOW[1]


def dome(face, x, y, W, H, tx, ty):
    """A shoulder dome: a yellow shell with a light top and a dark crescent, and a thick black
    border along the bottom."""
    if face == "bottom":
        return BLACK
    if face == "top":
        cx, cy = W / 2.0, H / 2.0
        return YELLOW[2] if x + y < (W + H) / 3.0 else YELLOW[1]
    if y >= H - 2:
        return BLACK
    if x == 0 or x == W - 1:
        return YELLOW[0]
    if y == 0:
        return YELLOW[2]
    if x >= W - 3 or y >= H - 3:
        return YELLOW[0]
    return YELLOW[1]


def claws_down(face, x, y, W, H, tx, ty):
    """Three claws hanging flush from the front of the hand, two pixels wide at the base and
    curling inwards to a one-pixel tip. The gaps between them stay transparent."""
    if face not in ("front", "back"):
        return None
    claw = {0: 0, 1: 0, 3: 1, 4: 1, 6: 2, 7: 2}.get(x)
    if claw is None:
        return None
    if y < 2:
        return CLAW
    return CLAW if x == {0: 1, 1: 3, 2: 6}[claw] else None


def claws_forward(face, x, y, W, H, tx, ty):
    """Three flat claws lying forward from the toes; the tip row is the last row (front)."""
    if face not in ("top", "bottom"):
        return None
    cx = x % 2
    if y < H - 1:
        return (96, 98, 112) if cx == 0 else CLAW
    return CLAW if cx == 1 else None


# ---- the ball ----

def crack(face, x, y, W, H, row):
    """Stepped black crack across a face with a dark edge beneath it."""
    def line(col):
        return row + 2 * ((col // 4) % 2)
    ly = line(x)
    if ly <= y <= ly + 1:
        return BLACK
    if x > 0 and x % 4 == 0 and min(line(x - 1), ly) <= y <= max(line(x - 1), ly) + 1:
        return BLACK
    if y == ly + 2:
        return YELLOW[0]
    if face == "top":
        return YELLOW[2]
    if face == "bottom":
        return YELLOW[0]
    return YELLOW[1] if y < H - 2 else YELLOW[0]


def ball_bar(shift):
    def paint(face, x, y, W, H, tx, ty):
        base = max(1, (H - 2) // 2 - 1 + shift + {"front": 0, "back": -2, "left": 2, "right": -1,
                                                  "top": 1, "bottom": 0}[face])
        return crack(face, x, y, W, H, base)
    return paint


# name: (u, v, w, h, d, painter), matching CannonboltModel
BOXES = {
    "body": (0, 72, 16, 14, 10, body),
    "dome": (56, 72, 9, 6, 8, dome),
    "back_band": (92, 72, 16, 4, 2, band),
    "arm": (0, 98, 6, 15, 6, arm),
    "leg": (24, 98, 6, 7, 6, leg),
    "arm_disc": (56, 88, 2, 8, 8, disc),
    "leg_disc": (80, 88, 2, 6, 6, disc),
    "hand_claws": (0, 120, 8, 3, 0, claws_down),
    "foot_claws": (20, 120, 6, 0, 2, claws_forward),
    "ball_x_bar": (0, 34, 20, 16, 14, ball_bar(0)),
    "ball_y_bar": (68, 34, 14, 20, 16, ball_bar(1)),
    "ball_z_bar": (0, 0, 16, 14, 20, ball_bar(-1)),
}


def main():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    px = img.load()
    for name, (u, v, w, h, d, painter) in BOXES.items():
        for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    colour = painter(face, x, y, fw, fh, fx + x, fy + y)
                    if colour is not None:
                        px[fx + x, fy + y] = colour + (255,)
    img.save(OUT)
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
