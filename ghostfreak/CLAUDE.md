# Ghostfreak: Forge 1.20.1 mod

A new mod in this repository, separate from the Dacian Draco mod in the parent directory. It adds the **Ghostfreak**,
a tall ghost based on the Ectonurite from Ben 10. Same stack and rules as the Draco mod (see `../CLAUDE.md`):
Minecraft 1.20.1, Forge 47.4.26, official mappings, Java 17 through the Gradle toolchain, vanilla-style models.

- Mod id `ghostfreak`, package `com.ghostfreakmod.ghostfreak`. Author in `gradle.properties` is a placeholder.
- Run commands from this directory. The Gradle cache in `~/.gradle` is shared with the Draco mod.
- Never commit `build/`, `run/` or `logs/` (ignored by the repository `.gitignore`).

## Commands

| Command | Purpose |
|---|---|
| `./gradlew build` | Builds `build/libs/ghostfreak-1.0.0.jar` |
| `./gradlew runGameTestServer` | Headless GameTests. All 12 should pass |
| `./gradlew dumpModel` | Bakes the real `GhostfreakModel` to `build/preview/model.json` |
| `python3 -I tools/paint_texture.py` | Repaints `ghostfreak.png` and `ghostfreak_eyes.png` from the dumped geometry |
| `python3 -I tools/render_preview.py` | Software-renders `build/preview/ghostfreak_{a,b,c}.png` (no translucency, eye layer merged in) |
| `python3 -I tools/make_item_textures.py` | Rebuilds the bottle and lantern textures from vanilla textures |

`paint_texture.py` overwrites both entity textures, so hand edits are lost when it runs. Poses for the preview are in
`src/preview/java/.../ModelDump.java`. Do not use `runServer` or `runClient` in the sandbox (EULA, no display).

## Behaviour (decisions from the user)

- **Hostile by default, but only in the dark.** Wild ones hunt players within 10 blocks without needing line of sight.
  They flee light level 9 and above (turning transparent and drifting to a darker spot), will not attack from a lit
  area, and fade away for good after 400 ticks at level 12 or above (unless named or tame).
- **Pacifying:** soul sand or soul soil offered by hand calms it for 30 seconds, with a 1 in 3 chance to tame it.
  Music also calms it (a playing jukebox within 6 blocks, or a note block within 12): it drops its target and dances.
- **Tamed:** follows, sits or wanders. An empty hand from the owner cycles follow, sit, wander. Soul sand heals it.
  It defends its owner. A glass bottle from the owner gives a Bottle of Ectoplasm (1 minute cooldown). Dances to music.
- **Phasing:** it turns see-through (alpha 0.2) and passes through blocks (`noPhysics`) when the way to where it wants
  to go is blocked, when it is fleeing light, or when it is stuck inside a block. It cannot be hurt while phased, and it
  never becomes solid while its hitbox is inside a block. The eye stays opaque and glows faintly (separate eyes layer).
- **Tentacles:** eight black and white striped tentacles, hidden until it fights (revealed for 40 ticks after the last
  target sighting, lash forward on each attack) or dances.
- **Spawning:** soul sand valley (weight 12) and, rarely, dark forest (weight 1), `MobCategory.MONSTER`, never on
  peaceful, only where `Monster.isDarkEnoughToSpawn`.
- **Pacifism rule:** no kill-for-drops. Death drops nothing, ectoplasm comes from a tame one, taming and calming need only
  soul sand or music.
- **Items:** Bottle of Ectoplasm (drink for 90 seconds of invisibility, returns the bottle), Spectral Lantern (8 iron
  nuggets around a bottle, a soul lantern at light level 7, dim enough for a Ghostfreak), spawn egg.

## Layout

- `entity/GhostfreakEntity.java`: a `TamableAnimal` with `FlyingMoveControl(hoversInPlace = true)`. Phase, reveal, dance,
  light and taming logic sit in `customServerAiStep()`. Synced: phased, revealed, lashing, dancing, mode.
- `entity/goal/`: dance, sit, avoid light, attack, follow owner, wander. Goals steer with `setWantedPosition` and never
  use pathfinding, so phasing works.
- `client/GhostfreakModel.java`: chain of boxes, pure `applyPose(...)` for the preview tool. Texture 128x64, body packed
  from v = 0, tentacles from v = 32 (the painter relies on it). `GhostfreakEyesLayer` draws `ghostfreak_eyes.png`.
- `event/MusicEvents.java`: note blocks, through `NoteBlockEvent.Play`.
- `gametest/GhostfreakGameTests.java` and `data/ghostfreak/structures/empty.nbt` (24x24x24).

## Pitfalls

- The goal selector ticks running goals on odd ticks without re-checking `canContinueToUse`, so goals must tolerate one
  more `tick()` (the attack goal returns when its target is null).
- `Mob.serverAiStep` is final. Hooks go in `customServerAiStep()`, which runs before the move control. Phase has to be
  decided there, or the entity moves one tick with the old collision setting.
- `makeMockPlayer()` is not in `level.players()`, so owner lookups, follow and player targeting cannot be tested headlessly.
  `offerSoul(player, stack, chance)` takes the chance as an argument so tests can force or refuse taming.
- Light does not update on the tick a block is placed. GameTests that depend on light wait a couple of ticks first.
- `helper.spawn` marks mobs persistent, so persistence cannot be used to tell wild from named ones. Fading uses `hasCustomName()`.

## Not verified

The mod has never been loaded in a real Minecraft client. Untested: translucent rendering and the glow layer in game,
hitbox against the model, spawn frequency, sound balance (phantom sounds pitched down), real jukebox detection, and
following an owner through terrain. Note block detection is tested through a posted event, not a real note block.

## Ideas not done

A custom howl or whisper sound, an item that reveals or repels Ghostfreaks, tentacle damage particles, a rare variant,
and a sneaking click to force phasing.
