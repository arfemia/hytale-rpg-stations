# CLAUDE.md - RPG Stations

A **standalone Hytale mod** owning the diegetic interactive work-station engine (sawmill, forge,
and friends). It depends on `ziggfreed-common` ONLY; any other mod reaches the engine through a
soft extension surface (native events + the `api` artifact, both live), never a hard dependency in
either direction. The standalone mod is RICH, not a husk: with RpgStations alone installed, its own
jar-shipped Sawmill runs the full diegetic work loop plus a generic reward layer (conditional
lootables over native `ItemDropList`s, command rewards) - and needs nothing else. The buildable
Cooking Pit exemplar (the multiblock/socket/unattended/doneness stack end to end) is finished but
HELD under `unreleased/` for a later release; see the "0.1.0 release scope" section. Package root
`com.ziggfreed.rpgstations`. **Status:
phase 1 legs 0-6 landed** (scaffold, common lift, engine, lootables, api
artifact, consumer bridge, pack bridge) **plus the leg P0 closeout** (the `command/` package: `/rpgstations
camera <preset>|list` + `/rpgstations validate`); **phase 2 legs A-G are LANDED**: leg A (common kernel reshape), leg B
(multi-action schema + step engine), leg C (placed-input custody + block states + sawmill
migration), leg D (the `Hold.Mount` knob family - the Block/Entity surface discriminator, the
standing work mount), leg E (the anvil arc - the `Stamp` step, composable roll+cap models,
the stamper delegate seam, AND the live wiring that makes multi-action stations
actually run: diegetic action selection at engage, an authored-`Steps` program dispatch path,
`Work.Looping` session completion), leg F (the open flair/moment vocabulary - the fixed
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
javadoc-named non-deprecated equivalents, all reading through `ziggfreed-common`'s
`inventory.PlayerAccess`, the one shared primitive this mod and the MMO both call for that
accessor shape), and the
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
`{Yaw,Pitch,Roll}` degrees group, the vocabulary-agnostic enhance session-summary + `StationEnhanceCompletedEvent`,
the native-pickup-shaped item-gain toast) plus a maintainer-approved timing pass: **instant dispatch
for a non-repeating authored Steps program** (`Work.Looping: false`, e.g. the anvil's Enhance, fires
its first and only cycle immediately at engage rather than waiting a full `Work.CycleMs`), the
explicit `dispatchProgram` `resuming` flag + fresh-dispatch `stepDeadlineMs` zeroing, and the generic
per-step `Presentation` emission (any step's own `Presentation` plays once at step entry, not just
the `Present` step's). **Round-8**: (a) `Custody.Display` `Offset`/`Rotation` are now FACING-RELATIVE
to the placed block's yaw (authored `+Z` = block front, `+X` = block right, block yaw folded into
`Rotation.Yaw`, `Offset.Y` stays vertical; identity at yaw 0 so pre-round-8 values are byte-identical;
commit `cc52fb4`, read `StationCustodyDisplay`/`asset.Custody`), and (b) **step-synced puppet swings**
- a `StationStep` authoring its own `Puppet.Clip` plays that clip once at STEP ITERATION ENTRY
(`StationStepRegistry`'s guard, `StationStepDecisions.shouldPlayClipOnEntry` mirroring the generic
Presentation hook's once-per-entry / never-on-resume-recheck gate), and the generic engage/swing
puppet clip is SUPPRESSED for a stepped program whose steps author any clip
(`StationSession.stepProgramAuthorsClip`) so they never double-fire (a stepped program with no step
clips keeps its one generic engage swing). The temporary `[D77DIAG]` enhance-timing instrumentation
was REMOVED in round-8 after proving the ritual timing correct (every tagged `Log` line + the
resume-log throttle map gone; the functional instant-dispatch/resuming/Presentation changes stay).

**Scope-2 (wave 2, 2026-07-24) landed on top: the authoring surface + step model were reshaped
package-wide.** The old `StationStep.Type` union (Consume/Produce/Wait/Roll/Command/Stamp) is
GONE, replaced by an orthogonal-phase step record (a step composes any combination of nullable
`Walk`/`Consume`/`Stamp`/`Produce`/`Roll`/`Commands` phases in one fixed order; a phase-free step
is a pure beat); `StationAsset.Loot`/`ActionDef.Loot`/`StationStep.Roll` all take the unified
`LootRef` (`{Lootables[], Rolls[]}`, `Loot.Tables` renamed `Loot.Lootables`); a new standalone
`asset.ActionAsset` type (`Server/RpgStations/Actions/*.json`) lets an `Actions` map entry `Ref`
a reusable action instead of always inlining one; a new `asset.ExtensionAsset` type
(`Server/RpgStations/Extensions/*.json`) is the ONE additive fourth-party extension mechanism
(Station/Action/Lootable/RollPool targets, additive-only, base-wins key collisions) superseding
ad hoc full-file pack overrides; the Stamp step's `Caps` re-shaped onto a weighted factor-term
budget vocabulary (`Budgets[]`) that also now drives
loot chances, ladder values, and roll magnitudes (one factor vocabulary everywhere). The
multi-station seam - `StationStep.Walk`/`At`, `Produce.To:"Custody"`, and `ActionDef.Anchors`
discovery/claiming - decoded/validated this wave and fully EXECUTES as of a later wave (below).
This intro section documents phase 1/2 history and stays accurate for everything it describes
except the step-Type/Loot-shape/Caps-shape terminology scope-2 superseded, and except everywhere
the LATER action-first restructure (immediately below) superseded scope-2 itself: `StationAsset`
no longer has a `Loot` group at all (renamed `Bonus` and moved onto `ActionDef` only, alongside
every other per-action group), and `StationAsset.Actions` entries are no longer whole-group
overrides of a station-level default (station-level group inheritance was deleted outright).

**The action-first restructure (pre-release) landed on top of scope-2: station-level group
inheritance is DELETED.** `StationAsset` keeps only `Identity`/`Block`/`Requires`/`Flairs`/
`Actions[]`; every other group lives EXCLUSIVELY on `ActionDef` - `Work`/`Recipe`/`Tool`/`Custody`/
`Bonus`/`ContributionScale`/`Anchors`/`Steps`/`Moments` as direct keys, and the four presentation
groups `Hold`/`Camera`/`Animation`/`Puppet` under its one nested `Worker` group, with no
station-level fallback left to inherit from - two actions that
used to share a station-level default now share by REFERENCE (`Ref` to the same standalone
`ActionAsset`, or native `Parent` between `ActionAsset`s), never by implicit fallback.
`StationAsset.Recipes[]` (the tried-in-order recipe LIST, `station.RecipeSelection`) is GONE -
an action authors AT MOST ONE `Recipe`, gated by that action's own `Tool`; two variants that used
to be two `Recipes[]` entries are now two `ActionDef`s, since the diegetic `Select` match already
IS the "try this, else that" chain one level up. `Roll.Grants.BonusOutputCopies` is deleted;
the `rpgstations:output_items` reward (additive extra units of the cycle's own primary output;
FRACTIONAL by maintainer ruling - the whole part every time plus the leftover fraction as the chance
of one more, summed across the cycle and resolved once by `loot.OutputItemResolver`)
is now the ONLY probabilistic-output leaf, and `Recipe.Yield` is purely deterministic
(`Base`/`Scale`/`Min`/`Max`, no ladder, no roll). A new `ActionDef.ContributionScale` group (a
factor ladder following the SAME rules a loot `Roll.Ladder` does) pre-scales
`Work.PerCycleContributions` before the cycle-completed event dispatches, reporting the resolved
multiplier back for display only (`StationCycleCompletedEvent.contributionScale()`).
`ExtensionAsset`'s per-leaf overlay (rule 5) gained a third payload, `ContributionScale`, beside
`Puppet`/`Custody`. Docs-site retired: the docsite Next.js build (`docs-site/`) is gone, replaced
by nothing beyond the in-repo `docs/` markdown guides + the codec-autogenerated `SCHEMA.md` this
GitHub repo already ships - see `docs/SchemaDocWriter.java`. **Perf hardening landed alongside**:
`RpgStationsSettingsAsset.Limits` (`MaxSessionsPerWorld`/`MaxPuppetsPerWorld`/
`MaxStashesPerSection`, three independent nullable ceilings, unlimited by default - the stash one
is per chunk section since the custody persistence wave, which also retired the disconnect
claim-eviction sweep: placed custody now stays put on disconnect, see the custody-persistence
paragraph below; the group has since grown `UnattendedIntervalMs`, the unattended pass's pace
knob, and `MaxUnattendedGatherCycles`, the min-of-caps gather payout ceiling - see
`asset/CLAUDE.md`'s `RpgStationsSettingsAsset` bullet for the full group); a `RemoveWorldEvent` listener (`RpgStationsPlugin#registerWorldEviction` ->
`StationService#onWorldRemoved`) that EVICTS this engine's volatile global block-keyed maps by
world-uuid prefix on world unload (they never partitioned per world, so an instance-world fleet
leaked a stale entry per station block for the whole uptime);
`StationInterruptDamageSystem`/`StationDeathSystem` narrowed from `Query.any()` to
`PlayerRef.getComponentType()` (only a player can hold a work session, so every mob
damage/death event used to pay a dispatch + session lookup for nothing); and press-F custody
retrieval's match scoped to the presser's own WORLD (`StationCustodyRetrieval#owns`, a `NetworkId`
is per-world and repeats across worlds, so an unscoped match could resolve a claim standing in a
DIFFERENT world). `RpgStationsApi.stationCount()` (a default-bodied `stations().size()`, overridden
with the direct catalog size) is the cheap presence-check/count companion to `stations()`. See
`asset/CLAUDE.md` and `station/CLAUDE.md` for the full current schema/engine detail (both fully
rewritten for this restructure); this router's own release-scope and history sections below stay
accurate for everything they describe except the `Recipes[]`/station-level-group language this
restructure superseded.

**The SHARED-LOOT RE-BASE landed on top of all of it (pre-release, so the schema moved freely).**
This mod no longer owns a loot model. `Roll` and everything inside it (`Conditions`, `Chance`,
`Ladder`, `Grants`, the `Cue`), `LootRef`, the `Lootable` and `RollPool` asset types and their
stores, `StatRollEntry`, the stamp roll + budget engine, and the stamper contract are all
`ziggfreed-common`'s (`com.ziggfreed.common.loot`, `loot.stamp`, `loot.reward`), so identical JSON
behaves identically at a station, in a chest and at a quest turn-in. Four consequences an author or
a maintainer meets immediately:
- **Loot content moved house**: `Server/ZiggfreedCommon/Lootables/*.json` and
  `Server/ZiggfreedCommon/RollPools/*.json`, both registered by the shared library, not by this mod.
- **A `Chance` is the shared `{Base, Factors, Clamp}` formula** (`BasePercent`/`CapPercent` are
  gone), and every `Factors` array is the shared weighted `FactorFormula.Term` (this mod's own
  `FactorRef` is gone).
- **A `Cue` is a MOMENT ID, not a presentation body.** The loot layer names a moment; the station
  decides what it sounds like, through the same `emitMoment` funnel every other station moment uses.
  The open `cue:<yourName>` namespace sits beside the well-known ids and `step:<actionId>:<stepId>`.
- **The three station-only payouts are registered reward KINDS** inside `Grants.Rewards`:
  `rpgstations:output_items`, `rpgstations:contribution`, `rpgstations:effect`. They COLLECT onto
  the pass rather than acting, which is why they are a per-pass registry seeded from the
  process-wide vocabulary (`loot.StationRewardKinds`). The `api` artifact went to **0.2.0** for the
  stamper retirement.

**The CUSTODY PERSISTENCE wave (2026-09-01, multi-placement leg L4) landed on top.** Placed-input
custody is CHUNK-PERSISTED world state: the authority is ziggfreed-common's `BlockStashes` store
(one `BlockStash` per block on its own chunk section, registry id `ZigBlockStash`, saved and
loaded with the chunk), and `station.StationCustodyClaim` is a thin per-touch VIEW over the
stash's one reserved `main` pile - no in-memory claim map exists any more, every read resolves the
section live (the `PlacedBlockLedger` posture), and every in-place mutation ends with one
`markDirty` (zc's `Handle` dirty contract). Consequences: placed materials (the anvil's
metadata-bearing weapon included, via the stash's engine-codec `Unique` leaf) survive a logoff, a
restart and a chunk unload; a DISCONNECT / SERVER_STOP / WORLD_CHANGE stop leaves standing custody
in the world (`StationService#custodyReturnsAtStop` - the old disconnect claim sweep is deleted,
"leave the stew on and log off") while every still-present stop reason keeps its hand-back; world
EVICTION drops only volatile state and never touches stashes; a foreign stash refuses an anchor
claim across restarts; a Loaded block with a NON-EMPTY stash is CORRECT after a restart (the
self-heal resets to Empty only when the stash is truly empty), and the volatile display prop
respawns from the persisted contents shortly after the section loads (the unattended pass's
hydrate walk over loaded chunk sections, budgeted per tick) with the first-interaction call kept
as belt and braces (`StationService#respawnDisplayIfMissing`). The same pass drives UNATTENDED
processing (decision 90, `Work.Unattended`): a custody-loaded station keeps settling its
conversions on world game time with nobody engaged, and the accrued cycles' rolls/contributions
pay out at gather to the gathering player (`StationUnattendedGatheredEvent`); see
`station/CLAUDE.md`'s unattended section.
The display prop's ref + `NetworkId` live in the volatile `displayByBlock` side map (a NetworkId
is per-world, never boot-stable). `StationCustodyBreakSystem` gained a nested `Environment`
sibling over `EnvironmentBreakBlockEvent` (fired INSTEAD of `BreakBlockEvent` for fire/physics/
unattributed explosions) so a destroyed block never leaves a stash behind - same drop-once funnel,
no attribution. `Limits.MaxCustodyClaimsPerWorld` retired for the per-section
`Limits.MaxStashesPerSection` (the old leaf decodes into a warn-only slot; the settings fold names
the replacement). The deprecated `World.getBlock`/`getBlockType` call family left `StationService`
for zc's `BlockOps` (the block-gone fallback comparand is now the block-TYPE id string,
`StationSession#startBlockTypeId`). Gates: `CustodyPersistenceTest` (the payload round trip in
station vocabulary through `BlockRecordSection.buildCodec`), the stash-backed
`StationRefundLedgerTest` cases, `StationCustodyBreakSystemTest`; the live chunk save/load round
trip and the `Unique` stack's in-game survival are SMOKE-OWNED (a bare unit JVM can init neither a
`ChunkStore` section nor an `ItemStack` - the established zc boundary).

Design
authority (scope-2): `../../.claude/research/raw/rpg-stations-scope2-unified-design-2026-07-23.md`
+ decisions 33-41 in `../../.claude/research/rpg-stations-extraction-design.md` (BINDING over
everything below that conflicts). Phase 1/2 design authority:
`../../.claude/research/raw/rpg-stations-unified-design-2026-07-21.md`
(grounded by the decision log `../../.claude/research/rpg-stations-extraction-design.md` and the
adversarial critique `../../.claude/research/raw/rpg-stations-design-critique-2026-07-21.md`, ALL
adopted fixes binding). Origin plan: `../../.claude/plans/interactive-stations.md` +
`../../.claude/plans/work-stations-mod-extraction-prompt.md`.

## 0.1.0 release scope (maintainer ruling, 2026-08-06; re-affirmed 2026-09-02, superseding the ruling-7 amendment of the 2026-09-01 multiplacement round) - READ BEFORE TOUCHING SHIPPED CONTENT

**The first public release is `0.1.0`, not `1.0.0`, and ships the SAWMILL ONLY** (the original
2026-08-06 ruling; the 2026-09-01 round's ruling 7 briefly widened the set to include the
cooking-pit exemplar, and the 2026-09-02 ruling supersedes that amendment and returns the release
to Sawmill-only). The ENGINE is unchanged and complete - multiblock structure patterns, custody
sockets, custody persistence, doneness, unattended processing and the api all ship in code; only
the shipped default CONTENT set is narrowed.

- **Held back, not deleted:** `Stations/CookingFire.json`, `Stations/CuttingBoard.json` (+
  `Actions/PrepFish.json`, `Emote/RPG_Emote_Knife.json`), `Stations/MountSpike.json`,
  `NPC/Roles/RPG_Performer_Spike.json`, and (2026-09-02) the whole cooking-pit family - the
  `Patterns/CookingPit.json` structure, `Stations/CookingPit.json` with its Grill/Stew layering
  over `rpgstations:socket_filled`, the `RPG_Station_CookingPit` + `RPG_Station_Cooking_Pot`
  blocks, `RPG_Food_Hearty_Stew`, and `RPG_Station_CookingPit_Use` - each station with its own
  Item + RootInteraction, all moved into **`unreleased/`** (a byte-exact mirror of
  `src/main/resources`, outside every Gradle resource root). `unreleased/restore.ps1` moves any
  group or all of it back in one command;
  `unreleased/README.md` is the full inventory + rationale. **Do not re-create any of this content
  from scratch - restore it.** The cooking-pit family keeps its full parity gate while held:
  `station/HeldCookingPitPatternTest` decodes the held files directly (and
  `asset/ShippedAssetDecodeTest` scans the `unreleased/` mirror too), so a restore ships
  pre-verified.
- **Every `.lang` key STAYED shipped** in all 9 locales (an unreferenced key is invisible at
  runtime, and holding them back would have risked translation work). `i18n/RpgStationsLangKeys`
  and `LangFileIntegrityTest` therefore needed no change in either direction.
- **`/rpgstations npcspike` is unwired** (the field, dispatch case, and method were removed from
  `command/RpgStationsCommand`); `command/NpcPerformerSpike.java` stays in git, unreferenced.
- **The `api` artifact is NOT frozen.** The freeze was always scoped to a 1.0.0 release, so at
  0.1.0 the extension surface may still change. Anywhere this router or the code says
  "frozen once 1.0.0 releases", that condition has NOT been met yet.
- **The sibling `content-packs/skill-stations-pack` moved in lockstep** (also renumbered to
  `0.1.0`, manifest floor `"Ziggfreed:RpgStations": ">=0.1.0"`): its Anvil, `AnvilWeaponPool`,
  `CookingProgression`, and the `Smithing`/`Cooking` custom skills are under its own `unreleased/`.
  Its `CookingProgression` targets THIS jar's `cookingfire`, so the two sides restore together.

## Build

```powershell
cd 'D:\dev\business\hyMMO\additional-mods\rpg-stations'; .\build.ps1
.\build.ps1 -Install:$false     # build only
.\build.ps1 -ModsDir <path>     # explicit install target (else $env:HYTALE_MODS_DIR)
```
Produces `build/libs/RpgStations-<version>.jar` (**version is `0.1.0`** - the maintainer-set first
public release, a deliberate Sawmill-only scope; see the "0.1.0 release scope" section below) and
copies the runtime jar into the Hytale `Mods/` folder. `.\gradlew.bat
build`/`test` work too. The root hyMMO `rebuild.ps1 -Mods` (or `-Jar -Mods -Packs` for the full
stack) drives this mod's own `build.ps1` alongside every other `additional-mods/` sibling,
dependency-ordered (`ziggfreed-common` first).

**`ziggfreed-common` is the ONLY dependency** (`manifest.json` `Dependencies:
{"Ziggfreed:ZiggfreedCommon": ">=2.0.0"}`; `build.gradle` `compileOnly` + `testImplementation`
against the sibling submodule's built jar, the sibling-relative path pattern -
`${rootDir}/../ziggfreed-common/build/libs/ZiggfreedCommon-${ziggfreedCommonVersion}.jar`). **NO
reference to any other mod's jar anywhere**, and that is load-bearing, not incidental: RpgStations
is optionally listened to by other mods and is never a consumer of one. A `build.gradle` line that
would reverse that arrow is the failure this rule exists to prevent.

## Layout

```
settings.gradle / gradle.properties / build.gradle   RpgStations root module + the api submodule
build.ps1                                             build + auto-install (self-locating, pins RpgStations-<version>.jar from gradle.properties)
api/                                                   the extension-surface (freeze lands at 1.0.0; NOT frozen at 0.1.0)
  build.gradle                                         java-library, archivesName 'rpg-stations-api', BUNDLED into
                                                        the runtime jar AND builds standalone as
                                                        rpg-stations-api-<version>.jar for a consumer's compileOnly link
  src/main/java/com/ziggfreed/rpgstations/api/         see api/CLAUDE.md
src/main/resources/
  manifest.json                                        Group Ziggfreed, IncludesAssetPack:true, ServerVersion >=0.6.0-pre.13 <0.7.0 (Update 6)
  Server/RpgStations/{Stations,Actions,Patterns,Flairs,Extensions,Settings}/
                                                        the six Pattern A asset stores this mod registers
  Server/ZiggfreedCommon/Lootables/                      the Sawmill's loot tables (the SHARED library's store)
  Server/Item/{Items,RootInteractions}/                 the jar's OWN default blocks + their RootInteractions: the Sawmill
                                                        (+ its trophy hatchet) and the shared RPG_Station_Retrieve
                                                        (the cooking-pit family's blocks and Use chain are held
                                                        under unreleased/)
  Server/Drops/                                         the standalone Sawmill's native-namespace drop tables
  Server/Entity/Effects/RPG/                             RPG_Station_Hold.json (the effect-mode movement-lock effect)
  Server/Languages/<bcp47>/                             rpgstations.lang (all 9 locales) + native items.lang/avatarCustomization.lang
  Common/UI/Custom/Pages/                               RpgStationSummary.ui (the session-summary panel) + RpgStationPicker.ui/RpgStationPickerTab.ui (the sneak+F recipe picker)
src/main/java/com/ziggfreed/rpgstations/
  RpgStationsPlugin.java     JavaPlugin entry: injects the api singleton, registers the built-in
                             rpgstations: factors, this mod's own asset stores + their catalog folds,
                             the rpg_station_use interaction, the frame-drain + damage-interrupt
                             systems, the death/disconnect teardown hooks; shutdown() -> stopAll
  api/impl/                  see api/impl/CLAUDE.md - the concrete registry/event-dispatch impl
  asset/                     see asset/CLAUDE.md - StationAsset/ActionAsset/ExtensionAsset/FlairAsset/RpgStationsSettingsAsset/Presentation/Requires/Conditions codecs
  station/                   see station/CLAUDE.md - the session engine (THE big package; the hard-won engine rules live here)
  loot/                      see loot/CLAUDE.md - StationLootEngine/StationRewardKinds/OutputItemResolver/CommandRewardExecutor
                              (the loot MODEL + evaluator + Lootable/RollPool stores are ziggfreed-common's)
  command/                    see command/CLAUDE.md - RpgStationsCommand (/rpgstations camera|validate, admin-gated)
  interaction/                see interaction/CLAUDE.md - StationUseInteraction (the rpg_station_use RootInteraction handler) + StationRetrieveInteraction (rpg_station_retrieve, the press-F custody retrieval handler)
  pages/                      see pages/CLAUDE.md - RpgStationPickerPage (the sneak+F recipe picker) + PickerCategories
  ui/                         see ui/CLAUDE.md - StationSummaryHud (extends common's KeyedCustomHud)
  i18n/                       see i18n/CLAUDE.md - RpgMsg (the rpgstations. prefix wrapper over common Msg) + RpgStationsLangKeys
  util/                       Log (this mod's OWN guarded logging facade over RpgStationsPlugin.LOGGER - never another
                               mod's) + Permissions (OP-when-permissions-off else "rpgstations.admin") + ItemGrantUtil
                               (this mod's policy wrapper over ziggfreed-common's InventoryGrant, adding only
                               the drop-at-block fallback)
```

## Two engine traps that each cost a boot (read before touching drops or spawning entities)

Both were found the hard way, and neither is visible from the authored JSON or the call site.

1. **Spawning an entity from inside a station cycle throws.** `store.addEntity` fails with
   `IllegalStateException: Store is currently processing!` when called from a system, and the cycle
   drain and the interaction handler are both systems. The throw lands in a `catch` and the entity
   silently never exists - which is how every overflow ground-drop was destroyed while the log said
   only `STATION drop-at-block failed`. **Spawn through a `CommandBuffer<EntityStore>`**, threaded
   from the call site (`runRealCycle` and `StationStepContext` both already carry one).
   `util.ItemDropUtil` and `station.StationCustodyDisplay` are the two shipped examples.
2. **An `ItemDropList` whose container tree holds only `Droplist` references fails validation** with
   `Container must have something to drop!`, and a failed drop list takes the WHOLE mod's load down.
   `ItemDropList`'s validator calls `container.getAllDrops(...)` and fails on an empty result, while
   `DroplistItemDropContainer.getAllDrops` returns the list unchanged when its target is not
   resolvable yet - which during validation it usually is not. **Pair every `Droplist` with at least
   one concrete `Single` somewhere in the tree**, exactly as all 32 vanilla tables that use one do.

**A grant that reports what it did not deliver is the same class of bug.** `ItemGrantUtil.grant`
returning `FALLBACK` only means a drop was ATTEMPTED; use `grantOrDrop` when the answer decides
whether to count, notify, or summarise the item, or a failed drop gets reported to the player as a
reward they never received.

## Conventions (this mod's own; hyMMO's root CLAUDE.md does NOT auto-apply)

- **COMPLETELY PROGRESSION-AGNOSTIC (maintainer edict, 2026-08-05, supreme over everything below).**
  See the dedicated section below; it is the first thing to check before adding any leaf, event, or
  registry.
- `@Nonnull`/`@Nullable` on params; log through `util.Log` (info/warn/severe/fine, `Throwable`
  overloads, guarded `try/catch(Throwable)` so a unit-JVM without a Hytale log manager never
  crashes a test) - never a raw `RpgStationsPlugin.LOGGER` fluent chain outside `Log` itself.
- PascalCase upper-first codec keys; nested sub-object groups, never flat prefixed keys; every
  leaf `appendInherited` for native `Parent` reuse. Content ships as `Server/RpgStations/*.json`
  (Pattern A - the codec IS the schema).
- **A shipped asset `$Comment` is a TIP or an EXPLANATION for the server owner / pack author
  reading the file, never a record of how it came to look this way.** These ship inside the jar.
  Write what the asset DOES, what each number means in game, how to tune it, what to watch out for.
  NEVER write authoring history or the decision behind it: no "X was removed/retired/renamed", no
  "this used to live in Y", no "supersedes Z", no "we chose A over B", no reason-we-split-this-file
  narration. **If a sentence would make no sense to someone opening the file for the first time with
  no memory of any prior version, cut it.** Rationale for a rejected alternative belongs in the
  commit message or `CHANGELOG.md`. Forward-looking guidance the reader can act on is welcome
  ("override this file by id to re-tune it", "author a fraction for a half-step tier") - phrase it
  as advice, not as a decision already taken. hyMMO's `CommentHygieneTest` scans this mod's shipped
  resources and fails ITS build on the catchable phrasings.
- All display text via localization keys through common `i18n.Msg`, wrapped prefix-free by
  `i18n.RpgMsg` (`rpgstations.<key>` against `rpgstations.lang`); no em-dashes anywhere (code,
  comments, lang, docs). Nothing generates the `.lang` files - they are authored directly per
  locale, the same way the MMO Skill Tree authors its own; `i18n.LangFileIntegrityTest` (leg 7A) fails the build on
  a placeholder mismatch, an em-dash, or a duplicate key, scoped to whatever locale dirs exist
  (all 9 locales today; a further locale fan-out needs no test change).
- Orthogonal knobs, not modes; a union `Type`/`Surface`/`Trigger` discriminator between genuinely
  different code paths is not a mode.
- DRY: shared codecs (the `Conditions` gate leaf, `Roll`, `Presentation`) are ONE type reused
  everywhere they apply, never duplicated per consumer - and where `ziggfreed-common` already owns
  a primitive (the factor vocabulary + its condition leaf), this mod adapts to it rather than
  keeping a parallel copy.

## The progression-agnosticism paradigm (BINDS EVERYTHING)

**This engine carries no progression vocabulary at all.** XP, skills, levels, and every other
progression concept belong to whichever mod owns them and must NEVER appear in this mod's schema
keys, api types, engine identifiers, validator ids, lang values, or shipped jar JSON - **not even
as an uninterpreted declaration the engine merely forwards.** Forwarding-without-interpreting is
explicitly NOT a defense: the pre-1.0.0 `Work.Xp`/`XpAsk` shape was retired on exactly that
reasoning, having been defended on exactly that argument.

**One vocabulary, two directions.** The extension paradigm is one shape, mirrored. A content asset
names a namespaced id; some other mod owns what it means.

| | READ - factors | WRITE - contributions |
|---|---|---|
| authored leaf | `{"Factor": "<ns>:<id>", "Param": "<opaque>"}` | `{"Channel": "<ns>:<id>", "Param": "<opaque>", "Amount": <double>}` |
| asset type | common's `FactorFormula.Term` (a weighted read), `asset/Conditions` (common's `FactorCondition` gate) | `asset/Contribution` |
| api type | `api/StationFactorProvider` + `api/FactorContext` | `api/StationContribution` |
| registry | `FactorRegistry.register(id, provider)` | `ContributionChannelRegistry.declare(id)` |
| api accessor | `RpgStationsApi.factors()` | `RpgStationsApi.channels()` |
| editor dropdown | `rpgstations:factors` (LIVE) | `rpgstations:channels` (LIVE) |
| validator | `UNKNOWN_FACTOR` (WARN, fail-open) | `UNKNOWN_CHANNEL` (WARN, fail-open) |
| engine knowledge | resolves the id to a `double` | **never resolves anything** - forwards it on the event |

**The engine owns built-in FACTORS because it can compute them, and owns ZERO built-in channels
because it interprets none.** That asymmetry is the whole ruling in one line.

Scaling is decided by the authoring SITE, never a flag on the record:
`Work.PerCycleContributions[]` posts every completed cycle and IS scaled (the action's own
`ContributionScale` ladder, pre-applied by the engine before dispatch, and pre-scaled by
`Work.Idle.Fraction` on an idle cycle); an `rpgstations:contribution` loot reward posts once and
is VERBATIM, inheriting neither. Same record, two documented meanings, no mode.

**Three rules resolve every remaining case.**
1. **Schema / api / engine / validator / lang / jar JSON: a foreign mod's name, id, type, or
   domain concept may NEVER appear.** Not a key, not a value, not a branch, not a shipped
   `.documentation()` string (those land in the jar schema AND the generated schema reference,
   `SCHEMA.md`).
2. **Javadoc and `.documentation()` may name the ENGINE's own namespaces** (`EntityStatType`,
   `DamageCause`, `ItemDropList`) and this mod's own ids. For a third-party example, use the
   fictitious **`yourmod:`** namespace.
3. **The DOCS may keep ONE short "Known integrations" listing** naming a consumer with an
   outbound link. They may NOT host that mod's reference tables.

**Enforcement is a test, not a habit.** `src/test/java/com/ziggfreed/rpgstations/MmoAgnosticismTest`
scans `src/main/java`, `api/src/main/java`, `src/main/resources` AND the in-repo `docs/` prose
source (every `.java`, `.json`, `.lang`, `.ui`, `.txt`, `.md`, `.mdx`, `.tsx`, `.ts`) for a
forbidden-token regex and FAILS THE BUILD on any hit. The allowlist holds exactly ONE entry and
never grows: a line in `docs/integrations.md` may name the companion progression mod and its
outbound link, nothing else. `src/test` is deliberately out of scope (fixture values are
author-owned and ship nothing), and three prose surfaces are skipped BY FILENAME wherever they live
- `CHANGELOG.md`, `CURSEFORGE.md`, and these `CLAUDE.md` routers - because they are the surfaces
that STATE this rule and this mod's history, so they must be able to quote the retired vocabulary
while explaining why it is retired. **A leak in a
convenient comment is how the vocabulary creeps back in, one "for context" sentence at a time** -
that is the entire reason the test scans comments too.

When a new leaf / event / registry is progression-shaped, genericize it BEFORE it lands. A generic
primitive several mods would share belongs in `ziggfreed-common` per the root lift paradigm - but
`StationContribution`/`ContributionChannelRegistry` deliberately stay api-local until a genuine
SECOND consumer appears (see `api/CLAUDE.md`'s promotion trigger).

**Pack-authored VALUES are NOT covered by this.** A pack naming its own mod's ids in its own
content (`{"Factor": "mmoskilltree:station_luck"}`, a `Budgets` entry reading that mod's stat
channel) is CORRECT and untouched: this rule governs the ENGINE's vocabulary, not a pack's content.

**The shared `RPG_Station_Sawmill` block id**: both this jar AND the sibling stations pack ship a
Sawmill block under the exact SAME item id, `RPG_Station_Sawmill` - the pack's copy overrides the
jar default purely through native asset-pack LOAD ORDER (`defaults < pack`), not a different id.
The station id itself (`sawmill`, the `StationAsset` json's filename lowercased) is independent of
the block item id. See `interaction/CLAUDE.md` for the `rpg_station_use` interaction type both use.

## The extension surface (api/, live)

Package `com.ziggfreed.rpgstations.api` (+ `.api.event`), the freeze-at-1.0.0 (so NOT yet frozen) contract
between this engine and any mod that wants to hook it. Split by shape, per the native-events rule:
**observe-only moments are native Hytale events** (`StationSessionStartedEvent`/
`StationCycleCompletedEvent`/`StationSessionCompletedEvent`/`StationToolBrokeEvent`/
`StationEnhanceCompletedEvent`/`StationUnattendedGatheredEvent`, POJOs
`implements IEvent<Void>`, dispatched `HytaleServer.get().getEventBus().dispatchFor(...)` +
`hasListener()` on the world thread, fired from `station.StationEvents`); **request/response
points are typed registries** on the static `RpgStationsApi` holder (`FactorRegistry` - the one
extensible numeric vocabulary conditional lootables/`Requires` gates evaluate over, this mod's
stable surface over `ziggfreed-common`'s shared factor core;
`FlairUnlockRegistry` - union of every registered per-player unlock provider, seeded at setup
with the built-in `station.ZigFlairUnlockProvider` reading ziggfreed-common's persisted
unlocked-flair component;
`SummaryEnricherRegistry` - extra ledger rows + a themeable decorate hook on the summary panel;
`ValidationHookRegistry` - a foreign vocabulary's owner registers its own content checks, run
inside this engine's own full validate pass, advisory and never blocking; `ContributionChannelRegistry`
- the declaration-only channel set, see the READ/WRITE table above).
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
- **Leg B (LANDED, this mod; the "whole-GROUP override of a station-level default" model this leg
  introduced was ITSELF later superseded by the action-first restructure above, which deletes
  station-level group inheritance outright - an action reads its own groups or its `Ref`/`Parent`
  base, nothing else)**: multi-action stations (design 9.1) - a new `StationAsset.Actions`
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
  empty + a held matching stack places (or a repeat press tops up) into a per-block
  claim (`station.StationCustodyClaim`, keyed the SAME `"<worldUuid>:<x>:<y>:<z>"` blockKey
  `StationService` already used for session exclusivity; SINCE the 2026-09-01 custody-persistence
  wave the claim is a view over the block's chunk-persisted stash, see the custody-persistence
  paragraph above), loaded + owner F engages sourcing the
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
  flips (the kweebec shrine-furnace precedent) are HINT-ONLY
  this leg (mechanism-first ruling; visuals land in a later leg) and self-heal (this leg's
  "custody is never persisted, a crash loses it" posture was REVERSED by the 2026-09-01
  custody-persistence wave: the self-heal now settles against the surviving stash). The shipped
  sawmill (both the jar
  default and the pack's own copy) MIGRATED to placed input in this leg - `Custody:
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
  to the constructor's `Vector3f attachmentOffset` parameter (an honest spatial XYZ offset; the
  engine formerly declared this same offset as a `Rotation3f`). Every entity mount applies the SAME hold effect effect-mode uses plus
  a per-heartbeat anchor snap-back to defeat the native WASD-steers-the-anchor behavior;
  `DismountOnMove` (default true) runs the same origin-delta walk-off check effect-mode uses (the
  entity-mount controller has no native auto-dismount). (A `Steerable` escape hatch that skipped
  both mitigations was dropped in the Update 6 cycle - nothing shipped authored it and the combo
  was never verifiable in-game.) Because this path never populates the
  client's `MountedUpdate.Block` field, the mount mine infers the player renders STANDING by
  construction - in-game-unverifiable from server source alone, the FIRST phase-2 smoke item.
  `StationValidator` gained `MOUNT_FACE_BLOCK_CONFLICT` (generalized from the old
  `SEAT_FACE_BLOCK_CONFLICT`), `UNKNOWN_MOUNT_SURFACE`, and `MOUNT_ENTITY_GROUP_IGNORED`
  (all warn-only, per the maintainer ruling). See `station/CLAUDE.md`'s
  Mount bullet for the full file-by-file detail.
- **Leg E (LANDED, this mod + a consumer bridge + the pack)**: the anvil arc - the
  `Stamp` step un-reserved (`asset.StationStep.Stamp{Reagents,Durability,Stats}`, nested
  `Stats{Pool,Entries,Picks,Unique,Caps}`; **scope-2 (wave 2) re-shaped `Caps` onto a weighted
  `Budgets[]`/factor-term vocabulary - see
  `station/CLAUDE.md`'s anvil-arc bullet and `asset/CLAUDE.md`'s `StationStep` bullet for the
  current shape**), a
  roll-pool store (now the shared `Server/ZiggfreedCommon/RollPools/*.json` -> `RollPoolConfig`)
  + the shared `asset.StatRollEntry` codec both `RollPool.Entries` and inline `Stats.Entries` use,
  the PURE stamp roll + cap engine (weighted-pick/`Picks`/`Unique` + the cap-composition MIN
  rule; now the shared `loot.stamp.StampCapEngine`), and
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
  `Work.Looping: false` session completion (`StopReason.RITUAL_COMPLETE`) wired into
  `dispatchProgram`. **A genuine correctness fix along the way**: `StationCustodyClaim` gained an
  optional `uniqueStack` (the REAL placed `ItemStack`, metadata intact) for a `MaxQuantity: 1`
  placement - the pre-existing count-only model would have silently reset a placed weapon's
  durability/prior enhancements to a bare fresh stack on auto-return, an item-loss-equivalent bug
  the bulk sawmill-logs case never exercised. See `station/CLAUDE.md`'s Stamp bullet for the full
  file-by-file detail and `api/CLAUDE.md` for the stamper contract's current home;
  the shipped Anvil content lives in its own pack's repo.
  **Deviations from the design doc's literal prose** (all evidence-grounded, see each site's own
  javadoc): the Convert action matches vanilla `Metal_Bars` (not the doc's placeholder
  `Metal_Ingot`); the anvil's Tool gate uses `Ids: ["Tool_Hammer_Crude","Tool_Hammer_Iron"]` (no
  `Tags.Family:["Hammer"]` exists on the real vanilla hammer items); the shipped `PerStat` cap key
  is a real registered stat id from the pack's own consumer mod, not the doc's placeholder;
  the ritual's Wait steps used `DurationMs` as originally shipped (`Beats` stayed schema-reserved/
  unimplemented - the doc's own example would have hard-failed the ritual at its first step);
  **scope-2 (wave 2) retired the `Wait` type entirely - the anvil's strikes/settle now author
  plain `Duration{Ms}` beats on the orthogonal-phase `StationStep`, see `station/CLAUDE.md`'s
  step-engine section**; the placeholder empty `Roll`
  step in the doc's example was dropped (the Stamp step's OWN roll engine already covers stat
  rolling, a second roll layer added nothing); the stamper stayed a lean 2-method
  `inspect`/`apply` contract rather than the doc's literal `StampContext`/`StampResult` shape,
  because the roll/cap math sits outside it and the write boundary needs nothing richer (the
  shared-loot re-base above moved that contract to `ziggfreed-common`'s `loot.stamp.Stamper`, where
  it kept exactly that shape). Whatever progression a pack layers on top of the anvil is that pack's
  consumer mod's business and lives in that mod's own repo, never here.
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
  merge point rather than an inline-only view that would now be incomplete. A registered
  `FlairUnlockProvider` is UNTOUCHED by this leg (it only ever answered "which ids has this player
  unlocked", which was always vocabulary-agnostic). See `station/CLAUDE.md`'s "Loot +
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
  phase-2 leg added a new player-facing UI string), updates this router tree + the sibling repos'
  docs the earlier legs left slightly behind, and assembles the PHASE-2 SMOKE CHECKLIST as a
  clearly marked section in `../../.claude/plans/work-stations-mod-extraction-prompt.md` (the
  standing-mount spike, custody place/return incl. relog, state-dependent F-hints, placed-item
  visuals, the anvil Convert+Enhance ritual end to end incl. cancel custody-return and budget caps,
  multi-action selection UX, plus the still-pending phase-1 parity items). The actual
  in-game confirmation pass is still batched/pending, alongside phase 1's own parity gate (design
  section 11) - neither has run yet; both are one maintainer smoke session away.
