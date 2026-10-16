# Pixel Clouds: Forge 1.20.1 mod

Replaces the blocky vanilla clouds with flat pixel-art cloud sprites. Client-only (`clientSideOnly=true`).
Same stack and project guidelines as the Draco mod in the repo root (see `../CLAUDE.md`).

## What the user asked for

- Clouds as 2D particle-like sprites that look like pixel art (reference: white caps, pale blue body, darker
  blue underside, the pixel-art cumulus the user supplied).
- Pink at dawn and dusk, grey in storms. Clouds split and merge as they drift.
- **Never** volumetric anything, 3D cloud geometry or a painted skybox. Players must be able to look down on
  clouds from a mountain and fly through them.
- The user made an earlier cloud mod that failed. They forbid looking at it or taking inspiration from it. Do
  not search for it in other branches or directories.

## How it works

- `client/PixelCloudEffects` replaces the overworld `DimensionSpecialEffects` (registered for
  `minecraft:overworld`) and overrides `renderClouds`. Sky, fog and sunrise glow stay vanilla. The
  `enabled=false` config returns the vanilla clouds.
- `cloud/` is plain Java with no Minecraft imports, so it is unit-tested and previewed headlessly:
  - `CloudShapes` lays out puffs (base row, body, towers, perched accents). Flatness rises with rain.
  - `CloudRaster` shades each puff as a ball lit from the upper left, keeps the front-most puff per texel,
    and quantises to four tones (deep, shadow, body, light). It also fills enclosed holes and removes lone texels.
  - `CloudPalette` maps the four tones to colours for day, dusk, night, storm and lightning. Every palette has
    the same four slots, so a cloud is always a four-colour sprite.
  - `CloudField` is the simulation: wind, coverage from the weather, forming (puffs swell from tiny), evaporating,
    splitting (puffs drift apart until the touching-puff graph has two components) and merging.
- Sprites face the camera like particles: texel `u` runs along the camera's right, `v` down its up vector.
  Splits and merges are laid out along the camera's horizontal right vector at that tick, so from the
  player's view the pieces stay where they were on screen.
- `client/CloudAtlas` is one 2048x1024 dynamic texture with 128x64 slots (so 256 clouds at most). A slot is
  repainted and uploaded only when the cloud's shape, the palette or its fade step changes. Fades are a baked
  4x4 Bayer dither (pixel dissolve, never translucency). Texels are opaque, so it also works with Fabulous.
- `client/CloudRenderer` draws one quad per cloud with the vanilla `position_tex_color` shader. Clouds beyond the
  far plane (short render distance) are pulled closer and shrunk by the same factor, which looks the same on
  screen. Clouds dissolve near the radius edge, while forming, and as the camera flies into them.

## Commands (run inside `pixel_clouds/`)

| Command | Purpose |
|---|---|
| `./gradlew build` | Builds `build/libs/pixel_clouds-1.0.0.jar` and runs the unit tests |
| `./gradlew test` | Unit tests for shapes, rasteriser, palette, split and merge |
| `./gradlew previewClouds` | Writes `build/preview/clouds.png` (day, dusk, night, storm) and `split_merge.png` |

Open the preview PNGs with the Read tool. Do not use `runClient` in the sandbox (no display).

Maven Central often answers 429 here, and Gradle then caches the miss. If resolution fails with
"Could not find ... .jar", retry with `--refresh-dependencies` after a short wait.

## Not verified

- Never loaded in a real Minecraft client. The GL side (atlas upload, quad drawing, projection) is checked
  only by compiling against Forge. The look is checked only through the preview PNGs.
- Clouds are simulated per client and are not synced between players in multiplayer.
- Untested in game: the fly-through fade, far-plane squeezing, Fabulous graphics, interaction with shader
  packs or other sky mods that also replace the overworld effects.

## Config (`config/pixel_clouds-client.toml`)

`enabled`, `altitude` (180), `altitudeSpread` (30), `radius` (640), `pixelSize` (2.0 blocks per cloud pixel),
`coverage` (1.0), `splitAndMerge` (true).
