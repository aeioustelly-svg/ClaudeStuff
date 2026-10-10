#!/usr/bin/env python3
"""Paints Acela's skin (vanilla 64x64 player layout, wide arms) and the glowing eyes layer.

Acela is a man made of darkness: purple-black skin with a featureless face and two red eyes,
long black hair parted in the middle, a black-brown leather jacket with a silver zip, black jeans,
boots, and a briar pipe (in the unused skin area at u 56-64, v 16-25).

Three or four muted shades per material, lighter tops, cooler shadows, warmer highlights, and
patterns that follow the material (hair strands, leather creases, denim seams). No random noise.

    python3 -I tools/paint_skin.py
"""
import os

from PIL import Image

OUT = "src/main/resources/assets/aceliada/textures/entity"

# Palettes, dark to light.
DARK = [(10, 8, 14), (16, 13, 22), (24, 20, 32), (34, 29, 44)]          # the darkness he is made of
HAIR = [(12, 12, 16), (22, 22, 30), (36, 37, 50), (54, 56, 74)]         # black with a cold sheen
LEATHER = [(26, 20, 18), (40, 31, 27), (58, 45, 37), (82, 66, 52)]     # black-brown, warm highlights
DENIM = [(20, 21, 27), (30, 31, 40), (42, 44, 56)]
BOOT = [(14, 12, 12), (26, 22, 20), (40, 34, 30)]
METAL = [(110, 112, 118), (168, 170, 176), (214, 216, 222)]
BRIAR = [(54, 30, 18), (84, 48, 28), (112, 68, 40)]
EYE = [(150, 14, 14), (230, 34, 28), (255, 96, 70)]
EMBER = [(200, 70, 20), (255, 150, 50)]

img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()


def box(u, v, w, h, d):
    """Face rectangles (x, y, width, height) of a box with vanilla box UVs."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def fill(rect, fn):
    """Calls fn(x, y, w, h) for each texel; fn returns a colour or None."""
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            c = fn(x, y, w, h)
            if c is not None:
                px[x0 + x, y0 + y] = tuple(c) + (255,) if len(c) == 3 else tuple(c)


def put(x, y, c, layer=None):
    (layer or img).load()[x, y] = tuple(c) + (255,)


# ---- materials -------------------------------------------------------------------------------

def darkness(x, y, w, h, top=False):
    """Smoky darkness: lighter towards the top, faint diagonal wisps."""
    if top:
        return DARK[3] if (x + y) % 5 == 0 else DARK[2]
    level = 2 if y < h * 0.3 else 1 if y < h * 0.75 else 0
    if (x + 2 * y) % 7 == 0 and level < 3:
        level += 1
    return DARK[level]


def hair(x, y, w, h, top=False):
    """Strands run downwards: alternating columns, a sheen near the top, darker ends."""
    if top:
        return HAIR[2] if x % 2 else HAIR[1]
    col = (x * 3) % 4
    if y < 2:
        return HAIR[3] if col == 0 else HAIR[2]
    if y >= h - 2:
        return HAIR[0] if col else HAIR[1]
    return HAIR[2] if col == 0 else HAIR[1]


def leather(x, y, w, h, top=False):
    if top:
        return LEATHER[2]
    level = 2 if y < 2 else 1
    if (x + y) % 6 == 0:          # creases
        level = 0
    if (x - y) % 9 == 0 and y < h - 2:
        level = 3 if y < 4 else 2  # sheen
    return LEATHER[level]


def denim(x, y, w, h, top=False):
    if top:
        return DENIM[2]
    level = 1
    if x == 0 or x == w - 1:      # side seams
        level = 0
    elif y % 4 == 0:
        level = 2
    return DENIM[level]


def boot(x, y, w, h):
    return BOOT[2] if y == 0 else BOOT[1] if (x + y) % 3 else BOOT[0]


# ---- head ------------------------------------------------------------------------------------

head = box(0, 0, 8, 8, 8)
hat = box(32, 0, 8, 8, 8)

# Scalp and face are darkness; hair is painted over them.
for name in ("right", "front", "left", "back", "bottom"):
    fill(head[name], darkness)
fill(head["top"], lambda x, y, w, h: darkness(x, y, w, h, top=True))


def hair_top(x, y, w, h):
    # Parted down the middle: the part is a line of darkness from the forehead to the crown.
    if x == 3 and y < 6:
        return DARK[1]
    # Strands run from the part out to the sides, so the shading changes from row to row.
    near_part = x in (2, 4)
    return HAIR[3] if near_part and y % 3 == 1 else HAIR[2] if y % 3 == 1 else HAIR[1]


fill(head["top"], hair_top)
fill(head["back"], hair)
fill(head["right"], lambda x, y, w, h: hair(x, y, w, h) if x < 6 or y < 2 else None)
fill(head["left"], lambda x, y, w, h: hair(x, y, w, h) if x > 1 or y < 2 else None)


def face_hair(x, y, w, h):
    # Fringe swept to both sides from the centre part, strands framing the face.
    if y == 0:
        return None if x == 3 else HAIR[2]
    if y == 1 and (x <= 2 or x >= 5):
        return HAIR[1] if x in (2, 5) else HAIR[2]
    if y == 2 and (x <= 1 or x >= 6):
        return HAIR[1]
    if 3 <= y and (x == 0 or x == 7):
        return HAIR[1] if y < 6 else HAIR[0]
    return None


fill(head["front"], face_hair)

# Eyes: two pixels each, red, the only feature in the face.
fx, fy = head["front"][:2]
for ex in (1, 5):
    for i, c in enumerate((EYE[1], EYE[2])):
        put(fx + ex + i, fy + 4, EYE[0])
        put(fx + ex + i, fy + 4, c, eyes)

# Hat layer: the hair's outer volume. Back and sides fully covered, the front only at the edges.
hpx_fill = [("top", hair_top), ("back", hair)]
for name, fn in hpx_fill:
    fill(hat[name], fn)
fill(hat["right"], lambda x, y, w, h: hair(x, y, w, h) if x < 5 or y < 1 else None)
fill(hat["left"], lambda x, y, w, h: hair(x, y, w, h) if x > 2 or y < 1 else None)
fill(hat["front"], lambda x, y, w, h: face_hair(x, y, w, h) if (y == 0 or x in (0, 7)) else None)

# ---- body: jacket over darkness --------------------------------------------------------------

body = box(16, 16, 8, 12, 4)
for name in ("right", "front", "left", "back"):
    fill(body[name], leather)
fill(body["top"], lambda x, y, w, h: leather(x, y, w, h, top=True))
fill(body["bottom"], lambda x, y, w, h: DENIM[1])

bx, by = body["front"][:2]
# The neck shows darkness inside the collar, the lapels fold back to either side.
for x, y in ((3, 0), (4, 0), (3, 1), (4, 1), (4, 2)):
    put(bx + x, by + y, DARK[2] if y == 0 else DARK[1])
for x, y in ((2, 0), (2, 1), (5, 0), (5, 1), (1, 0), (6, 0)):
    put(bx + x, by + y, LEATHER[3] if y == 0 else LEATHER[2])
# Asymmetric zip, biker style: from the right of the collar down to the hem, slightly off centre.
for y in range(2, 12):
    put(bx + 3, by + y, METAL[1] if y % 2 else METAL[0])
put(bx + 3, by + 2, METAL[2])
# Zipped chest pocket and hand pockets.
put(bx + 5, by + 3, METAL[1]); put(bx + 6, by + 3, METAL[0])
put(bx + 1, by + 8, METAL[0]); put(bx + 5, by + 8, METAL[0]); put(bx + 6, by + 8, METAL[1])
# Waistband.
for x in range(8):
    if x != 3:
        put(bx + x, by + 11, LEATHER[0])
for name in ("right", "left", "back"):
    rx, ry, rw, rh = body[name]
    for x in range(rw):
        put(rx + x, ry + rh - 1, LEATHER[0])
# Collar seen from above.
tx, ty, tw, th = body["top"]
for x in range(tw):
    put(tx + x, ty, LEATHER[3] if x not in (3, 4) else DARK[2])

# Jacket overlay: long hair falling down the back.
jacket = box(16, 32, 8, 12, 4)
jx, jy, jw, jh = jacket["back"]
for x in range(jw):
    length = 5 if x in (0, 7) else 7 if x in (1, 6) else 8
    for y in range(length):
        put(jx + x, jy + y, hair(x, y, jw, length + 1))
# Over the shoulders at the top of the back.
tx, ty, tw, th = jacket["top"]
for x in range(tw):
    put(tx + x, ty + th - 1, HAIR[2])

# ---- arms: leather sleeves, darkness hands ---------------------------------------------------


def arm(u, v):
    faces = box(u, v, 4, 12, 4)
    for name in ("right", "front", "left", "back"):
        fill(faces[name], lambda x, y, w, h: (LEATHER[3] if y == 9 else leather(x, y, w, h)) if y < 10
             else darkness(x, y - 6, w, h))
    fill(faces["top"], lambda x, y, w, h: leather(x, y, w, h, top=True))
    fill(faces["bottom"], lambda x, y, w, h: DARK[0])
    # Zip at the cuff on the outer side.
    return faces


right_arm = arm(40, 16)
left_arm = arm(32, 48)
rx, ry = right_arm["right"][:2]
put(rx + 2, ry + 9, METAL[1])
lx, ly = left_arm["left"][:2]
put(lx + 1, ly + 9, METAL[1])

# ---- legs: black jeans and boots -------------------------------------------------------------


def leg(u, v):
    faces = box(u, v, 4, 12, 4)
    for name in ("right", "front", "left", "back"):
        fill(faces[name], lambda x, y, w, h: denim(x, y, w, h) if y < 9 else boot(x, y - 9, w, 3))
    fill(faces["top"], lambda x, y, w, h: denim(x, y, w, h, top=True))
    fill(faces["bottom"], lambda x, y, w, h: BOOT[0])
    return faces


leg(0, 16)
leg(16, 48)

# ---- pipe ------------------------------------------------------------------------------------

stem = box(56, 16, 1, 1, 3)
bowl = box(56, 20, 2, 3, 2)
for name, rect in stem.items():
    fill(rect, lambda x, y, w, h: BRIAR[1] if name != "bottom" else BRIAR[0])
for name, rect in bowl.items():
    if name == "top":
        continue
    fill(rect, lambda x, y, w, h: BRIAR[2] if y == 0 else BRIAR[1] if y == 1 else BRIAR[0])
tx, ty = bowl["top"][:2]
put(tx, ty, (40, 22, 14)); put(tx + 1, ty, EMBER[0]); put(tx, ty + 1, EMBER[0]); put(tx + 1, ty + 1, (40, 22, 14))
put(tx + 1, ty, EMBER[1], eyes); put(tx, ty + 1, EMBER[0], eyes)

os.makedirs(OUT, exist_ok=True)
img.save(os.path.join(OUT, "acela.png"))
eyes.save(os.path.join(OUT, "acela_eyes.png"))
print("wrote", OUT)
