# Gnoblars: Forge 1.20.1 mod

Tiny, big-nosed goblinoid scavengers inspired by the gnoblars of Warhammer's Ogre Kingdoms. Mostly harmless and annoying, and
they can be befriended with kindness. This is a separate mod from the Dacian Draco in the repository root. It has its own Gradle
project in this folder, and it shares the Forge cache in `~/.gradle`. Read the root `CLAUDE.md` first: the stack, the project
guidelines (pacifism, vanilla interaction, vanilla-style models) and the sandbox pitfalls all apply here too.

## Stack

- Minecraft 1.20.1, Forge 47.4.26, official mappings, Gradle 8.8 wrapper, Java 17 (the toolchain fetches it).
- Mod id `gnoblars`, package `com.gnoblarmod.gnoblars`. The author field in `gradle.properties` is still a placeholder.
- Run every command from inside `gnoblars/`.

## Commands

| Command | Purpose |
|---|---|
| `./gradlew build` | Builds `build/libs/gnoblars-0.1.0.jar` |
| `./gradlew runGameTestServer` | Runs the GameTests headless. No Minecraft EULA is needed |
| `./gradlew dumpModel` | Bakes the real `GnoblarModel` and writes `build/preview/model.json` |
| `python3 -I tools/paint_texture.py` | Repaints `gnoblar.png` from the dumped geometry |
| `python3 -I tools/render_preview.py` | Software-renders `build/preview/body.png`, `head.png`, `texture.png` |
| `python3 -I tools/check_clipping.py` | Fails if an arm cuts into the head or nose in any dumped pose. Run it after changing a pose or a cube |
| `python3 -I tools/make_item_textures.py` | Rebuilds the item textures from vanilla textures |

Use `--offline` only after a full online build: the runtime classpath needs artifacts that only an online build caches.
Do not use `runServer` or `runClient` in the sandbox (EULA and no display).

## Design (what exists in 0.1.0)

- **Mob:** `GnoblarEntity` is a `TamableAnimal`. 8 health, 0.5 x 0.9 hitbox, vanilla villager sounds pitched up. Never attacks.
- **Wild behaviour (the annoying part):**
  - `GnoblarPesterGoal` follows the nearest player and squeaks, then gets distracted and leaves them alone for 15 to 45 seconds.
  - `GnoblarScavengeGoal` picks up one item from dropped stacks and holds it in its hand.
  - Both can be switched off in `gnoblars-common.toml` (`pestering`, `scavenging`).
- **Cowardice:** `GnoblarAvoidMonstersGoal` runs from every `Monster` and sets the `scared` flag (arms flung up in the model).
- **Gifts and trust:** junk food earns trust (rotten flesh, spider eye, poisonous potato, bone, dried kelp, brown mushroom: 1 each,
  nose pickle: 2). At 4 trust the gnoblar is tamed, like a wolf. Hitting a wild gnoblar resets its trust to 0.
  A gift also makes a holding gnoblar drop what it carries. It drops the item by itself after 5 minutes, and on death
  (`setGuaranteedDrop`: the vanilla drop chance only applies to player kills otherwise).
- **Friends:** follow the owner, empty hand toggles sitting, food heals. `GnoblarSniffGoal` walks to soft ground (dirt, sand,
  gravel, clay), sniffs for 3 seconds and rolls `data/gnoblars/loot_tables/gameplay/gnoblar_sniffing.json`
  (flint, clay, sticks, bone, mushroom, string, leather, a rare gold nugget), then rests for 2 to 4 minutes.
- **Pacifism check:** nothing needs killing. Every gift and every loot entry has a peaceful source (fishing, farming, crafting,
  finding). Death drops nothing except what the gnoblar was carrying.
- **Items:** Nose Pickle (dried kelp + brown mushroom -> 2 pickles, texture recoloured from the vanilla sea pickle) and the spawn egg.
- **Spawns:** swamps, mangrove swamps, taigas and badlands, groups of 2 to 4, on any solid ground (`ModEntities`).

## Model and texture

- `client/GnoblarModel.java`: 64x64 texture, parts baked unrotated, all rotation in `applyPose(...)`, a pure function of its
  inputs that `ModelDump` calls. Poses: `idle`, `walk`, `scared`, `sit`, `sniff`.
- The nose is one 4x6x3 block, taller than deep and hanging 1 px below the chin, with a nostril painted on each side face. Keep it a
  single box that is not longer than it is tall: a 4x4x5 block read as a snout, a drooping hook read as a trunk, and a bridge plus
  knob was too fussy for a minimalist vanilla style. About one gnoblar in five has a wart (`hasWart`, set in
  `finalizeSpawn`, saved as `Wart`): a 1x1x1 cube on the nose that is only visible for those. The ears are
  three stepped zero-thickness planes in the XY plane (they face forward, the forward side is painted pink and the back side is skin),
  climbing to a point with one nick bitten out. The head sits on a 1 px neck box. The arms are single boxes with their top level with
  the neck, and the rag loincloth is a plane. Held items render through `ItemInHandLayer` and the model's `translateToHand`
  (shrunk to 0.65 for a small hand).
- Head turn and pitch are clamped in `applyPose` (yaw x0.6 up to 45 degrees, no looking up past level) because a hunched creature's
  head otherwise swings into its shoulders.
- `paint_texture.py` overwrites `gnoblar.png` and colours texels by 3D position, with an explicit box table (`BOXES`) that has to
  match the model code. When a cube in `GnoblarModel` moves, update that table.
- Ear rotation: for the left ear a negative `yRot` sweeps it back and a positive `zRot` droops it. The right ear mirrors that.
  `HEAD_RAISE` in `paint_texture.py` shifts the head-part rows, so the face rows are written for a head 2 px lower than it sits.

## Tests

`gametest/GnoblarGameTests.java`, template `data/gnoblars/structures/empty.nbt` (24x24x24, the same as the Draco). Same rules as the
Draco: mock players are not in `level.players()`, so `isOwnedBy` compares UUIDs, and a goal that ends itself must tolerate one
more `tick()`. Goals that scan `level().players()` (pester) cannot be tested headlessly.

## Not verified

- The mod has never been loaded in a real client. Previews and GameTests are the only checks.
- Untested: the pester feel and distance, held-item placement in the hand, sound balance, spawn frequency.

## Ideas not done

Camps (structures with a scrap heap, chest, cooking pot and bone totem), more variants (Trapper with a bell, Sneak with a sack,
Chieftain), shoulder perch, bell alarm, hats and armour on friends, a pack slot, a wandering gnoblar trader, a gnoblar horn,
scrap armour, an Ogre mob, a custom squeak sound.
