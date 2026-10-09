# Dacian Draco: Forge 1.20.1 mod

A wolf-headed flying serpent inspired by the Dacian Draco battle standard. Read this before changing anything.

**Other mods in this repository:** `gnoblars/` is a separate mod (own Gradle project, own `CLAUDE.md`). Run its commands from inside that folder. Nothing in this project depends on it.

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
| `python3 -I tools/make_item_textures.py` | Rebuilds the item and armour textures from vanilla textures (reads the vanilla client jar) |

Do not use `runServer` or `runClient` in the sandbox. A server needs the Minecraft EULA accepted, which is the user's decision, and there is no display.

## Seeing the model without Minecraft

`dumpModel` -> `paint_texture.py` -> `render_preview.py`, then open the PNGs with the Read tool. This renders the actual baked model and texture, so it shows geometry, UVs and poses faithfully. It is not the game's renderer, so lighting differs. Poses come from `ModelDump.java` (`idle`, `fly`, `howl`, `dive`, `lash`, `perch`). Add a pose there to preview it.

`paint_texture.py` overwrites `draco.png`. Hand-painted edits to that file are lost the next time it runs. It colours each texel by its 3D position on the model, which is what makes fur fade into scales across separate boxes.

## Project guidelines (the user's `minecraft-forge-mods` skill)

These come from the user's own skill and apply to every mod in this repo. Default stack: Minecraft 1.20.1, Forge 47.x, Java 17.

1. **Pacifism.** Violence is never required to do or get anything. Every item, drop and mechanic has a peaceful way to get it
   (tame, feed, trade, gift, craft, find in a chest). Hostile mobs may exist, but can always be calmed, avoided or befriended, and
   anything they drop on death is also obtainable another way.
2. **Interact with vanilla.** Vanilla items as inputs (food to tame, materials to craft), vanilla blocks in buildings, vanilla
   mechanics (name tags, leads, breeding, sitting, note blocks, biomes).
3. **Mobs look like vanilla mobs.** Vanilla proportions, blocky parts, 16 px per block, vanilla-style shading. Pitfalls to avoid:
   - Flat textures (one colour per face) or random speckle noise. Use a few shades per material (cooler shadows, warmer
     highlights), lighter tops, and a pattern that follows the material (fur, scales, feathers, wool).
   - Jointed limbs. A leg or arm is one box swinging from the hip or shoulder, not upper and lower segments.
   - Mismatched pixel size. 1 texture pixel = 1/16 block. Build at the size it should appear (no shrinking in the renderer) and
     no half-pixel boxes.
   - Over-saturated colours. Vanilla palettes are fairly muted.
   - Underusing flat and see-through parts (zero-thickness planes, transparent pixels) for claws, teeth, ears, fins, feathers,
     membranes.
4. **Buildings need depth and variety, not boxes.** Insets, protruding beams, real roof shapes with overhang, framed windows and
   doors, mixed related materials, partial blocks used in less obvious ways (upside-down stairs as eaves, top slabs, trapdoors as
   shutters, fences and walls as railings, lying logs as beams), and a lived-in interior.
5. **Follow the vanilla template for things of the same kind.** A new potion uses the vanilla bottle with only the liquid
   recoloured, a new door has vanilla door shapes, a food variant is based on that food's texture, new tools and armour follow
   vanilla outlines with new materials painted in. Vanilla textures are in
   `~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar`.

The skill also ships example files (five Alex's Mobs models and textures under the LGPL v3, and five buildings from the Structures
mod marked "personal reference only"). They are not copied into this repo because of those licences. Ask the user to upload
`minecraft-forge-mods.skill` again if the references are needed, and extract it into its own empty directory first.

### How the Draco measures up

- Pacifism: met. Nothing needs killing: shed skin from living Draco, tamed with bones, mamaliga from a tamed Draco, cap crafted
  from shed skin and wool.
- Vanilla interaction: bones, bowls, wool, meat healing, sitting.
- Textures: `paint_texture.py` uses three muted shades per material, staggered 2x2 scales lit from the top left, fur strokes, an
  ordered-dither fur-to-scale transition and lighter tops. Do not reintroduce random noise.
- Items: the felt cap and its worn layer come from the vanilla leather cap and layer, the shed skin from the phantom membrane and
  the mamaliga from the vanilla bowl, all through `make_item_textures.py`.
- Known deviations: the body is a chain of segments (no vanilla equivalent, chosen on purpose for a serpent). Odd-width boxes sit
  on half-pixel offsets, although every box dimension is a whole pixel. The 128x128 entity texture is mostly empty.

## Layout

- `entity/DracoEntity.java`: a `TamableAnimal` and `FlyingAnimal`. Taming, sitting, shedding, polenta trade, animation state.
- `entity/goal/`: `DracoDiveGoal`, `DracoHowlGoal`, `DracoTailLashGoal`, `DracoStalkGoal` (circles the target at range so dive and howl can trigger), `DracoPerchGoal` (sitting for a flier), `FollowCapWearerGoal` (wild only).
- `client/DracoModel.java`: vanilla-style segmented model. `applyPose(...)` is a pure function of its inputs, which is what the preview tool calls.
- `src/preview/java`: dev-only tool source set, not part of the mod jar.
- `gametest/DracoGameTests.java`: the GameTests. The empty test structure is `data/dacian_draco/structures/empty.nbt` (24x24x24).
- `data/.../forge/biome_modifier/draco_spawns.json`: natural spawns in windswept hills, peaks and meadows.

## Design decisions (from the user)

- Vanilla-style models, no GeckoLib. The snakes in Alex's Mobs (LGPL v3) are the reference for the technique (a chain of child segments with a phase-shifted wave). Its code uses the Citadel library, so it is not copied: the technique is reimplemented with vanilla `ModelPart`s.
- Minecraft mobs do not have separate jaws. The head is a skull, a snout and two ears, with eyes, nose and teeth painted on. The howl throws the head back instead of opening a jaw.
- No kill-for-drops. The Draco sheds a Draco Shed Skin every 5 to 10 minutes. Death drops nothing.
- Taming works like a wolf: bones, 1 in 3 chance, owner follows and defends, empty hand toggles sitting. Only a tamed Draco gives mamaliga, and only to its owner (bowl, 30 percent, 60 percent with the cap, 1 minute cooldown).
- Simple textures. Prefer modifying vanilla textures where sensible (see the guidelines above). The `mamaliga` texture is the vanilla bowl with the same pixels recoloured that vanilla recolours for mushroom stew.
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
- The felt cap uses the vanilla helmet layer, so it has no cone shape.
- Sounds are vanilla wolf and phantom sounds, pitched down.

## Ideas not done

Custom howl sound, a proper Dacian cap model, a collar or naming for tamed Draco, mounting.
