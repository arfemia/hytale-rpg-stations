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
unimplemented); **phase 2 legs A-G are LANDED**: leg A (common kernel reshape), leg B
(multi-action schema + step engine), leg C (placed-input custody + block states + sawmill
migration), leg D (the `Hold.Mount` knob family - the Block/Entity surface discriminator, the
standing work mount), leg E (the anvil arc - the `Stamp` step, composable roll+cap models,
the `EnhanceStamperRegistry` api registry, AND the live wiring that makes multi-action stations
actually run: diegetic action selection at engage, an authored-`Steps` program dispatch path,
`Work.Repeat` session completion), leg F (the open flair/moment vocabulary - the fixed
`Slot` enum retired for open string moment ids, a new standalone `FlairAsset` type ANY mod can
ship, `FlairCatalog` as the ONE merge point), and **leg G (the placed-input PLACED-AS-ENTITY
visual - a new `Custody.Display` group spawning a static, network-replicated, pickup-immune,
physics-free prop entity at the station's block-top anchor via `StationCustodyDisplay`, the
maintainer's directed route over a Blockbench baked-node model swap) - see the "Phase 2" section
below); **leg H (the phase-2 smoke round) is DOCS-LANDED** - the smoke checklist itself is
assembled (`../../.claude/plans/work-stations-mod-extraction-prompt.md`'s PHASE-2 SMOKE
CHECKLIST section).

**Since leg H, FIVE more maintainer in-game smoke rounds landed (2026-07-22):** fresh-boot fix
waves R1-R5 (item localization + HUD width, a `[SMOKEDIAG]`-instrumented seated-swing render
mystery since retired by the puppet route's own in-game confirm (the instrumentation itself was
deleted in the round-6 cleanup pass, 2026-07-23), inventory-pull custody placement, the placed-item
display entity's `CommandBuffer` tick-safety fix, a restart-orphan action-selection recovery), R6
(the anvil's Entity-mount
`NetworkId` fix + graceful degradation, PLUS the NEW press-F custody RETRIEVAL feature -
`rpg_station_retrieve`, in-game CONFIRMED working), R7 (the station-icon `BlockType#getItem()`
state-variant fix), the repo-wide deprecation sweep (33 call sites replaced with their
javadoc-named non-deprecated equivalents, a new `util.InventoryAccess` DRYing every one), and the
FULL PUPPET PRESENTATION BUILD (round-4: "mount the player, hide their player model, spawn a
skinned puppet performing the work" - `StationAsset.Puppet`, `station.StationPuppetController`,
`Hide.Route:"Scale"` in-game-CROWNED, primitives lifted to `ziggfreed-common`'s
`entity.PlayerPuppetService`/`ItemPropEntityService` - see `station/CLAUDE.md`'s puppet-engine
bullet and `asset/CLAUDE.md`'s `Puppet` bullet) plus a round-5 item-grant UX wave (hotbar-first-
if-space grants via common's `inventory.InventoryGrant`, native-pickup-mimic retrieve feedback via
common's `feedback.PickupMimic`, gold lucky-drop notifications - see `loot/CLAUDE.md` and
`station/CLAUDE.md`'s custody/retrieval bullets). Full narrative + the CONSOLIDATED next-session
in-game checklist: `../../.claude/plans/work-stations-mod-extraction-prompt.md`. **The maintainer's
FULL in-game puppet confirm landed (2026-07-23)** - held-item mirror updates within a beat, player
visible after every stop path incl. damage/death/relog, sawmill + anvil positioning good - so the
temporary `puppetspike/` P0 spike-harness package (`/rpgstations puppet <scale|modelswap|hidden|
show|off>`, see `command/CLAUDE.md`) and the five `[SMOKEDIAG]` log lines in
`StationService`/`StationHoldController` were DELETED in the round-6 cleanup pass. The PRODUCTION
puppet route (`station.StationPuppetController`, legs P3-P5) is unaffected and stays live.

**Round-7/round-8 (2026-07-23) landed on top:** round-7 fixes (the `Custody.Display.Rotation`
`{X,Y,Z}` degrees group, the MMO-agnostic enhance session-summary + `StationEnhanceCompletedEvent`,
the native-pickup-shaped item-gain toast) plus a maintainer-approved timing pass: **instant dispatch
for a non-repeating authored Steps program** (`Work.Repeat: false`, e.g. the anvil's Enhance, fires
its first and only cycle immediately at engage rather than waiting a full `Work.CycleMs`), the
explicit `dispatchProgram` `resuming` flag + fresh-dispatch `stepDeadlineMs` zeroing, and the generic
per-step `Presentation` emission (any step's own `Presentation` plays once at step entry, not just
the `Present` step's). **Round-8**: (a) `Custody.Display` `Offset`/`Rotation` are now FACING-RELATIVE
to the placed block's yaw (authored `+Z` = block front, `+X` = block right, block yaw folded into
`Rotation.Y`, `Offset.Y` stays vertical; identity at yaw 0 so pre-round-8 values are byte-identical;
commit `cc52fb4`, read `StationCustodyDisplay`/`asset.Custody`), and (b) **step-synced puppet swings**
- a `StationStep` authoring its own `Puppet.Clip` plays that clip once at STEP ITERATION ENTRY
(`StationStepRegistry`'s guard, `StationStepDecisions.shouldPlayClipOnEntry` mirroring the generic
Presentation hook's once-per-entry / never-on-resume-recheck gate), and the generic engage/swing
puppet clip is SUPPRESSED for a stepped program whose steps author any clip
(`StationSession.stepProgramAuthorsClip`) so they never double-fire (a stepped program with no step
clips keeps its one generic engage swing). The temporary `[D77DIAG]` enhance-timing instrumentation
was REMOVED in round-8 after proving the ritual timing correct (every tagged `Log` line + the
resume-log throttle map gone; the functional instant-dispatch/resuming/Presentation changes stay).

Design
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
  asset/                     see asset/CLAUDE.md - StationAsset/LootableAsset/RpgStationsSettingsAsset/Presentation/Requires/Roll/Condition codecs
  station/                   see station/CLAUDE.md - the session engine (THE big package; the hard-won engine rules live here)
  loot/                      see loot/CLAUDE.md - LootEngine/RollEvaluator/FactorSnapshot/CommandRewardExecutor/LootableCatalog
  command/                    see command/CLAUDE.md - RpgStationsCommand (/rpgstations camera|validate, admin-gated)
  interaction/                see interaction/CLAUDE.md - StationUseInteraction (the rpg_station_use RootInteraction handler)
  ui/                         see ui/CLAUDE.md - StationSummaryHud (extends common's KeyedCustomHud)
  i18n/                       see i18n/CLAUDE.md - RpgMsg (the rpgstations. prefix wrapper over common Msg) + RpgStationsLangKeys
  validation/                 see validation/CLAUDE.md - Finding/Severity/Report (the mini content-audit core; StationValidator itself lives in station/)
  util/                       Log (this mod's own SafeLog-shaped facade over RpgStationsPlugin.LOGGER - NEVER the MMO's
                               SafeLog) + Permissions (OP-when-permissions-off else "rpgstations.admin") + ItemGrantUtil
                               (round-5: this mod's policy wrapper over ziggfreed-common's InventoryGrant, adding only
                               the drop-at-block fallback)
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

## Phase 2 (legs A-G landed; H docs-landed, in-game smoke pending)

Full spec: design doc sections 9 + 10 (leg sequence A-H) + 12 (risks) + 13 (decision points).
Phase 2 work started ahead of the maintainer's in-game phase-1 parity gate smoke (design section
11, still batched/pending) - a deliberate call since every phase-2 leg lands on top of the SAME
engine files the parity smoke will exercise; each leg stays cleanly committed so a smoke-driven
fix layers on cleanly.

- **Leg A (LANDED, `ziggfreed-common`)**: the `cast.step` kernel reshape for resumable walks -
  `CastKernel.runResumable`/`Walk` (Completed/Suspended/Failed), `StepSemantics.isSuspend`/
  `nextIndex` (both optional, default to the pre-reshape `run()` behavior byte-parity). See
  `ziggfreed-common`'s `cast/CLAUDE.md`.
- **Leg B (LANDED, this mod)**: multi-action stations (design 9.1) - a new `StationAsset.Actions`
  map (`asset.ActionDef`, whole-GROUP override of the station's own groups, native `Parent`
  inherits the WHOLE map, same as `Flairs`), diegetic input-matched action selection
  (`asset.ActionInput`, `station.ActionResolver.selectAction`), and actions as STEP PROGRAMS
  (`asset.StationStep` - a `Type`-discriminated union: `Consume`/`Produce`/`Wait`/`Roll`/`Command`
  executable this leg, `Stamp`/`Mount` schema-reserved unimplemented) run through
  `station.StationStepKernel` (the one production `CastKernel` instance every program - implicit
  or authored - walks). The classic convert loop is now the IMPLICIT four-step program
  (`station.ImplicitProgram`: `[Consume, Produce, Roll, Present]`) a station with no `Actions` map
  (or an action with no `Steps`) gets for free - **the shipped sawmill authors NOTHING new and its
  JSON is byte-identical**; `StationService.runRealCycle`/`resumeCycleProgram`/`dispatchProgram`
  now dispatch every real cycle through this ONE engine ("no dual path"), with session-scoped
  suspend/resume plumbing (`StationSession.programSuspended`/`programIndex`/`stepDeadlineMs`/
  `activeProgram*`) ready for a future `Wait`-bearing authored program (unreached by the sawmill,
  which has no `Wait` step). `Camera.FaceBlockMode` is RENAMED `Camera.Recipe` (design 9.7, no
  alias - unreleased, no shipped JSON used the old key). `station.StationValidator.checkActions`
  covers per-action structure (warn-only, never blocks) - see `station/CLAUDE.md` for the full
  file-by-file detail (`ActionResolver`, `StationStepContext`/`Result`/`Semantics`/`Registry`/
  `Handlers`/`Decisions`, `ImplicitProgram`, `StationStepKernel`).
- **Leg C (LANDED, this mod)**: session-scoped placed-input custody + block states (design 9.4) -
  a new `asset.Custody` group (`{MaxQuantity, Input?, States?}`, whole-group-overridable on
  `ActionDef` same as `Hold`/`Tool`; `MaxQuantity` defaults to **100**, the maintainer decision
  overriding the design doc's draft 64) opts a station into a state-dependent F interaction:
  empty + a held matching stack places (or a repeat press tops up) into a per-block in-memory
  claim (`station.StationCustodyClaim`, keyed the SAME `"<worldUuid>:<x>:<y>:<z>"` blockKey
  `StationService` already used for session exclusivity), loaded + owner F engages sourcing the
  convert check from the claim instead of live inventory (`firstRunnableConversionFromCustody`).
  The implicit program's `Consume` step switches `From: "Custody"` whenever the resolved action
  authors `Custody` (`station.StationStepHandlers.ConsumeHandler`'s new drain branch, family-
  matched via a live `Item.getResourceTypes()` resolver injected the SAME way
  `StationToolScaling` avoids the `ItemToolSpec` construction trap - `StationCustody`, the pure
  decision core, unit-tested without a live server). Custody-Input acceptance is EITHER an
  explicit `Custody.Input` (reusing `ActionInput`'s ItemId/ResourceTypeId/Tags routes, `Function`
  still deferred to leg E) OR, when absent, derived from the resolved action's `Recipe.Conversions`
  inputs (the sawmill's "logs by ResourceTypeId family" - zero extra authoring). Unconsumed
  custody auto-returns on EVERY session stop reason (`StationService.stop`'s `returnCustody`,
  unconditional, resolving the store off `s.ref.getStore()` so it covers `stopAll`'s shutdown
  sweep too) - to the owner's inventory when reachable with room, else dropped at the block once
  via the native `ItemComponent.generateItemDrops` spawn (round-5, 2026-07-22: this hotbar-first-
  then-backpack-then-drop ordering now routes per-stack through `util.ItemGrantUtil`, a policy
  wrapper over `ziggfreed-common`'s `inventory.InventoryGrant` - superseding the retired
  `StationCustody.shouldReturnToInventory` all-or-nothing batch check); a NEW
  `StationCustodyBreakSystem` (`BreakBlockEvent`) covers the
  no-active-session case (placed input, block broken before a session ever starts). Block-state
  flips (`world.setBlockInteractionState`, the kweebec shrine-furnace precedent) are HINT-ONLY
  this leg (mechanism-first ruling; visuals land in a later leg) and self-heal: a Loaded state
  surviving a restart with no live claim behind it resets to Empty on the next interaction (custody
  is never persisted - a crash loses it, documented/accepted). The shipped sawmill (both the jar
  default and the pack's MMO-bridged copy) MIGRATED to placed input in this leg - `Custody:
  {"MaxQuantity":100,"States":{"Empty":"Default","Loaded":"Loaded"}}` in `Sawmill.json`, the block
  JSON gained `State.Definitions.Default/Loaded` with per-state `InteractionHint`, the backpack
  drain per real cycle is retired. See `station/CLAUDE.md` for the file-by-file detail
  (`StationCustody`/`StationCustodyClaim`/`StationCustodyBreakSystem`).
- **Leg D (LANDED, this mod)**: the `Hold.Mount` knob family (design 9.2) - `StationAsset.Hold.Mount`
  REPLACES `Hold.Seat.Enabled` (unreleased rename, no alias; the pack's own sawmill copy moved in
  lockstep). `Surface` is a UNION DISCRIMINATOR (`"Block"`|`"Entity"`, critique m3's bless - two
  structurally different code paths, not a mode) defaulting to `"Block"` when absent on an
  authored `Mount` group. `Surface: "Block"` refactors the existing seat mount behind the new
  group with ZERO behavior change (`StationMountController` untouched - the regression anchor).
  `Surface: "Entity"` is the STANDING work mount (`station.StationEntityMountController`, new): at
  engage, spawn a minimal anchor entity at the block center (a phase-2 SPIKE component set -
  `SpawnMinecartInteraction`'s own list minus the cart/model leaves) and attach
  `MountedComponent(anchorRef, attachmentOffset, MountController.Minecart)` to the player directly
  (no interaction chain). CRITIQUE FIX (m7): `Hold.Mount.Entity.Offset {X,Y,Z}` converts explicitly
  to the constructor's `Rotation3f attachmentOffset` parameter (a native mislabeling - it is really
  a spatial offset). `Steerable` (default false) applies the SAME hold effect effect-mode uses plus
  a per-heartbeat anchor snap-back to defeat the native WASD-steers-the-anchor behavior;
  `DismountOnMove` (default true) runs the same origin-delta walk-off check effect-mode uses (the
  entity-mount controller has no native auto-dismount). Because this path never populates the
  client's `MountedUpdate.Block` field, the mount mine infers the player renders STANDING by
  construction - in-game-unverifiable from server source alone, the FIRST phase-2 smoke item.
  `StationValidator` gained `MOUNT_FACE_BLOCK_CONFLICT` (generalized from the old
  `SEAT_FACE_BLOCK_CONFLICT`), `UNKNOWN_MOUNT_SURFACE`, `MOUNT_ENTITY_GROUP_IGNORED`, and
  `MOUNT_STEERABLE_UNTESTED` (all warn-only, per the maintainer ruling). See `station/CLAUDE.md`'s
  Mount bullet for the full file-by-file detail.
- **Leg E (LANDED, this mod + the MMO bridge + the pack)**: the anvil arc (design 9.5) - the
  `Stamp` step un-reserved (`asset.StationStep.Stamp{Reagents,Durability,Stats}`, nested
  `Stats{Pool,Entries,Picks,Unique,Caps{PerItemBudget,PerStat,SkillScaledBudget,Economics}}`), a
  NEW `asset.RollPool` Pattern-A store (`Server/RpgStations/RollPools/*.json`, `loot.RollPoolCatalog`)
  + the shared `asset.StatRollEntry` codec both `RollPool.Entries` and inline `Stats.Entries` use,
  the PURE `station.StampCapEngine` (roll + weighted-pick/`Picks`/`Unique` + the M2-bound
  cap-composition MIN rule, unit-tested with fixture caps - `StampCapEngineTest`), and
  `station.StationStepHandlers.StampHandler` (compute-then-commit per critique M5: roll/cap-clamp
  + reagent-availability + weapon-return-room validated with ZERO mutation first, then reagent
  consumption and the `applyStampMutation` weapon mutation each run under their OWN try/catch that
  restores exactly what was consumed on failure - `claim.setUniqueStack` is the LAST line, reached
  only on full success). **Also landed this leg (beyond the Stamp step itself, required to make
  a real multi-action station function at all - leg B shipped the schema/step-engine machinery but
  never wired the live entry point to honor it):** `StationSession.actionId` + diegetic action
  selection at `toggle()` (`ActionResolver.selectActionByFamily`, a resource-type-FAMILY-aware
  sibling of `selectAction`; a loaded custody claim commits to ITS OWN action, never re-selected by
  whatever is currently held), EVERY per-action group `toggle()` reads (`Work`/`Hold`/`Camera`/
  `Animation`/`Tool`/`Requires`) switched from the station-level default to the RESOLVED action,
  an authored-`Steps` dispatch path (`runAuthoredProgram`, bypassing the Convert-check machinery
  entirely - a Steps action's viability is "does its own Custody already hold something"), and
  `Work.Repeat: false` session completion (`StopReason.RITUAL_COMPLETE`) wired into
  `dispatchProgram`. **A genuine correctness fix along the way**: `StationCustodyClaim` gained an
  optional `uniqueStack` (the REAL placed `ItemStack`, metadata intact) for a `MaxQuantity: 1`
  placement - the pre-existing count-only model would have silently reset a placed weapon's
  durability/prior enhancements to a bare fresh stack on auto-return, an item-loss-equivalent bug
  the bulk sawmill-logs case never exercised. See `station/CLAUDE.md`'s Stamp bullet for the full
  file-by-file detail, `api/CLAUDE.md`'s `EnhanceStamperRegistry` entry for the api contract, and
  `content-packs/skill-stations-pack/CLAUDE.md` (hyMMO root) for the shipped Anvil content.
  **Deviations from the design doc's literal prose** (all evidence-grounded, see each site's own
  javadoc): the Convert action matches vanilla `Metal_Bars` (not the doc's placeholder
  `Metal_Ingot`); the anvil's Tool gate uses `Ids: ["Tool_Hammer_Crude","Tool_Hammer_Iron"]` (no
  `Tags.Family:["Hammer"]` exists on the real vanilla hammer items); the shipped `PerStat` cap key
  is `MMO_CritChance` (the MMO's real `reward.MmoStats` constant, not the doc's `MMO_Crit_Chance`);
  the ritual's Wait steps use `DurationMs` (`Beats` stays schema-reserved/unimplemented - the doc's
  own example would have hard-failed the ritual at its first step); the placeholder empty `Roll`
  step in the doc's example was dropped (the Stamp step's OWN roll engine already covers stat
  rolling, a second roll layer added nothing); `EnhanceStamper` is a lean 2-method
  `inspect`/`apply` contract, not the doc's literal `StampContext`/`StampResult` shape (the api is
  unfrozen pre-1.0.0, free to reshape - RpgStations owns all the roll/cap math, so the stamper
  needs nothing richer). **m9 correction (Smithing skill ownership)**: SMITHING is NOT shipped via
  `custom-skills.json` in the pack - that file is a local SERVER-OWNER config
  (`mods/mmoskilltree/custom-skills.json`, loaded from a `Path` at plugin startup), not a
  pack-authorable asset at all, so a pack zip cannot ship into it. SMITHING was ALREADY a dormant
  `BUILTIN_SKILL_DATA` entry in the MMO's `skill.SkillRegistry`; leg E promotes it to `BUILTIN_SKILL_NAMES`
  with `requiresFeatures: ["stations"]` - the EXACT TAMING precedent (a built-in, feature-gated on
  the owning integration's presence), a small MMO-jar code change, not pack content.
- **Leg F (LANDED, this mod)**: the open flair/moment vocabulary (design section 9.6) - the fixed
  `station.StationFlairs.Slot` enum (`CYCLE`/`SWING`/`RARE_FIND`/`COMPLETION`) is RETIRED for an
  open STRING moment id (`StationFlairs.MOMENT_CYCLE`/`MOMENT_SWING`/`MOMENT_IMPACT`/
  `MOMENT_RARE_FIND`/`MOMENT_COMPLETION` well-known constants, plus
  `stepMomentId(actionId, stepId)` building a per-step `step:<actionId>:<stepId>` id a `Present`
  step resolves against). `MOMENT_IMPACT` is a NEW id split off `MOMENT_SWING` this leg (the
  delayed swing-impact cue previously reused the swing slot verbatim - a flair author can now
  target either cue independently; no shipped content depended on the fused behavior). A new
  `asset.FlairAsset` Pattern-A type (`Server/RpgStations/Flairs/<Name>.json`, `{Stations?[],
  Moments}`) lets ANY installed mod/pack ship a flair layer without touching a station's own JSON;
  `station.FlairCatalog.effectiveFlairsFor` is the ONE merge point (a station's inline `Flairs` -
  reshaped to the SAME open `{Moments}` shape as `FlairAsset`, no more fixed leaves - UNIONED with
  every applicable `FlairAsset`, folded ONTO it for a same-id collision). `StationValidator` warns
  (never blocks) on an empty `Moments` map, an unrecognized moment id (typo detection - a
  `step:`-prefixed id or one of the 5 well-known ids always passes, so a FUTURE engine moment
  never breaks an OLDER pack), and a `FlairAsset.Stations` entry naming an unknown station.
  `api.impl.StationViewImpl.flairIds()` and `station.StationCatalog.allFlairIds()` both reuse the
  merge point rather than an inline-only view that would now be incomplete. The MMO bridge's
  `FlairUnlockRegistry`/`StationComponent` provider is UNTOUCHED (it already answers "which ids
  has this player unlocked" and was always vocabulary-agnostic). See `station/CLAUDE.md`'s "Loot +
  flairs" bullet for the full file-by-file detail.
- **Leg G (LANDED, this mod + the pack)**: the placed-input PLACED-AS-ENTITY visual - the
  maintainer's directed route (a scout-resolved, source-verified mechanism: the engine's own
  sanctioned admin "Entity Spawn Page" Items-tab exemplar) over a Blockbench baked-node model swap.
  A new nullable `asset.Custody.Display` group (`{Offset{X,Y,Z}, Scale, Rotation}`, every leaf
  `appendInherited`) opts a `Custody`-governed action into a spawned prop entity rendering the
  placed item at the station's block-top anchor - `station.StationCustodyDisplay` (new class):
  block-shaped items (the sawmill's placed logs) spawn a real `BlockEntity` (the actual block
  model, not a flat icon), everything else (the anvil's placed weapon) spawns a bare `ItemComponent`
  prop (the generic "dropped item minus physics" shape). Both routes
  `ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType())` - the display entity
  never survives a server restart, matching custody's own "never persisted, crash = loss"
  lifecycle exactly, which resolves "reconcile orphans on restart" by construction. Lifecycle
  tracked ON the claim (`StationCustodyClaim.displayRef`, mirroring `uniqueStack`'s own pattern):
  spawned once at first placement (`StationService#placeIntoCustody`), despawned at whichever of
  the two claim-removal sites fires first (`#returnCustody` or `#onCustodyBlockBroken`). Shipped
  on both pack exemplars (`Sawmill.json`'s `Custody.Display`, the anvil's `enhance` action's
  `Custody.Display`) - see the pack's own `CLAUDE.md` for the shipped (provisional,
  in-game-unverified) tuning values. See `station/CLAUDE.md`'s dedicated bullet for the full
  file-by-file detail, including the documented world-space-offset simplification (no existing
  block-facing-yaw helper to compose a rotated `Offset` against).
- **Leg H (docs-landed, this leg)**: the phase-2 smoke round. No engine change - collects the
  8-locale lang-key gap report (the pack's `items.lang`/`rpgstations.lang` overlay are the only
  ones with real gaps; RPG Stations' own `rpgstations.lang` needed zero new phase-2 keys, no
  phase-2 leg added a new player-facing UI string), updates this router tree + the MMO/pack docs
  the earlier legs left slightly behind (the pack's `README.md`, the MMO `CHANGELOG.md`'s
  bridge/item-seam entries for leg E), and assembles the PHASE-2 SMOKE CHECKLIST as a clearly
  marked section in `../../.claude/plans/work-stations-mod-extraction-prompt.md` (the standing-mount
  spike, custody place/return incl. relog, state-dependent F-hints, placed-item visuals, the anvil
  Convert+Enhance ritual end to end incl. cancel custody-return and budget caps, Smithing XP + the
  skill page, multi-action selection UX, plus the still-pending phase-1 parity items). The actual
  in-game confirmation pass is still batched/pending, alongside phase 1's own parity gate (design
  section 11) - neither has run yet; both are one maintainer smoke session away.
