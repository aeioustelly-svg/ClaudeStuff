#!/usr/bin/env python3
"""Builds the Field Guide item texture from the vanilla book, as the project guidelines ask: the
vanilla shape and shading, with the brown cover recoloured to the Cannonbolt's yellow.

    python3 tools/make_item_textures.py [client-extra.jar]
"""
import io
import os
import sys
import zipfile

from PIL import Image

JAR = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
OUT = "src/main/resources/assets/alien_fauna/textures/item/field_guide.png"

# vanilla book cover colours -> yellow ramp (dark outline stays near black)
RAMP = {(22, 16, 5): (24, 24, 28), (49, 33, 4): (98, 74, 18), (68, 37, 10): (120, 90, 22),
        (82, 46, 16): (152, 112, 24), (84, 62, 19): (176, 138, 30), (101, 75, 23): (200, 164, 38)}

with zipfile.ZipFile(JAR) as zf:
    img = Image.open(io.BytesIO(zf.read("assets/minecraft/textures/item/book.png"))).convert("RGBA")
px = img.load()
for y in range(img.height):
    for x in range(img.width):
        r, g, b, a = px[x, y]
        if a and (r, g, b) in RAMP:
            px[x, y] = RAMP[(r, g, b)] + (255,)
img.save(OUT)
print("Wrote", OUT)
