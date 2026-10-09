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
| `python3 -I tools/paint_texture.py` | Repaints every variant texture, the dye masks and the mud overlay from the dumped geometry |
| `python3 -I tools/render_preview.py` | Software-renders `build/preview/body.png` (every pose), `head.png`, `variants.png`, `extras.png` (mud and dyed sashes), `texture.png` |
| `python3 -I tools/check_clipping.py` | Fails if an arm cuts into the head or nose in any dumped pose, or if two coplanar faces overlap (z-fighting). Run it after changing a pose or a cube |
| `python3 -I tools/make_item_textures.py` | Redraws the item textures (the nose pickle, from an ASCII map) |
| `python3 -I tools/make_camp.py` | Rebuilds `data/gnoblars/structures/camp.nbt` block by block and draws `build/preview/camp.png` (top and side) |

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
- **Friends:** follow the owner, food heals, and an empty-hand click cycles the mode (`GnoblarMode`): follow, then sit, then wander,
  with an action-bar message for each (lang `message.gnoblars.mode.*`). Wander uses `Mob.restrictTo` around the spot where it was told
  (radius `WANDER_RADIUS` 10, not saved by vanilla, so `readAdditionalSaveData` sets it again), sit uses vanilla's ordered-to-sit, and
  `GnoblarFollowOwnerGoal` only runs in follow mode. A hurt sitting friend gets up and follows. `GnoblarSniffGoal` walks to soft
  ground (dirt, sand, gravel, clay), sniffs for 3 seconds and rolls `data/gnoblars/loot_tables/gameplay/gnoblar_sniffing.json`
  (flint, clay, sticks, bone, mushroom, string, leather, a rare gold nugget), then rests for 2 to 4 minutes. It works in follow and
  wander mode, not while sitting or riding.
- **Riding:** sneak and click your own gnoblar to carry it piggyback (too big for a shoulder). It becomes a passenger of the player
  (`startRiding`), `rideTick` places it 0.4 behind and 0.9 above them in their body direction (the player's own passenger offset
  would put it inside them), and the model shows the `ride` pose (`entity.isPassenger()`). Sneak and use a block with an empty hand to
  put it down (`GnoblarEvents`, `putDownPassengers`). A player can carry one. **The carrier's own client is never told about a passenger by
  vanilla** (`ServerEntity` uses `broadcast`, which skips the entity's own player), so riding first looked broken: the gnoblar stayed
  frozen where it mounted. `GnoblarEntity.startRiding` and `stopRiding` therefore send `ClientboundSetPassengersPacket` to the carrier by
  hand. A rider is also invulnerable to wall, drowning and fall damage, with `fallDistance` and the air supply reset each tick, because
  it is carried through walls and water. None of this can be tested headlessly (no real `ServerPlayer`), so the fix is unseen in a client. The mounted gnoblar cannot be clicked from the player's
  view (it is behind them), which is why putting it down is a block click and not an entity click.
- **Pacifism check:** nothing needs killing. Every gift and every loot entry has a peaceful source (fishing, farming, crafting,
  finding). Death drops nothing except what the gnoblar was carrying.
- **Items:** Nose Pickle (dried kelp + brown mushroom -> 2 pickles; its own drawing, a gherkin curved like a nose with a pair of nostrils, because the recoloured vanilla sea pickle read as a sea cucumber) and the spawn egg.
- **Spawns:** swamps, mangrove swamps, taigas and badlands, groups of 2 to 4, on any solid ground (`ModEntities`).

## The camp

A generated structure, `data/gnoblars/structures/camp.nbt` (15 x 8 x 15), built by `tools/make_camp.py` (edit the script and rerun it;
the NBT is not meant to be edited by hand). A trodden clearing with a cooking pot (a water cauldron on a campfire, in a ring of
cobblestone), two hide tents (brown wool and grey wool A-frames: a bedroom with a loot chest, a hay bed and a barrel, and a store of
barrels with a composter), a scrap heap with a cauldron and iron bars, a totem of bone blocks with a carved pumpkin face, a drying
rack, log seats, lanterns on posts and five gnoblars of different variants (the variant is in the entity NBT, because structure
entities skip `finalizeSpawn`). Layer 0 is foundation and layer 1 the ground, and a built cell with no earth below it gets a foundation.
Loot, `data/gnoblars/loot_tables/chests/camp.json`, is junk and small goods (rotten flesh, bones, kelp, mushrooms, sticks, string,
nuggets, a chance of a lead, name tag, bell, emerald or nose pickles): everything peaceful.

Worldgen is data driven, vanilla format: `worldgen/structure/gnoblar_camp.json` (a jigsaw structure with one single-piece pool,
`start_height` -2 so the two foundation layers sit in the ground, projected on `WORLD_SURFACE_WG`, `beard_thin` terrain adaptation),
`worldgen/template_pool/camp/start.json`, `worldgen/structure_set/gnoblar_camps.json` (random spread, spacing 20, separation 8) and the
biome tag `tags/worldgen/biome/has_structure/gnoblar_camp.json` (swamps, taigas, badlands, as for the spawns). GameTests place the
template, count its features and residents, check the loot table, check the registrations and the biome tag, and ask the structure to
generate a start. Nothing has generated a camp in a real world yet, so the look and the fit to terrain are unseen.

## Kindness

Ways to be kind to a gnoblar, all peaceful and all using vanilla items or blocks. (Affection levels were considered and rejected by the
user as overcomplicated: there is no hidden friendship score, only trust for the wild and what is below.)

| Kindness | How | Effect |
|---|---|---|
| Brushing | right-click with a brush (anyone) | hearts, +1 trust for a wild gnoblar, +1 health for a friend, wears the brush, then a 10 second rest |
| Washing | right-click a muddy gnoblar with a water bottle (anyone) | the mud is gone, the empty bottle comes back, same comfort as brushing, no rest needed |
| Dyeing | owner right-clicks with a dye | recolours the sash (or the wrap on the one variant without a sash), a different dye recolours it again |
| Banner hat | owner right-clicks with a banner | it wears it on its head (a second banner swaps and hands the first back), shears take it off, death drops it |
| Beds | a hay block with any carpet on top | at night a wild or wandering gnoblar within 10 blocks climbs on and curls up until morning (`GnoblarSleepGoal`); followers and sitters do not |
| Dancing | a jukebox playing within 3.46 blocks | the dance pose, client only, like a parrot (`setRecordPlayingNearby`) |
| Cake parties | a cake block within 8 blocks | a gnoblar walks over and takes a bite (`GnoblarCakeGoal`), then everyone within 8 blocks of the cake cheers and dances for 10 seconds (`GnoblarPartyGoal`); a wild one gains trust up to one point short of taming, a friend heals |

Mud: a gnoblar gets muddy by standing on mud or mud roots (checked with `getOnPos`, as mud is shorter than a block) and by digging when
it sniffs, and loses it in water or rain. It is shown by a mud overlay layer. The dyed sash is a grey mask per variant tinted by the dye
in `GnoblarSashLayer`, like a wolf's collar. Banners are drawn by `GnoblarBannerLayer` with the vanilla helmet transform scaled to
this head (scale 0.5, 3/16 lower): that placement has never been seen, so check it first in a client. Muddy and sash colour are saved
(`Muddy`, `SashColor`); the banner is the head equipment slot, kept by vanilla and set to drop on death.

Everything that walks to a bed or a cake gives up after 300 or 200 ticks (and waits before trying again), so an unreachable one never
traps a gnoblar. A hand-placed hay block with a carpet is not checked for headroom: a gnoblar on the carpet is under 2 blocks above
the hay, so a tent ridge over it leaves room. The camp has two beds. Several GameTests share one world, so one that changes the time
of day (the night test) restores it, and none may leave the clock changed.

## Variants

Six looks, so gnoblars can be told apart in a crowd (`entity/GnoblarVariant.java`, chosen in `finalizeSpawn` by weight, saved as
`Variant`, synched as an int, shown by `GnoblarRenderer` through `variant.texture()`):

| Variant | Skin | Wart | Outfit |
|---|---|---|---|
| green (weight 4) | muted green | none | blue sash from the left shoulder |
| mossy | dark green | on top of the nose | green-grey sash |
| rusty | orange-brown | on the cheek | red sash, light wraps |
| bark | brown | on the forehead | ochre sash worn the other way, grey vest |
| pickle | yellow-olive | on the side of the nose | purple sash worn the other way |
| sooty | dark grey-green | none | no sash, a stitched chest patch, red-brown wraps |

Every other variant has weight 2, sooty weight 1. The geometry is identical for all: four wart cubes (`wart_nose`, `wart_nose_side`,
`wart_cheek`, `wart_forehead`, all using one texture patch) and `GnoblarModel.setWartSpot` shows the one that the variant names, or
every one for the preview tools when given null. `tools/paint_texture.py` holds the same table (`VARIANTS`) and writes
`gnoblar_<id>.png` for each, so adding a variant means: a constant in `GnoblarVariant` with its wart spot and weight, an entry in
`VARIANTS` with the same id, then `dumpModel`, `paint_texture.py` and `render_preview.py` (which writes `variants.png`, every look
side by side). Older saves with the removed `Wart` flag load as the plain green variant.

## Model and texture

- `client/GnoblarModel.java`: 64x64 texture, parts baked unrotated, all rotation in `applyPose(...)`, a pure function of its
  inputs that `ModelDump` calls. Poses: `idle`, `walk`, `scared`, `sit`, `sniff`, `ride`, `dance`, `sleep` (flags 0 or 1 in `applyPose`).
- The nose is one 4x6x3 block, taller than deep and hanging 1 px below the chin, with a nostril painted on each side face. Keep it a
  single box that is not longer than it is tall: a 4x4x5 block read as a snout, a drooping hook read as a trunk, and a bridge plus
  knob was too fussy for a minimalist vanilla style. The wart cubes are described under "Variants".
  The ears are three stepped 1 px boxes that climb to a point with one nick bitten out, painted on the front face only and two-toned
  (skin rim, skin row, then pink with a vein) so the mirrored back looks right too: painting both faces put two sheets 1 px apart
  that read as four ears. The head sits on a 1 px neck box. The arms are single boxes with their top level with the neck, and the
  rag loincloth is a 4x2x1 box painted on its front face only, level with the front of the body. Held items render through
  `ItemInHandLayer` and the model's `translateToHand` (shrunk to 0.65 for a small hand).
- **Flat parts: do it the way vanilla does a chicken's leg.** The chicken leg is an ordinary 3x5x3 box (`addBox(-1,0,-3,3,5,3)`), but
  its texture paints one 1 px column on one face and the toes on the bottom face, and leaves every other face transparent. The cutout
  render drops transparent texels, so a single flat sheet shows, and no two visible faces share a place. So the ears and the loincloth
  are 1 px boxes with every face but the front left transparent (`paint_ear` and the loincloth branch in `paint_texture.py` return
  `None` for the rest). The loincloth front is flush with the body front. The user does not want visible slabs and rejected other tricks (zero-thickness boxes flicker, a tiny `CubeDeformation`):
  paint one face and leave the rest transparent.
- **Z-fighting between parts.** Two faces in the same plane that face the same way and overlap are both drawn at the same depth and
  flicker (invisible in every preview). The hunch pushed the body's belt below the leg tops, over the legs' side faces, which had been
  flush with a 6 wide body. The body is now 8 wide over legs 1..3 (inset), so no two faces share a plane whatever the pose.
  `check_clipping.py` clips every pair of coplanar same-facing faces in several poses and fails on any overlap. Run it after changing
  a cube, a pivot or a pose. It is only as good as the poses dumped, and nothing here has been seen in a real client.
- Head turn and pitch are clamped in `applyPose` (yaw x0.6 up to 45 degrees, no looking up past level) because a hunched creature's
  head otherwise swings into its shoulders.
- `paint_texture.py` overwrites the textures in `textures/entity/` and colours texels by 3D position, with an explicit box table (`BOXES`) that has to
  match the model code. When a cube in `GnoblarModel` moves, update that table.
- Ear rotation: for the left ear a negative `yRot` sweeps it back and a positive `zRot` droops it. The right ear mirrors that.
  `HEAD_RAISE` in `paint_texture.py` shifts the head-part rows, so the face rows are written for a head 2 px lower than it sits.

## Tests

`gametest/GnoblarGameTests.java`, template `data/gnoblars/structures/empty.nbt` (24x24x24, the same as the Draco). Same rules as the
Draco: mock players are not in `level.players()`, so `isOwnedBy` compares UUIDs, and a goal that ends itself must tolerate one
more `tick()`. Goals that scan `level().players()` (pester) cannot be tested headlessly.

## Not verified

- The mod has never been loaded in a real client. Previews and GameTests are the only checks.
- Untested: the pester feel and distance, held-item placement in the hand, how a banner sits on the head, the dance and sleep poses in
  motion, the mud overlay and dyed sash in the real renderer, sound balance, spawn frequency.

## Ideas not done

More variants (Trapper with a bell, Sneak with a sack, Chieftain), bell alarm, a camp variant in the badlands or swamp with its
own materials, hats and armour on friends, a pack slot, a wandering gnoblar trader, a gnoblar horn,
scrap armour, an Ogre mob, a custom squeak sound.
