# Aceliada: Forge 1.20.1 mod

A boss mod about Acela, a man made of darkness (a friend's in-joke). Separate Gradle project from the Draco mod in the
repository root. Run every command from this `acela/` directory.

## Stack

- Minecraft 1.20.1, Forge 47.4.26, official mappings, Gradle 8.8 wrapper, Java 17 (the toolchain fetches it).
- Mod id `aceliada`, package `com.aceliada`. The author field in `gradle.properties` is a placeholder.
- The pacifism guideline of the root CLAUDE.md is waived for this mod, at the user's request. The other guidelines apply.

## Commands

| Command | Purpose |
|---|---|
| `./gradlew build` | Builds `build/libs/aceliada-1.0.0.jar` |
| `./gradlew runGameTestServer` | Runs the 11 GameTests headless. Also writes `build/preview/arena.json` |
| `./gradlew dumpModel` | Bakes the real model and writes `build/preview/model.json` |
| `python3 -I tools/render_preview.py` | Renders `build/preview/body.png`, `head.png`, `texture.png` from the dump and the skin |
| `python3 -I tools/render_arena.py` | Renders `build/preview/arena*.png` from the arena dump |
| `python3 -I tools/paint_skin.py` | Repaints `acela.png` and `acela_eyes.png` (overwrites hand edits) |
| `python3 -I tools/make_item_textures.py` | Rebuilds the disc and syringe textures from vanilla textures |
| `tools/import_song.sh song.mp3` | Converts the song into the disc's `.ogg` |

Do not use `runServer` or `runClient` in the sandbox (EULA, no display).

## The song is not in git

The repo is public and the song is a recording it has no licence to publish, so
`assets/aceliada/sounds/la_crucea_din_mormant.ogg` is in `.gitignore`. Without it the mod builds and runs, but the disc
and the boss music are silent. Ask the user for the mp3 and run `tools/import_song.sh`. Its length (2:23.7, 2875 ticks)
is hard-coded in `ModItems.DISC_LENGTH_TICKS`.

## How it plays

1. Craft the disc (any music disc + bone + red dye) and play it in a jukebox.
2. While the in-game clock reads 3:55 (day time 21917 to 21933), every jukebox playing the disc within 2 chunks of a
   player summons Acela, once per jukebox per night. In practice: start the disc at night and wait. The song is 2.9
   in-game hours long, so starting it after about 1:05 works. `/time set 21900` while it plays triggers it in a second.
3. "3:55 3:55 3:55!!!" flashes red three times in chat and as a title, with bell strikes.
4. 2.5 seconds later every player within 24 blocks of the jukebox goes to Dealul Bohii on the Nether roof (above the
   jukebox's Nether coordinates), blinded. Acela stands 3 blocks in front of the first player, glowing red, invulnerable.
5. An Undertale-style text box asks "Mă, da știi ce mă fute?" with four answers. Any answer starts the fight. No answer
   in 60 seconds starts it anyway.
6. The fight: boss bar, the song streams over the arena, taunts in chat every 8 to 17 seconds, dodges (45 percent at full
   health, 15 percent near death), bone wave, bone cage, gravity slam, smoke blast.
7. On death: "Mă da tu n-ai empatie...", 1 to 3 Drogul Zombie, the "Defeated the Aceliada" advancement (Nether tab),
   and 10 seconds later the players still in the arena go back to where they were summoned from.

## Layout

- `summon/SummonEvents`: clock and jukebox scan. `AcelaSummoning`: flash, teleport, return trip (saved in the player's
  persistent data under `aceliada_return`). `ArenaBuilder`: the arena, deterministic. `ServerScheduler`: delayed tasks,
  not persisted.
- `entity/AcelaEntity`: intro state (synced flag, `isImmobile` while it lasts), dialogue, dodge, taunts, music, boss
  bar, death. `entity/goal/AcelaSpecialAttackGoal`: the four attacks. `entity/BoneSpikeEntity`: works like evoker fangs.
- `network/`: open and close the dialogue (server to client), the chosen answer (client to server).
- `client/`: vanilla player mesh plus a pipe (`AcelaModelLayer`), eyes and pipe ember on an `EyesLayer`, the bone
  renderer (the vanilla bone item stood upright), `DialogueScreen`.
- `item/`: Drogul Zombie (syringe) and its effect, which makes undead mobs ignore the user.

## Pitfalls already hit

- `Mob.serverAiStep` is final. The intro freezes the AI through `isImmobile()` and runs its own tick from `aiStep`.
- `EntityModel.young` defaults to true. The preview dump sets it to false or everything comes out baby-sized.
- `JukeboxBlockEntity.setItem` only accepts items in `#minecraft:music_discs`, so the disc is in that tag (hoppers too).
- Invulnerable mobs are never targeted (`canBeSeenAsEnemy`), which once made a GameTest pass for the wrong reason.
- GameTest mock players have no connection. Everything that sends packets checks `connection != null`.

## Not verified

- Never loaded in a real client: the dialogue screen, the renderers, the particles, the music and the Romanian
  diacritics in the font are untested. GameTests cover the server side only.
- Untested: the summoning with real players (the scan, flash, teleport and return), multiplayer, peaceful difficulty.
