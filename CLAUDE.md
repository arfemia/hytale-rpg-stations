# CLAUDE.md - RPG Stations

A **standalone Hytale mod** owning the diegetic interactive work-station engine (sawmill, forge,
and friends) extracted out of the MMO Skill Tree mod. It depends on `ziggfreed-common` ONLY -
never the MMO jar; the MMO reaches the engine back through a soft extension surface (native
events + the `api` artifact, both live). Neither mod hard-deps the other, and the standalone mod
is RICH, not a husk: with RpgStations alone installed, its own jar-shipped Sawmill runs the full
diegetic work loop plus a generic reward layer (conditional lootables over native `ItemDropList`s,
command rewards) with zero progression. Package root `com.ziggfreed.rpgstations`. **Status:
phase 1 (extraction) legs 0-6 landed** (scaffold, common lift, engine move, lootables, api
artifact, MMO bridge, pack bridge) **plus the leg P0 closeout** (the `command/` package: `/rpgstations
camera <preset>|list` + `/rpgstations validate`, the design 4.1 scope the phase-1 legs had left
unimplemented); phase 2 (multi-action stations, the anvil arc) is design-only, not started. Design
authority: `../../.claude/research/raw/rpg-stations-unified-design-2026-07-21.md`
(grounded by the decision log `../../.claude/research/rpg-stations-extraction-design.md` and the
adversarial critique `../../.claude/research/raw/rpg-stations-design-critique-2026-07-21.md`, ALL
adopted fixes binding). Origin plan: `../../.claude/plans/interactive-stations.md` +
`../../.claude/plans/work-stations-mod-extraction-prompt.md`.

## Build

```powershell
cd 'D:\dev\business\hyMMO\additional-mods\rpg-stations'; .\build.ps1
.\build.ps1 -Install:$false     # build only
.\build.ps1 -ModsDir <path>     # explicit install target (else $env:HYTALE_MODS_DIR)
```
Produces `build/libs/RpgStations-<version>.jar` (version stays `1.0.0` until first release, the
repo-wide no-churn rule) and copies the runtime jar into the Hytale `Mods/` folder. `.\gradlew.bat
build`/`test` work too. The root hyMMO `rebuild.ps1 -Mods` (or `-Jar -Mods -Packs` for the full
stack) drives this mod's own `build.ps1` alongside every other `additional-mods/` sibling,
dependency-ordered (`ziggfreed-common` first).

**`ziggfreed-common` is the ONLY dependency** (`manifest.json` `Dependencies:
{"Ziggfreed:ZiggfreedCommon": ">=1.4.0"}`; `build.gradle` `compileOnly` + `testImplementation`
against the sibling submodule's built jar, the mmo-mob-scaling sibling-relative path pattern -
`${rootDir}/../ziggfreed-common/build/libs/ZiggfreedCommon-${ziggfreedCommonVersion}.jar`). **NO
reference to the MMO jar anywhere** - this is the load-bearing structural difference from
mmo-mob-scaling (which hard-deps the MMO): RpgStations sits in kweebec's structural position,
optionally listened to by other mods, never a consumer of one.

## Layout

```
settings.gradle / gradle.properties / build.gradle   RpgStations root module + the api submodule
build.ps1                                             build + auto-install (self-locating, pins RpgStations-1.0.0.jar)
api/                                                   the FROZEN-ONCE-1.0.0-releases extension-surface
  build.gradle                                         java-library, archivesName 'rpg-stations-api', BUNDLED into
                                                        the runtime jar (kweebec api-bundling mechanic) AND builds
                                                        standalone as rpg-stations-api-<version>.jar for compile-time
                                                        consumers (the MMO bridge's own compileOnly link)
  src/main/java/com/ziggfreed/rpgstations/api/         see api/CLAUDE.md
src/main/resources/
  manifest.json                                        Group Ziggfreed, IncludesAssetPack:true, ServerVersion Update 5
  Server/RpgStations/{Stations,Lootables,Settings}/     the three Pattern A asset stores this mod registers
  Server/Item/{Items,RootInteractions}/                 the jar's OWN default Sawmill block + its RootInteraction
  Server/Drops/, Server/Emote/                           the standalone Sawmill's native-namespace drop tables + work emote
  Server/Entity/Effects/RPG/                             RPG_Station_Hold.json (the effect-mode movement-lock effect)
  Server/Languages/<bcp47>/                             rpgstations.lang (en-US only so far) + native items.lang/avatarCustomization.lang
  Common/UI/Custom/Pages/RpgStationSummary.ui           the session-summary panel
src/main/java/com/ziggfreed/rpgstations/
  RpgStationsPlugin.java     JavaPlugin entry: injects the api singleton, registers the built-in
                             rpgstations: factors, the three asset stores + their catalog folds,
                             the rpg_station_use interaction, the frame-drain + damage-interrupt
                             systems, the death/disconnect teardown hooks; shutdown() -> stopAll
  api/impl/                  see api/impl/CLAUDE.md - the concrete registry/event-dispatch impl
  asset/                     see asset/CLAUDE.md - StationAsset/LootableAsset/SettingsAsset/Presentation/Requires/Roll/Condition codecs
  station/                   see station/CLAUDE.md - the session engine (THE big package; the hard-won engine rules live here)
  loot/                      see loot/CLAUDE.md - LootEngine/RollEvaluator/FactorSnapshot/CommandRewardExecutor/LootableCatalog
  command/                    see command/CLAUDE.md - RpgStationsCommand (/rpgstations camera|validate, admin-gated)
  interaction/                see interaction/CLAUDE.md - StationUseInteraction (the rpg_station_use RootInteraction handler)
  ui/                         see ui/CLAUDE.md - StationSummaryHud (extends common's KeyedCustomHud)
  i18n/                       see i18n/CLAUDE.md - RpgMsg (the rpgstations. prefix wrapper over common Msg) + RpgStationsLangKeys
  validation/                 see validation/CLAUDE.md - Finding/Severity/Report (the mini content-audit core; StationValidator itself lives in station/)
  util/                       Log (this mod's own SafeLog-shaped facade over RpgStationsPlugin.LOGGER - NEVER the MMO's
                               SafeLog) + Permissions (OP-when-permissions-off else "rpgstations.admin")
```

## Conventions (this mod's own; hyMMO's root CLAUDE.md does NOT auto-apply)

- `@Nonnull`/`@Nullable` on params; log through `util.Log` (info/warn/severe/fine, `Throwable`
  overloads, guarded `try/catch(Throwable)` so a unit-JVM without a Hytale log manager never
  crashes a test) - never a raw `RpgStationsPlugin.LOGGER` fluent chain outside `Log` itself.
- PascalCase upper-first codec keys; nested sub-object groups, never flat prefixed keys; every
  leaf `appendInherited` for native `Parent` reuse. Content ships as `Server/RpgStations/*.json`
  (Pattern A - the codec IS the schema).
- All display text via localization keys through common `i18n.Msg`, wrapped prefix-free by
  `i18n.RpgMsg` (`rpgstations.<key>` against `rpgstations.lang`); no em-dashes anywhere (code,
  comments, lang, docs). No `EnglishDefaults.java` generator in 1.0.0 - the small `.lang` key
  count is authored directly per locale; `i18n.LangFileIntegrityTest` (leg 7A) fails the build on
  a placeholder mismatch, an em-dash, or a duplicate key, scoped to whatever locale dirs exist
  (en-US only today; a locale fan-out needs no test change).
- Orthogonal knobs, not modes; a union `Type`/`Surface`/`Trigger` discriminator between genuinely
  different code paths is not a mode.
- DRY: shared codecs (`Condition`, `Roll`, `Presentation`) are ONE type reused everywhere they
  apply, never duplicated per consumer.

## Origin story: the RPG Stations extraction

Interactive work stations shipped first as an in-jar MMO Skill Tree feature (press F on a block,
camera pulls third-person, a work loop converts materials per cycle for skill XP). The 2026-07-21
maintainer pivot moved the entire diegetic ENGINE out to this standalone mod (phase 1) so the
mechanic is independently valuable (a rich standalone reward loop, third-party-consumable) while
every piece of PROGRESSION (XP awards with the named-factor breakdown, luck aggregation,
flair-unlock persistence, `WORK_STATION` objectives/statistics, the session-summary XP rows) stays
MMO-side, reached back through native events (observe-only moments) + typed registries
(request/response points) - never a bespoke listener registry, per the root hyMMO CLAUDE.md's
native-events rule. See `../CLAUDE.md` (the `additional-mods/` router) for this mod's one-line
entry among its siblings, and `../../src/main/java/com/ziggfreed/mmoskilltree/integration/stations/CLAUDE.md`
for the MMO-side half of the bridge.

**The shared `RPG_Station_Sawmill` block id story** (maintainer decision, phase 1 leg 6): both
this jar AND the standalone `skill-stations-pack` ship a Sawmill block, and BOTH use the exact
SAME item id, `RPG_Station_Sawmill` - the pack's copy overrides the jar default purely through
native asset-pack LOAD ORDER (`defaults < pack`), not a different id. This means a dev-world
Sawmill placed BEFORE the extraction (the old `MMO_Station_Sawmill` id) breaks on upgrade
(accepted - stations were pre-release, never shipped publicly under the old id). The station id
itself (`sawmill`, the `StationAsset` json's filename lowercased) never changed; only the block
item id and its RootInteraction's registered `Type` did (`mmo_station_use` -> `rpg_station_use`,
this mod's own interaction type - see `interaction/CLAUDE.md`).

## The extension surface (api/, live)

Package `com.ziggfreed.rpgstations.api` (+ `.api.event`), the FROZEN-once-1.0.0-releases contract
between this engine and any progression mod that wants to hook it (the MMO bridge is the first and
only consumer today). Split by shape, per the root hyMMO CLAUDE.md's native-events rule:
**observe-only moments are native Hytale events** (`StationSessionStartedEvent`/
`StationCycleCompletedEvent`/`StationSessionCompletedEvent`/`StationToolBrokeEvent`, POJOs
`implements IEvent<Void>`, dispatched `HytaleServer.get().getEventBus().dispatchFor(...)` +
`hasListener()` on the world thread, fired from `station.StationEvents`); **request/response
points are typed registries** on the static `RpgStationsApi` holder (`FactorRegistry` - the one
extensible numeric vocabulary conditional lootables/`Requires` gates evaluate over;
`FlairUnlockRegistry` - union of every registered per-player unlock provider;
`SummaryEnricherRegistry` - extra ledger rows + a themeable decorate hook on the summary panel).
See `api/CLAUDE.md` for the full type-by-type reference and `api/impl/CLAUDE.md` for the concrete
implementation this mod installs at `setup()`.

## Phase 2 (not started)

Multi-action stations (per-action orthogonal knob overrides of station-level defaults), the
`Hold.Mount` knob family (`Surface: Block|Entity` - a standing work mount via a spawned anchor
entity), step-sequence actions over a reshaped `ziggfreed-common` `cast.step` kernel, session-scoped
placed-input custody with block-state visuals, the anvil (Convert + Enhance, the flagship
step-sequence exemplar), and the open flair/moment vocabulary. Full spec: design doc sections 9 +
10 (leg sequence A-H) + 12 (risks) + 13 (decision points). Do not start phase 2 work before the
phase 1 parity gate (design section 11) passes.
