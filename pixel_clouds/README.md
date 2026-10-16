# Pixel Clouds

A client-only Forge 1.20.1 mod (Forge 47.4.26, Java 17, mod id `pixel_clouds`, package `com.pixelclouds`) that
replaces the vanilla cube clouds with pixel-art clouds that exist in the world.

- Clouds are camera-facing sprites at fixed world positions, so they can be flown through and looked down on from
  mountains. They are not a skybox.
- Each cloud is a cluster of spheres baked at nine viewing elevations, so a top-down view shows puffy tops and an
  upward view shows the flat base.
- Sprites store normals, not colours. A small core shader lights them with the current sun or moon and a four-shade
  palette that changes with the time of day, rain and thunder.
- Rain brings in a lower, denser overcast deck. Dawn and dusk use different palettes.
- Inside a cloud the fog closes in and takes the cloud's colour.
- If the shader fails to load or rendering throws, the mod logs the error and vanilla clouds are used instead.

The in-game clouds setting must not be Off. Fancy and Fast both use the pixel clouds.

## Build

    cd pixel_clouds
    ./gradlew build        # build/libs/pixel_clouds-1.0.0.jar

## Config

`config/pixel_clouds-client.toml`: `enabled`, `coverage`, `size`, `windSpeed`, `height`, `range`, `cloudFog`.

## Previews without Minecraft

`gen/`, `sky/`, `client/CloudField.java` and `client/Billboard.java` have no Minecraft dependencies. The dev-only
tools in `src/preview/java` run on plain Java and use the same placement, billboard and lighting code as the game
(the lighting is a CPU copy of `pixel_cloud.fsh`, keep them in step):

    S=/tmp/pc; mkdir -p $S/cls
    javac -d $S/cls src/main/java/com/pixelclouds/gen/*.java src/main/java/com/pixelclouds/sky/*.java \
      src/main/java/com/pixelclouds/client/CloudField.java src/main/java/com/pixelclouds/client/Billboard.java \
      src/preview/java/com/pixelclouds/preview/Preview.java src/preview/java/com/pixelclouds/client/SkyPreview.java
    java -cp $S/cls com.pixelclouds.preview.Preview $S/preview   # sprite sheets and lighting sheet
    java -cp $S/cls com.pixelclouds.client.SkyPreview $S/sky     # whole skies: noon, dusk, dawn, peak, rain, night

The shader can be compile-checked by converting `#version 150` to `#version 300 es` and loading it in headless
Chromium with WebGL2.

## Not verified

The mod has never been loaded in a real Minecraft client. Verified: it compiles and reobfuscates, the GLSL compiles
as WebGL2, the Forge hooks exist in the 1.20.1 jar (`DimensionSpecialEffects.renderClouds`, `RegisterShadersEvent`,
`ViewportEvent.RenderFog`). Untested: the in-game look, performance, fog inside clouds, the shader on real drivers,
Embeddium and shader packs (Iris is likely to override the custom shader).

## Ideas not done

Avoiding mountains, pixel moon and sun textures, stars, near-camera particles, higher-resolution sprites for
close clouds.
