#!/usr/bin/env python3
"""Reports arms that cut into the head or nose in any dumped pose, and coincident faces (z-fighting).

Quads come out of the dump in the same order in every pose, so the unrotated "rest" pose labels
each quad with its part, and the head's oriented box is rebuilt from its own quads in each pose.

    ./gradlew dumpModel
    python3 -I tools/check_clipping.py [model.json]
"""
import json
import sys

import numpy as np

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import paint_texture  # noqa: E402  (only for its BOXES table and part_of)

MODEL = sys.argv[1] if len(sys.argv) > 1 else "build/preview/model.json"
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


def coincident_faces(quads):
    """Quads that occupy the same place (a zero-thickness box makes two) flicker in game from z-fighting."""
    seen = {}
    found = 0
    for i, q in enumerate(quads):
        verts = np.array(q).reshape(4, 8)[:, :3]
        if np.ptp(verts, axis=0).min() > 0.01 and np.ptp(verts, axis=0).max() > 0.01:
            key = tuple(sorted(tuple(np.round(v, 2)) for v in verts))
        else:
            key = tuple(sorted(tuple(np.round(v, 2)) for v in verts))
        if key in seen:
            found += 1
            print(f"coincident faces: quads {seen[key]} and {i} at {verts.mean(axis=0).round(1).tolist()}")
        else:
            seen[key] = i
    return found


def main():
    data = json.load(open(MODEL))
    labels = [label(q) for q in data["poses"]["rest"]]
    problems = coincident_faces(data["poses"]["rest"])
    for pose, quads in data["poses"].items():
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
