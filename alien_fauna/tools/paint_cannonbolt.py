#!/usr/bin/env python3
"""Paints the Cannonbolt entity texture (128x128).

Mirrors the boxes in CannonboltModel.createBodyLayer: every entry in BOXES has the same texture
offset and size as the cube with that name there, so change both together. Every texel is placed
by face, and the pattern follows the material (white fur strokes, yellow shell plates with a dark
crescent, steel-grey hands and feet, flat claws with transparent gaps) instead of random noise.
Placeholder art: repaint over it in Blockbench if wanted. Running this overwrites cannonbolt.png.

    python3 -I tools/paint_cannonbolt.py [out.png]
"""
import math
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
FINGER = (58, 60, 72)
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
        # the lower shadow band: staggered diagonal strokes in three greys, so it is not flat
        k = (tx // 2 + (y - (H - max(2, H // 5))) * 2) % 4
        return (WHITE[0], (146, 148, 158), WHITE[0], (194, 195, 200))[k]
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


def flat_side(face, y, H):
    """The sides of a back band: a plain slab, light on top, dark underneath, with no pattern."""
    if face == "top":
        return YELLOW[2]
    if face == "bottom":
        return YELLOW[0]
    return YELLOW[0] if y == H - 1 else YELLOW[1]


def back_band(kind):
    """Each back band gets its own surface so the three never look like one texture repeated:
    vertical ridges on the first, a diagonal scratch on the second, a row of rivets on the third."""
    def paint(face, x, y, W, H, tx, ty):
        if face != "back":
            return flat_side(face, y, H)
        c = plate(face, x, y, W, H, tx, ty)
        if c != YELLOW[1]:
            return c
        if kind == 0 and x % 3 == 0:
            return YELLOW[0]
        if kind == 1 and (x + y) % 6 == 0:
            return YELLOW[0]
        if kind == 2 and y == H // 2 and x % 3 == 1:
            return YELLOW[2]
        return c
    return paint


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

BABY = False                                       # set while the baby texture is painted


def eye_big(x, y):
    """The baby's eye: five wide and five tall, a black outline round a 3x3 amber centre."""
    if not (0 <= x < 5 and 0 <= y < 5):
        return None
    if y in (0, 4) or x in (0, 4):
        return BLACK
    return AMBER_LIGHT if (x, y) in ((1, 1), (2, 1), (1, 2)) else AMBER


def eye(x, y):
    """Eye pixel at (x, y) relative to the eye's top-left corner, or None outside it. Four wide and
    four tall: a one-pixel black outline round a 2x2 amber centre."""
    if not (0 <= x < 4 and 0 <= y < 4):
        return None
    if y in (0, 3) or x in (0, 3):
        return BLACK
    return AMBER_LIGHT if (x, y) == (1, 1) else AMBER


BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))
SIDE_TONES = ((30, 31, 38), (52, 54, 66), (78, 80, 95), (112, 114, 128), (152, 154, 164))


def hood_bleed(y):
    """How far the hood's shadow reaches down the body at row y: a soft, long ease-out from the
    third row, never darker than a mid-dark grey, so there is no sudden band under the hood."""
    t = max(0.0, min(1.0, 1.0 - (y - 3) / 12.0))
    return 0.45 * t * t * (3.0 - 2.0 * t)


def dither_tone(amount, tx, ty, fur_colour):
    f = 5.0 * (1.0 - amount)
    idx = int(f + BAYER[ty % 4][tx % 4] / 16.0)
    return fur_colour if idx >= 5 else SIDE_TONES[max(0, idx)]


def front_edge(x, y, W, tx, ty, fur_colour):
    """The hood's shadow carries on round the corner onto the front, over the three columns next to
    each side, matching the value the side has at that corner so there is no seam."""
    e = min(x, W - 1 - x)
    amount = hood_bleed(y) * 0.4 * max(0.0, 1.0 - e / 4.0)
    return fur_colour if amount < 0.04 else dither_tone(amount, tx, ty, fur_colour)


def side_shade(face, x, y, W, H, tx, ty, d):
    """The side of the torso. The black hood and the black under the back plates fade into the fur
    through six tones, mixed by an ordered dither, along a gently wavering edge, so there is no
    straight line and no hard step between the regions."""
    wob = 0.9 * math.sin(y * 0.85 + 0.6) + 0.6 * math.sin(y * 2.3 + 1.7)         # keeps the edge organic
    reach = 6.5 * max(0.0, 1.0 - (y - 3) / 15.0) ** 1.15 + wob                      # how far the dark reaches in
    dark = max(0.0, min(1.0, 1.0 - d / max(reach, 0.5))) ** 1.15 if reach > 0 else 0.0
    hood = hood_bleed(y) * (0.4 + 0.6 * max(0.0, 1.0 - d / 10.0))               # the hood bleeds down, gently
    amount = max(dark, hood)
    return dither_tone(amount, tx, ty, fur(face, x, y, W, H, tx, ty))


def body(face, x, y, W, H, tx, ty):
    """The torso carries the face: black hood and cowl, outlined amber eyes, a black stripe that
    lines up with the one below the frown, and a long frown."""
    if face == "top":
        return BLACK
    if face == "bottom":
        # the centre stripe carries on under the body, so it loops all the way round
        return BLACK if W // 2 - 1 <= x <= W // 2 else STEEL[0]
    if face in ("left", "right"):
        if y < 3:
            return BLACK                            # same depth as the hood on the front and back
        d = x if face == "right" else W - 1 - x     # pixels in from the back edge of this side
        if d < 2 and y in (5, 12):
            return BLACK                            # the lines between the back bands wrap round the corner
        return side_shade(face, x, y, W, H, tx, ty, d)
    if y < 2:
        return BLACK
    if face == "back" and (y in (5, 12) or W // 2 - 1 <= x <= W // 2):
        return BLACK                                # the lines between the back bands, and the stripe coming up the back
    if face == "front":
        mid = W // 2
        if y == 2:
            return BLACK
        if mid - 1 <= x <= mid and (y <= (2 if BABY else 6) or y >= (12 if BABY else 11)):
            return BLACK
        if BABY:
            left, right = eye_big(x - 1, y - 3), eye_big(x - 8, y - 3)
        else:
            left, right = eye(x - 1, y - 3), eye(x - 9, y - 3)
        if left is not None:
            return left
        if right is not None:
            return right
        # The mouth: a thick frown that touches neither stripe, with a grey lip shadow under the
        # middle and the corners drooping.
        my = y - 1 if BABY else y                   # the baby's mouth sits a row lower, below the bigger eyes
        if my == 8 and 4 <= x <= 9:
            return BLACK
        if my == 9:
            if x in (3, 10):
                return BLACK
            if 4 <= x <= 9:
                return STEEL[2]
        if my == 10 and x in (2, 11):
            return BLACK
    elif y == 2:
        return BLACK
    if face == "front" and y >= 3:
        return front_edge(x, y, W, tx, ty, fur(face, x, y, W, H, tx, ty))
    return fur(face, x, y, W, H, tx, ty)


def plate_mid(face, x, y, W, H, tx, ty):
    """The middle column of a round limb plate: all black (the outline). The yellow sits on top of
    it as a separate raised box. The face against the limb is left empty."""
    return None if face == "left" else BLACK


def bulge(face, x, y, W, H, tx, ty):
    """The yellow part of a limb plate, raised exactly one pixel out of the black: a shaded yellow
    face with dark amber sides."""
    if face == "left":
        return None
    if face != "right":
        return YELLOW[0]
    lit = (x - W / 2.0) + (y - H / 2.0)
    return YELLOW[2] if lit < -W * 0.2 else (YELLOW[0] if lit > W * 0.25 else YELLOW[1])


def plate_side(face, x, y, W, H, tx, ty):
    """A short side column of a round limb plate: all black, as part of the outline."""
    return None if face == "left" else BLACK


def dome(face, x, y, W, H, tx, ty):
    """A shoulder plate. Only a two-pixel black band along the bottom, where it meets the body,
    is black; the rest of the plate is not outlined."""
    if face == "bottom" or (face != "top" and y >= H - 2):
        return BLACK
    return plate(face, x, y, W, H, tx, ty)


def arm(face, x, y, W, H, tx, ty):
    if face == "right" and x in (2, 3) and 1 <= y <= 6:
        return BLACK                                # joins the plate to the black of the shoulder
    if face == "top":
        return BLACK
    if face == "bottom":
        return STEEL[0]
    if y < 1:
        return BLACK
    if face == "right" and y in (H - 3, H - 2) and x in (0, 2, 4):
        return FINGER                               # three faint finger lines on the outer side
    if face == "front" and y in (H - 3, H - 2) and x == 1:
        return FINGER                               # one faint thumb line on the front, one pixel in from the outer edge
    if y >= H - 4:
        return STEEL[2] if y < H - 1 else STEEL[1]
    if y == H - 5:
        return dither(x, y, STEEL[1], WHITE[1])
    return fur(face, x, y, W, H, tx, ty)


def leg(face, x, y, W, H, tx, ty):
    if face == "front" and y in (H - 3, H - 2) and x in (0, 2, 4):
        return FINGER                               # three faint toe lines, facing forwards, no thumb
    if face == "top":
        return WHITE[1]
    if face == "bottom":
        return STEEL[0]
    if y >= H - 2:
        return STEEL[1] if y == H - 2 else STEEL[0]
    return fur(face, x, y, W, H, tx, ty)


def ball_bar(shift):
    def paint(face, x, y, W, H, tx, ty):
        base = max(1, (H - 2) // 2 - 1 + shift + {"front": 0, "back": -2, "left": 2, "right": -1,
                                                  "top": 1, "bottom": 0}[face])
        return crack(face, x, y, W, H, tx, ty, base)
    return paint


BOXES = {
    "body": (0, 72, 14, 18, 12, body),
    "back_band_1": (72, 0, 14, 5, 2, back_band(0)),
    "back_band_2": (72, 7, 16, 6, 3, back_band(1)),
    "back_band_3": (72, 16, 14, 5, 2, back_band(2)),
    "dome": (68, 72, 8, 6, 7, dome),
    "arm": (0, 102, 6, 20, 6, arm),
    "arm_plate_mid": (48, 102, 1, 8, 4, plate_mid),
    "arm_plate_side": (58, 102, 1, 6, 1, plate_side),
    "arm_bulge": (48, 114, 1, 6, 4, bulge),
    "leg": (24, 102, 6, 8, 6, leg),
    "knee_plate_mid": (68, 90, 1, 5, 2, plate_mid),
    "knee_plate_side": (74, 90, 1, 3, 1, plate_side),
    "knee_bulge": (74, 95, 1, 3, 2, bulge),
    "ball_x_bar": (0, 34, 20, 16, 14, ball_bar(0)),
    "ball_y_bar": (68, 34, 14, 20, 16, ball_bar(1)),
    "ball_z_bar": (0, 0, 16, 14, 20, ball_bar(-1)),
}


def paint(path):
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    px = img.load()
    for name, (u, v, w, h, d, painter) in BOXES.items():
        for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    colour = painter(face, x, y, fw, fh, fx + x, fy + y)
                    if colour is not None:
                        px[fx + x, fy + y] = colour + (255,) if len(colour) == 3 else colour
    img.save(path)
    print("Wrote", path)


def main():
    global BABY
    BABY = False
    paint(OUT)
    BABY = True                                      # the baby: same model and texture, bigger eyes
    paint(OUT.replace("cannonbolt.png", "cannonbolt_baby.png"))


if __name__ == "__main__":
    main()
