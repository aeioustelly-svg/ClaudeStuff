#!/usr/bin/env python3
"""Builds the mod's item textures from vanilla textures, as the project guidelines ask
(same family means same template, new material painted in).

  nose_pickle.png   vanilla sea pickle shape and shading, on a warty olive-to-pale-green ramp

    python3 -I tools/make_item_textures.py [client-extra.jar]
"""
import io
import os
import sys
import zipfile

import numpy as np
from PIL import Image

JAR = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
OUT = "src/main/resources/assets/gnoblars/textures/item/"
T = "assets/minecraft/textures/"


def load(zf, name):
    return Image.open(io.BytesIO(zf.read(T + name))).convert("RGBA")


def ramp(img, dark, light):
    """Recolour by brightness: darkest pixel -> dark, brightest -> light. Alpha is kept."""
    a = np.array(img).astype(float)
    solid = a[..., 3] > 0
    lum = (a[..., 0] * 0.3 + a[..., 1] * 0.59 + a[..., 2] * 0.11) / 255.0
    lo, hi = lum[solid].min(), lum[solid].max()
    t = ((lum - lo) / (hi - lo + 1e-9))[..., None]
    out = a.copy()
    out[..., :3] = np.array(dark) * (1 - t) + np.array(light) * t
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


def main():
    with zipfile.ZipFile(JAR) as zf:
        pickle = load(zf, "item/sea_pickle.png")
    os.makedirs(OUT, exist_ok=True)
    ramp(pickle, (58, 62, 30), (178, 176, 104)).save(OUT + "nose_pickle.png")
    print("Wrote", OUT + "nose_pickle.png")


if __name__ == "__main__":
    main()
