# Okapi: Forge 1.20.1 mod

A shy jungle okapi that can be befriended and then forages with its tongue. Same stack and guidelines as the Dacian Draco in the repository root (see `../CLAUDE.md`): Minecraft 1.20.1, Forge 47.4.26, official mappings, Java 17, pacifism, vanilla interaction, vanilla-style models. This is a separate Gradle project. Mod id `okapi`, package `com.okapimod.okapi`.

## Commands (run inside `okapi/`)

| Command | Purpose |
|---|---|
| `./gradlew build` | Builds `build/libs/okapi-1.0.0.jar` |
| `./gradlew runGameTestServer` | Headless GameTests. All 15 should pass |
| `./gradlew dumpModel` | Bakes the real `OkapiModel` and writes `build/preview/model.json` (poses: flat, rest, idle, walk, reach, sit, calf) |
| `python3 -I tools/paint_texture.py` | Repaints `okapi.png` from the dumped "flat" geometry. Hand edits are lost |
| `python3 -I tools/render_preview.py` | Software-renders `build/preview/body.png`, `head.png`, `texture.png` |

Do not use `runServer` or `runClient` in the sandbox (EULA, no display).

## Design

- **Befriending, no bones:** a wild adult flees from anyone who is not sneaking (`AvoidEntityGoal`). Three leaves (any leaves, apples or sweet berries) offered while sneaking tame it. Offered while standing, the gift only startles it and is not used up.
- **Forager (tamed only):** `OkapiForageGoal` walks to the nearest ripe cocoa pod, sweet berry bush or glow berry vine within 8 blocks (leaves as a fallback, trimmed within 5 blocks), reaches with the tongue (synched `TONGUE_OUT`, model scales the tongue part) and puts the harvest in a 9-slot pack. Crops reset to their young stage, leaves are never removed and usually give nothing (apple, sapling or stick now and then). Wild okapis only trim leaves in reach, for show.
- **Delivery:** `OkapiDeliverGoal` starts at 16 carried items, a full pack, or 20 s without a harvest. It goes to the nearest barrel within 16 blocks (the okapi's basket) and falls back to the owner (items are spat out). A full barrel is skipped for a minute. Sneak with an empty hand to collect the pack at any time. Empty hand alone toggles sitting. A sitting okapi is off duty.
- **Pacifist:** no attack, no targets. Death drops nothing except the pack (empty loot table). Breeding needs a tamed pair and food.
- Sounds are cow sounds pitched down, quiet and rare. Spawns in `#minecraft:is_jungle`.

## Pitfalls

- Vanilla `SitWhenOrderedToGoal` sits a tamed animal whenever its owner cannot be looked up. `OkapiSitGoal` replaces it, which also keeps the GameTests meaningful (mock players are not in `level.players()`).
- `registerGoals()` runs in the `Mob` constructor, before subclass fields exist. Goal constructors must only store the entity.
- Goals here use `requiresUpdateEveryTick()` and tolerate a `tick()` after they ended.
- Model: integer cube dimensions, one box per leg, front of the animal is -Z, ground is y = 24. `applyPose` is a pure function for the preview tool.
- The ears are zero-thickness planes (like the wings of a vanilla bee). The renderer does not cull, so both sides of a plane are drawn at the same depth and must carry the same pixels: the painter uses unlit colours for them. The tongue is an ordinary 2x1x12 box: a flat tongue was tried and the user preferred the box.
- Every limb is rooted inside its parent so it comes out of it instead of hanging off it: the legs start 3 px inside the body (17 px tall, pivot y = 7, feet still on y = 24), the neck starts inside the shoulders (pivot y = 4, z = -6) and the head sits back on the neck (pivot z = +3), so the neck comes out from under it. The sit pose lowers the body by 13 px to match.
- The tail starts two pixels inside the rump (pivot z = 8, rump ends at z = 10) so it comes out of the body. The neck is 5x14x5 (a half-pixel x/z offset, whole-pixel dimensions) at texOffs (66, 40).
- Texture, as checked against the vanilla cow, horse and llama: a few close, muted shades in irregular clusters, elongated along the hair (along the body on the flanks, up and down on legs and neck), low contrast. Three versions failed and must not come back: a regular fur-stroke pattern (looks like plush with spots), dithered flank patches (checkerboard) and flat colour (lifeless). Stripes must wobble, vary in thickness, start in different places and break up. Ear rims are brown, not cream.

## Not verified

Never loaded in a real client. Untested: pathing to cocoa on real jungle trees, barrel delivery with a real owner, sound balance, spawn frequency, baby hitbox and head scale.

## Ideas not done

Hidden calves in leaf nests, a mineral-lick prospector (clay and charcoal), an infrasound lookout, mounting.
