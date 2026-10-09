# Notes for making a new Minecraft mod in this repository

Lessons from building the Dacian Draco and the Gnoblars. Read this before starting a new mod, together with the root `CLAUDE.md`
(stack, commands, the project guidelines on pacifism, vanilla interaction and vanilla-style models). Each mod keeps its own
`CLAUDE.md` for its own design; this file holds what carries over. Mistakes are recorded with their causes because most of them cost
the user several rounds of feedback.

## 1. Working with this user

- **Look at vanilla first, before inventing a fix.** Twice the user got angry because a new idea was tried where the vanilla
  answer was available (flickering flat parts: the vanilla chicken leg; see section 6). When something looks wrong or breaks, find out
  how vanilla does the same thing (section 4) and copy that. If the user says "look at how vanilla does it", do exactly that and
  nothing else.
- **Do what was asked and show it.** The user judges by looking at the jar in game. After each change: build, run the GameTests,
  commit, push, and hand over the jar with `SendUserFile` (the jar is in `build/libs/`, which is git-ignored). Add preview images
  when the change is visual.
- **Say what is unverified.** Nothing here has ever been run in a real client (no display, and a server needs the EULA, which is the
  user's decision). State this plainly, name the specific things most likely to be wrong, and never call something "fixed" when only
  a headless test passes.
- **Small, plain wording.** The user likes few segments and a minimalist vanilla look ("Minecraft is kind of minimalist on
  segments"). Prefer one box over a clever assembly. When asked to simplify, simplify; do not add a replacement idea.
- **Reverting is allowed and normal.** If a version looked better earlier, restore it and say so. Keep notes of why a design was
  rejected in the mod's `CLAUDE.md` so it is not tried again (the gnoblar nose was a trunk, then a snout, before it was right).
- **Ask only when truly blocked.** Pick the obvious default, say which, and go on. A short ranked list of ideas is welcome when the
  user asks for ideas, and they pick from it. They rejected an idea (affection levels) as overcomplicated: keep systems light.
- **Chat style preferences** (the user's settings): British English, formal register, no first or second person pronouns in chat,
  no triads where avoidable, no semicolons in prose, no intensifiers. Apologise briefly when the user is upset and fix the
  problem; do not argue or lecture.
- **Keep the docs honest and current in the same commit** as the change. Several times a doc edit silently failed because a
  `str.replace` did not match: use an `assert old in text` before replacing, and re-read the doc after.

## 2. Environment and build

- Minecraft 1.20.1, Forge 47.4.26, official mappings, Gradle 8.8 wrapper, Java 17 (the sandbox has 21; Gradle fetches 17).
- **Warm the cache once with an online `./gradlew build`** (about 4 minutes). Later `--offline` runs fail on the runtime classpath
  (`srgutils` missing) unless an online build has run, so do not rely on `--offline`.
- `./gradlew runGameTestServer` takes about a minute and prints "All N required tests passed". Grep the log for
  `required tests`, `failed!` and `error:`; redirect the whole output to a file in the scratchpad.
- A command that runs longer than 2 minutes is moved to the background by the tool: redirect output to a file and read it after.
- Run each mod's commands from inside its own folder (`gnoblars/`), as each has its own Gradle project sharing `~/.gradle`.
- Never commit `build/`, `run/`, `logs/`, `__pycache__`. Commit and push to the session branch, never open a pull request unless asked.
- Commit messages end with the attribution lines given in the session's system reminder.

## 3. Starting a new mod: what to copy

Copy the whole `gnoblars/` folder as a template, then rename:

- `gradle.properties` (`mod_id`, `mod_name`, `mod_group_id`, `mod_version`, `mod_description`), `build.gradle` (the `dumpModel` task's
  `mainClass`), `src/main/resources/META-INF/mods.toml` (uses the properties), `pack.mcmeta`.
- Java package, the `@Mod` class, `ModEntities`, `ModItems`, the `GameTests` class (`@GameTestHolder`), and the preview tool
  `ModelDump` in `src/preview/java` (a source set in `build.gradle`, not part of the jar).
- `data/<modid>/structures/empty.nbt`: the 24x24x24 empty GameTest arena. Copy it, as a flat 3x3x3 arena traps fliers.
- `tools/`: `paint_texture.py`, `render_preview.py`, `check_clipping.py`, `make_item_textures.py`, `make_camp.py` (the last for any
  structure). Change their paths and the `BOXES` table. They are written for a single entity with named boxes, so adapt, do not
  rewrite.
- Spawn: biome modifier JSON in `data/<modid>/forge/biome_modifier/`, plus `SpawnPlacementRegisterEvent` in `ModEntities`.

## 4. Finding out how vanilla does something

The Forge jar with Mojang names is at
`~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.26_mapped_official_1.20.1/forge-1.20.1-47.4.26_mapped_official_1.20.1.jar`.
Extract one class and read it:

```
unzip -o -q $JAR 'net/minecraft/client/model/ChickenModel.class' -d out
javap -p -c -constants out/net/minecraft/client/model/ChickenModel.class | grep -E "ldc|fconst|bipush|invokevirtual|String"
```

Vanilla textures and assets: `~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar` (open with
`zipfile`; the textures are under `assets/minecraft/textures/`). Look at the texture itself, enlarged, not only at the model. Things
learned this way: the chicken's "flat" leg (section 6), and that `ServerEntity` sends the passengers packet in a way that skips
the carrying player (section 8). There are no decompiled sources, so read the bytecode, and test the reading with a GameTest where
possible.

## 5. Seeing a model without Minecraft

Pipeline: `./gradlew dumpModel` bakes the real model in several poses and writes every quad (position, UV, normal) to
`build/preview/model.json`. `paint_texture.py` paints from it, `render_preview.py` software-renders contact sheets
(`body.png`, `head.png`, `variants.png`, `extras.png`, `texture.png`), and `check_clipping.py` tests it. Open the PNGs with the Read tool.

- It shows geometry, UVs, poses and the painted texture faithfully. It does **not** show lighting, z-fighting, the cutout render
  dropping transparent texels exactly as the game does, held items, armour layers, or how a block-item (a banner) sits on the head.
  Everything in that list has gone wrong in game while the preview looked fine.
- Dump a wide range of poses (walk phases, head turned up, down, left, right, scared, sitting, riding, dancing, sleeping) and run the
  checks on all of them. Problems appear at the extremes.
- `ModelDump` calls `applyPose(...)`, so keep it a **pure function** of its numeric inputs and bake the model unrotated (all rotation
  in `applyPose`), so painters read axis-aligned boxes.
- Dump the "rest" geometry with every optional part visible (warts, banners) so the painter sees all boxes, and dump each variant
  as the game shows it for the previews.

## 6. Models

- **Integer cube sizes, one box per limb**, `texOffs` laid out so UV rectangles do not overlap (keep a table of regions as a
  comment; the painter asserts on overlaps only indirectly). A limb is one box swinging from its pivot, never two segments.
- **Rotation signs** (for `ModelPart`): a positive `xRot` tips the top of a part forward (towards -z) and lowers the front of a part
  that extends towards +z; negative raises it. For the left side (+x), a negative `zRot` swings a part outward and a negative `yRot`
  turns a forward-pointing part outward; the right side mirrors.
- **Hunched bodies need care.** Tilting the body about the hips swings its front below the leg tops. If a leg is flush with the body
  (same x plane) the belt then overlaps the leg's side face and the two coplanar, same-facing faces **z-fight and flicker** in game.
  The vanilla chicken has legs flush with its body but never tilts it, and its leg box starts exactly at the hip, so the faces only
  touch along an edge. Fix by insetting: a body 8 wide over legs 1..3. `check_clipping.py` clips every pair of coplanar same-facing
  faces from different parts in several poses and fails on overlap: run it after every change to a cube, pivot or pose.
- **Flat parts (ears, cloth, fins, wings): do it the way vanilla does a chicken's leg.** The chicken leg is an ordinary 3x5x3 box
  (`addBox(-1,0,-3,3,5,3)`) whose texture paints one 1 px column on one face and the toes on the bottom face and leaves **every
  other face transparent**. The cutout render drops transparent texels, so a single flat sheet shows and no two visible faces
  share a place. Do that: a thin box, paint one face. Things that were tried and are wrong:
  - a zero-thickness box (two coincident faces with opposite normals: lit and shaded copies z-fight, flicker in game);
  - the same with a tiny `CubeDeformation` (rejected by the user, not needed);
  - painting all faces of a 1 px box (a visible slab, which the user said looked worse);
  - painting front and back as two sheets 1 px apart (read as four ears).
  A single painted face is seen from behind mirrored, so give it a texture that works both ways (symmetrical two-tone).
- **Noses and snouts.** A box longer than tall reads as a snout; a drooping hook reads as a trunk; a bridge plus knob is too fussy.
  A nose is one box taller than it is deep, hanging slightly below the chin, with nostrils painted on the side faces.
- **Heads, necks, arms.** Put the head on a 1 px neck, start arms lower than the head, splay arms outward in poses where the head
  is down, and clamp head yaw and pitch for hunched bodies (yaw 0.6 x up to 45 degrees, no looking up past level) or the head swings
  into the shoulders. Raised arms go beside the head (z-rotation out), not in front of it.
- **Held items.** The item layer assumes a full-size arm: scale the pose stack (0.65) in `translateToHand`.
- **Optional parts** (warts): one cube per position, shown or hidden per variant, all sharing one texture patch.
- **Banners and other block items on the head** are drawn with `ItemInHandRenderer.renderItem(..., ItemDisplayContext.HEAD, ...)` after
  moving to the head part; the vanilla helmet transform is translate(0,-0.25,0), turn 180, scale(0.625,-0.625,-0.625) for an 8 px
  head. The placement for a smaller head was a guess and has never been seen.
- Mobs look like vanilla mobs: vanilla proportions, muted colours, 16 px per block, no half-pixel boxes (odd-width boxes sit on
  half-pixel offsets, a known small deviation).

## 7. Textures

**The method (`tools/paint_texture.py`):** every texel is coloured from its 3D position on the model, taken from the dumped geometry,
not from its place on the unfolded sheet. That keeps patterns continuous over box edges and makes fur fade into scales or a sash run
round a body. The script overwrites the PNGs, so hand edits are lost: say so, and offer Blockbench for hand-painting.

- **A table of boxes** (`BOXES`: name, x range, y range, z range in rest-pose pixels) tells the painter which part a quad belongs to.
  `part_of` must require the quad to span the box's **whole face** as well as lie on its surface, or a neighbour's face on the same
  plane (a leg's side at the edge of the arm's range) is taken for the wrong part. Give it a tolerance (0.02). When a cube moves,
  update the table; a wrong table paints the wrong part without any error.
- **Palettes:** three shades per material (cool dark, base, warm light), kept muted. Lighter on top faces (x1.08), darker underneath
  (x0.9). Use patterns that follow the material, never random noise:
  - skin: diagonal creases from `(3x + 5y + 7z) % 11` (dark at 0, light at 5), calmer than `% 9`;
  - leather: seams on a grid, `(2x + 3y + 5z) % 7`;
  - cloth: a checker `(x + y + z) % 2`;
  - fur or scales: ordered (Bayer) dither for transitions, staggered 2x2 cells for scales.
  Derive related palettes from one (nose = skin plus (30, 32, 20); hands = skin x 0.72; belt = leather x 0.6; wart = skin plus a warm shift),
  so a new variant needs few numbers.
- **Face details by position:** heavy brow row, eyes set wide with the pupil inner and the iris outer, bags under the eyes, cheek
  creases, tusks at the mouth corners, an ear hole on the side face, hair wisps on top and back. Nostrils on the **side** faces of a
  nose, with a flare crease above. Keep face rows as named constants; the `HEAD_RAISE` shift (rows written for a head 2 px lower)
  was a convenience that made the code confusing, so prefer writing the true coordinates.
- **Outfit details:** a sash from one shoulder (direction per variant, +1, -1, or none with a stitched chest patch), a thin back
  strap, lacing, belt and buckle, a pouch on the side, a rivet, foot and wrist wraps, a scar. Details such as these are what make
  a placeholder read as designed.
- **Coloured layers and overlays:** a **grey mask** of the part that changes (the sash), tinted by the dye colour in a render
  layer (`renderColoredCutoutModel`, exactly as a wolf's collar), and a **mud overlay** drawn from a hash of the block position
  (`(73x + 151y + 257z + 13xz) % 100`) so splotches are the same on every run. Overlay layers re-render the same geometry at the same
  depth: that is fine when the vertices are identical (vanilla does it for the collar).
- **Variants:** one texture per variant from one painter run over a table of palettes (`VARIANTS`). Keep the ids identical in the Java
  enum and the Python table. Make variants differ in several ways (skin colour, sash colour and direction, eye colour, wart place),
  so they can be told apart in a crowd. A lineup sheet (`variants.png`) is the way to judge them.
- **Transparency** is how flat parts are made (section 6); a nick in an ear is just one transparent texel.
- **Item textures:** follow the vanilla template for the kind (a food is based on that food, armour on the vanilla outline, a potion
  on the bottle) but do **not** recolour an unrelated vanilla item that merely shares a word: the nose pickle made from the vanilla sea
  pickle read as a sea cucumber. Draw such an item from an ASCII map in `make_item_textures.py` (one character per pixel, a palette of a
  dark outline and three shades), then view it enlarged (nearest-neighbour, 16x). Check it reads at 16x16: two dark dots read as eyes.
- **Layout:** plan the 64x64 (or 128x128) sheet as a table of rectangles; a box w x h x d occupies a rectangle `2(d+w)` wide and
  `d+h` tall. Flat parts need the same rectangles as thin boxes. Leave a free patch for shared bits (warts).
- **Check by looking:** render the model with the real texture from several angles and enlarged head close-ups before delivering.
  Compare against the vanilla mob's texture side by side when judging vanilla style.

## 8. Entities and game logic

- **Goal ticking:** the goal selector may tick a goal one more time after it ended itself without re-checking `canContinueToUse`.
  Every `tick()` must tolerate a null target. Goals that walk somewhere need a **give-up timer and a cooldown**, or an unreachable
  target traps the mob forever (found with beds and cake).
- **Taming and pets:** `TamableAnimal`; override `canFallInLove` to false when there is no breeding; `getBreedOffspring` returns null.
  `SitWhenOrderedToGoal` keeps a pet seated if its owner cannot be found (offline, or a mock player): vanilla behaviour.
  Pet modes (follow, sit, wander): wander uses `Mob.restrictTo`, which vanilla does not save, so re-apply it on load.
- **Equipment drops:** a mob's equipment only drops on death for a player kill unless the drop chance is above 1: use
  `setGuaranteedDrop(slot)`.
- **Structure entities skip `finalizeSpawn`**, so anything rolled there (a random variant) must also be in the entity NBT.
- **Mud and short blocks:** `getOnPos()` finds the block stood on even when it is shorter than a full block.
- **`isPassenger` mobs:** goals keep running and the mob keeps being ticked; override `rideTick` to position it, stop its
  navigation, reset `fallDistance` and the air supply, and make it invulnerable to wall, drowning and fall damage.
- **Client-only knowledge:** the jukebox event reaches only the client (`setRecordPlayingNearby`), like a parrot dancing. A purely
  visual effect needs no server state.
- **Synched data** for anything the renderer needs (variant, flags, colour); NBT for anything that must persist.
- **Everything peaceful** (project guideline): every drop and every route to an item must have a non-violent source.

### Networking: a player that carries an entity

A mob riding a **player** works on the server but the carrier's own client is never told, so the rider stays frozen where it mounted.
Reason (read in the bytecode): `ServerEntity.sendChanges` sends `ClientboundSetPassengersPacket` through `broadcast`, and
`ChunkMap.TrackedEntity.updatePlayer` skips the entity's own player. Every other player is told. Fix: send
`new ClientboundSetPassengersPacket(player)` to `serverPlayer.connection` yourself whenever the rider mounts or leaves
(override both `startRiding` and `stopRiding` on the rider, so every way of leaving is covered). Also: the rider's own position is not
synced while it is a passenger, so position it in `rideTick`, which runs on both sides. A ridden player's passenger offset would put
the rider inside the player, so place it yourself. A rider behind the player cannot be clicked from the player's view
(`canRiderInteract`), so dismount with a block click or a key, not an entity click. `makeMockServerPlayerInLevel()` crashes in the
sandbox, so none of this can be tested headlessly: say so.

## 9. GameTests

- Arena: copy `empty.nbt` (24x24x24). The floor is dirt at relative y = -1, so entities stand at y = 0.
- Tests run side by side in **one world**: a test that changes the time of day or a game rule affects the others, so restore it
  immediately, and never rely on global state. Randomised goals can make a test flaky: give generous `timeoutTicks`, and run the suite
  three times before trusting a new timing test.
- `helper.spawn` does not call `finalizeSpawn`. `helper.makeMockPlayer()` is not in `level.players()` (so `getOwner()` is null:
  compare owner UUIDs in `isOwnedBy`) and goals that scan `level().players()` cannot be tested. Set `instabuild = false` on it when
  testing item consumption. `helper.spawnItem`, `setBlock`, `absolutePos`, `succeedWhen` and `runAfterDelay` are the useful pieces.
- Test registration data too: biome tags, template pools, structure sets, loot tables, recipes, and ask a structure to generate a
  start (`structure.generate(...)`) to catch a wrong jigsaw set-up.
- A test that places a structure into the arena should count its features (fire, pot, containers with the loot table, beds, residents).

## 10. World generation and structures

- Spawns: a Forge biome modifier JSON (`forge:add_spawns`) and `SpawnPlacementRegisterEvent` (`ON_GROUND` with
  `Mob::checkMobSpawnRules` lets a mob spawn on any solid ground, not just grass).
- **A generated structure in vanilla data format** (all JSON, no Java): `worldgen/structure/<id>.json` (type `minecraft:jigsaw`, one
  `start_pool`, `size` 1, `start_height` absolute -2 so two foundation layers sit in the ground, `project_start_to_heightmap`
  `WORLD_SURFACE_WG`, `terrain_adaptation` `beard_thin`), `worldgen/template_pool/<path>.json` with a `single_pool_element`, a
  `worldgen/structure_set/<id>.json` (`random_spread`: spacing, separation, a unique salt), and a biome tag under
  `tags/worldgen/biome/has_structure/`. The pieces need no jigsaw blocks.
- **Authoring the NBT by script** (`tools/make_camp.py`): a small NBT writer (gzip, big-endian: compound, list, string, int, double,
  float, byte) and a grid of `(x, y, z) -> block`. Palette entries carry string properties. Block entities (chests, barrels)
  carry `{id, LootTable}` and no coordinates. Entities carry `pos`, `blockPos` and `nbt`. Fill the air above the footprint with
  explicit air so trees do not grow through it, give every built cell earth below it, and draw a top and side preview.
- Make a structure out of vanilla blocks in less obvious ways (stepped wool blocks for a tent, a campfire under a water cauldron in a
  stone ring, a bone and pumpkin totem, hay with a carpet for a bed, a fence and lantern post). Avoid stairs unless the facing has
  been checked, as the facing convention is easy to get backwards and cannot be previewed.
- A structure that nobody has seen generate is the biggest unknown: tell the user to look for one in a fresh world.

## 11. Before delivering anything

1. `dumpModel`, `paint_texture.py`, `check_clipping.py`, `render_preview.py`; look at the sheets.
2. `./gradlew build runGameTestServer`; all green, and a new timing test run three times.
3. Update the mod's `CLAUDE.md` (what exists, what was rejected and why, what is unverified) and this file if something general was learned.
4. Commit, push, `SendUserFile` the jar and any preview images.
5. In the reply: what changed, what could not be checked, and what to look at first in the game.
