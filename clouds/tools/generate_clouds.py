#!/usr/bin/env python3
"""Stage 3: build new cloud sprites out of puff lobes cut from the shaded source clouds.

Lobes
  Every white highlight cluster (band 4, at least MIN_SEED cells) on a source cloud seeds one lobe. Each cloud
  cell joins the seed it reaches first by a walk that stays inside the cloud, so the cuts between lobes run
  through the lavender and light-blue folds between puffs, where the artist already drew a break. Tiny lobes are
  folded into their neighbour. For every lobe the script records which sides are the source's own outline and
  which are cuts, so the ends of a new cloud can be given lobes whose outer side is a drawn edge.

Sprites
  A recipe picks a silhouette type (heap, low stratus, tower, small puff), then lays
  lobes on a baseline with overlap: upper lobes first, front lobes last, so each later lobe covers the seams of
  the ones behind it. Each placed lobe may be flipped horizontally and rescaled by nearest neighbour with
  separate x and y factors (0.8 to 1.25). Nothing is drawn procedurally: every pixel comes from the sheet.
  A cleanup pass fills single-cell holes and trims single cells hanging off a cut edge.

    python3 -I clouds/tools/generate_clouds.py [sprites_dir] [count] [seed]

Writes sprites_dir/generated/cloud_gen_NN.png (palette PNG, pixel value = shade index) and
sprites_dir/lobes/lobe_NNN.png for inspection.
"""
import glob
import os
import sys
from collections import deque

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shade_clouds import save_indexed  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else "clouds/sprites"
COUNT = int(sys.argv[2]) if len(sys.argv) > 2 else 35
SEED = int(sys.argv[3]) if len(sys.argv) > 3 else 1977

MIN_SEED = 5        # smallest white cluster that counts as a puff top
MIN_LOBE = 30       # lobes smaller than this are merged into a neighbour
MAX_SEED = 45       # white cells per seed before a puff top is split in two (keeps lobes puff-sized)
# Source clouds by kind (indices into shaded/cloud_NN.png, see extract_clouds.py for the order).
PUFFY = {0, 1, 2, 3, 6, 8, 9, 11, 12}
THIN = {1}          # the long thin cloud: its lobes are puffy but too flat to build with
MIN_ROUND = 0.5     # smallest top-third fill for a puff lobe (keeps pointed tops out)
LAVENDER_FROM = 0.45  # lavender patches centred above this fraction of the cloud's height go (round 4)
MAX_SLAB = 16       # longest straight top edge of a lavender area (round 4: 26 read as too straight, 14 passed)
MAX_WHITE_WALL = 5  # longest straight vertical white edge (the sheet's clouds: at most 5; round 9 flagged a 7)
MAX_NAVY = 4        # thickest the navy underside may be, in cells
MAX_FLAT_SIDE = 7   # longest near-vertical run (within one column) allowed on either side of a sprite
WISPY = {4, 7, 10}
SCATTERED = {5}
N8 = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]
N4 = [(-1, 0), (1, 0), (0, -1), (0, 1)]


# ----------------------------------------------------------------------------------------------- lobes

def label(mask, nbrs=N8):
    lab = np.zeros(mask.shape, int)
    n = 0
    h, w = mask.shape
    for y, x in zip(*np.nonzero(mask)):
        if lab[y, x]:
            continue
        n += 1
        lab[y, x] = n
        stack = [(y, x)]
        while stack:
            cy, cx = stack.pop()
            for dy, dx in nbrs:
                ny, nx = cy + dy, cx + dx
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not lab[ny, nx]:
                    lab[ny, nx] = n
                    stack.append((ny, nx))
    return lab, n


def geodesic_voronoi(solid, seeds):
    """Assign every solid cell to the nearest seed label by a breadth-first walk inside the cloud."""
    own = np.where(seeds > 0, seeds, 0)
    q = deque(zip(*np.nonzero(own)))
    h, w = solid.shape
    while q:
        y, x = q.popleft()
        for dy, dx in N4:
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and solid[ny, nx] and not own[ny, nx]:
                own[ny, nx] = own[y, x]
                q.append((ny, nx))
    return own


def merge_small(own, solid):
    while True:
        ids, counts = np.unique(own[own > 0], return_counts=True)
        small = [i for i, c in zip(ids, counts) if c < MIN_LOBE]
        if not small or len(ids) == 1:
            return own
        k = small[0]
        ys, xs = np.nonzero(own == k)
        votes = {}
        for y, x in zip(ys, xs):
            for dy, dx in N4:
                ny, nx = y + dy, x + dx
                if 0 <= ny < own.shape[0] and 0 <= nx < own.shape[1]:
                    o = own[ny, nx]
                    if o and o != k:
                        votes[o] = votes.get(o, 0) + 1
        if not votes:      # an island: keep it as its own lobe even if small
            own[own == k] = -k
            continue
        own[own == k] = max(votes, key=votes.get)


def side_edges(mask, src_solid, oy, ox):
    """Fraction of each side's outermost cells whose outward neighbour is outside the source cloud (a drawn edge)."""
    h, w = mask.shape
    H, W = src_solid.shape

    def drawn(y, x):
        y, x = y + oy, x + ox
        return not (0 <= y < H and 0 <= x < W) or not src_solid[y, x]

    res = {}
    for name, rows, dy, dx in (("left", True, 0, -1), ("right", True, 0, 1), ("top", False, -1, 0)):
        hits = tot = 0
        for i in range(h if rows else w):
            line = mask[i] if rows else mask[:, i]
            nz = np.nonzero(line)[0]
            if not len(nz):
                continue
            j = nz[0] if (dx < 0 or dy < 0) else nz[-1]
            y, x = (i, j) if rows else (j, i)
            tot += 1
            hits += drawn(y + dy, x + dx)
        res[name] = hits / max(tot, 1)
    return res


def erode4(mask):
    pad = np.pad(mask, 1)
    return mask & pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]


def split_seeds(tops, solid):
    """Split a seed whose geodesic lobe would be too big: k-means on its white cells, farthest-point start."""
    out = np.zeros_like(tops)
    n = 0
    for k in np.unique(tops[tops > 0]):
        pts = np.argwhere(tops == k).astype(float)
        parts = max(1, int(round(len(pts) / MAX_SEED)))
        centres = [pts[0]]
        while len(centres) < parts:
            d = np.min([((pts - c) ** 2).sum(1) for c in centres], axis=0)
            centres.append(pts[int(np.argmax(d))])
        centres = np.array(centres)
        for _ in range(20):
            lab = np.argmin([((pts - c) ** 2).sum(1) for c in centres], axis=0)
            centres = np.array([pts[lab == i].mean(0) if (lab == i).any() else centres[i] for i in range(parts)])
        for i in range(parts):
            n += 1
            for y, x in pts[lab == i].astype(int):
                out[y, x] = n
    return out


def cut_lobes(shaded, src_id):
    solid = shaded > 0
    # Erode the white first: neighbouring puff tops often touch through a one-cell bridge.
    tops, n = label(erode4(shaded == 4), N4)
    for k in range(1, n + 1):
        if (tops == k).sum() < MIN_SEED:
            tops[tops == k] = 0
    if tops.max() == 0:
        tops = solid.astype(int)
    tops = split_seeds(tops, solid)
    own = geodesic_voronoi(solid, tops)
    # Cells not reached (islands without a white seed) get their own label.
    rest, rn = label(solid & (own == 0), N8)
    own[rest > 0] = rest[rest > 0] + own.max()
    own = np.abs(merge_small(own, solid))
    lobes = []
    for k in np.unique(own[own > 0]):
        ys, xs = np.nonzero(own == k)
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        m = own[y0:y1, x0:x1] == k
        idx = np.where(m, shaded[y0:y1, x0:x1], 0).astype(np.uint8)
        if m.sum() < 8:
            continue
        lobes.append({
            "idx": idx,
            "src": src_id,
            "kind": "wisp" if src_id in WISPY else "scatter" if src_id in SCATTERED else "puff",
            "edges": side_edges(m, solid, y0, x0),
            "rel_y": ((y0 + y1) / 2) / shaded.shape[0],
            "navy": float((idx == 1).sum()) / m.sum(),
            "area": int(m.sum()),
            # Fill of the top third of the bounding box: round puff tops score high, pointed shards low.
            "round": float(m[:max(2, (y1 - y0) // 3)].mean()),
            "w": int(x1 - x0),
            "h": int(y1 - y0),
        })
    return lobes


# ---------------------------------------------------------------------------------------------- sprites

def transform(lobe, rng, flip=None, sx=None, sy=None):
    idx = lobe["idx"]
    edges = dict(lobe["edges"])
    if flip is None:
        flip = rng.random() < 0.5
    if flip:
        idx = idx[:, ::-1]
        edges["left"], edges["right"] = edges["right"], edges["left"]
    sx = rng.uniform(0.8, 1.25) if sx is None else sx
    sy = rng.uniform(0.8, 1.2) if sy is None else sy
    h, w = idx.shape
    nw, nh = max(2, int(round(w * sx))), max(2, int(round(h * sy)))
    # Nearest neighbour: sample the centre of each destination cell.
    ys = np.minimum(((np.arange(nh) + 0.5) * h / nh).astype(int), h - 1)
    xs = np.minimum(((np.arange(nw) + 0.5) * w / nw).astype(int), w - 1)
    return idx[ys][:, xs], edges


def stamp(canvas, idx, x, y):
    """Paint idx with its bottom-left cell at (x, y) on the canvas (y = bottom row). Later stamps sit in front."""
    h, w = idx.shape
    top = y - h + 1
    region = canvas[top:top + h, x:x + w]
    np.copyto(region, idx, where=idx > 0)


def fit_pool(pool, target_h, lo=0.8, hi=1.25):
    """Lobes that reach target_h within the allowed rescale. Falls back to the closest heights."""
    ok = [L for L in pool if lo <= target_h / L["h"] <= hi]
    return ok if len(ok) >= 3 else sorted(pool, key=lambda L: abs(np.log(target_h / L["h"])))[:6]


def pick(pool, rng, key=None, k=6):
    """Random choice biased towards the k best by key (higher is better)."""
    if key is not None:
        pool = sorted(pool, key=key, reverse=True)[:max(k, 1)]
    return pool[rng.integers(len(pool))]


def plan_row(rng, pool, n, cx, base_y, target_h, spacing=(0.45, 0.65), base=True, squash=1.0):
    """Placements for n lobes centred on cx, bottoms on base_y. Ends get lobes whose outer side is drawn."""
    # A lobe that is mostly navy (the slab under the flat-bottomed source cloud) reads as a block when cut loose.
    # Taper: the end lobes of a row are smaller, so the sides step down in puffs instead of standing as a wall.
    th = [target_h * (0.78 + 0.22 * np.sin(np.pi * (i + 0.5) / n)) if n >= 3 else target_h * (1.0 - 0.15 * i)
          for i in range(n)]
    pools = [fit_pool([L for L in pool if L["navy"] <= 0.35], t) for t in th]
    row = []
    for i in range(n):
        cands = pools[i]
        if base:
            score = lambda L: min(L["navy"], 0.25) * 6 + L["rel_y"]
        else:
            score = lambda L: L["edges"]["top"] - L["navy"] * 2
        if n > 1 and i == 0:
            lobe = pick(cands, rng, key=lambda L: score(L) + max(L["edges"]["left"], L["edges"]["right"]) * 4, k=5)
            flip = lobe["edges"]["right"] > lobe["edges"]["left"]
        elif n > 1 and i == n - 1:
            lobe = pick(cands, rng, key=lambda L: score(L) + max(L["edges"]["left"], L["edges"]["right"]) * 4, k=5)
            flip = lobe["edges"]["left"] > lobe["edges"]["right"]
        else:
            lobe = pick(cands, rng, key=score, k=8)
            flip = None
        sy = np.clip(th[i] / lobe["h"] * rng.uniform(0.92, 1.08), 0.8, 1.25) * squash
        sx = np.clip(sy / squash * rng.uniform(0.85, 1.18), 0.8, 1.25)
        idx, _ = transform(lobe, rng, flip=flip, sx=sx, sy=sy)
        if not base:
            # A puff resting on another one shows a lavender fold where it meets it, not the navy underside.
            idx = np.where(idx == 1, 2, idx).astype(np.uint8)
        row.append([idx, 0, base_y + int(rng.integers(-1, 2))])
    x = 0
    for i, r in enumerate(row):
        r[1] = x
        x += max(3, int(r[0].shape[1] * rng.uniform(*spacing)))
    width = row[-1][1] + row[-1][0].shape[1]
    for r in row:
        r[1] += cx - width // 2
    return row


def stamp_rows(canvas, rows):
    """Top rows first, then lower rows in front of them. Inside a row: ends first, centre last."""
    for row in reversed(rows):
        n = len(row)
        for i in sorted(range(n), key=lambda i: -abs(i - (n - 1) / 2)):
            stamp(canvas, *row[i])


def stack(rng, pool, levels, cx=160, base_y=170, lean=0, squash=1.0):
    """levels: list of (lobe count, target lobe height). Each level sits on the upper half of the one below."""
    rows = []
    y = base_y
    for li, (n, th) in enumerate(levels):
        row = plan_row(rng, pool, n, cx, y, th, base=(li == 0), squash=squash)
        rows.append(row)
        h = int(np.median([r[0].shape[0] for r in row]))
        y -= max(2, int(h * rng.uniform(0.4, 0.55)))
        cx += lean + int(rng.integers(-2, 3))
    return rows


def recipe(kind, rng, lib):
    # Puffs only, and roundish ones: solidity (area over bounding box) keeps out crescent horns and wisp shards.
    # Lobes cut from the long thin cloud and the wisps made ragged, smeared sprites, so they are not used.
    puffs = [L for L in lib if L["kind"] == "puff" and L["src"] not in THIN and L["w"] <= 2.2 * L["h"]
             and L["area"] >= 30 and L["area"] >= 0.55 * L["w"] * L["h"] and L["round"] >= MIN_ROUND]
    C = np.zeros((240, 320), np.uint8)
    lean = int(rng.choice([-2, -1, 0, 1, 2]))
    if kind == "heap":
        b = int(rng.integers(16, 25))
        levels = [(int(rng.integers(3, 6)), b), (int(rng.integers(2, 4)), b - 3)]
        if rng.random() < 0.5:
            levels.append((int(rng.integers(1, 3)), b - 5))
        stamp_rows(C, stack(rng, puffs, levels, lean=lean))
    elif kind == "stratus":
        # Low and wide, but not thin: the one stratus that was liked was 16 rows tall at 3 to 1.
        b = int(rng.integers(15, 19))
        levels = [(int(rng.integers(4, 6)), b), (int(rng.integers(2, 4)), b - 3)]
        stamp_rows(C, stack(rng, puffs, levels, squash=0.85))
    elif kind == "tower":
        b = int(rng.integers(15, 19))
        levels = [(2, b), (2, b - 1), (int(rng.integers(1, 3)), b - 2), (1, b - 3)]
        if rng.random() < 0.5:
            levels.append((1, b - 5))
        stamp_rows(C, stack(rng, puffs, levels, lean=lean))
    elif kind == "small":
        b = int(rng.integers(11, 15))
        levels = [(int(rng.integers(3, 5)), b), (int(rng.integers(1, 3)), b - 2)]
        stamp_rows(C, stack(rng, puffs, levels, lean=lean))
    return C


# ---------------------------------------------------------------------------------------------- quality

def side_runs(c, tol=1):
    """Longest run of rows whose leftmost (or rightmost) cell stays inside a band tol+1 columns wide: a flat side."""
    best = 0
    for side in (0, -1):
        xs = [np.nonzero(r)[0][side] for r in c if r.any()]
        for i in range(len(xs)):
            lo = hi = xs[i]
            j = i
            while j < len(xs) and max(hi, xs[j]) - min(lo, xs[j]) <= tol:
                lo, hi = min(lo, xs[j]), max(hi, xs[j])
                j += 1
            best = max(best, j - i)
    return best


def slab(c):
    """Longest horizontal run of lavender cells with a lighter cell directly above each: a straight-topped slab."""
    above = np.pad(c, ((1, 0), (0, 0)))[:-1]
    edge = (c == 2) & ((above == 3) | (above == 4))
    best = 0
    for row in edge:
        run = 0
        for v in row:
            run = run + 1 if v else 0
            best = max(best, run)
    return best


def seam(c):
    """Longest straight vertical boundary between two shades, and the share of side-by-side neighbours two or more
    bands apart. Together they mark lobes whose shading does not blend into their neighbour's."""
    a, b = c[:, :-1].astype(int), c[:, 1:].astype(int)
    both = (a > 0) & (b > 0)
    jumps = (both & (np.abs(a - b) >= 2)).sum() / max((c > 0).sum(), 1)
    edge = both & (a != b)
    longest = 0
    for x in range(edge.shape[1]):
        run = 0
        for v in edge[:, x]:
            run = run + 1 if v else 0
            longest = max(longest, run)
    return longest, jumps


def white_wall(c):
    """Longest straight vertical edge of white against a darker shade: a lobe's white side cut off flat."""
    a, b = c[:, :-1], c[:, 1:]
    edge = ((a == 4) & (b > 0) & (b < 4)) | ((b == 4) & (a > 0) & (a < 4))
    best = 0
    for x in range(edge.shape[1]):
        run = 0
        for v in edge[:, x]:
            run = run + 1 if v else 0
            best = max(best, run)
    return best


def navy_cover(c):
    """Share of columns with navy in them. The sheet's puffy clouds: 0.89 to 1."""
    cols = [x for x in range(c.shape[1]) if c[:, x].any()]
    return sum(1 for x in cols if (c[:, x] == 1).any()) / max(len(cols), 1)


def flat_top(c, tol=1):
    """Longest run of columns whose top cell stays within tol+1 rows, in cells and as a share of the width."""
    s = c > 0
    ys = [np.nonzero(s[:, x])[0][0] for x in range(s.shape[1]) if s[:, x].any()]
    best = 0
    for i in range(len(ys)):
        lo = hi = ys[i]
        j = i
        while j < len(ys) and max(hi, ys[j]) - min(lo, ys[j]) <= tol:
            lo, hi = min(lo, ys[j]), max(hi, ys[j])
            j += 1
        best = max(best, j - i)
    return best, best / max(len(ys), 1)


def block_corner(c, bw=4, bh=3):
    """True if an all-lavender bw x bh rectangle has lighter cells along its whole top and along a whole side: a
    square-cornered block. None of the sheet's clouds has one at 4 x 3."""
    h, w = c.shape
    lav = c == 2
    light = c >= 3
    for y in range(1, h - bh + 1):
        for x in range(0, w - bw + 1):
            if not lav[y:y + bh, x:x + bw].all() or not light[y - 1, x:x + bw].all():
                continue
            if (x > 0 and light[y:y + bh, x - 1].all()) or (x + bw < w and light[y:y + bh, x + bw].all()):
                return True
    return False


def thick_lip(c, cells=4):
    """Share of underside columns whose lavender band above the navy is at least `cells` thick."""
    cols = thick = 0
    for x in range(c.shape[1]):
        ys = np.nonzero(c[:, x] == 1)[0]
        if not len(ys):
            continue
        y, k = ys[0] - 1, 0
        while y >= 0 and c[y, x] == 2:
            k += 1
            y -= 1
        cols += 1
        thick += k >= cells
    return thick / max(cols, 1)


def measures(c):
    """Sprite measures that separated the liked from the disliked sprites in the user's ratings
    (clouds/feedback/ratings.json)."""
    h, w = c.shape
    solid = c > 0
    n = solid.sum()
    gaps = 0
    for row in solid:
        xs = np.nonzero(row)[0]
        if len(xs):
            gaps += (~row[xs[0]:xs[-1] + 1]).sum()
    pad = np.pad(c, 1)
    differ = sum((pad[1 + dy:h + 1 + dy, 1 + dx:w + 1 + dx] != c).astype(int) for dy, dx in N4)
    return {
        "aspect": w / h,
        "fill": n / (h * w),                        # liked >= 0.62, thin and straggly ones below
        "gaps": gaps / n,                           # sky inside rows: liked <= 0.02, gappy ones 0.03 to 0.16
        "speckle": ((differ == 4) & solid).sum() / n,   # lone cells of another shade: liked <= 0.033
        "navy": (c == 1).sum() / n,                 # liked >= 0.11: a cloud needs its dark underside
        "lavender": (c == 2).sum() / n,             # above 0.25 the folds read as stripes
        "white": (c == 4).sum() / n,
        "seam": seam(c),                            # round 5: all four "do not blend" had (>= 8, >= 0.006)
        "thick_lip": thick_lip(c),                  # round 7: "too much lavender" at 0.66, others <= 0.40
        "slab": slab(c),                            # longest straight top edge of a lavender area, in cells                # above 0.5 the cloud looks flat and washed out
    }


# Width over height allowed per layout (liked heaps 1.06 to 1.87, liked towers 0.8, liked small 1.4 to 1.9,
# the one liked stratus 3.06; the twins at 2.1 to 2.3 and the long stratus at 4.8 to 5.5 were all rejected).
ASPECT = {"heap": (1.0, 1.9), "tower": (0.7, 1.0), "small": (1.3, 1.9), "stratus": (2.4, 3.2)}


def acceptable(c, kind):
    h, w = c.shape
    if side_runs(c) > min(MAX_FLAT_SIDE, max(4, 0.3 * h)):
        return False
    m = measures(c)
    lo, hi = ASPECT[kind]
    flat, flat_share = flat_top(c)
    return (lo <= m["aspect"] <= hi and m["fill"] >= 0.62 and m["gaps"] <= 0.02 and m["speckle"] <= 0.033
            and m["navy"] >= 0.11 and m["lavender"] <= 0.25 and m["white"] <= 0.5 and m["slab"] <= MAX_SLAB
            and m["thick_lip"] <= 0.45
            and not (m["seam"][0] >= 8 and m["seam"][1] >= 0.006)
            and not block_corner(c)
            and white_wall(c) <= MAX_WHITE_WALL
            and navy_cover(c) >= 0.85
            and not (flat >= 10 and flat_share >= 0.5)
            and not (m["fill"] >= 0.72 and flat_share >= 0.4))    # a box: full bounding box and a level top


# ---------------------------------------------------------------------------------------------- cleanup

def cleanup(c):
    ys, xs = np.nonzero(c)
    c = np.pad(c[ys.min():ys.max() + 1, xs.min():xs.max() + 1], 1)
    solid = c > 0
    h, w = c.shape
    # Close short gaps along rows (notches between two lobes' bases) and along columns (slits of sky one or two
    # cells tall cutting sideways into the cloud, which read as a broken line), then fill what is left enclosed.
    for y in range(h):
        xs = np.nonzero(solid[y])[0]
        for a, b in zip(xs, xs[1:]):
            if 1 < b - a <= 3:
                solid[y, a + 1:b] = True
    sandwich = np.zeros_like(solid)
    for x in range(w):
        ys = np.nonzero(solid[:, x])[0]
        for a, b in zip(ys, ys[1:]):
            if 1 < b - a <= 3:
                sandwich[a + 1:b, x] = True
    for y in range(h):          # only long thin slits; short notches between stacked puffs keep the outline
        x = 0
        while x < w:
            if sandwich[y, x]:
                e = x
                while e < w and sandwich[y, e]:
                    e += 1
                if e - x >= 3:
                    solid[y, x:e] = True
                x = e
            else:
                x += 1
    gap = solid & (c == 0)
    while gap.any():
        for y, x in zip(*np.nonzero(gap)):
            nb = [c[y + dy, x + dx] for dy, dx in N4 if 0 <= y + dy < h and 0 <= x + dx < w and c[y + dy, x + dx]]
            if nb:
                c[y, x] = max(set(nb), key=nb.count)
                gap[y, x] = False
    # Fill sky enclosed by the cloud (slits left between lobes) from the most common neighbour shade.
    # Small enclosed pockets only: a large gap is a deliberate opening in the silhouette.
    sky, n = label(~solid, N4)
    border = set(np.unique(np.concatenate([sky[0], sky[-1], sky[:, 0], sky[:, -1]])))
    holes = np.isin(sky, [k for k in range(1, n + 1) if k not in border and (sky == k).sum() <= 12])
    while holes.any():
        for y, x in zip(*np.nonzero(holes)):
            nb = [c[y + dy, x + dx] for dy, dx in N4 if 0 <= y + dy < h and 0 <= x + dx < w and c[y + dy, x + dx]]
            if nb:
                c[y, x] = max(set(nb), key=nb.count)
                holes[y, x] = False
    # Trim cells hanging by a single 4-neighbour that are part of a one-cell-wide spur (rescale artefacts).
    for _ in range(2):
        solid = c > 0
        pad = np.pad(solid, 1).astype(int)
        n4 = pad[:-2, 1:-1] + pad[2:, 1:-1] + pad[1:-1, :-2] + pad[1:-1, 2:]
        n8 = n4 + pad[:-2, :-2] + pad[:-2, 2:] + pad[2:, :-2] + pad[2:, 2:]
        spur = solid & (n4 <= 1) & (n8 <= 1) & (c != 4)
        c[spur] = 0
    # Drips: cells with nothing left, right or below them, hanging off the underside.
    for _ in range(2):
        pad = np.pad(c, 1)
        drip = (c > 0) & (pad[1:-1, :-2] == 0) & (pad[1:-1, 2:] == 0) & (pad[2:, 1:-1] == 0)
        c[drip] = 0
    return smooth_underside(c)


def smooth_underside(c):
    """Navy only belongs on the underside, as one band whose thickness changes gently along the cloud.

    The thickness each column's lobe brought with it is measured (bottom run of navy, one lighter cell allowed
    inside it), then median-filtered over 5 columns, limited to change by one cell per column and capped. The band
    is repainted at that thickness: navy above it becomes lavender, light cells inside it become navy, and
    light blue resting on the band becomes the lavender lip, as on the source clouds."""
    h, w = c.shape
    bottom = np.full(w, -1)
    thick = np.zeros(w, int)
    for x in range(w):
        ys = np.nonzero(c[:, x])[0]
        if not len(ys):
            continue
        bottom[x] = y = ys[-1]
        light = 0
        while y >= 0 and c[y, x] and (c[y, x] == 1 or light == 0):
            light += c[y, x] != 1
            y -= 1
        run = c[y + 1:bottom[x] + 1, x]
        thick[x] = 0 if not (run == 1).any() else bottom[x] - y - (1 if run[0] != 1 else 0)
    t = np.array([np.median(thick[max(0, x - 2):x + 3]) for x in range(w)]).round().astype(int)
    t = np.minimum(t, MAX_NAVY)
    # The band is continuous and runs under the whole cloud: every column keeps at least one cell. (A front lobe
    # without navy of its own left the band stopping halfway.) The thin outer rim becomes lavender further down.
    t[bottom >= 0] = np.maximum(t[bottom >= 0], 1)
    for _ in range(2):          # at most one cell of change between neighbouring columns
        for x in range(1, w):
            t[x] = min(t[x], t[x - 1] + 1)
        for x in range(w - 2, -1, -1):
            t[x] = min(t[x], t[x + 1] + 1)
    for x in range(w):
        col = c[:, x]
        col[col == 1] = 2
        if bottom[x] < 0:
            continue
        # Only fill navy over the solid cells at the column's bottom (not across a sky gap).
        y, k = bottom[x], 0
        while k < min(t[x], bottom[x] + 1) and col[y]:
            col[y] = 1
            y -= 1
            k += 1
        if k and y >= 0 and col[y] == 3:
            col[y] = 2
    # A navy cell with open sky directly above is the rim of the band seen edge-on: lavender.
    pad = np.pad(c, 1)
    c[(c == 1) & (pad[:-2, 1:-1] == 0)] = 2
    # Only the main band stays navy. Bits cut off from it (under a puff hanging at the side) become lavender.
    pieces, n = label(c == 1, N8)
    if n > 1:
        sizes = [(pieces == k).sum() for k in range(1, n + 1)]
        c[(pieces > 0) & (pieces != 1 + int(np.argmax(sizes)))] = 2
    # On the source clouds light blue never touches navy, and white hardly ever does: lavender always sits between.
    # Any lighter cell touching the band (above or beside it) becomes lavender, so lobes blend into the underside.
    pad = np.pad(c, 1)
    touches = ((pad[2:, 1:-1] == 1) | (pad[:-2, 1:-1] == 1) | (pad[1:-1, 2:] == 1) | (pad[1:-1, :-2] == 1))
    c[touches & (c >= 3)] = 2
    # Re-derive lavender from the finished cloud with the stage 2 rules (the sheet has no lavender of its own):
    # light blue or white touching the navy, and light blue deep in a fold with no white within 2 cells. Lavender carried
    # in from the lobes kept their cut lines and showed as square blocks.
    c[c == 2] = 3
    solid = c > 0
    pad = np.pad(c, 1)
    touches = ((pad[2:, 1:-1] == 1) | (pad[1:-1, 2:] == 1) | (pad[1:-1, :-2] == 1))
    c[touches & (c >= 3)] = 2
    white = np.pad(c == 4, 2)
    near_white = np.zeros_like(solid)
    for dy in range(5):
        for dx in range(5):
            if abs(dy - 2) + abs(dx - 2) <= 2:  # a diamond, not a square: square corners made square blocks
                near_white |= white[dy:dy + h, dx:dx + w]
    sp = np.pad(solid, 2)
    enclosed = sp[2:-2, :-4] & sp[2:-2, 4:] & sp[4:, 2:-2]
    c[(c == 3) & ~near_white & enclosed] = 2
    # Lavender is a shadow colour and does not belong on the lit tops. It is judged per lavender patch, not per
    # cell: a patch whose centre lies in the upper part of the cloud becomes light blue as a whole, any other patch
    # stays whole. (Cutting cell by cell at a fixed height left straight-topped, square-cornered blocks.)
    top = np.full(w, -1)
    span = np.ones(w)
    for x in range(w):
        ys = np.nonzero(c[:, x])[0]
        if len(ys):
            top[x], span[x] = ys[0], max(ys[-1] - ys[0], 1)
    patches, n = label(c == 2, N4)
    for k in range(1, n + 1):
        ys, xs = np.nonzero(patches == k)
        if np.mean((ys - top[xs]) / span[xs]) < LAVENDER_FROM:
            c[ys, xs] = 3
    # Last: nothing lighter than lavender may touch the navy, whatever the patch rule decided.
    pad = np.pad(c, 1)
    touches = ((pad[2:, 1:-1] == 1) | (pad[:-2, 1:-1] == 1) | (pad[1:-1, 2:] == 1) | (pad[1:-1, :-2] == 1))
    c[touches & (c >= 3)] = 2
    ys, xs = np.nonzero(c)
    return c[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def main():
    lib = []
    for path in sorted(glob.glob(os.path.join(OUT, "shaded", "cloud_*.png"))):
        src_id = int(os.path.basename(path)[6:8])
        lib += cut_lobes(np.asarray(Image.open(path)).astype(np.uint8), src_id)
    os.makedirs(os.path.join(OUT, "lobes"), exist_ok=True)
    for f in glob.glob(os.path.join(OUT, "lobes", "*.png")) + glob.glob(os.path.join(OUT, "generated", "*.png")):
        os.remove(f)
    for i, L in enumerate(lib):
        save_indexed(L["idx"], os.path.join(OUT, "lobes", f"lobe_{i:03d}.png"))
    print(len(lib), "lobes:", {k: sum(L["kind"] == k for L in lib) for k in ("puff", "wisp", "scatter")})

    rng = np.random.default_rng(SEED)
    kinds = (["heap"] * 17 + ["stratus"] * 4 + ["tower"] * 5 + ["small"] * 9)
    os.makedirs(os.path.join(OUT, "generated"), exist_ok=True)
    sprites = []
    for i in range(COUNT):
        kind = kinds[i % len(kinds)]
        # Rejection sampling: build candidates until one has no flat side (and the right proportions).
        for attempt in range(3000):
            sprite = cleanup(recipe(kind, rng, lib))
            if acceptable(sprite, kind):
                break
        else:
            print(f"cloud_gen_{i:02d}: no candidate passed, keeping the last one")
        save_indexed(sprite, os.path.join(OUT, "generated", f"cloud_gen_{i:02d}.png"))
        sprites.append(sprite)
        print(f"cloud_gen_{i:02d} {kind:8s} {sprite.shape[1]} x {sprite.shape[0]}")
    preview(sprites, os.path.join(OUT, "preview_generated.png"))


def preview(sprites, path, scale=4, cols=7):
    """Contact sheet in the daylight palette over the sheet's sky colour, for looking at, not for the mod."""
    from shade_clouds import PALETTE
    cw = max(s.shape[1] for s in sprites) * scale + 8
    ch = max(s.shape[0] for s in sprites) * scale + 8
    rows = (len(sprites) + cols - 1) // cols
    sheet = np.zeros((ch * rows, cw * cols, 3), np.uint8)
    sheet[:] = (55, 113, 238)
    pal = np.array(PALETTE, np.uint8)
    for k, s in enumerate(sprites):
        big = np.kron(s, np.ones((scale, scale), np.uint8))
        y, x = (k // cols) * ch + 4, (k % cols) * cw + 4
        region = sheet[y:y + big.shape[0], x:x + big.shape[1]]
        np.copyto(region, pal[big], where=(big > 0)[..., None])
    Image.fromarray(sheet).save(path)


if __name__ == "__main__":
    main()
