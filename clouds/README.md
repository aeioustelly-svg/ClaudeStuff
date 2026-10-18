# Pixel-art cloud sprites (groundwork for a cloud mod)

Sprite pipeline only. The mod itself (camera-facing billboard clouds that change colour at dusk, dawn and in
storms) is not started yet.

## Pipeline

Run from the repository root, in order. Only numpy and Pillow are needed.

| Step | Command | Output |
|---|---|---|
| 1. Extract | `python3 -I clouds/tools/extract_clouds.py` | `sprites/extracted/cloud_NN.png`: RGBA, sky alpha 0, 1 art pixel = 1 image pixel |
| 2. Shade | `python3 -I clouds/tools/shade_clouds.py` | `sprites/shaded/cloud_NN.png`: shade indices |
| 3. Generate | `python3 -I clouds/tools/generate_clouds.py [dir] [count] [seed]` | `sprites/generated/cloud_gen_NN.png` (35 by default), `sprites/lobes/`, `sprites/preview_generated.png` |

The source is `reference/cloud_sheet.jpg`. Its pixel grid pitch is detected as about 8.61 source pixels per art
pixel (a non-integer upscale), with the phase refitted per cloud. 13 clouds come out of the sheet.

## Shade indices

Shaded and generated sprites are 8-bit palette PNGs. The pixel value is the shade index. The embedded palette
only makes the files viewable, the mod will colour by index.

| Index | Meaning | Daylight colour |
|---|---|---|
| 0 | transparent | |
| 1 | deep base shadow | `#1F4294` |
| 2 | lavender crevice shadow | `#8A96DE` |
| 3 | light-blue midtone | `#A4C4FA` |
| 4 | white highlight | `#FFFFFF` |

The sheet only has three solid tones. Band 2 is taken from the source's blend pixels between navy and light blue,
from light blue touching the navy underside, and from light blue in deep folds with no white nearby.

## How the sprites are generated

Every pixel is from the sheet, nothing is drawn procedurally.

- Lobes: each white highlight cluster (eroded by one cell, split when large) seeds a lobe. Cloud cells join the
  nearest seed by a walk inside the cloud, so cuts run along the drawn folds. Each lobe records which of its
  sides are the artist's own outline.
- Layouts: heap, stratus, tower, small, trailing wisp and broken cluster. Rows of lobes sit on a baseline, upper
  rows are drawn first and lower rows in front, so seams are covered. Row ends get lobes with a drawn outer edge.
- Each lobe may be flipped and rescaled by nearest neighbour with separate x and y factors (0.8 to 1.25).
- Upper lobes have their navy underside turned to lavender, since they rest on a puff and not on open sky.
- Cleanup fills small enclosed sky pockets, trims one-cell spurs, and turns navy with sky directly above into
  lavender.

## Known issues

- Some seams still show a straight cut or a patch of mixed texture where lobes from different source clouds meet.
- The broken clusters are the weakest layout.
- The sheet's origin and licence are unknown. Check before shipping sprites derived from it.
