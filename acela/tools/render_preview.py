#!/usr/bin/env python3
"""Software-renders the baked Acela model with the real skin, no Minecraft needed.

Reads the geometry written by `./gradlew dumpModel` (the vanilla player mesh with the pipe, baked
by the game's own code) and the skin plus the glowing eyes layer, and writes contact sheets to
build/preview/. It shows geometry and UVs faithfully. It does not reproduce the game's lighting.

    ./gradlew dumpModel
    python3 tools/render_preview.py
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

MODEL = "build/preview/model.json"
TEXTURE = "src/main/resources/assets/aceliada/textures/entity/acela.png"
EYES = "src/main/resources/assets/aceliada/textures/entity/acela_eyes.png"
OUT_DIR = "build/preview"
BG = (120, 60, 52)   # a nether-ish backdrop so the black figure stands out
LIGHT = np.array([-0.3, 0.8, -0.5])
LIGHT /= np.linalg.norm(LIGHT)


def load_quads(data, pose):
    quads = []
    for q in data["poses"][pose]:
        v = np.array(q).reshape(4, 8)
        pos = v[:, :3] * np.array([1, -1, 1])           # y up, front of the figure at -z
        uv = v[:, 3:5]
        normal = v[0, 5:8] * np.array([1, -1, 1])
        quads.append((pos, uv, normal))
    return quads


def render(quads, tex, yaw, pitch, scale, size, focus=None, ss=3):
    W, H = size
    Ws, Hs = W * ss, H * ss
    a, p = np.radians(yaw), np.radians(pitch)
    cam = np.array([np.sin(a) * np.cos(p), np.sin(p), np.cos(a) * np.cos(p)])   # towards camera
    fwd = -cam
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)

    pts = np.concatenate([q[0] for q in quads])
    if focus is not None:
        pts = pts[focus(pts)]
    target = (pts.min(0) + pts.max(0)) / 2

    color = np.zeros((Hs, Ws, 3), np.float32)
    color[:] = BG
    depth = np.full((Hs, Ws), np.inf, np.float32)
    tsize = tex.shape[0]

    for pos, uv, normal in quads:
        rel = pos - target
        sx = rel @ right * scale * ss + Ws / 2
        sy = -(rel @ up) * scale * ss + Hs / 2
        dz = rel @ fwd
        n = normal / (np.linalg.norm(normal) + 1e-9)
        if n @ fwd > 0:
            n = -n                                     # two-sided
        shade = 0.55 + 0.45 * max(0.0, float(n @ LIGHT))
        for tri in ((0, 1, 2), (0, 2, 3)):
            i0, i1, i2 = tri
            x0, y0, x1, y1, x2, y2 = sx[i0], sy[i0], sx[i1], sy[i1], sx[i2], sy[i2]
            denom = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
            if abs(denom) < 1e-9:
                continue
            minx, maxx = int(max(0, np.floor(min(x0, x1, x2)))), int(min(Ws - 1, np.ceil(max(x0, x1, x2))))
            miny, maxy = int(max(0, np.floor(min(y0, y1, y2)))), int(min(Hs - 1, np.ceil(max(y0, y1, y2))))
            if minx > maxx or miny > maxy:
                continue
            X, Y = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            l0 = ((y1 - y2) * (X - x2) + (x2 - x1) * (Y - y2)) / denom
            l1 = ((y2 - y0) * (X - x2) + (x0 - x2) * (Y - y2)) / denom
            l2 = 1 - l0 - l1
            inside = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
            if not inside.any():
                continue
            d = l0 * dz[i0] + l1 * dz[i1] + l2 * dz[i2]
            u = l0 * uv[i0, 0] + l1 * uv[i1, 0] + l2 * uv[i2, 0]
            v = l0 * uv[i0, 1] + l1 * uv[i1, 1] + l2 * uv[i2, 1]
            tu = np.clip(np.floor(u).astype(int), 0, tsize - 1)
            tv = np.clip(np.floor(v).astype(int), 0, tsize - 1)
            texel = tex[tv, tu]
            sub_depth = depth[miny:maxy + 1, minx:maxx + 1]
            write = inside & (d < sub_depth) & (texel[..., 3] > 128)
            sub_depth[write] = d[write]
            sub_color = color[miny:maxy + 1, minx:maxx + 1]
            sub_color[write] = texel[..., :3][write] * shade

    img = Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGB")
    return img.resize((W, H), Image.BOX)


def sheet(cells, cols, cell_size, path, title_h=18):
    W, H = cell_size
    rows = (len(cells) + cols - 1) // cols
    out = Image.new("RGB", (cols * W, rows * (H + title_h)), (24, 26, 30))
    draw = ImageDraw.Draw(out)
    for i, (label, img) in enumerate(cells):
        x, y = (i % cols) * W, (i // cols) * (H + title_h)
        out.paste(img, (x, y + title_h))
        draw.text((x + 6, y + 3), label, fill=(230, 230, 230))
    out.save(path)
    print("Wrote", path)


def skin():
    """The skin with the eyes layer drawn on top, as the eyes render type adds it at full brightness."""
    base = Image.open(TEXTURE).convert("RGBA")
    base.alpha_composite(Image.open(EYES).convert("RGBA"))
    return base


def main():
    with open(MODEL) as f:
        data = json.load(f)
    t = skin()
    tex = np.array(t)
    os.makedirs(OUT_DIR, exist_ok=True)

    views = [("front", 180, 6), ("3/4", 215, 14), ("side", 90, 4), ("back", 0, 10)]
    cells = []
    for pose in ("idle", "walk", "attack"):
        quads = load_quads(data, pose)
        for name, yaw, pitch in views:
            cells.append((f"{pose} / {name}", render(quads, tex, yaw, pitch, 7.0, (260, 300))))
    sheet(cells, 4, (260, 300), os.path.join(OUT_DIR, "body.png"))

    head_only = lambda pts: pts[:, 1] > -0.5
    cells = []
    quads = load_quads(data, "idle")
    for name, yaw, pitch in (("front", 180, 6), ("3/4", 215, 14), ("side", 90, 4), ("top", 180, 75)):
        cells.append((f"head / {name}", render(quads, tex, yaw, pitch, 18.0, (300, 300), focus=head_only)))
    sheet(cells, 4, (300, 300), os.path.join(OUT_DIR, "head.png"))

    bg = Image.new("RGBA", t.size, (70, 70, 76, 255))
    bg.alpha_composite(t)
    bg.resize((512, 512), Image.NEAREST).convert("RGB").save(os.path.join(OUT_DIR, "texture.png"))
    print("Wrote", os.path.join(OUT_DIR, "texture.png"))


if __name__ == "__main__":
    main()
