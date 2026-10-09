#!/usr/bin/env python3
"""Paints the Cannonbolt entity texture (128x128).

Mirrors the boxes in CannonboltModel.createBodyLayer: every entry in BOXES has the same texture
offset and size as the cube with that name there, so change both together. Every texel is placed
by face, and the pattern follows the material (white fur strokes, yellow shell plates with a dark
crescent, steel-grey hands and feet, flat claws with transparent gaps) instead of random noise.
Placeholder art: repaint over it in Blockbench if wanted. Running this overwrites cannonbolt.png.

    python3 -I tools/paint_cannonbolt.py [out.png]
"""
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/alien_fauna/textures/entity/cannonbolt.png"

# Three shades each: cooler shadow, base, warmer highlight. Muted, like vanilla.
WHITE = ((176, 177, 182), (214, 214, 212), (238, 237, 232))
YELLOW = ((152, 112, 24), (200, 164, 38), (230, 198, 74))
STEEL = ((70, 72, 86), (104, 106, 120), (140, 142, 156))
BLACK = (24, 24, 28)
AMBER = (228, 150, 36)
AMBER_LIGHT = (250, 196, 84)
CLAW = (28, 28, 34)
CLAW_LIGHT = (120, 122, 138)


def faces(u, v, w, h, d):
    """Texture rectangles (x, y, width, height) of the six faces of a box at (u, v)."""
    return {
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
    }


# ---- materials ----

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


def dither(x, y, a, b):
    return a if (x + y) % 2 == 0 else b


def plate(face, x, y, W, H, tx, ty):
    """Yellow shell: lighter tops, darker undersides, a dark rim and a dark crescent on one side."""
    if face == "top":
        return YELLOW[2] if min(x, y, W - 1 - x, H - 1 - y) > 0 else YELLOW[1]
    if face == "bottom":
        return YELLOW[0]
    if W < 3 or H < 3:
        return YELLOW[1]
    if x == 0 or x == W - 1 or y == H - 1:
        return YELLOW[0]
    crescent = (W // 4) * (1.0 - ((2.0 * y - H) / H) ** 2)
    if x <= crescent:
        return YELLOW[0]
    if y == 1 and x < W - 2:
        return YELLOW[2]
    return YELLOW[1]


def crack(face, x, y, W, H, tx, ty, row):
    """Stepped black crack across a ball face with a dark edge beneath it."""
    def line(col):
        return row + 2 * ((col // 4) % 2)
    ly = line(x)
    if ly <= y <= ly + 1:
        return BLACK
    if x > 0 and x % 4 == 0 and min(line(x - 1), ly) <= y <= max(line(x - 1), ly) + 1:
        return BLACK
    if y == ly + 2:
        return YELLOW[0]
    return plate_flat(face, x, y, W, H)


def plate_flat(face, x, y, W, H):
    if face == "top":
        return YELLOW[2]
    if face == "bottom":
        return YELLOW[0]
    return YELLOW[1] if y < H - 2 else YELLOW[0]


# ---- boxes (name: u, v, w, h, d, painter) ----

def eye(x, y):
    """Eye pixel at (x, y) relative to the eye's top-left corner, or None outside it. Four wide and
    four tall: a one-pixel black outline round a 2x2 amber centre."""
    if not (0 <= x < 4 and 0 <= y < 4):
        return None
    if y in (0, 3) or x in (0, 3):
        return BLACK
    return AMBER_LIGHT if (x, y) == (1, 1) else AMBER


def body(face, x, y, W, H, tx, ty):
    """The torso carries the face: black hood and cowl, outlined amber eyes, a black stripe that
    lines up with the one below the frown, and a long frown."""
    if face == "top":
        return BLACK
    if face == "bottom":
        return STEEL[0]
    if face in ("left", "right"):
        if y < 6:
            return BLACK
        return fur(face, x, y, W, H, tx, ty)
    if y < 2:
        return BLACK
    if face == "front":
        mid = W // 2
        if y == 2:
            return BLACK
        if mid - 1 <= x <= mid and (y <= 6 or y >= 11):
            return BLACK
        left = eye(x - 1, y - 3)
        right = eye(x - 9, y - 3)
        if left is not None:
            return left
        if right is not None:
            return right
        # The mouth: a thick frown that touches neither stripe, with a grey lip shadow under the
        # middle and the corners drooping.
        if y == 8 and 4 <= x <= 9:
            return BLACK
        if y == 9:
            if x in (3, 10):
                return BLACK
            if 4 <= x <= 9:
                return STEEL[2]
        if y == 10 and x in (2, 11):
            return BLACK
    elif y == 2:
        return BLACK
    return fur(face, x, y, W, H, tx, ty)


def dome(face, x, y, W, H, tx, ty):
    """A shoulder plate. Its bottom edge, where it meets the torso, is outlined in black."""
    if face == "bottom" or (face != "top" and y == H - 1):
        return BLACK
    return plate(face, x, y, W, H, tx, ty)


def arm(face, x, y, W, H, tx, ty):
    if face == "top":
        return BLACK
    if face == "bottom":
        return STEEL[0]
    if y < 1:
        return BLACK
    if y in (3, 6, 9) and face != "top":
        return BLACK                                # three black lines round the arm
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


def claws_down(face, x, y, W, H, tx, ty):
    """Three claws hanging flush from the outer side of the hand, each two pixels wide at the base
    and curling inwards to a single-pixel tip. The gaps between them stay transparent."""
    if face not in ("right", "left"):
        return None
    claw = {0: 0, 1: 0, 3: 1, 4: 1, 6: 2, 7: 2}.get(x)
    if claw is None:
        return None
    if y < 2:
        return CLAW
    return CLAW if x == {0: 1, 1: 3, 2: 6}[claw] else None   # row 2: the tip only


def claws_forward(face, x, y, W, H, tx, ty):
    """Three flat claws lying forward from the toes; the tip row is the last row (front)."""
    if face not in ("top", "bottom"):
        return None
    k, cx = divmod(x, 2)
    if y < H - 1:
        return CLAW_LIGHT if cx == 0 else CLAW
    return CLAW if cx == 1 else None


def ball_bar(shift):
    def paint(face, x, y, W, H, tx, ty):
        base = max(1, (H - 2) // 2 - 1 + shift + {"front": 0, "back": -2, "left": 2, "right": -1,
                                                  "top": 1, "bottom": 0}[face])
        return crack(face, x, y, W, H, tx, ty, base)
    return paint


BOXES = {
    "body": (0, 72, 14, 15, 8, body),
    "dome": (68, 72, 8, 6, 7, dome),
    "arm": (0, 96, 6, 17, 6, arm),
    "arm_plate": (48, 96, 2, 6, 6, plate),
    "leg": (24, 96, 6, 8, 6, leg),
    "knee_plate": (68, 90, 1, 5, 4, plate),
    "hand_claws": (100, 96, 0, 3, 8, claws_down),
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
                        px[fx + x, fy + y] = colour + (255,) if len(colour) == 3 else colour
    img.save(OUT)
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
