# RPG Stations

Diegetic interactive work-station engine (a Hytale mod). Router only: the authoring reference is `docs/` plus the generated `SCHEMA.md`; per-package routers load lazily under `src/`. The family-wide rules (root `CLAUDE.md`, `.claude/rules`) apply here; this file adds only what is specific to this mod.

## Dependencies and build

- Depends on `ziggfreed-common` ONLY. No `build.gradle` line may reference another mod's jar; other mods reach this one through native events and the `api` artifact, never a hard dependency either way.
- `ziggfreed-common` is `compileOnly`, never bundled (double-loading breaks classloader identity). The manifest's `ZiggfreedCommon` floor moves with `ziggfreedCommonVersion` in `gradle.properties`.
- Build with `.\build.ps1` (installs to `$env:HYTALE_MODS_DIR`; `-Install:$false` builds only; `-ModsDir` overrides). It compiles against the installed `HytaleServer.jar` (`hytaleHome`) and `../ziggfreed-common/build/libs/ZiggfreedCommon-<version>.jar`: build ziggfreed-common first (root `rebuild.ps1 -Mods` orders it).
- `gradle/deprecation-gate.gradle` (run by `check`) is hyMMO's, copied byte for byte: it changes only by copying hyMMO's.

## MMO-agnostic engine

- The engine carries no progression vocabulary (XP, skills, levels, MMO, any consumer's ids) in schema keys, api types, engine or validator ids, lang, shipped JSON or `docs/`, not even as a forwarded value. Examples use the fictitious `yourmod:` namespace. `MmoAgnosticismTest` fails the build (routers, `CURSEFORGE.md` and `patch-notes/` are exempt).
- A pack naming its own mod's ids in its own content is fine; the rule governs the engine's vocabulary only.
- Progression flows through generic namespaced `Contribution` channels a consumer interprets (see the `api/` router).

## Release scope

- 1.1.0 is released (Oct 9, 2026, with MMO Skill Tree 1.7.0): the disenchanting wave (two Disenchanting Tables, the ritual queue and paced beats, the protect-list store, `STATION_INPUT`, api 1.1.0 at contract 10, `ziggfreed-common` floor `>=2.2.0`). The next is 1.1.1 on Hytale Update 7; its scope is the 1.7.1 board's. Finished held content (cooking pit, fire, cutting board, mount spike, the unwired `NpcPerformerSpike` harness) sits in `unreleased/`, a mirror of `src/main/resources` outside the resource roots. Restore it with `unreleased/restore.ps1`, never re-create it (inventory: `unreleased/README.md`), and restore the `skill-stations-pack`'s `unreleased/` in lockstep.
- The api is not frozen; declaring it frozen is the maintainer's decision, never implied by a version number.

## Gotchas

- A station's anchor is the block CENTRE (`blockX+0.5, blockY+0.5, blockZ+0.5`), not the block top, for the placed-piece prop and the puppet `Offset`.
- A custody block-state flip swaps the block to the state's own child `BlockType`, so its texture, animation, light, loop and particles come with it.
- Server-wide protect-lists are their own asset store (`Server/RpgStations/ProtectLists/`), not a `Settings` leaf: every loaded file adds to one list, so a pack's `Settings.json` never carries one.
- Spawn entities from a cycle or interaction handler through the threaded `CommandBuffer<EntityStore>`. `store.addEntity` inside a system throws "Store is currently processing!" and the surrounding catch hides it (`util.ItemDropUtil` and `StationCustodyDisplay` are the examples).
- An `ItemDropList` whose tree holds only `Droplist` references fails validation ("Container must have something to drop!") and takes the whole mod down. Pair every `Droplist` with a concrete `Single`, as every vanilla table does.
- Use `ItemGrantUtil.grantOrDrop` when the result decides counting, notifying or summarising; `grant()`'s `FALLBACK` only means a drop was attempted.
- Log through `util.Log`, never `RpgStationsPlugin.LOGGER` (this mod's counterpart of the MMO's `SafeLog` rule). Display text goes through `i18n.RpgMsg`, which prefixes `rpgstations`.
- No owner-file layer and no Control map: precedence is jar defaults < pack by asset load order, and every fold is additive.
- The jar and the stations pack both ship the `RPG_Station_Sawmill` block under the same item id and the pack's copy wins by load order. The station id (`sawmill`) is the `StationAsset` filename, independent of the block id.
