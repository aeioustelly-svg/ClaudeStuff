#!/usr/bin/env python3
"""Stage 2: map each extracted cloud to the mod's five shade indices.

  0 transparent
  1 deep base shadow        the dark navy underside
  2 lavender crevice shadow the folds between puffs and the soft edge where the underside meets a puff
  3 light-blue midtone      the body of each puff
  4 white highlight         the lit tops

The sheet itself holds only three solid tones (navy, light blue, white) plus JPEG blends between them, so
band 2 cannot come from colour alone. It is taken from two places:
  - blend cells whose luminance falls between the navy and the light blue (the source's own transition pixels),
  - light-blue cells that touch the navy underside, or that lie deep in a fold with no white within 2 cells.
That keeps band 2 on the source's crevices instead of scattering it by JPEG noise.

    python3 -I clouds/tools/shade_clouds.py [sprites_dir]

Reads sprites_dir/extracted/*.png and writes sprites_dir/shaded/*.png as 8-bit palette PNGs: the pixel value
is the shade index, and the embedded palette is only there so the files open as pictures.
"""
import glob
import os
import sys

import numpy as np
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "clouds/sprites"

# Display palette for the indices (daylight). The mod will recolour by index for dusk, dawn and storms.
PALETTE = [
    (0, 0, 0),          # 0 transparent (alpha via the PNG transparency entry)
    (31, 66, 148),      # 1 deep base shadow
    (138, 150, 222),    # 2 lavender crevice shadow
    (164, 196, 250),    # 3 light-blue midtone
    (255, 255, 255),    # 4 white highlight
]


def luminance(rgb):
    return rgb[..., 0] * 0.3 + rgb[..., 1] * 0.59 + rgb[..., 2] * 0.11


def near(mask, r):
    """True where mask is set within a (2r+1) square. Out-of-range cells count as unset."""
    h, w = mask.shape
    pad = np.pad(mask, r)
    out = np.zeros_like(mask)
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            out |= pad[r + dy:r + dy + h, r + dx:r + dx + w]
    return out


def shade(rgba):
    rgb = rgba[..., :3].astype(float)
    solid = rgba[..., 3] > 0
    lum = luminance(rgb)
    idx = np.zeros(lum.shape, np.uint8)
    idx[solid & (lum < 95)] = 1
    idx[solid & (lum >= 95) & (lum < 172)] = 2
    idx[solid & (lum >= 172) & (lum < 222)] = 3
    idx[solid & (lum >= 222)] = 4

    mid = idx == 3
    # Light blue resting on the navy underside: the soft lavender lip (directly above or beside navy).
    navy = idx == 1
    above_navy = np.zeros_like(navy)
    above_navy[:-1] = navy[1:]
    beside = np.zeros_like(navy)
    beside[:, 1:] |= navy[:, :-1]
    beside[:, :-1] |= navy[:, 1:]
    idx[mid & (above_navy | beside)] = 2
    # Deep folds: light blue with no white anywhere near, and enclosed on both sides by cloud.
    mid = idx == 3
    far_from_white = ~near(idx == 4, 2)
    enclosed = solid & np.roll(solid, 2, 1) & np.roll(solid, -2, 1) & np.roll(solid, -2, 0)
    idx[mid & far_from_white & enclosed] = 2
    return idx


def save_indexed(idx, path):
    im = Image.fromarray(idx, "P")
    flat = [c for rgb in PALETTE for c in rgb]
    im.putpalette(flat + [0] * (768 - len(flat)))
    im.info["transparency"] = 0
    im.save(path, transparency=0)


def main():
    os.makedirs(os.path.join(OUT, "shaded"), exist_ok=True)
    for path in sorted(glob.glob(os.path.join(OUT, "extracted", "cloud_*.png"))):
        idx = shade(np.asarray(Image.open(path).convert("RGBA")))
        save_indexed(idx, os.path.join(OUT, "shaded", os.path.basename(path)))
        counts = np.bincount(idx.ravel(), minlength=5)
        print(os.path.basename(path), "bands 1-4:", counts[1:].tolist())


if __name__ == "__main__":
    main()
