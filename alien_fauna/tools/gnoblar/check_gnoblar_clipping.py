#!/usr/bin/env python3
"""Reports arms that cut into the head or nose in any dumped pose, and coincident faces (z-fighting).

Quads come out of the dump in the same order in every pose, so the unrotated "rest" pose labels
each quad with its part, and the head's oriented box is rebuilt from its own quads in each pose.

    ./gradlew dumpGnoblar
    python3 -I tools/gnoblar/check_gnoblar_clipping.py [model.json]
"""
import json
import sys

import numpy as np

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import paint_gnoblar as paint_texture  # noqa: E402  (only for its BOXES table and part_of)

MODEL = sys.argv[1] if len(sys.argv) > 1 else "build/preview/gnoblar.json"
SOLID = ("head", "nose")
CHECKED = ("left_arm", "right_arm")


def label(quad):
    verts = [quad[i * 8:(i + 1) * 8] for i in range(4)]
    return paint_texture.part_of(verts)


def inside_box(points, box_points, normals, shrink=0.15):
    """Points strictly inside the oriented box (shrunk a little so touching faces do not count)."""
    hit = np.ones(len(points), bool)
    for n in normals:
        proj = box_points @ n
        p = points @ n
        hit &= (p > proj.min() + shrink) & (p < proj.max() - shrink)
    return hit


def sample(quad):
    v = np.array(quad).reshape(4, 8)[:, :3]
    pts = []
    for s in np.linspace(0, 1, 5):
        for t in np.linspace(0, 1, 5):
            pts.append(v[0] + s * (v[1] - v[0]) + t * (v[3] - v[0]))
    return pts


def _clip(subject, clipper):
    """Sutherland-Hodgman polygon clipping (both counter-clockwise)."""
    def inside(p, a, b):
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) >= 0

    def cross(p, q, a, b):
        x1, y1 = p
        x2, y2 = q
        x3, y3 = a
        x4, y4 = b
        den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if abs(den) < 1e-12:
            return q
        t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
        return (x1 + t * (x2 - x1), y1 + t * (y2 - y1))

    out = subject
    for i in range(len(clipper)):
        a, b = clipper[i], clipper[(i + 1) % len(clipper)]
        inp, out = out, []
        if not inp:
            break
        s = inp[-1]
        for e in inp:
            if inside(e, a, b):
                if not inside(s, a, b):
                    out.append(cross(s, e, a, b))
                out.append(e)
            elif inside(s, a, b):
                out.append(cross(s, e, a, b))
            s = e
    return out


def _area(poly):
    if len(poly) < 3:
        return 0.0
    return abs(sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
                   for i in range(len(poly)))) / 2


def _ccw(poly):
    twice = sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
                for i in range(len(poly)))
    return poly if twice >= 0 else poly[::-1]


def coplanar_overlaps(quads, labels, pose):
    """Two faces of different boxes in the same plane, facing the same way and overlapping, are both visible
    and z-fight (flicker). A vanilla chicken's leg is flush with its body too, but the two only touch along an
    edge. A tilting body that sweeps over a leg's side face makes them overlap, as did a hunched 6 wide body
    over legs 2 px in from a 6 wide body's edge."""
    planes = []
    for q in quads:
        arr = np.array(q).reshape(4, 8)
        v = arr[:, :3]
        n = arr[0, 5:8]
        ln = np.linalg.norm(n)
        planes.append((v, n / ln, (n / ln) @ v[0]) if ln > 1e-6 else None)
    found = 0
    for i in range(len(quads)):
        for j in range(i + 1, len(quads)):
            if planes[i] is None or planes[j] is None or labels[i] == labels[j]:
                continue
            vi, ni, di = planes[i]
            vj, nj, dj = planes[j]
            if ni @ nj < 0.999 or abs(di - dj) > 0.08:
                continue
            a = np.cross(ni, [1, 0, 0])
            if np.linalg.norm(a) < 0.1:
                a = np.cross(ni, [0, 1, 0])
            a /= np.linalg.norm(a)
            b = np.cross(ni, a)
            pa = _ccw([(p @ a, p @ b) for p in vi])
            pb = _ccw([(p @ a, p @ b) for p in vj])
            overlap = _area(_clip(pa, pb))
            if overlap > 0.02:
                found += 1
                print(f"{pose}: {labels[i]} and {labels[j]} share a plane and overlap by {overlap:.2f} px2 (z-fighting)")
    return found


def main():
    data = json.load(open(MODEL))
    labels = [label(q) for q in data["poses"]["rest"]]
    problems = 0
    for pose, quads in data["poses"].items():
        if pose in ("rest", "idle", "walk", "sit", "scared", "sniff", "walk_3", "walk_5", "scared_3", "sniff_3"):
            problems += coplanar_overlaps(quads, labels, pose)
        if pose == "rest":
            continue
        for solid in SOLID:
            box_pts, normals = [], []
            for lab, q in zip(labels, quads):
                if lab == solid:
                    arr = np.array(q).reshape(4, 8)
                    box_pts.extend(arr[:, :3])
                    n = arr[0, 5:8]
                    if not any(abs(abs(n @ m) - 1) < 1e-3 for m in normals):
                        normals.append(n / np.linalg.norm(n))
            box_pts = np.array(box_pts)
            for part in CHECKED:
                pts = []
                for lab, q in zip(labels, quads):
                    if lab == part:
                        pts.extend(sample(q))
                hits = inside_box(np.array(pts), box_pts, normals)
                if hits.any():
                    problems += 1
                    print(f"{pose}: {part} cuts into {solid} ({int(hits.sum())} sample points)")
    print("No clipping found." if problems == 0 else f"{problems} clipping case(s).")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
