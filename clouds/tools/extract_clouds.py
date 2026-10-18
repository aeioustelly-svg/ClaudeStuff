#!/usr/bin/env python3
"""Stage 1: cut the clouds out of the reference sheet and bring them back to one art pixel per image pixel.

  1. The sky colour is the median of the sheet. A cell is cloud when its colour is far enough from it
     (the deep base shadow is darker than the sky, so a colour distance catches it as well as the whites).
  2. The pixel grid pitch is found from the gradient energy: the art was upscaled by a non-integer factor
     (about 8.6), so the pitch is searched in 0.005 steps and the phase is fitted again for each crop.
  3. Each art cell is sampled at its centre (median of the middle 40 percent), which skips the JPEG ringing
     at cell edges. That is the nearest-neighbour downscale.
  4. Connected components on the downscaled mask give the clouds. Specks within 2 art pixels of a cloud
     stay with it, so the loose puffs around the wispy clouds are kept.

    python3 -I clouds/tools/extract_clouds.py [sheet.jpg] [out_dir]

Writes out_dir/extracted/cloud_NN.png (RGBA, sky alpha 0) and out_dir/extracted/grid.json.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

SHEET = sys.argv[1] if len(sys.argv) > 1 else "clouds/reference/cloud_sheet.jpg"
OUT = sys.argv[2] if len(sys.argv) > 2 else "clouds/sprites"
SKY_DISTANCE = 42.0     # RGB distance from the sky colour above which a cell is cloud
MIN_CELLS = 12          # components smaller than this are stray specks, dropped unless near a cloud
MERGE_GAP = 2           # specks this many art pixels from a cloud belong to it


def fit_grid(sig, lo=8.0, hi=9.4):
    """Pitch and phase that put grid lines on the strongest gradient columns (or rows)."""
    best = (-1.0, 0.0, 0.0)
    mean = sig.mean() + 1e-9
    for p in np.arange(lo, hi, 0.005):
        k = np.arange(0, len(sig) / p)
        for ph in np.arange(0, p, 0.25):
            idx = np.round(ph + k * p).astype(int)
            idx = idx[idx < len(sig)]
            s = sig[idx].mean() / mean
            if s > best[0]:
                best = (s, p, ph)
    return best[1], best[2]


def fit_phase(sig, p):
    best = (-1.0, 0.0)
    k = np.arange(0, len(sig) / p)
    for ph in np.arange(0, p, 0.1):
        idx = np.round(ph + k * p).astype(int)
        idx = idx[idx < len(sig)]
        s = sig[idx].mean()
        if s > best[0]:
            best = (s, ph)
    return best[1]


def sample(img, x0, y0, p, w, h):
    """Median colour of the central part of each art cell. Cell (i, j) spans x0+i*p .. x0+(i+1)*p."""
    out = np.zeros((h, w, 3))
    inset = p * 0.3
    for j in range(h):
        ya, yb = int(round(y0 + j * p + inset)), int(round(y0 + (j + 1) * p - inset))
        for i in range(w):
            xa, xb = int(round(x0 + i * p + inset)), int(round(x0 + (i + 1) * p - inset))
            out[j, i] = np.median(img[ya:yb, xa:xb].reshape(-1, 3), axis=0)
    return out


def components(mask):
    """4-connected labelling without scipy. Returns a label array (0 = background) and the count."""
    lab = np.zeros(mask.shape, int)
    n = 0
    h, w = mask.shape
    for y in range(h):
        for x in range(w):
            if mask[y, x] and not lab[y, x]:
                n += 1
                stack = [(y, x)]
                lab[y, x] = n
                while stack:
                    cy, cx = stack.pop()
                    for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                        if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not lab[ny, nx]:
                            lab[ny, nx] = n
                            stack.append((ny, nx))
    return lab, n


def dilate(mask, r):
    out = mask.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            out |= np.roll(np.roll(mask, dy, 0), dx, 1)
    return out


def main():
    img = np.asarray(Image.open(SHEET).convert("RGB")).astype(float)
    sky = np.median(img.reshape(-1, 3), axis=0)
    lum = img.mean(2)
    p_x, ph_x = fit_grid(np.abs(np.diff(lum, axis=1)).sum(0))
    p_y, ph_y = fit_grid(np.abs(np.diff(lum, axis=0)).sum(1))
    p = (p_x + p_y) / 2
    print(f"sky {sky}, pitch x {p_x:.3f} y {p_y:.3f}, using {p:.3f}")

    # Coarse pass over the whole sheet to find the clouds.
    w, h = int((img.shape[1] - ph_x) // p), int((img.shape[0] - ph_y) // p)
    coarse = sample(img, ph_x, ph_y, p, w, h)
    cloud = np.linalg.norm(coarse - sky, axis=2) > SKY_DISTANCE
    lab, n = components(dilate(cloud, MERGE_GAP))
    boxes = []
    for k in range(1, n + 1):
        ys, xs = np.nonzero((lab == k) & cloud)
        if len(ys) < MIN_CELLS:
            continue
        boxes.append((ys.min(), xs.min(), ys.max(), xs.max()))
    # Reading order: rows of the sheet (by centre height in thirds), then left to right.
    boxes.sort(key=lambda b: (int((b[0] + b[2]) / 2 / (h / 3)), b[1]))

    os.makedirs(os.path.join(OUT, "extracted"), exist_ok=True)
    meta = {"sky": sky.tolist(), "pitch": p, "clouds": []}
    for idx, (y0, x0, y1, x1) in enumerate(boxes):
        # Refit the phase on this crop (the generated sheet drifts by a fraction of a cell).
        m = 2
        px0, py0 = ph_x + (x0 - m) * p, ph_y + (y0 - m) * p
        px1, py1 = ph_x + (x1 + 1 + m) * p, ph_y + (y1 + 1 + m) * p
        crop = img[max(0, int(py0)):int(py1), max(0, int(px0)):int(px1)]
        cl = crop.mean(2)
        fx = fit_phase(np.abs(np.diff(cl, axis=1)).sum(0), p)
        fy = fit_phase(np.abs(np.diff(cl, axis=0)).sum(1), p)
        cw, ch = int((crop.shape[1] - fx) // p), int((crop.shape[0] - fy) // p)
        cells = sample(crop, fx, fy, p, cw, ch)
        solid = np.linalg.norm(cells - sky, axis=2) > SKY_DISTANCE
        # Keep only the component(s) belonging to this cloud, not a neighbour's edge.
        clab, cn = components(dilate(solid, MERGE_GAP))
        sizes = [((clab == k) & solid).sum() for k in range(1, cn + 1)]
        keep = (clab == 1 + int(np.argmax(sizes))) & solid
        ys, xs = np.nonzero(keep)
        keep = keep[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
        cells = cells[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
        rgba = np.zeros(keep.shape + (4,), np.uint8)
        rgba[..., :3] = np.clip(cells, 0, 255).astype(np.uint8)
        rgba[..., 3] = keep * 255
        name = f"cloud_{idx:02d}.png"
        Image.fromarray(rgba).save(os.path.join(OUT, "extracted", name))
        meta["clouds"].append({"file": name, "size": [int(keep.shape[1]), int(keep.shape[0])]})
        print(name, keep.shape[1], "x", keep.shape[0])
    with open(os.path.join(OUT, "extracted", "grid.json"), "w") as f:
        json.dump(meta, f, indent=1)


if __name__ == "__main__":
    main()
