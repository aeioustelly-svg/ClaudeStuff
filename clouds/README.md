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
| 5 | mist, one layer (generated sprites only), opacity 40/255 | `#B0CCFA` |
| 6 | mist, two layers overlapping, opacity 74/255 (two layers of 5 stacked) | `#B0CCFA` |

The sheet only has three solid tones. Band 2 is taken from the source's blend pixels between navy and light blue,
from light blue touching the navy underside, and from light blue in deep folds with no white nearby.

## How the sprites are generated

Every pixel is from the sheet, nothing is drawn procedurally.

- Lobes: each white highlight cluster (eroded by one cell, split when large) seeds a lobe. Cloud cells join the
  nearest seed by a walk inside the cloud, so cuts run along the drawn folds. Each lobe records which of its
  sides are the artist's own outline.
- Lobe pool: only round puffs (solid enough, with a filled top third). Lobes from the long thin cloud, the wisps,
  the scattered cloud and the crescent horns are not used, as they gave ragged, smeared sprites.
- Layouts: heap (17), low stratus (4), tower (5) and small (9). Twin heaps were dropped after all six were rated
  down. Rows of lobes sit on a baseline,
  upper rows are drawn first and lower rows in front, so seams are covered. Lobes at the ends of a row are
  smaller, so the sides step down in puffs. Row ends get lobes with a drawn outer edge.
- Each lobe may be flipped and rescaled by nearest neighbour with separate x and y factors (0.8 to 1.25).
- The navy underside is repainted as one band. Each column's navy thickness from its lobe is measured, then
  median-filtered over 5 columns, allowed to change by at most one cell per column and capped at 4. The band is
  continuous and runs under the whole cloud (at least one cell in every column), and navy cut off from it
  becomes lavender. Navy above
  the band becomes lavender, light cells inside it become navy, and light blue resting on it becomes the lavender
  lip. Upper lobes lose their navy for the same reason.
- Lavender is re-derived on the finished sprite with the stage 2 rules (the sheet has no lavender of its own):
  light blue or white touching the navy, and light blue deep in a fold with no white within 2 cells (measured
  in a diamond, as a square neighbourhood gave the folds square corners). Lavender
  carried in from the lobes kept their cut lines and showed as square blocks.
- A lavender patch whose centre lies in the upper 45 percent of the cloud becomes light blue as a whole, so the
  lit tops carry no shadow colour. Patches are judged whole: cutting cell by cell at a fixed height left
  straight-topped blocks.
- Last, nothing lighter than lavender may touch the navy. On the source clouds light blue never does and white
  hardly ever does.
- Cleanup closes slits of sky 1 or 2 cells tall and at least 3 long (they read as a broken line), closes row gaps
  of up to 2 cells, fills small enclosed sky pockets, trims one-cell spurs and drips under the base.
- Rejection sampling, with limits taken from the user's ratings in `feedback/ratings.json`: a candidate must have
  sky gaps inside rows of at most 2 percent, a navy share of at least 11 percent, at least 62 percent fill of its
  bounding box, at most 25 percent lavender with no straight-topped lavender slab longer than 16 cells, no straight vertical white edge longer than 5 cells (the sheet's maximum), navy under at least 85 percent of
  the columns (the sheet's puffy clouds: 89 to 100 percent), no all-lavender 4 x 3 block with a straight lighter top and side, no top level (within one row) for half the
  width and 10 cells, no box shape (fill at least 72 percent with a top level for 40 percent of the width),
  at most 45 percent of underside columns with a lavender
  band 4 or more cells thick (the sheet's clouds reach 38 percent), no
  combination of a straight vertical shade boundary of 8 or more cells with a 0.6 percent share of shade jumps
  of two bands or more (the four "do not blend" sprites of round 5), at most 50 percent white, little speckle, and a width/height ratio
  suited to its layout (heap 1.0 to 1.9, tower 0.7 to 1.0, small 1.3 to 1.9, stratus 2.4 to 3.2). These limits
  reject every sprite rated down in round 2 and keep 12 of the 14 liked ones.
- Earlier rejection rule, still applied: a candidate is rebuilt when either side stays within one column for more than 7 rows (or
  30 percent of its height), or when a small, stratus or twin cloud is not at least 1.4 times wider than tall.

## Layers and manifest

Each generated cloud is written as separate layers so that in game the broken-off pieces can float on their own:

- `generated/cloud_gen_NN.png`: the cloud with its mist.
- `generated/cloud_gen_NN_bitK.png`: each broken-off piece with its own thin mist.
- `generated/clouds.json`: for every cloud its file, layout kind, size and its pieces, each with `x` and `y` in
  pixels relative to the cloud layer's top-left corner (where the piece starts out; the mod may let it drift).

## Mist

Two independent translucent layers sit behind each cloud, as in the reference: shade 5 where one layer covers,
shade 6 where both overlap. Each layer is made of lumps (outlines of the sheet's puff lobes, rescaled to 3 to 8
cells tall and widened up to 1.6 times) scattered along the cloud's edge, one per 10 edge cells, and pushed a
little outwards. Mist reaches at most 3 cells beyond the cloud, and stretches of edge without a lump stay clear,
so it does not repeat the cloud's outline. Pieces get the same two layers, reaching 1 cell. Mist and pieces use
their own random streams, so the clouds are the same with or without them.

## Broken-off bits

After a sprite passes the checks, about half get one to three small loose pieces, as some of the sheet's clouds
have. The pieces are the sheet's own loose components of 2 to 15 cells (navy turned lavender, as a loose piece
has no underside band), flipped at random but never rescaled. They sit beside the upper two thirds of a side or
above a shoulder, 1 to 3 cells away, touching nothing. They use their own random stream, so the clouds
themselves are the same with or without them.

## Known issues

- Some seams still show a patch of mixed texture where lobes from different source clouds meet.
- The quality limits make the set more uniform than before.
- Some small and stratus sprites flare out at the base into thin navy points. The ratings so far do not show
  whether that is liked or disliked.
- The sheet's origin and licence are unknown. Check before shipping sprites derived from it.
