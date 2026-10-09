# Dacian Draco: Forge 1.20.1 mod

A wolf-headed flying serpent inspired by the Dacian Draco battle standard. Read this before changing anything.

## Stack

- Minecraft 1.20.1, Forge 47.4.26, official (Mojang) mappings, Gradle 8.8 wrapper.
- Java 17 is required. The sandbox has Java 21, but the Gradle toolchain fetches a 17 by itself.
- Mod id `dacian_draco`, package `com.dacianmod.draco`. The author field in `gradle.properties` is still a placeholder.
- Never commit `build/`, `run/`, `logs/` or `__pycache__` (all are in `.gitignore`).

## Commands

| Command | Purpose |
|---|---|
| `./gradlew build` | Builds `build/libs/dacian_draco-1.0.0.jar` (ignore any stale `examplemod` jar) |
| `./gradlew runGameTestServer` | Runs the GameTests headless. No Minecraft EULA is needed. All 16 should pass |
| `./gradlew dumpModel` | Bakes the real `DracoModel` and writes `build/preview/model.json` |
| `python3 -I tools/paint_texture.py` | Repaints `draco.png` from the dumped geometry |
| `python3 -I tools/render_preview.py` | Software-renders `build/preview/body.png`, `head.png`, `texture.png` |

Do not use `runServer` or `runClient` in the sandbox. A server needs the Minecraft EULA accepted, which is the user's decision, and there is no display.

## Seeing the model without Minecraft

`dumpModel` -> `paint_texture.py` -> `render_preview.py`, then open the PNGs with the Read tool. This renders the actual baked model and texture, so it shows geometry, UVs and poses faithfully. It is not the game's renderer, so lighting differs. Poses come from `ModelDump.java` (`idle`, `fly`, `howl`, `dive`, `lash`, `perch`). Add a pose there to preview it.

`paint_texture.py` overwrites `draco.png`. Hand-painted edits to that file are lost the next time it runs. It colours each texel by its 3D position on the model, which is what makes fur fade into scales across separate boxes.

## Layout

- `entity/DracoEntity.java`: a `TamableAnimal` and `FlyingAnimal`. Taming, sitting, shedding, polenta trade, animation state.
- `entity/goal/`: `DracoDiveGoal`, `DracoHowlGoal`, `DracoTailLashGoal`, `DracoStalkGoal` (circles the target at range so dive and howl can trigger), `DracoPerchGoal` (sitting for a flier), `FollowCapWearerGoal` (wild only).
- `client/DracoModel.java`: vanilla-style segmented model. `applyPose(...)` is a pure function of its inputs, which is what the preview tool calls.
- `src/preview/java`: dev-only tool source set, not part of the mod jar.
- `gametest/DracoGameTests.java`: the GameTests. The empty test structure is `data/dacian_draco/structures/empty.nbt` (24x24x24).
- `data/.../forge/biome_modifier/draco_spawns.json`: natural spawns in windswept hills, peaks and meadows.

## Design decisions (from the user)

- Vanilla-style models, no GeckoLib. The snakes in Alex's Mobs are the reference for the technique (a chain of child segments with a phase-shifted wave). Its code is not copied: it has no licence file and depends on Citadel.
- Minecraft mobs do not have separate jaws. The head is a skull, a snout and two ears, with eyes, nose and teeth painted on. The howl throws the head back instead of opening a jaw.
- No kill-for-drops. The Draco sheds a Draco Shed Skin every 5 to 10 minutes. Death drops nothing.
- Taming works like a wolf: bones, 1 in 3 chance, owner follows and defends, empty hand toggles sitting. Only a tamed Draco gives mamaliga, and only to its owner (bowl, 30 percent, 60 percent with the cap, 1 minute cooldown).
- Simple textures. Prefer modifying vanilla textures where sensible. The `mamaliga` texture is the vanilla bowl with the same pixels recoloured that vanilla recolours for mushroom stew. Vanilla assets were read from `~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar`.
- Wild Draco is neutral and only fights whoever hurts it. No wind gust attack.

## Pitfalls already hit

- **Goal ticking:** the goal selector ticks running goals on odd ticks without re-checking `canContinueToUse`. A goal that ends itself must tolerate one more `tick()`. The dive goal crashed the server this way.
- **Flying and gravity:** `FlyingMoveControl(..., hoversInPlace = true)` leaves gravity off. `DracoPerchGoal` switches gravity on while sitting and off again when released. Fall damage is disabled.
- **Mock players in GameTests:** `helper.makeMockPlayer()` returns a player that is not in `level.players()`, so `getOwner()` is null. `DracoEntity.isOwnedBy` compares UUIDs for that reason. `makeMockServerPlayerInLevel()` crashes in this environment (no network channel). Goals that iterate `level().players()` cannot be tested headlessly.
- **GameTest arena:** a 3x3x3 template is walled in and a flier cannot climb, so the template is 24x24x24. Spawn and target positions in the tests are around (12, 2, 12).
- **Model cubes:** use integer cube dimensions so UV rectangles stay aligned. Rear segments extend towards +Z, the head towards -Z.
- **Preview of `ModelPart` rotation:** a negative `xRot` raises the front of a segment. A positive one lowers it.

## Network and environment

- Outbound hosts are restricted by the cloud environment. Forge needs `maven.minecraftforge.net`, `files.minecraftforge.net`, Mojang's `piston-meta`, `piston-data`, `libraries` and `resources` hosts, plus Maven Central and the Gradle plugin portal. Maven Central sometimes answers 429. Retry the build and the cache fills up.
- The user changes network access in the environment settings. They are not experienced with that UI, so name the exact host and give short directions.

## Not verified

- The mod has never been loaded in a real Minecraft client. Render previews and GameTests are the only checks.
- Untested: owner follow and teleport, flying behaviour in a real world, sound balance, the cap's follow behaviour, natural spawn frequency.
- The felt cap uses the flat helmet layer, so it has no cone shape.
- Sounds are vanilla wolf and phantom sounds, pitched down.

## Ideas not done

Custom howl sound, a proper Dacian cap model, a collar or naming for tamed Draco, mounting.
