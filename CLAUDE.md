# RPG Stations

Diegetic interactive work-station engine (a Hytale mod). Router only: the authoring reference is `docs/` plus the generated `SCHEMA.md`, per-package routers load lazily under `src/`, and the family-wide rules (root `CLAUDE.md`, `.claude/rules`) apply; this file adds only what is specific to this mod.

## Dependencies and build

- Depends on `ziggfreed-common` ONLY. No `build.gradle` line may reference another mod's jar; other mods reach this one through native events and the `api` artifact, never a hard dependency either way.
- `ziggfreed-common` is `compileOnly`, never bundled. `ziggfreedCommonVersion` in `gradle.properties` follows the library's version in lockstep (the umbrella fails a mismatch); the manifest's `ZiggfreedCommon` floor moves only when the code uses newer library API (R364, `ManifestTargetTest`).
- Build and install with `.\build.ps1` (its header lists the flags and the R205 install target); `build.gradle`'s comments say which server and library jars it compiles against.

## MMO-agnostic engine

- The engine carries no progression vocabulary (XP, skills, levels, MMO, any consumer's ids) in anything it ships or compiles, `docs/` included, not even as a forwarded value; examples use the fictitious `yourmod:` namespace. `MmoAgnosticismTest` fails the build (its javadoc lists what it scans and skips). A pack naming its own mod's ids in its own content is fine.
- Progression flows through generic namespaced `Contribution` channels a consumer interprets (see the `api/` router).

## Release scope

- Finished held content (cooking pit, fire, cutting board, mount spike, the unwired `NpcPerformerSpike` harness) sits in `unreleased/`, a mirror of `src/main/resources` outside the resource roots. Restore it with `unreleased/restore.ps1`, never re-create it (inventory: `unreleased/README.md`), and restore the `skill-stations-pack`'s `unreleased/` in lockstep.

## Gotchas

- A station's anchor is the block CENTRE (`blockX+0.5, blockY+0.5, blockZ+0.5`), not the block top, for the placed-piece prop and the puppet `Offset`.
- A custody block-state flip swaps the block to the state's own child `BlockType`, so its texture, animation, light, loop and particles come with it.
- Log through `util.Log`, never `RpgStationsPlugin.LOGGER`; display text goes through `i18n.RpgMsg`.
- No owner-file layer and no Control map: precedence is jar defaults < pack by asset load order, and every fold is additive.
- The jar and the stations pack both ship the `RPG_Station_Sawmill` block under the same item id, and the pack's copy wins by load order. The station id (`sawmill`) is the `StationAsset` filename, independent of the block id.
