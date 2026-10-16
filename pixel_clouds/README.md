# Pixel Clouds

Client-only Forge 1.20.1 mod. Replaces vanilla clouds with flat pixel-art cloud sprites that exist in the world:
fly through them, look down on them from a mountain.

- Sprite: `assets/pixel_clouds/textures/cloud/rappenem_a.png`, taken from Rappenem's pixel-art cloud tutorial.
  Personal use only. More sprites: add PNGs there and list them in `assets/pixel_clouds/clouds.txt`
  (texel red = band 50/100/150/200, alpha 255 = solid). A resource pack can override both.
- Three drifting layers (different cell size, speed and altitude) pass through each other, so clouds overlap,
  merge and separate. Rain adds a lower overcast layer.
- Colours follow time of day and weather (`sky/CloudPalette`). Fog thickens inside a cloud.
- If the shader fails to load, vanilla clouds are used. Config: `config/pixel_clouds-client.toml`.

Build: `./gradlew build` (jar in `build/libs/`). The mod has never been loaded in a real client.
