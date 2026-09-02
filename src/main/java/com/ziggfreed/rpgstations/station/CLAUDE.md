# station/ - the session engine (interactive work stations)

Router for `station/`, THE big package in this mod: the diegetic work-loop session machine. Press
F on a station block -> camera pulls third-person (or the player mounts the block as a seat, or a
puppet spawns and performs the work), the work animation plays per swing, items convert per cycle
or per an authored step program, loot rolls through `loot/`, and authored contributions forward as
`StationContribution`s whichever mod owns the named channel interprets. Design authority:
`../../../../../../../../../.claude/research/raw/rpg-stations-scope2-unified-design-2026-07-23.md`
(sections 2-3, decisions 33-41 in `../../../../../../../../../.claude/research/rpg-stations-extraction-design.md`),
superseding the phase-1/phase-2 design for everything the scope-2 redesign touches.

## WAVE BOUNDARY (scope-2 wave 3 landed - the multi-station seam EXECUTES)

The scope-2 schema (`../asset/CLAUDE.md`) carries the FULL multi-station field set; as of wave 3
the WHOLE set executes:

- **Single-station (waves 1-2, unchanged)**: `Consume`/`Stamp`/`Produce`(`To:"Inventory"`)/`Roll`/
  `Commands`/`Duration`/`Repeat` at the PRIMARY station. Placed-input custody, the anvil's Stamp
  ritual, the sawmill's implicit convert loop, camera/mount/puppet, exit hooks - all unchanged.
- **Multi-station (wave 3, NEW - this leg)**: `StationStep.Walk` (the puppet travels to an anchor
  via `ziggfreed-common`'s `PuppetNav` bounded A* + `PlayerPuppetService.walkTick`/`setWalking`,
  suspend/resume per frame through `StationWalkState`), `StationStep.At` (a step's `Consume`/
  `Produce` custody resolves the anchor's blockKey, not the primary), `Produce.To:"Custody"`
  (cross-station output into the anchor's claim + display), and `ActionDef.Anchors`
  DISCOVERY/CLAIMING (the DERIVED block-item seed below, plus the lazy `knownStationBlocks` index fed
  by interaction warming + `PlaceBlockEvent`,
  bounded ring scan last resort; atomic first-wins claim into the generalized `byBlock` map with the
  gate-m5 own-session/custody precedence). New stop reasons `INPUTS_EXHAUSTED` (repeat-while-inputs,
  design 2.4), `ANCHOR_LOST` (a remote anchor broken mid-program, design 2.6), `PATH_BLOCKED` (a
  walk-step-entry re-solve failed). The `iterationConsumed` refund ledger (design 2.5/M1) refunds an
  in-flight iteration's consumed inputs at `stop()`, cleared by any `Produce.To:Custody` (refund and
  custody-return mutually exclusive). The `WAVE3_PENDING` validator warn is GONE; the anchor/walk
  validator checks (`ANCHOR_STATION_UNKNOWN`/`WALK_TARGET_UNKNOWN_ANCHOR`/`STEP_AT_UNKNOWN_ANCHOR`/
  `WALK_REQUIRES_PUPPET`) stay as the live discovery-time coverage. See `StationAnchors`
  (pure cores), `StationWalkState`, `StationBlockPlaceSystem`, and the anchor section of
  `StationService`.

## Content + catalogs

- **Stations**: [`asset.StationAsset`](../asset/CLAUDE.md) (`Server/RpgStations/Stations/*.json`)
  folds into [`StationCatalog`](StationCatalog.java). Ids are lowercase (canonicalized at
  decode). This jar ships its OWN default Sawmill (`Server/RpgStations/Stations/Sawmill.json`,
  standalone-playable with the built-in `rpgstations:` factors + the `SawmillFinds`/`SawmillTrophy`
  lootables); the sibling stations pack adds its own find, output and masterwork lootables as an
  ADDITIVE `Extensions/*.json` (below) rather than a full-file station override, and separately
  REPLACES `SawmillTrophy` by id to make the chase luck-scaled. **The jar Sawmill
  declares NO `Work.PerCycleContributions` at all** - jar-layer content is progression-free by
  design, so a pack OWNS the sawmill's contributions outright and there is no base entry for an
  extension to collide with. The station's tool ladder lives where its EFFECT is visible: ONE INLINE
  `Bonus.Roll` (a `Ladder` over three weighted tool factors granting `Grants.OutputItems` per
  floor - the mid rung's `1.5` is the FRACTIONAL half-step, one plank always plus a second half the
  time) plus a
  parallel `ContributionScale` ladder describing the identical tool curve for whichever mod adds
  contributions - rather than in a separate baked curve
  - see `../../../../../../../../../CONTENT_PACKS.md`'s Station authoring section for the authoring guide
  (brief reference only; do not duplicate it here). That one is the action's OWN roll;
  `Bonus.Lootables` pulls in one roll from each of `SawmillFinds`, `SawmillTrophy` and
  `SawmillMasterworkFinds` (all below), so the live per-cycle pass evaluates four.
  **A second inline roll, a small flat windfall `Chance` scaled by tool power, was authored here and
  REMOVED as needless complexity** - it proc'd on the same axis the ladder already priced, so it
  added variance without adding a decision and made the pair read worse than the ladder alone. The
  shape itself stays good authoring and survives as a worked example in `docs/loot-and-factors.md`.
  **Do not re-add it here**, and when authoring a new station reach for a bare `Chance` roll only
  for an outcome its ladder genuinely cannot express.
  **The yield ladder's five floors** are `11`/`22`/`33`/`40`/`50` paying `1`/`1.5`/`2`/`3`/`4`
  OutputItems on top of `Yield.Base` 1, and `ContributionScale` crosses at the SAME five Mins with
  `2.0`/`2.5`/`3.0`/`4.0`/`5.0` - the two curves track rung for rung on purpose, trophy rung
  included. Over the vanilla hatchets: Wood 10.6 / Crude 10.55 reach nothing (1 plank), Copper 11.3
  (2), Iron 22.3 (2.5 average, the fractional rung), Thorium 33.5 / Cobalt 34.0 / Adamantite 34.5
  (3), Onyxium 40.9 / Mithril 45.5 (4). **The 50 floor is unreachable by any FORGEABLE tool** - only
  the jar's own drop-only `RPG_Tool_Hatchet_Sawmiller` (scoring 55.55) or a better modded tool gets
  the fifth plank, which is what makes the trophy the bench's one endgame upgrade. Any doc claiming
  "up to four" predates the trophy.
  **The Sawmill's three shipped lootables are split by REPLACEABILITY, not by theme - ONE ROLL
  EACH.** A `Lootable` folds by id and a later layer replaces the whole FILE (the fold is a `putAll`), so **which rolls share a file decides what a layering mod must inherit in order to
  re-tune one of them.** Any roll another mod is expected to reshape therefore gets its own file.
  **Keep that split when adding a station: one file per independently replaceable concern**, and
  when in doubt split, since merging later is free and unpicking a shipped id is not. The shipped
  consumer is the MMO stations pack, whose own `SawmillTrophy` replaces the flat chase below with a
  luck-scaled 1-in-3000 one and costs one small file to do it.
  **`SawmillFinds` (`Lootables/SawmillFinds.json`) is the tool-driven find roll**: one Condition,
  `hytale:tool_quality >= 2`, a `Chance` of `Base` 0 summed from the three tool factors
  (quality x16.2, item level x0.162, power x1.62) and clamped at 90, and a `Ladder` over
  `cycle_count` plus `tool_quality` (weight 5) whose 5/25/50 floors grant the jar's own
  `Drops/RPG_Station_Sawmill_T{1,2,3}` tables (the upper two floors name the `rare_find` cue, kept
  honest by the smart-cue rule since each table authors its own empty weight). The tool decides how
  often; the session decides how deep.
  **`SawmillMasterworkFinds` (`Lootables/SawmillMasterworkFinds.json`) is the T4 find tier** - gated
  on the TROPHY's own axes (`tool_quality >= 5`, `tool_item_level >= 50`, `tool_power >= 0.55` - quality and power each one
  notch above `SawmillTrophy`'s own gate, item level held at the trophy's 50, and the set is
  unreachable by any forgeable vanilla tool),
  no cycle GATE since the trophy already proved that loyalty - the cycle count drives the CHANCE
  instead (15 percent rising 0.5 a cycle, `Clamp.Max` 75, so the cap lands at cycle 120 and a
  ten-minute session only just reaches it). Deliberate contrast with `SawmillFinds`, where the TOOL
  drives frequency and the session drives depth: here the tool has already done its work by
  unlocking the roll, so the session is the only variable left. Pays `RPG_Station_Sawmill_T4`, the
  one find table with no empty entry.
  **`SawmillTrophy` (`Lootables/SawmillTrophy.json`) is ONE roll**, the chase itself: all three tool
  axes at the vanilla Mithril hatchet's own values (`tool_quality >= 4`, `tool_item_level >= 50`,
  `tool_power >= 0.5`) plus `cycle_count >= 5`, a visible `Chance.Base` of `0.04` (1 in 2500)
  with NO factors - deliberately the plainest possible curve, since a luck-scaled one reads channels
  this mod knows nothing about - granting inline through its own top-level `Grants.Commands`
  (`give {player} RPG_Tool_Hatchet_Sawmiller`) under its own top-level `Cue: "cue:trophy"`, the open
  cue-namespace moment id the action's `Moments` map then dresses. It is the
  shipped exemplar of the roll-level cue, and a command grant always counts as produced so the
  fanfare can never fire dry. `RPG_Tool_Hatchet_Sawmiller` itself is a drop-only Legendary
  masterwork Mithril copy (500 durability, Woods power `0.55`, explicit `Tags.Family: Hatchet`),
  authored with NO `Parent` and NO `Recipe` deliberately - a Parent off the vanilla Mithril hatchet
  would inherit its forge recipe and make the chase pointless.
  **The jar's Sawmill BLOCK is craftable, jar-only.** `Item/Items/RPG_Station_Sawmill.json` authors a
  `Recipe` at the Crafting `Workbench` with `RequiredTierLevel: 2` (1 `Tool_Hatchet_Crude` + 1
  `Wood_Trunk` + 4 `Wood_Planks`, both materials as native resource-type families). A pack shipping
  its own same-id block replaces the WHOLE item by load order, so a pack authoring no `Recipe` makes
  the station uncraftable and owns acquisition itself - no flag, no engine branch, pure load order.
  **The jar Sawmill owns the PRESENTATION defaults
  too** (`Puppet` + `Custody.Display`, the maintainer's in-game-tuned values, plus `Work.CycleMs`
  4805): they used to live only in the pack's full-file station override, so retiring that override
  dropped them and the sawmill regressed to a visible seat-mounted player working invisible placed
  logs. They ship here now, and a pack re-skins them through an `ExtensionAsset` `Puppet`/`Custody`
  per-leaf overlay instead of re-owning the whole file.
- **Standalone actions**: `asset.ActionAsset` (`Server/RpgStations/Actions/*.json`) folds into
  `ActionCatalog` (same `AssetStoreRegistrar` + `LoadedAssetsEvent` pattern as every other type).
  An inline `Actions` map entry's `Ref` leaf resolves against this catalog - see the ActionAsset
  bullet below.
- **Extensions**: `asset.ExtensionAsset` (`Server/RpgStations/Extensions/*.json`) folds into
  `ExtensionCatalog` - see its own bullet below.
- **Flairs/Settings**: unchanged catalog shape (`FlairCatalog`, `SettingsCatalog`).
- **Structure patterns**: `asset.StructurePatternAsset` (`Server/RpgStations/Patterns/*.json`)
  folds into `PatternCatalog`, which COMPILES rather than merely stores - see the multiblock
  section below.
- **Lootables/RollPools are the SHARED library's stores** at `Server/ZiggfreedCommon/{Lootables,
  RollPools}/`, folded into `LootableConfig`/`RollPoolConfig` - this mod registers neither. What
  stays station-side is the pass around them; see `../loot/CLAUDE.md`.

## Sessions

[`StationService`](StationService.java) (the biggest class in the mod) owns the
`IDLE -> STARTING -> WORKING -> STOPPING` machine over transient, player-anchored
[`StationSession`](StationSession.java)s (never persisted - no per-player state lives in this mod,
by construction). One entry (`toggle`, from `interaction/StationUseInteraction`'s object-form
`Station` param), one idempotent exit funnel (`stop`), every start-denial a localized toast.
Sessions bucket per world in a `WorldKeyedQueues` (ziggfreed-common) drained by
[`StationFrameSystem`](StationFrameSystem.java) `extends AbstractWorldFrameSystem` (ECS systems
are class-keyed - this is RpgStations' OWN concrete subclass).

## The step engine (design 2.1, decisions 34/38 - REWRITTEN this wave)

The pre-scope-2 engine dispatched a `StationStep.Type` union through a per-type handler table.
**That union is gone.** A step is now an orthogonal-phase record (`../asset/CLAUDE.md`'s
`StationStep` bullet); the engine walks a FIXED phase order per iteration instead of branching on
a discriminator.

- [`StationStepRegistry`](StationStepRegistry.java) is now ONE composite handler walking
  Conditions -> `Walk` -> `Consume` -> `Stamp` -> `Produce` -> `Roll` -> `Commands` ->
  `Presentation`/`Puppet.Clip` -> `Duration` per step, still wrapped in the conditions-gate +
  throw-guard layer (design 9.3/M4's binding fix carries forward: a throwing phase degrades to a
  clean session `stop()`, never crashes the shared per-world frame drain) - never six independent
  per-type entries.
- [`StationStepKernel`](StationStepKernel.java)/[`StationStepSemantics`](StationStepSemantics.java)
  keep the `cast.step` contract unchanged (`isSuspend`/`nextIndex` still wire the `OnConditionFail
  .Goto` branch mechanism); they are agnostic to whether a step is single-phase or multi-phase.
- [`StationStepDecisions`](StationStepDecisions.java) gains the PURE cores this reshape needs:
  `Repeat` resolution (delegates to `StationStep.Repeat#resolveCount`, the caller supplies the
  resolved `factorContribution`), and `Duration` suspend/resume math (reusing the old `Wait`
  step's suspend/resume math verbatim - a `Duration` hold suspends exactly like the retired
  `Wait` type did).
- [`StationStepContext`](StationStepContext.java)/[`StationStepResult`](StationStepResult.java)
  (the per-run bundle + sealed `Success`/`Suspend`/`Skip`/`Fail`) are unchanged in shape.
- [`ImplicitProgram`](ImplicitProgram.java) COLLAPSES to ONE step
  (`{Consume, Stamp:null, Produce, Roll, Presentation}` folded onto a single `StationStep`
  instead of the old four-step `[Consume, Produce, Roll, Present]` array) - byte-equivalent
  behavior (a station with no `Actions`/`Steps` authored runs identically), simpler anchor for
  the phase model. Its `Roll` phase is where the action's own `Bonus` rides on THIS route, which
  is why `dispatchProgram`'s completion-time `Bonus` pass is flagged off for it (see the cadence
  section: one `Cycle` moment per completed pass, whichever program shape ran).
- `StationSession` resume state (`programSuspended`/`programIndex`/`stepDeadlineMs`/
  `activeProgramSteps`) is UNCHANGED this wave - a `Duration` hold suspends/resumes through the
  exact same fields the old `Wait` type used. The design's two further resume fields landed with
  wave 3 beside them: `stepIteration` (the per-step `Repeat` iteration index, so a REPEATING step
  that holds a `Duration` resumes at the right iteration instead of re-running the earlier ones -
  the composite handler is its sole reader/writer) and `walkState` (a `StationWalkState`, non-null
  ONLY while a `Walk` phase drives the puppet toward an anchor, carrying the solved waypoints +
  parametric progress across ticks and cleared the instant the walk arrives or its path blocks).
- **A step combining `Consume` + `Produce` is an ATOMIC transform** (no consumed-without-produced
  window across one iteration) - the design's transactional-edges rule (2.5/decision 38): a step's
  commit (EITHER committed destination, `To:"Custody"` or `To:"Inventory"`) clears the CURRENT
  iteration's consumed ledger, so refund and custody-return stay mutually exclusive per iteration.
  A `Walk` phase can split a `Consume`+`Produce` pair across a suspend, which is exactly why the
  `iterationConsumed` ledger refunds an in-flight iteration at `stop()`.

## Action resolution: Id lookup -> Ref overlay -> extension overlays

**Station-level group inheritance is DELETED.** A station supplies no defaults of its own any
more - `StationAsset` keeps only `Identity`/`Block`/`Requires`/`Flairs`/`Actions[]` (see
`../asset/CLAUDE.md`), so there is no station-wide `Work`/`Recipe`/`Tool`/`Hold`/`Camera` for an
action to fall back to. Every live read goes through [`ActionResolver`](ActionResolver.java), the
ONE choke point, in three layered steps:

1. **Id lookup** - `findAction(asset, actionId)` walks `StationAsset.getActions()` (folded with
   any `Target:{Station}` `ExtensionAsset` appends, `effectiveActions`) for the entry whose
   `effectiveActionId` (its own `Id`, else the `ActionAsset` its `Ref` names, else its array
   index) matches case-insensitively.
2. **`Ref` overlay** - the pure 3-arg `resolve(asset, actionId, refLookup)` core: when the found
   inline entry authors `Ref`, the named `ActionCatalog` entry is the BASE and the inline entry's
   OTHER groups overlay it group-wise, ONE level (the inline entry's own group wins when authored,
   else the `Ref` base's, else neither contributes). A dangling `Ref` resolves as if no `Ref`
   existed (validator finding `ACTION_REF_UNKNOWN`; the entry is still selected and engage falls
   through to the ordinary denial for whatever the inline entry authors on its own - a bare `Ref`
   entry with no other group ends at `ui.station.no_materials` - rather than throwing). This core
   stays catalog-free for unit tests.
3. **Extension overlays** - the live 2-arg `resolve(asset, actionId)` wraps step 2 and layers the
   Action-targeted `ExtensionAsset` per-leaf overlays on top (`Puppet`/`Custody`/
   `ContributionScale` - see the ExtensionAsset section below); identity-preserving, so the
   zero-extension path returns the pure result untouched.

The result is a flat [`ActionResolver.ResolvedAction`](ActionResolver.java) - every accessor a
`station.step` handler or the direct-Java engine path should read, never the raw `StationAsset`/
`ActionDef`/`ActionAsset` group directly once an action id is chosen. `ActionResolver.selectAction`/
`selectActionByFamily` (the diegetic input-matched selection cores) sit BEFORE resolution, not
inside it - they operate on the RAW `ActionDef`s in `effectiveActions`, choosing WHICH action id
to resolve.

**Ordered-array selection, gate-aware.** `StationAsset.Actions[]` is authored order, and authored
order IS selection priority: `selectAction`/`selectActionByFamily` walk front to back and return
the FIRST action whose effective `Select` (its own, or its `Ref` base's) is absent, catch-all, or
matches the held/placed context. `ActionResolver.selectActionsByFamily` (the plural core; the
single form answers its head) returns EVERY match in that order, and
`StationService#selectActionForHeld` picks the first whose own `Requires` gate PASSES, falling
back to the first match when none does - so the engage gate denies that one with the honest
requirements-unmet toast, and a single-action station selects and denies byte-identically to the
pre-gate walk. `Requires` is a "when it applies" concern beside `Select` (the `ActionDef` javadoc's
own grouping), which is what lets the shipped cooking pit layer a vessel-gated Stew over an open
Grill on one block. A station authoring no actions selects nothing and is inert
(`STATION_NO_ACTIONS`). A loaded custody claim already owned by the
player commits to ITS OWN action first, BYPASSING selection (re-pressing F with a different item
held never switches a ritual already in progress; the engage gate still checks the committed
action, which is how a loaded stew whose pot was removed denies instead of running); a
restart-orphan recovery path
(`ActionResolver.selectActionForBlockState`) falls back to matching the block's own persisted
`Custody.States.Loaded` name when neither a live claim nor the held item resolves an action.

**`Requires` ANDing.** `toggle()` checks `checkRequires(asset.getRequires(), ...)` AND
`checkRequires(resolvedAction.getRequires(), ...)` - both must pass. Neither defaults the other:
an action authoring no `Requires` is gated by the station's alone, and a station authoring none
leaves the action's own gate as the only one. Both calls (and the per-candidate selection walk)
share ONE `socketsFilledAt` snapshot per press - the `rpgstations:socket_filled` readings, the
UNION over every action's effective sockets (an Item socket answers by its pile, a Block socket by
`blockSocketSatisfied`'s world read; the first action wins a duplicate id; pure core
`socketsFilledInto`) - carried into the api `FactorContext` as plain data, so the built-in
provider never touches the world itself and every other build site simply omits the readings
(the factor then fails closed there).

**Per-action completion.** The session-end `completion` moment, and a
`Roll{Trigger:"Completion"}` in the action's own `Bonus`, are both read off the RESOLVED action -
there is no station-level completion-loot fallback any more, matching the "no station-level
group" rule everywhere else.

## ExtensionAsset resolution

`ExtensionCatalog.extensionsFor(targetType, targetId, stationId)` is the ONE resolve-at-read gather
point (the `FlairCatalog.effectiveFlairsFor` pattern generalized): given a station/action/lootable/
rollpool about to be used, it collects every folded `ExtensionAsset` whose `Target` resolves to
that type+id AND whose station scope matches the context, then sorts them via
`ExtensionAsset.sortedForApply` (the `(Priority, extension id)` tuple), cached per fold generation
so a hot per-cycle read never re-walks the extension set. One `applyTo*` entry point per payload
sits on top of it (`applyToActionBonus`, `applyToActionContributions`, `applyToActionPuppet`,
`applyToActionCustody`, `applyToActionContributionScale`, `applyToActionConversions`,
`applyToActionSteps`, `applyToActionAnchors`, `applyToStationActions`, `applyToLootableRolls`,
`applyToRollPoolEntries`), each delegating to a PURE merge core that applies the codec's own
documented rules (`../asset/CLAUDE.md`'s ExtensionAsset bullet: additive-only, base-wins key
collisions, append for unkeyed arrays, anchored insertion for `Steps`).

**The station context is a parameter, not an assumption.** A `Target` may SCOPE an Action target to
one station (`{Station, Action}`), so every Action-targeted `applyTo*` takes the station the action
is being read ON as its first argument, and `extensionsFor` keys its cache on that context too - two
stations sharing one `Ref`'d action legitimately see different extension lists, and a single key per
`(type, id)` would hand the second station whichever list the first one warmed. A bare target still
matches every station (including a caller with none); a scoped one only its own.

**Where each one is applied** (there is no decode-only payload): `Puppet`/`Custody`/
`ContributionScale`/`Anchors` layer inside `ActionResolver.applyExtensionOverlays`, so every reader
of a `ResolvedAction` sees them at once; `Bonus`/`PerCycleContributions` at `StationService`'s
per-cycle read sites (`Bonus` through the ONE `effectiveBonus` all three of its read routes
share; `PerCycleContributions` through BOTH `onCycleCompleted`'s forwarded list AND
`contributionParams`, the channel/`Param` projection every `FactorContext` carries - a factor
reading `contributionParams(channel)` must see exactly what the cycle will post); `Steps` in
`StationService.effectiveProgramSteps`, the ONE read of "the program this session
will run" (shared by the dispatch, the engage-time walk-anchor reachability check, and the
per-step-clip detection); `Actions` in `ActionResolver.effectiveActions`; `Conversions` inside
`StationCatalog.resolvedConversions`, before that derivation is cached - which is why
`ExtensionCatalog.fold` AND `ActionCatalog.fold` both call
`StationCatalog.invalidateResolvedConversions()`, since the three stores fold in no guaranteed order
and a layer arriving after the first conversion resolve would otherwise never be seen; `Rolls`
inside `loot.StationLootEngine.resolve`, at the point a
referenced lootable table is read (so a table gains its extended rolls at EVERY reference site - an
action's `Bonus` and a step's `Roll` phase both route through that one resolution, which also hands
back that table's `Pool`); `Entries` inside
`StationStepHandlers.StampHandler.withExtendedEntries`, at the point a `Stamp.Stats.Pool` is read. Those last two read
their catalog per call and derive nothing, so unlike `Conversions` they need no invalidation
companion.

**`Steps` is deliberately NOT merged onto `ResolvedAction.getSteps()`.** That array is what decides
WHICH engine an action runs (authored program vs. the recipe-driven convert loop, branched on at
`toggle`'s viability check and at `runCycle`), so merging insertions in there would let an
extension flip a convert action into a step program and silently skip its conversion check. The
merge happens at the program READ instead, over a base the action itself authored: an insertion can
only add beats to a program that already exists. Composition order
(m7): extensions apply to the `Parent`-resolved target at READ time and do NOT flow down `Parent`
chains; a `Target:{Action}` extension reaches every `Ref` user of that action, a
`Target:{Station}` step-insert applies post-`Ref` to that one station only. Boot log carries one
INFO `EXTENSION_APPLIED` summary line per target, enumerating the CONTRIBUTION KINDS that composed
onto it and not just how many extensions did (`EXTENSION_APPLIED: Station sawmill <- 1 extension(s)
[Bonus]`); the enumeration comes from the pure `authoredPayloadKinds`.

**The three PER-LEAF overlays (`Puppet`, `Custody`, `ContributionScale`)** are the non-collection
payloads, so they merge leaf-wise instead of appending: `applyToActionPuppet`/`applyToActionCustody`/
`applyToActionContributionScale` are the read-side entry points over the pure
`overlayPuppet`/`overlayCustody`/(a `ContributionScale`-shaped overlay) cores, which walk the group
recursively and take the OVERLAY's leaf where it is authored, the BASE's where it is not
(`firstNonNull` is the ONE rule at every depth). A `Custody` overlay carrying only `Display`
therefore never clobbers `States`/`MaxQuantity`/`Input` - that is the whole reason the capability
exists, so a pack can re-skin a station's placed-input visual without silently disabling its
placement mechanics; a `ContributionScale` overlay authoring only `Floors` keeps the base action's
own `Factors`. Overlays apply in `APPLY_ORDER`, so the later (higher-priority) extension wins a
same-leaf contest and the fold stays deterministic; a null overlay group returns the base object
unchanged. Covered by `ExtensionOverlayTest` (`src/test/java/com/ziggfreed/rpgstations/station/`,
fixture JSON authored by the test and decoded through the real shipped codecs). The keyed `Anchors`
map layers in the same place but by the keyed rule, not the leaf rule (`applyToActionAnchors` over
the pure `mergeAnchors`: base keys first, then each extension's NEW keys in `APPLY_ORDER`, the base
winning a collision case-insensitively). It is safe at this level precisely because nothing branches
on the map being non-empty - it is only ever read to resolve a `Walk`/`At` target - so an added key
widens what an inserted step can address without changing which engine path the action takes.
**Call-site status: WIRED** - the 2-arg live `ActionResolver.resolve` applies all four after the
pure resolution (`applyExtensionOverlays`: `actionTargetId` resolves the ONE Action-target
identity - the `Ref`'d `ActionAsset` id when the entry Refs one, else the entry's own effective id
- identity-preserving, so the zero-extension path returns the pure result untouched). The 3-arg
pure core stays extension-free for unit tests. Unlike `Bonus`/`PerCycleContributions` (applied at
`StationService`'s own read sites), `Puppet`/`Custody`/`ContributionScale`/`Anchors` overlay INSIDE
the resolver choke point, so every reader (`StationService`, `StationStepHandlers`,
`selectActionForBlockState`'s restart recovery) sees the same effective groups with no per-site
wiring.

**Validator: an Action target is an inline action id too.** `StationValidator`'s
`actionBodiesByTargetId` builds the union the runtime resolves by - standalone `ActionAsset` ids
PLUS every station's own inline (non-`Ref`) action ids - and `EXTENSION_TARGET_UNKNOWN` plus the
three base-resolution checks (`resolveBaseAnchors`, `resolveBaseContributionKeys`,
`resolveTargetStepIds`) all read it. Resolving against the standalone collection alone made the
shipped pack's own progression extension report its target as unknown while applying perfectly, and
silently skipped those three checks for the only target shape a pack can currently author. A SCOPED
`{Station, Action}` target resolves instead as "that station exists AND resolves an action answering
to that id" (`stationResolvesActionTarget`, the runtime's own `actionTargetId` rule), and its base
body is taken from THAT station first (`resolveTargetActionBody`) - the id-keyed union holds one
body per id, which cannot represent the very case a scoped target is authored for (two stations,
same inline action id). Cross-extension claims are bucketed by target key alone (`claimKey`, no
scope segment) and partitioned by scope at report time (`overlapGroups`), so two extensions claiming
one key on the same action id but on different stations are still not reported as colliding, while a
BARE claim and a scoped one on that key - which genuinely both apply on the scoped station - are.
Putting the scope IN the key had bought the first property by filing those two under different keys,
which silently cost the second.

## Held-tool gate (identity routes unchanged; a separate WEAR gate this wave)

`StationAsset.Tool`, checked at start AND per heartbeat -> `TOOL_CHANGED` stop
(`heldToolMatches`): the player must HOLD a matching tool. Three NATIVE routes, match = ANY
(null/no-live-route group = ungated): `Tags` = the shared `asset.TagMatch` item-tag object map
intersected case-insensitively with the held item's raw tags; `Gather` = the FUNCTIONAL test over
the held item's `ItemToolSpec.getGatherType()/getPower()`; `Ids` = the FALLBACK for modded items, exact id
OR case-insensitive underscore-segment match. Diegetic AND load-bearing for client stability: the
work emote NEVER sets `HideItemInHand` (correlated with a client `NullReferenceException` in
early smoke testing). Cycle consume prefers BACKPACK storage over the combined view for the same
reason.

**`Tool.Durability.MinStartPercent` is a SEPARATE, orthogonal WEAR gate**, not a
fourth identity route: which tool and how worn it may be are two independent questions, so it
composes with whichever routes are authored instead of joining their ANY-of match. It lives INSIDE
the `Durability` group (beside the `PerSwing`/`PerCycle` drains) because it is a wear number, and its
name states the semantics the old flat `MinDurabilityPercent` spelling made a reader hunt for:
`StationService#toggle` checks it at ENGAGE ONLY, against the resolved action's own `Tool` gate,
denying with
`ui.station.tool_worn`; the PER-HEARTBEAT re-check deliberately stays about tool IDENTITY, so a
session already running still ends at breakage (`TOOL_BROKEN`) rather than being cut short the
moment wear crosses the threshold. `hasDurabilityGate()` (non-null and `> 0`) is the one
is-it-active predicate; the live read is `resolveHeldToolDurabilityPercent`.

## Recipe: one per action, no selection needed

**`Recipes` is not a list any more; `RecipeSelection` is deleted.** An action authors AT MOST one
`Recipe` (`{Conversions?, FromCrafting?, Yield?}`, see `../asset/CLAUDE.md`), gated by that
action's own `Tool` - the "which tool" and "which transform" questions are answered by the SAME
group a reader is already looking at, so there is no per-recipe tool override left to resolve and
nothing to try-in-order. Two variants that used to be two `Recipes[]` entries sharing one action
are now two `ActionDef`s (see `../asset/CLAUDE.md`'s `Select` bullet): the diegetic held-item
match already IS the "try this, else that" chain, one level up. `s.toolReq` is set to the resolved
action's own `Tool` gate at engage, so the heartbeat identity re-check and the wear drain follow
the same gate the engage checked.

`ConversionCheck` (built off `StationAsset.Conversion.Input`/`Output`, resolved via
`firstRunnableConversion`/`firstRunnableConversionFromCustody`) carries the resolved action's
`Recipe` unchanged, so the produce phase reads THAT recipe's own `Yield`.

**The output-room question is `ziggfreed-common`'s `InventoryGrant.canAdd`/`canAddAll`, never a
container read of our own** - the probe half of the very granter `util.ItemGrantUtil` hands outputs
over with, so "is there room" and "where did it land" can never disagree. Both conversion scans and
the Stamp phase's return-room check read it. A single stack therefore also counts a free hotbar
slot, which is exactly what the grant would have used; a multi-output batch is answered against
backpack storage alone, since no container can answer for a batch the granter places one stack at a
time. The sneak+F picker's
category strip and custody's derived acceptance matcher still read the WHOLE effective conversion
set of the resolved action (`StationService#allConversionsFor`), because both answer "what can
this action make/accept at all", not "what runs this cycle" - that flatten is unaffected by the
list-to-singular change, since a single `Recipe.Conversions[]`/`FromCrafting`-derived set can
still hold several conversions (e.g. the sawmill's 33 species x category combinations).

**`ContributionScaling`** ([`ContributionScaling.java`](ContributionScaling.java)) is the pure
resolution of an action's `ContributionScale` ladder into ONE multiplier, over the SAME
ladder rules a loot `Roll.Ladder` follows (an empty `Factors` resolves to 0, a `Min <= 0` floor IS
reachable, an equal-`Min` tie goes to the LAST authored floor). `StationService` calls
`ContributionScaling.multiplier(action.getContributionScale(), snapshot::resolve)` at both
per-cycle contribution sites and PRE-SCALES every `Work.PerCycleContributions` amount before
`StationCycleCompletedEvent` dispatches, reporting the resolved multiplier back on
`contributionScale()` for DISPLAY only.

## THE `ItemToolSpec` construction trap ([`StationToolScaling`](StationToolScaling.java))

`heldPowerFor` takes an injected `ToolPower(gatherType, power)` value shape rather than the live
`ItemToolSpec` directly, because merely CONSTRUCTING a real `ItemToolSpec` triggers its
`AssetBuilderCodec` static init, which THROWS outside a running Hytale server - the same trap
[`StationRecipeDeriver`](StationRecipeDeriver.java)'s `CraftingCandidate` shape avoids. If you add
a new pure-tested helper that reads tool data, follow this pattern - do not construct
`ItemToolSpec` (or any other `AssetBuilderCodec`-backed engine type) in code that must run in a
unit JVM.

## The engine holds NO baked tool curve (`Tool.PowerScale` is deleted)

There is no engine-owned multiplier over a station's contributions any more, and
`StationCycleCompletedEvent` carries no `toolMultiplier()`. The retired `Tool.PowerScale` group was a
baked, non-composable curve (`clamp((heldPower/ReferencePower)^Exponent, MinMult, MaxMult)`) over the
same number `hytale:tool_power` already exposes as a freely composable FACTOR, it was inert on the
standalone Sawmill (which declares no `PerCycleContributions` for it to scale), and its only possible
output was a contribution amount - i.e. it could only ever move a number the engine forwards without
interpreting. "A better tool earns more" is now authored where its effect is visible: as a factor
inside an action's own `Bonus` Rolls for OUTPUT (a visible `Ladder`/`Chance` granting
`Grants.OutputItems`), an action's own `ContributionScale` ladder for a CONTRIBUTION amount, or by
whichever mod owns a channel for anything else it decides a contribution means. `StationToolScaling`
keeps only `heldPowerFor` (the pure spec scan behind the `hytale:tool_power` factor) plus the
idle-cadence and durability-drain reader defaults.

## Recipe ingredients (`asset.Ingredient` ARRAYS, the native CraftingRecipe shape)

`Conversion.Input`/`Output` are `asset.Ingredient[]` (`../asset/CLAUDE.md`) - an Input entry
authors AT MOST one of `ItemId`/`ResourceTypeId`/`Tags` (route-less = the legal MATCH-ANY input,
which draws only from placed custody, never a player's open inventory - the inventory scan skips
such a row and the validator warns `MATCH_ANY_INPUT_WITHOUT_CUSTODY`); an Output entry is `ItemId`
only. `ResourceTypeId` is a native `Item.ResourceTypes` FAMILY (e.g. `Wood_Hardwood_Trunk` = any
hardwood log); `Tags` is the shared `TagMatch` map (an empty value list = family-key presence, the
single-native-tag form). `ItemResourceType` exposes its id as a PUBLIC FIELD `.id` (no `getId()` -
a protocol class quirk). **All route COMPARING is ziggfreed-common's `match.ItemMatch`**, reached
through `StationCustody.ingredientEntryMatcher` (piles; `StationService.liveIngredientMatcher` is
the live-resolver wiring) and `StationCustody.matchesIngredient`/`matchesInput` (held/placed
acceptance) - `ActionInput` and `Ingredient` stay two leaves over ONE matcher, pinned by
`IngredientActionInputRouteParityTest`. A `Tags` input consumed from INVENTORY counts/drains
through `InventoryIngredients` (a slot walk over the same predicate; no native batch API speaks
our tag-map shape).

**Selection order is `Conversion.Tier` (lower first), stable-sorted in `selectConversion`
(`StationService.tierOrdered`), authored order inside a tier** - no Tier authored anywhere = pure
authored order byte-identical; derived rows are stamped `Conversion.DERIVED_TIER` (1) so an
unauthored hand-written row outranks derivation. `Conversion.IsExactSet` is an independent knob:
the row matches only while the pile(s) its inputs draw from (per-entry `Socket` aware) hold
nothing beyond those inputs (`StationCustody.exactSetSatisfied`; extras in undrawn sockets never
block; inert on the inventory route). Exact-first / match-any-last is an authoring CONVENTION the
validator nudges (`RECIPE_ROW_ORDER_MISLEADING` / `CONVERSION_TIER_SHADOWED` INFOs), never an
invisible engine reordering. Pinned by `RecipeTierSelectionTest` + `StationSetRecipeMatchTest`.

A conversion is ALL-OR-NOTHING per cycle: `firstRunnableConversion`/`firstRunnableConversionFromCustody`
require EVERY input available and room for EVERY output before a cycle starts, and the chosen
conversion's whole arrays drive the implicit program's one atomic Consume/Produce step pair
(`ConversionCheck` carries them; `Conversion#primaryInput`/`#primaryOutput` are the display/matching
convenience the picker preview, custody acceptance, and validator labels speak in, never the consume
path).
[`StationRecipeDeriver`](StationRecipeDeriver.java)'s `Recipe.FromCrafting` derives one
`Conversion` per LIVE `Item` whose native `Recipe.BenchRequirement[].Categories` intersects the
authored `Categories` (bench-TYPE-agnostic: a Crafting bench's category rows, e.g. the
Cookingbench tabs, scope exactly like a Processing bench's), carrying that recipe's WHOLE native
`Input` array (a multi-material recipe derives rather than being skipped; a native `ItemTag` input
derives onto the `Ingredient.Tags` presence form, its tag NAME recovered from the items' own raw
tag keys since `MaterialQuantity` exposes only the index - an unresolvable tag skips the candidate
with ONE fold WARN naming the output item; only a recipe with no inputs at all, or one with no
usable route, is skipped), zero hardcoding. The PURE core (`resolve`/`deriveFromCrafting`)
takes injected `CraftingCandidate`s, unit-tested without a live item map. A derived conversion
carries a quantity of 1: the native `CraftingRecipe.primaryOutputQuantity` is a protected field with
no getter and is absent from the recipe packet, so it is unreadable at that seam (and is verified 1
for every recipe family the shipped content derives).

**Yield is [`StationYield`](StationYield.java)'s job, not the deriver's - and it is PURELY
DETERMINISTIC now, with zero factor/roll involvement.** `Recipe.Yield` (`../asset/CLAUDE.md`) is
resolved PER CYCLE at the one point a chosen conversion becomes a live produce phase
(`StationService#runRealCycle`), because even a purely deterministic `Base`/`Scale` still needs
re-reading every cycle (a tool swap mid-session changes nothing about `Yield` itself, but the
conversion driving it can). `StationYield` is now just `resolveQuantity`/`applyToOutputs` -
`floor(base * Scale)` clamped into `[max(1, Min), Max]` - with NO ladder, NO roll, and NO
`FactorSnapshot` dependency at all; a null `Yield` is the IDENTITY (the conversion's own authored
quantity, untouched). **Everything probabilistic moved to the loot layer**: a `Roll` in the
action's own `Bonus` (evaluated by `loot.StationLootEngine` over the shared roll core, off the SAME per-cycle
`FactorSnapshot` `runRealCycle` builds once for the whole cycle - "one aggregation, several
consumers") tallies `Grants.OutputItems`, and `StationService#grantBonusOutputItems` hands out
additional units of `s.cycleOutputItemId` (the cycle's own resolved primary output,
captured right after `StationYield.applyToOutputs` runs) through the same `util.ItemGrantUtil`
seam every other grant uses. **That tally is FRACTIONAL and resolves ONCE PER CYCLE**
(`loot.OutputItemResolver` with `ThreadLocalRandom`: the whole part always, plus one more at the
leftover fraction's probability), so a `1.5` ladder floor pays one item always plus a second half
the time, and two rolls paying `0.5` each average a whole item instead of rounding twice; the
produced row's breakdown records the RESOLVED count, since that is what the player received. **It
also NOTIFIES that count** (`notifyItemGain`, not lucky-flagged): the Produce phase only ever
announces the recipe's own deterministic `Yield`, so a bonus that is not separately notified makes
every toast under-report - a cycle paying one base plank plus four from the tool ladder announced a
single plank, which reads in game as the bonus not working at all. **Any NEW grant path owes its own
notification for the same reason.** An
authored `Steps` program has no single "cycle output" for
`OutputItems` to add to (`s.cycleOutputItemId` stays null, and `LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT`
warns on an action authoring `OutputItems` there - on its own `Bonus` or on a step's `Roll` phase
alike). The three tool factors that make a `Bonus`/`ContributionScale`
ladder authorable (`hytale:tool_quality`, `hytale:tool_item_level`, `hytale:tool_power`) are read
by `StationService#resolveHeldToolQuality`/`#resolveHeldToolItemLevel`/`#resolveHeldToolPower`;
the quality one is an asset-map index resolve, not a raw index compare - see its javadoc.

## Cadence + the `emitMoment` choke point (unchanged)

1000ms heartbeat (terminate checks: ref/store validity, block-gone, walk-off `MaxMoveMeters`,
crouch exit, held-tool still matching, `MaxDurationMs` cap, the engine-toggle check via
`SettingsCatalog`; hold TTL refresh) + per-`Work.CycleMs` cycle (Convert transaction with
output-room PRE-check before consume; loot rolls via `loot/StationLootEngine`; `StationEvents
.fireCycleCompleted`; the cycle `Presentation` at the block via `emitMoment`). A multi-action
station's authored `Steps` program dispatches through the step engine above instead of the
classic Convert transaction; the implicit single-step program (`ImplicitProgram`) is what a
station with no `Actions`/`Steps` gets, so both paths converge on the SAME step engine.

**`Trigger: "Cycle"` means THE action's cycle-completed moment, whatever program shape runs it.**
Both program shapes read the action's effective `Bonus` through the ONE
`StationService#effectiveBonus` (its own group plus every matching extension's) and expand it
through the ONE `loot.StationLootEngine#resolve` (each referenced table's extension-composed rolls
AND its pool), and they differ only in WHERE the `Cycle` pass fires: the implicit convert program
hands that whole ref to its own `Roll` phase, while an authored `Steps` program has no such phase,
so `dispatchProgram` runs the pass itself on a COMPLETED walk
(`rollCycleBonus`, gated by its `bonusAtCompletion` flag so the implicit route never double-rolls).
A referenced table's POOL draws on the `Cycle` pass only - see `../loot/CLAUDE.md`.
It fires BEFORE `onCycleCompleted`, so a `Grants.Contributions` find rides that same cycle's event
on either route. Exactly one moment per completed pass: a suspend/resume pair is still ONE pass, and
an idle-practice cycle rolls no loot at all by design. The one grant kind that still lands nowhere
under an authored program is `Grants.OutputItems` - there is no single cycle output to add items to
(`s.cycleOutputItemId` stays null and `grantBonusOutputItems` no-ops), which is what
`LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT` warns about at authoring time; every other kind (droplists,
commands, effects, contributions, the reached floor's presentation) applies.

`emitMoment(store, s, momentId, presentation, targetPos)` in `StationService` is the ONE
presentation-playback funnel every station moment goes through (`StationFlairs.MOMENT_CYCLE`/
`MOMENT_SWING`/`MOMENT_IMPACT`/`MOMENT_RARE_FIND`/`MOMENT_COMPLETION`, plus a per-step
`StationFlairs.stepMomentId(actionId, stepId)`) - it is ALSO the flair-resolution choke point
(`StationFlairs.effective` against `FlairCatalog.effectiveFlairsFor`'s merged map).

**SPECIFICITY WINS, and this is the ONE place it is decided.** The `presentation` argument is
whatever base the CALLER already holds for this emission (a step's own `Presentation`, a reached
`Ladder.Floor`'s cue). When it is null, `emitMoment` falls back to the running action's own
`Moments` entry for that moment id - `StationSession.moments`, the action's map canonicalized to
lowercase and snapshotted ONCE at engage (`ActionResolver.ResolvedAction#getMoments`), so a
mid-session catalog re-fold can never swap a moment out from under a running run. That one rule is
why `runSwing` passes `null` for both of its moments and why `playCompletionMoment` no longer
re-resolves the action at stop: a call site that has nothing more specific to say says nothing, and
the map answers. The one place the gate also had to widen is the per-step emission
(`StationStepHandlers.emitEntryCues` -> `StationStepDecisions.shouldEmitPresentationOnEntry`, which
now takes an `actionAuthorsThisMoment` flag): a step with no `Presentation` of its own still has a
moment to play when the action authored one under its `step:<actionId>:<stepId>` id. That second
route is gated to per-STEP ids only (`StationStepDecisions.actionAuthorsStepMoment`): an id-less step
resolves to the action-wide `cycle` moment, which the cycle machinery itself owns, so honoring a
`Moments.cycle` entry there would replay the cycle cue once per unnamed beat.

**Which moment id a step's entry cue plays under** is `StationStepDecisions.momentIdForStep`
(`StationStepHandlers.presentMomentId` wraps it with the live action): `step:<actionId>:<stepId>` for
an AUTHORED step id, else `cycle`. The implicit convert loop an action with no `Steps` runs is
`cycle` too - its one step is engine-synthesized (`ImplicitProgram.ID_WORK`, which no author ever
wrote), and its iteration IS the cycle, so a flair re-skins the classic work loop by the same `cycle`
id the docs name for it rather than by a `step:` id derived from an engine-internal name.
**A loot CUE is a moment id like any other**, so `rare_find` (and any author-defined `cue:<name>`)
IS action-authorable now: the loot layer names the moment and the map decides what it sounds like.
That is the whole reason the cue stopped being a presentation body - one edit re-skins every table
that names it, and the tables stay pure numbers.

**`Presentation.DelayMs` is applied INSIDE `emitMoment`, after the flair fold** (`../asset/CLAUDE.md`'s
Presentation bullet), so the winning presentation is the one whose timing is honored and a flair can
re-time a moment as well as re-skin it. An undelayed cue plays inline through `playMoment` (the
extracted body, byte-identical to the pre-delay path); a delayed one is parked in
`pendingMomentsByWorld` (a `WorldKeyedQueues<PendingMoment>`) carrying the ALREADY-RESOLVED
presentation, so a mid-wait catalog re-fold can never change what was scheduled. Four rules bind:
- **ONE scheduler.** `scheduleCueAt(now, delay)`/`cueDue(now, dueAt)` are the pure pair EVERY offset
  cue in this engine resolves through: a moment's own `DelayMs`, a single `Sounds` entry's, and the
  `impact` moment that is late purely because it authors one. There is no dedicated single-slot
  machinery beside the queue any more - the session carries no pending-impact field, the frame drain
  has no impact branch, and `stop()` has no impact reset. Do not add a second due-time rule.
- **A `Sounds` entry with its own `DelayMs` is split into its own cue, before anything is queued.**
  `emitMoment` runs two pure cores over the flair-resolved presentation: `offsetSoundCues(p)` yields
  one sound-only `Presentation` per offset entry, each already carrying `group delay + its own` as
  its whole `DelayMs`, and `withoutOffsetSounds(p)` is the remainder (returned as the SAME object
  when no entry carries an offset, so shipped content allocates nothing). Each is then an ordinary
  delayed-or-inline cue - per-sound timing needs no downstream special case, and `playMoment` never
  re-reads a per-sound delay. A remainder with nothing left to play is skipped rather than queued.
- **The queue is per WORLD, never per session, and `drainPendingMoments` runs at the TOP of
  `tickFrameOnce`, ahead of the session loop's empty-queue early return.** `MOMENT_COMPLETION` is
  emitted from inside `stop()`, so its cue routinely outlives its own session and, when that was the
  world's last one, the whole session queue - a session-scoped queue would silently swallow every
  delayed completion cue.
- **Nothing is dropped for being late, and the delay orders cues ACROSS ticks (not within one).**
  The drain walks the whole queue each tick and plays every entry at or past its own due time, so two
  cues with different delays land in the order their delays put them at a resolution of one server
  tick (about 33ms at `TickingThread.TPS` 30). Two cues coming due inside the SAME tick both play in
  that tick, in emission order - the queue iterates in insertion order and does not sort. A cue whose
  player has an invalid ref or has left the world is discarded (it is a positional cue for a body
  that is no longer there). Past `MAX_PENDING_MOMENTS_PER_WORLD` a cue plays IMMEDIATELY instead of
  queueing: the offset is the first thing worth losing, dropping the cue outright would silence the
  station. The ceiling is read off a per-world counter (`pendingMomentCounts`) rather than the
  queue's own `size()`, which is a full traversal on a `ConcurrentLinkedQueue`; that counter map is
  also the KEY registry for the cross-world sweep, since `WorldKeyedQueues` exposes values only.
- **An INTERRUPTED stop drops this session's parked cues; a COMPLETION keeps them.**
  `dropsPendingCuesAtStop(reason)` is the pure gate, and `stop()` runs the sweep
  (`dropPendingMoments`) only when it says so. Walking off, taking a hit, dying, or breaking a tool
  should not keep playing the sounds of
  work that is no longer happening - but `RITUAL_COMPLETE` and `INPUTS_EXHAUSTED` are real
  completions, and a non-looping ritual emits its final cycle's cues microseconds BEFORE stopping
  itself, so a sweep keyed on session identity alone silences exactly the moment the ritual exists to
  celebrate. **If you touch either side of this, keep `StationServiceTest`'s exhaustive
  `dropsPendingCuesAtStop` coverage green - it is the only guard on the interaction.** The completion
  cue itself is outside the question either way: `stop()` emits it further down, after the sweep,
  into the world-scoped queue.

**Which delay to reach for** - three offsets, one scheduler, and the choice is about IDENTITY, not
timing:
- **A separate `Moments` entry** (`impact` beside `swing`) when the late cue is its own BEAT that a
  flair should be able to re-skin or re-time on its own. It is late because its own
  `Presentation.DelayMs` says so; the moment id is what buys the flair target.
- **`Presentation.DelayMs`** when a whole moment simply reads early and wants nudging onto its beat,
  keeping the moment vocabulary alone.
- **A `Sounds` entry's own `DelayMs`** when ONE sound inside a moment needs to trail the rest and
  nothing else about that moment moves. It ADDS to the moment's own delay.

Two more consequences worth knowing:
- A flair overlays `DelayMs` like any other leaf, so a flair that OMITS it inherits the base moment's
  timing. The only way for a flair to cancel a base delay is to author `DelayMs: 0` explicitly, which
  the reader then treats as "play at once".
- A `Presentation.Effect` on a cue that outlives its session is tracked on a session whose teardown
  already ran, so it lives out its own `EffectRef.DurationMs` / the effect asset's TTL. That is the
  lifetime the UNDELAYED completion cue has always had too - `stop()` strips tracked effects before
  it plays that moment.

**`MOMENT_RARE_FIND` plays only EARNED cues (the smart-cue rule - see `../loot/CLAUDE.md`).**
`applyGrantResult` walks `GrantResult.getCues()` and emits each CUE ID at the block through
`emitMoment` with a null base, so the action's own `Moments` entry for that id (and every applicable
flair) supplies the presentation. That list is deliberately pre-filtered by the shared loot engine:
BOTH cue altitudes ride it (a `Roll`'s own top-level `Cue` when the roll hit, and a reached
`Ladder.Floor`'s, roll cue first when one roll carries both), and each was admitted only if it is a
PURE cue (no `Grants` group authored beside it) or its own paired grants group actually PRODUCED
something. This package therefore needs no new plumbing and must not grow its own second filter: the
"was it earned" question is answerable only where the grant was applied, which is why `applyGrants`
returns a boolean over there. The transport name predates the roll-level altitude; it now carries
both.

**Particles are an authored ARRAY of tunable bursts** (`Presentation.ModelParticle[]`, see
`../asset/CLAUDE.md`) played in order by `StationService#spawnMomentParticles`: per burst a
`Scale`, a `DurationSeconds` playback cap, a `RotationOffset` (degrees, converted to the engine's
radian yaw/pitch/roll arguments), and a FACING-RELATIVE `PositionOffset` composed through the one
shared [`StationBlockFacing`](StationBlockFacing.java) reader. **The per-burst duration cap
(default 4s) is a LEAK GUARD, not decoration** - at least one shipped particle asset
(`Block_Gem_Sparks`) authors an UNBOUNDED spawner (`TotalParticles < 0`) that, fired uncapped,
never stops spawning; authoring `DurationSeconds: 0` deliberately means uncapped, so only do it for
a system whose own spawner budget terminates. That spawn reaches the engine's full-arity
`ParticleUtil` overload directly rather than ziggfreed-common's `ModelParticleService.spawnAt`,
whose signature hardcodes exactly the rotation/scale arguments this schema now authors - the
convergence target is a common-side overload taking the full argument set; lift the call when one
exists. Route a new moment call site through `emitMoment` - never spawn particles at a station
moment yourself, or you lose the flair overlay AND the leak guard (this bug was found in-game; do
not reintroduce it).

## Per-swing cadence

`StationAsset.Animation.Swing` is pure CADENCE (`IntervalMs` and nothing else): an independent
server-side timer re-fires the work animation as a one-shot. The work emote must NOT loop
client-side by convention: a looping emote (`IsLooping:true`) with no `Swing` group behaves as
before (client loops it, zero re-fires); a non-looping emote needs an authored `Swing.IntervalMs`.
`runSwing` picks the animation ROUTE via `useActionSlotForSwing(seatMode)` - see the seat/swing
routing bullet below.

**What a swing SOUNDS like is two `Moments` entries, not a leaf on this group.** Each tick emits
`MOMENT_SWING` and `MOMENT_IMPACT`, both with a `null` base, so both resolve against the action's own
`Moments` map through `emitMoment`. Both emit UNCONDITIONALLY: an action authoring neither entry
plays nothing (the flair fold early-returns on a null base), while a FLAIR authoring one without a
base entry still gets to play - which an "only emit when the action authored it" gate would have
silently forbidden. The impact cue is late purely because its own `Presentation.DelayMs` holds it,
riding the same per-world queue as every other delayed cue.

## THE camera packet shapes - written in blood, do not improvise a fourth combination

([`StationHoldController`](StationHoldController.java)`.applyCamera`): the working camera is
sent in the FIRST-PARTY packet shape ONLY - engage = `ClientCameraView.Custom` + a
fully-populated `ServerCameraSettings`, disable = `Custom` + `false` + `null`. NEVER send a
built-in view (`ThirdPerson`/`FirstPerson`) or locked+null-settings; that unexercised client path
correlated with a deterministic post-walk-off client `NullReferenceException` pre-extraction. The
fixed-look camera recipe (`applyFaceBlockPreset`, reached whenever `Camera.Recipe` is authored at
all - there is no second boolean gating it) only combines fields the THREE
first-party `ServerCameraSettings` senders in the shared source actually establish:
`movementForceRotationType=Custom` + `movementForceRotation` is necessary but NOT sufficient to
stop mouse-driven camera spin while STANDING STILL - that additionally needs
`rotationType=Custom` + a fixed `rotation` `Direction` + `mouseInputType=LookAtPlane` +
`planeNormal=(0,1,0)`. [`StationCameraPreset`](StationCameraPreset.java) is the surviving
experimentation enum (`FROZEN`/`FREE_NULL`/`FREE_DIR`/`LOOK_ROT`/`LOOK_ROT_BLEND`/
`LOOK_ROT_NO_TARGET`/`LOOK_ROT_ATTACHED`/`CUSTOM_SEED`), each a targeted field-diff experiment;
`FROZEN` is the full fixed-camera win. [`StationCameraPrefs`](StationCameraPrefs.java) is the
transient, never-persisted per-player override, set via `/rpgstations camera <preset>|list` (see
`../command/CLAUDE.md`). **Never invent a fourth `ServerCameraSettings` field combination beyond
what those three first-party sources establish.**

## The `Hold.Mount` knob family (unchanged by scope-2)

`StationAsset.Hold.Mount.Surface` is a union discriminator between `"Block"` (default) and
`"Entity"` - two structurally different engine mechanisms, not a mode.
- **`"Block"`** ([`StationMountController`](StationMountController.java)): mounts the player on
  the station block via native `BlockMountAPI.mountOnBlock` - the client renders its own
  free-orbit camera for a seated entity, and every OTHER viewer sees the seated player's facing
  from the seat's fixed geometry. Requires the station BLOCK to author `BlockType.Seats[]`
  (`{"Offset":{...},"Yaw":<degrees>}`). The `RPG_Station_Sawmill` block authors `Seats[].Yaw:
  180` because the engine adds a hard, unconditional +180deg in `BlockMountPoint
  .computeRotationEuler` (a first-party `// ?` comment). Movement lock: the Block route forces
  `movementLock = false` (`Hold.MovementLock`/`EffectId` are ignored while mounted this way).
- **`"Entity"`** ([`StationEntityMountController`](StationEntityMountController.java)) - the
  standing work mount (spawns a minimal anchor entity at the block center, attaches
  `MountedComponent` directly to the player, no interaction chain). Never populates the client's
  `MountedUpdate.Block`, so the player renders standing. `Hold.Mount.Entity.Offset` (the shared `Vec3`)
  converts to the constructor's `Vector3f attachmentOffset` parameter (an honest spatial XYZ
  offset; the engine's former `Rotation3f`-typed mislabeling of it was corrected upstream). Every entity mount applies the SAME hold effect
  effect-mode uses plus a per-heartbeat `snapBack` (defeating the native WASD-steers-the-anchor
  behavior); `DismountOnMove` (default true) runs the same origin-delta walk-off check
  effect-mode uses. Anchor lifecycle: session-scoped,
  despawned in the ONE idempotent `stop()` funnel via `CommandBuffer` (tick-safe from an
  interaction handler or the heartbeat frame drain). **A WORKING Entity mount renders NOTHING by
  design** (decision 62, source-traced: no model on the anchor, no pose packet -
  `MountedUpdate.Block == null` is what makes the client render default STANDING): the mount is a
  positioning/input-lock primitive; real station visuals come from `Camera`/`Animation`/`Puppet`
  authoring. `Entity.VisibleAnchorItemId` (nullable codec leaf) is the confirm-kit/diagnostic
  knob - the anchor wears a dropped-item-style marker body (prop-hygiene trio carried); the
  native `/mount check` command reads the attach live, and a client that silently REJECTS a
  server-attached mount has its whole movement packet dropped (`GamePacketHandler`'s mountedTo
  filter), observable as the worker's head-look freezing for other viewers.

## Seat/swing routing, the seated-worker fix (Block route only)

`useActionSlotForSwing` stays keyed to `seatMode`: a seat-mode session's swing does NOT re-fire
the work emote on the `Emote` slot (the sit pose wins over that slot's clip). `StationService
.runSwing`'s `useActionSlotForSwing(seatMode)` routes a seat-mode session through
`StationHoldController.playActionSwing` instead - fires the swing on `AnimationSlot.Action`
against the CURRENTLY HELD ITEM'S OWN `ItemPlayerAnimations` clip set, the exact mechanism
vanilla combat swings ride. The clip id is `StationHoldController.effectiveActionClip` -
`StationAsset.Animation.ActionClip` when authored, else `DEFAULT_ACTION_CLIP` (`"Chop"`) -
deliberately NOT a cross-family default; a station gated on a different tool family must author
its own `ActionClip` or the swing plays nothing (`ACTION_CLIP_WITHOUT_SWING` warns).

**A PUPPET-active session bypasses `useActionSlotForSwing` but reaches the same Action-slot clip**
through its own slot choice (see the puppet-engine bullet's Animation-routing paragraph): the two
routes share `StationHoldController`'s `heldItemAnimationsId` + `effectiveActionClip` resolution,
so an authored `ActionClip` means the same thing whether the worker is seated or a puppet.

## Idle practice mode + tool durability drain (unchanged)

`StationAsset.Work.Idle` (opt-in, default OFF): a `NO_INPUTS` start proceeds into idle mode
instead of denying. An idle cycle posts fractional contributions only (each `Amount *
Work.Idle.Fraction`, ALREADY pre-scaled on the event) with NO
conversion and NO loot, marked `idle=true` on
`StationCycleCompletedEvent`. `StationAsset.Tool.Durability {PerSwing, PerCycle}` (both default
OFF): the mutation is native `ItemUtils.updateItemStackDurability`; a broken held stack
(`ItemStack.isBroken()`) stops the session (`TOOL_BROKEN`) and fires `StationToolBrokeEvent`.

## Exit hooks

Re-press F / crouch / walk-off (heartbeat), damage
([`StationInterruptDamageSystem`](StationInterruptDamageSystem.java), read-only, calls `stop`
only), death ([`StationDeathSystem`](StationDeathSystem.java) -> `stopForRef`), disconnect
(`RpgStationsPlugin`'s `PlayerDisconnectEvent` registration -> `stopFor`), world-change
(heartbeat store check), world-unload (`onWorldRemoved`, below), shutdown (`stopAll`). `stop()` is
the ONE idempotent exit funnel: it fires `StationSessionCompletedEvent` UNCONDITIONALLY (every
stop, silent included).

**`StationInterruptDamageSystem`/`StationDeathSystem` are PLAYERS-ONLY queries**
(`getQuery()` returns `PlayerRef.getComponentType()`, the same query
`StationCustodyBreakSystem`/`StationBlockPlaceSystem` already used): only a player can hold a work
session, so a mob taking damage or dying - the overwhelming majority of either event in a
populated world - used to pay a dispatch plus a session lookup for a question whose answer could
only ever be "no". The prior `Query.any()` on the damage system paid that cost for EVERY entity.

## World-unload teardown + the disconnect posture (`RemoveWorldEvent`)

**Every one of this engine's VOLATILE block-keyed maps (`displayByBlock` - whose keys append a
per-socket `#<socketId>` suffix - `byBlock`, `workingByPlayer`'s block-anchored entries,
`knownStationBlocks`, and the `unattendedIndex`'s blocks + hydrated-section markers) is GLOBAL,
keyed by a composite
`"<worldUuid>:<x>:<y>:<z>"` string, rather than partitioned per world.** The world-uuid prefix the
key already carries is the whole per-world sweep; without it a fleet that creates and destroys
instance worlds would accumulate stale entries (and pinned display `Ref`s) for the whole uptime.
Placed custody is NOT in any of these maps - it lives on the block's own chunk section (see the
custody section below) and unloads/reloads with the chunks.

- **[`RpgStationsPlugin`](../RpgStationsPlugin.java)`#registerWorldEviction`** registers a global
  `com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent` listener (skipping a
  CANCELLED removal, since the world then stays loaded): `StationService.getInstance()
  .onWorldRemoved(removed)` runs FIRST (it reads the per-world session queue the shared
  `ziggfreed-common` `WorldEvictors.onWorldRemoved` fan-out is about to drop), THEN that shared
  fan-out runs. This mod owns its own world-unload listener rather than relying on a co-installed
  mod's - depending on `ziggfreed-common` alone means nothing else is guaranteed to register one.
- **`StationService#onWorldRemoved(world)`** stops every session still queued or tracked for that
  world (`StopReason.WORLD_CHANGED`, idempotent so a session reached via both the world's own
  queue AND the belt-and-braces `byPlayer` scan costs nothing extra), then
  `forgetBlockKeyedState(key -> key.startsWith(worldPrefix))` drops every VOLATILE block-keyed
  entry whose key names that world. Eviction never reads, returns or clears a stash: the stashes
  ride the world's chunks, and a world deleted outright takes them with its chunk files, exactly
  like chests. Never throws.
- **Disconnect** (`RpgStationsPlugin#registerTeardownHooks`'s `PlayerDisconnectEvent` handler):
  `stopFor` runs on the departure world's thread (via `World#execute`; direct fallback for a
  dead/gone world) and stops the session - refunding its in-flight iteration - but standing PLACED
  custody stays in the world stash: the player collects it at the block on return ("leave the stew
  on and log off"). No cross-world claim sweep exists because there is nothing volatile to reach
  in a world the player is not in; `stop()`'s `custodyReturnsAtStop` is what makes DISCONNECTED /
  SERVER_STOP / WORLD_CHANGED the three leave-it reasons.

## Owner ceilings (`Settings.Limits`, `RpgStationsSettingsAsset.Limits`)

Three INDEPENDENT ceilings plus the unattended pass's pace knob (`UnattendedIntervalMs`, reader
default 1000ms, read live in `tickUnattended` - see the unattended section above; see
`../asset/CLAUDE.md`'s `RpgStationsSettingsAsset` bullet), read
live (never cached) so a settings reload takes effect on the next press. The shared predicate
`RpgStationsSettingsAsset.Limits.atCapacity(max, currentCount)` treats null OR non-positive as
unlimited, so a server that never authors `Limits` behaves exactly as it did before the group
existed, and the check costs one null read until an owner sets a number.

- **`atSessionCap(world)`** (`MaxSessionsPerWorld`) - checked in `toggle()` AFTER the custody
  placement branch (a press that only loads material starts no session, so a busy world must never
  block placing/topping-up) but BEFORE the tool gate: a world already running as many sessions as
  the owner allows denies a NEW engage with `ui.station.server_busy`. Counts the world's own
  session queue (`countLiveSessions`), not the global player map, so the answer is per-world by
  construction, and excludes an already-stopped session still awaiting its frame drain.
- **`atStashCap(world, x, y, z)`** (`MaxStashesPerSection`, per CHUNK SECTION - the bound a
  per-section store can enforce, counted via `BlockStashes.countInSection`) - checked ONLY when a
  press would actually CREATE a brand-new stash: `claim == null` AND something acceptable was
  found to place. Both halves matter, so the check sits AT each `placeIntoCustody` call rather
  than at the top of the branch - hoisting it would deny presses that place nothing, reporting
  "storage full" for what is really a no-materials press and making idle practice unreachable at
  an empty `Work.Idle` station. Topping up a stash that already stands adds no new record, so it
  is never denied and a player can never be locked out of material they already placed. Denies
  with `ui.station.storage_full`. The retired `MaxCustodyClaimsPerWorld` leaf still decodes into a
  warn-only slot (`SettingsCatalog#warnRetiredLeaves` names the replacement at fold; never a parse
  failure) and enforces nothing.
- **`atPuppetCap(world)`** (`MaxPuppetsPerWorld`) - checked right before `StationPuppetController
  .spawnAndHide`. Past the ceiling the session starts and runs completely normally; it simply
  performs in the player's own body instead of spawning a worker double - the EXACT graceful
  fallback a failed puppet spawn already takes, so no engage is ever denied purely over
  presentation. A world without a busy puppet count pays nothing extra.

## Placed-input custody + block states (CHUNK-PERSISTED via zc's `BlockStashes`)

**Custody is chunk-persisted state, resolved live per touch.** The authority is ziggfreed-common's
`BlockStashes` store (registry id `ZigBlockStash`, registered by the zc plugin at setup): one
`BlockStash` per block on the block's own chunk section, saved and loaded with the chunk, holding
ONE PILE PER SOCKET keyed by socket id (a socket-less custody keeps its whole tally under the
reserved `StationCustodyClaim.MAIN_PILE` id `"main"` - the degenerate one-socket case) - per pile
`Owner` = the first contributor's uuid (a produce pile = the session worker's), `Items` = the
insertion-ordered tally (oldest-first drain order), `Unique` = the metadata-preserving stack (the
anvil's placed weapon, riding the engine's own item codec so it survives restarts); the stash
`Tag` = `rpgstations:<stationId>/<actionId>` (how a claim remembers its committed action across a
restart, and how this mod's stashes are told apart from another consumer's - a foreign tag is
never adopted or clobbered) and the stash-level `Owner` = whoever stood it up.
[`StationCustodyClaim`](StationCustodyClaim.java) is a THIN VIEW over those piles (per-socket
accessors `pile`/`items`/`addTo`/`pileOwner`/`totalQuantity(socketId)`/`toItemStacks(socketId)`/
`removePile`, plus the main-pile delegates the degenerate paths ride), materialized fresh by
`StationService#custodyClaimAt` per touch (NO authoritative write-through cache - the
`PlacedBlockLedger` posture; an unloaded section answers "no claim"); `ensureClaimAt` mints +
stamps a new stash (tag + stash owner; each PILE is minted by its first `addTo`, which is also
what records the pile's own owner), `removeStashAt` deletes one. **Whoever mutates, marks**: every
in-place mutation batch (a placement's adds, a Consume drain, a pile removal, the Stamp
unique-stack write-back) ends with exactly one `claim.markDirty()` (zc's `Handle` dirty contract -
nothing else flags the section for a save); `ensure`/`remove` mark themselves. The display props'
refs + NetworkIds are VOLATILE and PER SOCKET (`displayByBlock`, a `"<blockKey>#<socketId>" ->
DisplayHandle` side map - `StationCustodyRetrieval.displayKey/blockKeyOf/socketIdOf` are the
composite-key helpers - with the reverse networkId walk press-F retrieval uses, resolving THE
SOCKET first and then that pile's owner) - a NetworkId is per-world and not boot-stable, so it
never rides the stash; `respawnDisplayIfMissing` rebuilds EVERY socket's prop from the persisted
contents - reached from the unattended pass's HYDRATE WALK within a pass or two of the section
loading (see the unattended section below; budgeted spawns, so a big load never stutters), with the
first-touch call kept as belt and braces.

**SOCKETS (the multi-placement model - see `../asset/CLAUDE.md`'s `Custody` bullet for the
schema).** `Custody.effectiveSockets()` is the ONE resolution (authored sockets folded with the
custody-level defaults, or the synthesized degenerate `main` socket whose leaves ARE the
custody-level values - `asset.SawmillSocketParityTest` is the parity gate). Placement routes
through the pure `StationCustody.routePlacement` (authored order, first accepting Item socket
with room; `PlacePerPress` absent = whole stack; quantity = min of press size / socket room /
block room; the most SPECIFIC refusal survives: `NOT_SHARED` > `FULL` > `WRONG_INPUT`, toasted as
`ui.station.not_shared`/`socket_full`/`socket_wrong_input` for authored sockets and the classic
`no_materials` for the degenerate). Ownership + sharing are the pure `canPlace`/`canUse`/
`canReclaim` cores (decision 82's empty-pile rule: `Share.Place` opens pile CREATION only,
one owner per pile, first contributor owns until drained; `Use` relaxes the engage-over-foreign-
pile deny - the degenerate custody keeps the classic whole-claim `occupied` gate verbatim;
`Reclaim` relaxes retrieval's `NOT_OWNER` branch per socket). `Required` sockets gate ENGAGE
(`firstRequiredSocketUnsatisfied` - an Item socket needs a non-empty pile, a Block socket its
matching world block; deny `ui.station.socket_missing`, `_named` with the socket's `Label`), and
the session snapshots its Required BLOCK sockets (`s.requiredBlockSockets`) for the heartbeat to
re-verify beside `blockGone` - a vanished pot stops the session with `StopReason.SOCKET_LOST`
(`ui.station.socket_lost`), a present-player graceful stop like `ANCHOR_LOST`. A Block socket is
WORLD STATE: `blockSocketSatisfied` composes the `At` offset with the station block's facing
(`StationCustody.blockSocketTarget` over `StationBlockFacing`, quarter-turn exact), reads the
block via zc `BlockOps.blockItemIdAt`, normalizes a state variant onto its base
(`baseItemIdOf`), and matches the item identity (`blockSocketMatches`, fail-closed on air/
unreadable); nothing is stored or refunded for one. `Consume`/`Produce` address sockets by id
(per-entry `Ingredient.Socket` wins over the phase's `Socket`, absent = the first authored Item
socket via `StationCustody.socketIdFor`); the refund ledger's custody half
(`StationSession.iterationConsumedCustody`, keyed `"<blockKey>#<socketId>"`) refunds an
interrupted iteration's drains back INTO each ORIGINATING pile with a null adder (contents
return, ownership never changes; the player-refund half keeps covering inventory-sourced
consumes), and `returnCustody` hands back only the piles the stopping player OWNS - foreign piles
stay standing with their props.

[`StationCustody`](StationCustody.java) stays the PURE decision core (`placeableQuantity` incl.
the socket-aware min-of-caps form, `available`/`drain` + the per-pile `availableInPile`/
`drainFromPile` (each with a Predicate core the four-route `ingredientEntryMatcher` feeds),
`matchesInput`/`matchesAnyConversionInput`/`matchesIngredient` (tags-aware overloads; comparing is
zc `match.ItemMatch`), `exactSetSatisfied` (the `IsExactSet` per-drawn-pile check),
`acceptsFamily`/`pileAcceptsFamily` (per-socket, decision 89), the share cores, the block-socket
cores, `routePlacement`) - zero
engine touch, operating on the claim view (whose detached test constructor wraps a real
`BlockStash`). `toggle` gates a `Custody`-governing action behind ONE state-dependent F:
not-loaded + a matching held stack places/tops-up (`placeIntoCustody`, socket-routed); a foreign
claim denies (`ui.station.occupied` for the degenerate custody, the per-socket share gates for
authored sockets); otherwise falls through to the classic engage flow, sourcing viability from
the claim (`firstRunnableConversionFromCustody`, per-socket availability + the `IsExactSet`
skip). The implicit program's
`Consume` phase reads `From:"Custody"` whenever the resolved action authors `Custody`
(`StationStepHandlers`'s Consume phase, matched over injected `itemId -> resourceTypeId[]` and
`itemId -> rawTags` resolvers, same pattern as `StationToolScaling`; a `Tags` input consumed
`From:"Inventory"` counts/drains through [`InventoryIngredients`](InventoryIngredients.java)'s
slot walk, and a match-any item is custody-only there too).

**Hand-back vs leave-it at stop** (`custodyReturnsAtStop`, pure + test-pinned): every stop whose
player is still present hands custody back through `returnCustody` (room-checked, hotbar-first
via `util.ItemGrantUtil`, else dropped at the block once); DISCONNECTED / SERVER_STOP /
WORLD_CHANGED leave the stash standing in the world - it is persisted, the player collects at the
block later - and those are exactly the paths that can run off the world thread, so they must not
touch chunk state anyway. `releaseAnchorClaims` threads the same flag for remote-anchor custody.
[`StationCustodyBreakSystem`](StationCustodyBreakSystem.java) covers the no-active-session break
(input placed, block broken before a session starts) AND, via its nested `Environment` sibling
(`WorldEventSystem` over `EnvironmentBreakBlockEvent` - the engine fires it INSTEAD of
`BreakBlockEvent` for fire/physics/unattributed explosions), the actor-less break: both funnel
into `onCustodyBlockBroken` (remove stash + drop once + despawn display, no player attribution on
the environment route). Block-state flip (`flipCustodyState` over the extracted `setBlockState`,
reading + writing through zc's `BlockOps`) is HINT-ONLY and self-heals AGAINST THE STASH: a
Loaded state whose stash is truly empty resets to Empty on the next interaction, while a
NON-EMPTY surviving stash makes the Loaded look CORRECT after a restart (the inversion
persistence buys). **Precedence rule (gate m5)**: a block busy with its OWN session OR a
non-empty custody claim REFUSES an incoming anchor claim - and because the claim read is the live
stash resolve, a foreign claim placed before a restart still refuses it.

**`Custody.SingleFamily`** (schema-review wave) locks a NON-EMPTY pile to the first-placed item's
resource family, so a station holds 50 oak or 50 pine but never 100 mixed - SCOPED PER SOCKET
(decision 89: the meat rack locking to beef never stops the herb basket; a socket's own leaf wins,
absent inherits the custody-level one). The pure core is `StationCustody#pileAcceptsFamily`,
called from the ONE placement router, so both the held-item place route and the inventory-scan
fallback honour it; an empty pile accepts anything again. It is orthogonal to `MaxQuantity` (a
capacity of 1 already enforces exclusivity on its own, which is what
`CUSTODY_SINGLE_FAMILY_REDUNDANT` warns about).

## Multiblock structures (`StructurePatternAsset` -> `PatternCatalog` -> `StationStructures`)

A player-built arrangement becomes a station: [`PatternCatalog`](PatternCatalog.java) folds
`asset.StructurePatternAsset` (`Server/RpgStations/Patterns/*.json`, the schema bullet in
`../asset/CLAUDE.md`) and compiles each pattern into TWO zc `world.pattern.BlockPattern` forms
published as one immutable snapshot (sorted by pattern id = the deterministic candidate order):
**DETECT** (the authored cells verbatim - the anchor tests as its pre-activation block) and
**HOLD** (the standing-build re-check - the anchor cell's matcher replaced by `Activate.Block`,
and any cell coinciding with a Block-route socket `At` of the activated station's actions
EXCLUDED, so the pot placed onto its socket never reads as the shape breaking; the station
resolves from `Activate.Block` through `StationService#stationIdForBlockItem`, the same
asset-derived index below). Per-cell payloads are [`PatternCells`](PatternCells.java)
`CellMatcher`s (exact id / resource family / tags over the block's ITEM identity,
base-normalized via `BlockOps.baseItemIdOf` so state variants fold; `Empty` cells match the
engine's `"Empty"` air answer; a malformed both/neither cell matches NOTHING). The placement
`PatternIndex` seeds from every DETECT cell authoring an exact `ItemId`; the catalog recompiles at
every pattern AND station fold plus once post-load.

[`StationStructures`](StationStructures.java) is the runtime, fed by the same two event surfaces
custody listens on. **Placement is DEFERRED**: `PlaceBlockEvent` fires BEFORE the engine writes
the block, so `onBlockPlaced` only pre-filters (index probe + pending-radius check) and hands the
authoritative walk to `world.execute` - which both sees the real placement and re-verifies against
a later listener cancelling it, and keeps the anchor swap from being clobbered by the engine's own
write. The scan walks pending candidates first, then the placed block's own index candidates;
the FIRST completed walk is THE outcome (`decideActivation`, pure): a stash already tagged by
ANOTHER pattern (or a foreign consumer) refuses with `ui.station.structure_conflict`, the same
pattern is idempotent, the pattern's `Requires` gate (evaluated against the placer; creative
places still evaluate it) denies with `ui.station.pattern_requirements_unmet[_named]`, and an
activation swaps the anchor via `BlockOps.setBlock` CARRYING its read rotation (`swapFor`, pure:
skip when the base ids already match - the custom-core style), stamps the stash tag's pattern
segment, feeds `registerKnownStationBlock`, and plays the `activated` moment
(`playPatternMoment`: sounds/particles positionally - the particles through
`StationService.spawnPresentationParticles`, the ONE leak-guarded spawn core - shake/native
payloads on the placer; cues play at once, no session exists to queue a `DelayMs`).

**No stored membership** (decision: re-walk from the index). The one persisted mark is the anchor
stash tag's `|pattern=<id>/<variant>` segment (`StationCustodyClaim`'s tag vocabulary: a
pattern-only tag is `rpgstations:|pattern=...`, upgraded IN PLACE to
`rpgstations:<station>/<action>|pattern=...` by the first engage - `ensureClaimAt` stamps the
custody half in front of the segment, and `stampNewStash` PRESERVES an existing segment). **The
mark must OUTLIVE the custody record**: draining a station's last pile used to remove the stash
outright, which would have erased the mark and left the standing build revert-proof - the three
block-still-stands removal sites (`returnCustody`, `retrieveCustody`, `releaseAnchorClaims`) route
through `removeOrDemoteStashAt`, which DEMOTES a pattern-marked stash to its pattern-only shape
(custody half + owner dropped, so the emptied station is open to the next engager exactly as a
removal would have been) instead of deleting it; only the block-GONE path (`onCustodyBlockBroken`)
still removes outright. The
volatile [`PendingAnchorIndex`](PendingAnchorIndex.java) buys build-order freedom (anchor-first
works: an indexed placement that completes nothing registers its implied anchors; later placements
within `maxBoundingRadius` re-walk just those; bounded per world, evicted on world remove, never
persisted - post-restart a half-built shape completes by re-placing any exact-id block,
documented).

**Break side** (`onBlockBroken`, called from BOTH break systems BEFORE `onCustodyBlockBroken` so
an anchor's tag is still readable): the broken position's pending entries drop and a pattern-only
stash there is removed (anchor broken = clean up only, no swap-back onto air; a custody-carrying
stash stays for the L4 funnel right behind). Then the member re-walk: `holdCandidatesFor` (pure -
WIDER than the index on purpose, testing every HOLD cell matcher so a family/tag-matched ring
block still finds its anchor; Empty cells and the anchor cell skipped) derives each implied
anchor, a pattern-tagged stash there gates the walk, and `holdStands` walks the TAGGED variant
(stale index degrades to any-variant, so a `Rotate` re-tune never demolishes builds) over a
reader with the broken position overlaid as air (`withBrokenAt` - the event fires before
removal). A failed walk reverts: `stopSessionsForStructureLost` (every session at the anchor,
primary or claimed remote, `StopReason.STRUCTURE_LOST` - the present-player hand-back family,
`ui.station.structure_lost`), then the L4 `onCustodyBlockBroken` funnel (drop remaining piles
once, remove stash, despawn props, de-index), the `broken` moment, and the swap back to
`effectiveRevertBlock` (again rotation-carrying). Pure cores + the walk live under
`PatternCompileTest` / `StructureDetectionTest` / `StructureRevertTest` /
`PendingAnchorIndexTest`; the live chunk walk, the deferred-scan timing and the in-game swap
visuals are smoke-owed.

## Anchor discovery: the DERIVED block-item seed (AV wave) + the two denial toasts

Anchor discovery resolves "is there a `cookingfire` near me" through the `blockItemId -> stationId`
index `stationBlockItemToId`. That index used to be LEARNED ONLY - `registerKnownStationBlock`
filled it from an actual F press, and nothing persists it - so on a COLD server (any restart, or a
world nobody has pressed F in yet) the ring scan resolved every scanned block to `null` and the
`PlaceBlockEvent` feed no-oped, and a `Walk` action denied "No Cooking Fire found within 12 blocks"
for old AND freshly placed fires alike. It is now DERIVED from the native assets, zero new
authoring (maintainer ruling):

- **`StationService#seedStationBlockIndexFromAssets()`** inverts how a station block is authored.
  Pass 1 walks the `RootInteraction` asset map, and for each root interaction resolves its
  `getInteractionIds()` through the `Interaction` asset map, collecting `rootInteractionId ->
  stationId` for every entry the engine decoded into this mod's own `StationUseInteraction` (its
  `Station` leaf is a real codec field, so this is an `instanceof` read, never a JSON re-parse - the
  new `StationUseInteraction#getStationId()` is the accessor). Pass 2 walks the `BlockType` asset map
  once, pairing each block's `getItem()` id (the SAME accessor `blockItemIdAt` reads back in the
  world, so state variants fold onto their base item by construction) with the RootInteraction its
  `Interactions.Use` names. `StationAnchors#deriveBlockItemIndex` is the PURE join (case-insensitive
  both sides, lowercased output, first-wins, blanks skipped; unit-tested).
- **Two call sites, both idempotent**: the `StationAsset` `LoadedAssetsEvent` fold, and once more at
  the first `PlayerReadyEvent` immediately before `StationValidator.runAndLog()` (a native
  Item/BlockType layer from a later pack can settle AFTER the station fold fires - the same timing
  reason the FULL validator pass is deferred there). Try-guarded at both altitudes: a malformed
  entry skips with one warn, a total failure logs, nothing ever throws into the fold.
- The F-press learning and the `PlaceBlockEvent` feed STAY as harmless redundancy (they re-write an
  identical entry once the derivation already covered a block).
- **Validator**: `ANCHOR_STATION_NOT_DISCOVERABLE` (warn-only) rides beside
  `ANCHOR_STATION_UNKNOWN` - an `Anchors[].Station` naming a station that EXISTS but that no block
  item maps to is undiscoverable until a player interacts with such a block.
  `StationValidator#stationDiscoverableLive` fails OPEN on an EMPTY index (unseeded fold /
  unit JVM), exactly as `benchIdKnownLive` does for a cold Item map.

**Two failures, two toasts** (they shared one before): `ui.station.anchor_missing` ("No {0} found
within {1} blocks") is the DISCOVERY miss; `ui.station.anchor_unreachable` ("The {0} nearby cannot
be reached") is the walk-targeted anchor whose engage-time `PuppetNav.solve` fails - the block was
found, it just cannot be pathed to. Both denials roll back every partial claim.

## The ACTIVELY-WORKING block state (`Custody.States.Working`, AV wave)

The maintainer ruling is **"Lit = actively cooking"**: a station block's working look (the cooking
fire's flames) must be on ONLY while work is genuinely running there, never merely because input
was placed. That is `asset.Custody.States`' nullable `Working` leaf (`../asset/CLAUDE.md`), driven by
two package-private seams on `StationService`:

- **`enterWorkingState(session, anchorId)`** resolves the anchor through the SAME
  `anchorBlockKeyFor` the step phases use, so ONE call covers both altitudes: the PRIMARY block
  (absent/`"self"`) and a CLAIMED REMOTE ANCHOR. It is IDEMPOTENT per block (re-entering the same
  block never re-writes the state, so a repeating single-step convert program holds a steady look
  instead of flickering once per cycle) and exits any previously-working block first, so at most
  one block per player is ever left working (`workingByPlayer`, a transient `UUID -> WorkingFlip`
  map, never persisted).
- **`exitWorkingState(session)`** returns the block to its RESTING look
  (`StationDoneness.restingStateName`): `Loaded` (a claim still stands there), `Ready` (an open
  doneness window's batch waits), `Overdone` (a collapsed pile), or `Empty`. Idempotent, so every
  "work is no longer running" moment can call it freely.

**Which steps count as work** is `StationStep.effectiveIsWork()` (derived default: a
`Consume`+`Produce` atomic-transform CONVERT is work, everything else is not; an authored
`"IsWork"` boolean overrides either way). **Flip sites, the complete set:**

1. `toggle`'s engage, for a CLASSIC (non-`Steps`, non-idle) session - the implicit program has no
   authored step to light on entry and its first conversion only commits a full `CycleMs` later,
   but that whole `CycleMs` IS the work, so it lights from engage rather than a cycle late.
2. `StationStepHandlers.runIterations`, per iteration, POST-walk: a working step enters at its
   `At` anchor, any other step exits.
3. `runIterations` again, at a `Walk` phase's departure (dark while travelling, per the ruling).
4. `stop()`, unconditionally, AFTER `releaseAnchorClaims` + `returnCustody` so the Loaded-vs-Empty
   read sees the post-return truth. This ONE call is what covers every stop reason with no
   per-reason hook - `RITUAL_COMPLETE`, `INPUTS_EXHAUSTED`, `ANCHOR_LOST`, `PATH_BLOCKED`,
   `STEP_FAILED`, `TOOL_CHANGED`, damage, death, disconnect, world change, shutdown - because a
   failing step program reaches `stop()` through `dispatchProgram`'s `Failed` branch.

`releaseAnchorClaims` also now resets each released anchor's block state to Empty (mirroring
`returnCustody`'s long-standing flip for the primary block, and skipped when a FOREIGN claim still
stands there): a program can hand an anchor its Loaded look and harvest it empty several steps
later, which without this would strand a "has input" hint over nothing.

The raw write is the extracted `setBlockState` (one guard set over zc's `BlockOps`, returns
whether the write landed); `flipCustodyState` is now a thin Empty/Loaded wrapper over it. A
disconnect/shutdown stop has no world to write through, so the block can be left wearing its
Working look until the next interaction, where `toggle`'s self-heal settles it against the
persisted stash (non-empty keeps Loaded correct, empty resets to Empty).

**Crackle + embers are NATIVE, zero engine work.** `BlockType` carries a per-state
`AmbientSoundEventId` (LOOPING+MONO validated, "a looping ambient sound event that emits from this
block when placed") and per-state `Particles`; both start and STOP automatically with the
`setBlockInteractionState` flip, which matters because nothing in the protocol can stop a playing
sound or particle system. The SHIPPED `RPG_Station_CookingPit` block copies vanilla
`Furniture_Crude_Brazier` verbatim on its `Lit` state for this (the held-back
`RPG_Station_CookingFire` under `unreleased/Server/Item/Items/` is the same pairing), and the
shipped `RPG_Station_Cooking_Pot` block carries the vanilla cauldron's always-on bubbling ambient
the same native way. Corollary for step `Presentation.Sound`: only ever
author a ONE-SHOT SoundEvent there - a looping id fired as a one-shot never ends (both cauldron
bubbling events loop, which is why the pit's Stew cycle cue is a one-shot slosh and the bubbling
lives on the pot block).

## Doneness: the lazy ready window (decisions 87/88)

A `Produce.To:"Custody"` batch under a resolved `Recipe`/`Conversion.Doneness` (the per-leaf fold
is `asset.StationAsset.Doneness.resolve`, conversion over recipe - see `../asset/CLAUDE.md`) sits
READY in its pile for `ReadyMs` of WORLD GAME TIME, then collapses once to the authored `Overdone`
items. The pieces:

- **The clock is game time** (`StationService#gameTimeMs`: `WorldTimeResource.getGameTime()`, the
  native processing-bench precedent) - it stands still while the server is down, so an outage
  advances every window by exactly zero (pinned by `StationDonenessTest`'s codec-round-trip case).
- **The window RECORD is persisted stash state** (`StationCustodyClaim`'s doneness accessors): the
  stash-level `ProgressGameTime` leaf is the window's (re)start; the windowed pile carries
  `StationDoneness.BATCHES_KEY` (`"doneness:batches"`, the produced-batch count) in its
  `PendingCycles` map, and a collapsed pile wears `"doneness:overdone"`. The whole
  `"doneness:"` `PendingCycles` prefix is RESERVED - the unattended pass's per-conversion accrual
  keys (`StationUnattended.accrualKey`, `accrual:conversion:<resolvedIndex>`) live beside it in the
  same map, never inside it. The stash-level `LastGameTime` leaf is deliberately untouched by
  doneness: it is the unattended pass's last-settled catch-up clock.
- **One window per stash, opened/re-stamped by `StationService#noteCustodyProduce`** - called ONCE
  per committed produce PHASE from `StationStepHandlers.producePhase` (never per item; a
  multi-socket phase's window sits on its FIRST produced socket). Every batch re-stamps the clock
  ("stirring the pot"); only the FIRST fires the `ready` moment + `ui.station.output_ready` toast
  and flips `States.Ready` (skipped while a Working flip holds the block - the resting flip shows
  Ready at the next stop instead).
- **The settle is LAZY and there is ONE core**, `StationService#settleDoneness` (the
  `settleDonenessAt` convenience resolves the claim's own recipe-level fold): called at the
  toggle/placement first touch (before anything reads the claim), press-F retrieval (before the
  decide, so an overdue gather retrieves the settled items), and the engaged session's heartbeat
  (throttled by `DONENESS_SETTLE_MS`, guarded on the `s.doneness` engage snapshot); and the
  UNATTENDED pass's per-block settle calls the same function with its CONVERSION-level fold
  (`donenessFoldFor`: the row the windowed pile's own accrual key recorded, else the recipe-level
  fold - the conversion-over-recipe precedence exercised sessionless). Expiry is
  boundary-exact (`StationDoneness.expired`: `elapsed >= ReadyMs`).
- **The collapse rule ("one pot, one fate")**: the WHOLE windowed pile's counted tally is replaced
  by the valid `Overdone` entries (exact-`ItemId` only; others ignored) scaled by the batch count
  (`StationDoneness.overdoneReplacement`); the pile's owner and `Unique` stack are untouched, no
  other pile is touched (per-socket isolation), the window clears, `States.Overdone` flips, the
  pile's display prop despawns (the hydrate/first-touch respawn rebuilds it from the settled
  contents), the `overdone` moment fires - through an engaged session's cue queue when one works
  the block (`sessionAt`), else immediately at the block via `playPresentationAt` (the ONE
  sessionless playback core `StationStructures`' pattern moments also delegate to) - and the
  toucher gets `ui.station.output_overdone`.
- **The D38 window case**: `pilesToHandBack` (pure, both hand-back paths read it - `returnCustody`
  and the anchor sweep) EXEMPTS the open-windowed pile from every present-player stop hand-back:
  the produced batch belongs to the pile and the window keeps running - it is world state now,
  gathered later (press-F) or expiring where it stands. A stop neither refunds it (the produce
  already cleared the iteration ledger) nor duplicates it (`StationRefundLedgerTest`'s window
  case).
- **Gather clears**: retrieving the windowed pile takes the batches key with the pile and clears
  the stamp; the block resets per what remains. `removeOrDemoteStashAt`'s demote branch nulls BOTH
  game-time clocks (custody bookkeeping dies with the custody half). A window whose content no
  longer resolves one (a recipe edit) closes silently at the next settle; `ReadyMs` with no valid
  `Overdone` is purely presentational and clears only at gather.

Pure cores in [`StationDoneness`](StationDoneness.java) (boundary math, replacement rule,
resting-state pick), record ops on the claim, orchestration on `StationService`; the moment ids
`ready`/`overdone` are well-known (`StationFlairs.MOMENT_READY`/`MOMENT_OVERDONE`), so flairs
overlay them like any cue.

## Unattended processing (decision 90)

A custody-loaded block whose committed action authors `Work.Unattended` (group presence = opt-in;
`Enabled` exists for a Parent child to flip it off; `MaxCycles` default 24 caps ONE settle burst
AND one gather's payout; `CatchUpMaxMs` default 24h mirrors the native processing bench) keeps
settling its recipe conversions while nobody is engaged. Three classes, three altitudes:

- **[`StationUnattended`](StationUnattended.java)** - the PURE core: the catch-up math over the
  stash's `LastGameTime` clock (world GAME time - an outage settles zero; `usableElapsed` caps at
  `CatchUpMaxMs`, `rawCycles` floors over the row's cycle pace), the bank-vs-forfeit clock rule
  (`advancedLastGameTime`: an UNCLAMPED settle banks the sub-cycle remainder; a clamped one -
  inputs, room, `MaxCycles`, or no runnable row - forfeits the backlog so a top-up never
  burst-pays idle hours), the analytic `settle` (first runnable row by tier over per-pile
  availability + `IsExactSet` + NET-FLOW custody room, drained/produced/Yield-applied as one
  batch, the produce pile inheriting the FIRST-consumed socket's owner per decision 82), the
  accrual namespace (`accrualKey` = `accrual:conversion:<resolvedIndex>` - the L7 picker row-key
  channel, NEVER inside the reserved `doneness:` prefix), and the gather plan (`gatherPlan`
  allocates the `MaxCycles` budget across accrued keys in pile order; `scaledByCycles` multiplies
  the idle-scaled per-cycle contributions). Pinned by `UnattendedCatchUpTest` +
  `UnattendedGatherTest`; the D38 no-refund-ledger invariant by `StationRefundLedgerTest`'s
  appended case (a settle has no session - nothing can ever queue a refund).
- **[`UnattendedIndex`](UnattendedIndex.java)** - the volatile per-boot index: unattended-capable
  block keys plus hydrated-section markers, fed by (a) a LIVE stash write at an opting-in action
  (`registerUnattendedIfEnabled` in `toggle`'s placement + `produceIntoCustody`), (b) the HYDRATE
  walk, and (c) lazy eviction (a visit finding the section unloaded drops the block AND re-arms
  its section marker so a reload re-seeds; a gone stash just evicts; a broken block evicts in
  `onCustodyBlockBroken`; `forgetBlockKeyedState` sweeps it by the same world-prefix predicate as
  every other volatile map).
- **`StationService#tickUnattended`** - the impure orchestration, riding `tickFrameOnce` OUTSIDE
  the session early-return, throttled per world to `Limits.UnattendedIntervalMs` (default
  1000ms). Each pass: (1) `hydrateLoadedSections` - the first-party loaded-section iteration
  (`chunkStore.getStore().forEachChunk(ChunkSection.getComponentType(), ...)`, the exact walk the
  engine's own `SectionUnloadingSystem` runs; world coords via
  `ChunkUtil.worldCoordFromLocalCoord`), at most `UNATTENDED_HYDRATE_SECTION_BUDGET` (64) NEW
  sections a pass, discovery inside the walk and all processing DEFERRED after it; each found
  stash seeds the index, respawns missing props under `UNATTENDED_PROP_SPAWN_BUDGET` (32,
  re-arming unfinished sections), and heals the resting block state - this walk RETIRES the
  first-touch-only display interim (the touch paths stay as belt and braces). (2)
  `visitUnattendedBlocks` - per indexed block: live-session skip (`StationUnattended.shouldVisit`
  - attended is the authority), then `settleUnattendedAt`: re-verify capability (evict when
  content changed), doneness settle FIRST through the CONVERSION-level fold (`donenessFoldFor`
  reads the windowed pile's accrual key), the pure settle, then the impure edges - doneness batch
  stamping (one `noteDonenessBatch` per settled cycle; a fresh window flips `States.Ready`),
  display refresh, resting-state flip, ONE `markDirty` for a TRANSFORM only (a clock-only stamp -
  first anchor or forfeited backlog - stays best-effort in the loaded section rather than flagging
  the chunk for a save every pass for every input-starved station; an unsaved unload costs at most
  one MaxCycles-capped burst). TRANSFORM ONLY: no rolls, no commands, no
  worker moments. Steps/Anchors actions run attended-only (`UNATTENDED_WITH_STEPS`/`_WITH_ANCHORS`
  warn; `UNATTENDED_WITHOUT_CUSTODY` for the unplaceable case), and
  `DONENESS_WITHOUT_PRODUCE_SOCKET` is EXEMPT for an unattended action (its settle produces into
  custody by construction). **A Required BLOCK socket gates the settle** exactly as the attended
  heartbeat's `SOCKET_LOST` re-check does: `requiredBlockSocketsStand` failing hands the settle NO
  conversions, so the clock stamps forward and the backlog FORFEITS (the same no-runnable-row
  posture as input starvation - re-mounting the pot never burst-pays), while the doneness settle
  above still runs (a batch standing in its pile keeps aging whatever happened to the socket
  block).
- **The gather payout** (`grantAccruedAtGather`, called at press-F retrieval, `returnCustody`'s
  hand-back and `releaseAnchorClaims`' both branches, BEFORE the piles leave the stash): drains
  the accrual keys (`drainAccruedCycles` - doneness keys untouched), caps at `MaxCycles`, builds
  ONE gatherer factor snapshot (`buildGatherFactorContext` - decision 90: factors resolve against
  the GATHERING player; the pile owner does NOT gate, `Share.Reclaim` already did), forwards
  `Work.PerCycleContributions` at the idle rate x the gatherer-resolved `ContributionScale` x the
  granted cycles, replays the effective `Bonus` ONE `Cycle`-trigger pass per granted cycle (at
  most 24 passes, so no batched-Repeat approximation is needed; `applyGatherGrantResult` is the
  sessionless `applyGrantResult` twin - items/droplists/commands/effects land on the gatherer,
  cues play via `playPresentationAt`, `OutputItems` pays the accrued conversion's primary output,
  and a replayed roll's one-shot `rpgstations:contribution` grants are DROPPED, the documented
  boundary), then fires `StationUnattendedGatheredEvent` (gatherer never null). Breaking the block
  forfeits accrual with the stash, like the doneness window.

**Sections have no listenable unload for this mod's purposes** (the engine's `SectionUnloadEvent`
is dispatched only for cubic sections, not the common column-bound ones, and via a `ChunkStore` ECS
invoke this mod registers no system for), which is why dehydrate is LAZY by design: an unloaded
section answers no claim, the visit evicts and re-arms, and a reload re-hydrates. Smoke-owed: the
live loaded-section walk, prop-respawn timing in game, a real long catch-up, and the gather flow
end to end.

## The placed-input PLACED-AS-ENTITY visual

[`StationCustodyDisplay`](StationCustodyDisplay.java) spawns a static, network-replicated,
pickup-immune, physics-free prop entity rendering a pile's placed item at the station's
block-top anchor, gated on the SOCKET's own `Display` group (the custody-level `Display` IS the
degenerate socket's). Each socket with a `Display` renders its OWN prop; a socket without one
renders nothing. Block-shaped items (the sawmill's placed logs) spawn a real `BlockEntity`;
everything else (the anvil's placed weapon) spawns a bare `ItemComponent` prop. Both routes
`ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType())` - the PROP never survives
a restart, and the persisted stash does, which is why `StationService#respawnDisplayIfMissing`
rebuilds every socket's prop from the stored contents - from the unattended pass's hydrate walk
shortly after the section loads (budgeted, `UNATTENDED_PROP_SPAWN_BUDGET`), and from the
first-touch paths as belt and braces. Both the
ref AND the spawned entity's own `NetworkId` live in `StationService`'s VOLATILE `displayByBlock`
side map, keyed `"<blockKey>#<socketId>"` (a `DisplayHandle` record, captured together at spawn
via `spawnDisplayIfAbsent` - a NetworkId is per-world and not boot-stable, so neither may ride
the stash); spawned once at first placement into that socket, despawned at whichever removal path
fires first (hand-back, retrieval of that pile, block break).
`Offset`/`Rotation` are FACING-RELATIVE to the placed block's own yaw (via the shared
[`StationBlockFacing`](StationBlockFacing.java)`.yawRadians`, which reads
the block's live `BlockSection#getRotationIndex` (via `World#getChunkStore()` ->
`getChunkSectionReferenceAtBlock`), try-guarded to yaw 0 on any failure, plus its `rotateOffset` core -
the SAME one-reader helper the puppet engine composes against since the round-3 smoke) - see
`../asset/CLAUDE.md`'s `Custody.Display` bullet for the full authoring convention. Press-F RETRIEVAL
([`StationCustodyRetrieval`](StationCustodyRetrieval.java)) resolves the clicked display entity's
`NetworkId` back to its owning (blockKey, SOCKET) pair - the composite display key resolves THE
SOCKET first, then the eligibility runs against exactly that socket's pile - and routes through the
pure `decide` (precedence: `UNKNOWN_TARGET` -> `BUSY` -> `NOT_OWNER` (the pile's owner, relaxed by
that socket's `Share.Reclaim`) -> `NOTHING_TO_RETRIEVE` -> `RETRIEVE` - a session actively working
the block always wins over ownership checks); a `RETRIEVE` hands back THAT pile only, foreign piles
stay standing. The BUSY input comes from
`sessionWorkingAt(blockKey)`, NOT from `byBlock` alone: the engage claim only writes that map for an
EXCLUSIVE station's primary block, so a `Block.Exclusive: false` bench had nothing standing between
a press-F retrieval and the materials its own running session was mid-way through consuming. The
occupancy map stays the fast path and a sweep of live sessions (primary block or claimed anchor) is
the backstop. **The match is
WORLD-SCOPED (`StationCustodyRetrieval#owns`), not global**: a `NetworkId` is issued from a
per-world counter that starts at 1 in EVERY world, so the same integer routinely names a different
entity in each loaded world, and an unscoped match could resolve a claim in a DIFFERENT world and
hand over its contents. `owns(blockKey, worldPrefix, claimDisplayNetworkId, targetNetworkId)`
requires BOTH the network id match AND `blockKey.startsWith(worldPrefix)` (the presser's own
`"<worldUuid>:"`, the exact prefix `StationAnchors#blockKey` already encodes).
The `displayByBlock` side map records each prop's `NetworkId` at spawn time, so
this walk reads NO live components at all (it used to fetch `NetworkId` off every claim's display
entity, across EVERY world, on every single press). **A successful `RETRIEVE`
plays the presser's own COLLECT gesture** (`StationService#playCollectAnimation`, round-3 smoke):
the native `"Interact"` clip on the `Action` slot against the held item's own
`ItemPlayerAnimations` set (falling back to the engine's item-agnostic `Default` set), fired with
`sendToSelf=true` so the presser sees their own reach - the engine ships no clip literally named
Collect/Gather/Pickup (checked across all 38 `HytaleAssets/Server/Item/Animations/*.json`
catalogs), and `"Interact"` is what native interactions themselves use for this moment shape (22
vanilla `Effects.ItemAnimationId: "Interact"` sites, e.g. `Crops/Seed_Place.json`). The clip id is
the engine constant `StationService.COLLECT_ANIMATION_CLIP`, deliberately NOT authored content (one
fixed gesture, not a per-station knob) - retune or disable it (null/blank) in one line. Fully
try-guarded and fired only inside the `RETRIEVE` branch, so every denial/no-op path is untouched.

## The anvil arc - the Stamp step + roll/cap engine (mechanism unchanged; Caps reshaped)

The shared `loot.stamp.StampCapEngine` (pure, unit-tested in the library) is called
ONLY from `StationStepHandlers`'s Stamp phase handler: compute-then-commit (roll + weighted-pick/
`Picks`/`Unique` + cap-clamp validated with ZERO mutation first, then reagent consumption and the
weapon mutation each run under their OWN try/catch that restores exactly what was consumed on
failure - `claim.setUniqueStack` is the LAST line, reached only on full success). **Caps
composition is re-anchored on the scope-2 `Budgets[]` shape** (`../asset/CLAUDE.md`'s Stamp
bullet: MIN over every `Budget` entry, `PerStat` layered on top, `Economics` unchanged) - the
engine's MIN-composition RULE is identical to pre-scope-2, only the authoring shape changed;
`ActionResolver.selectActionByFamily` (a DIFFERENT NAME from
`selectAction`, never an overload) is the resource-type-FAMILY-aware selection entry
`StationService` calls from `selectActionForHeld`/`liveFunctionOf`. `StationCatalog`'s
`resolvedConversions(asset, actionId, recipe)` caches the derived conversions per
`"<station>::<action>"` - one recipe per action, so an action id fully identifies the entry; the
`recipe` argument is the caller's already-`ActionResolver`-resolved `Recipe`, never re-derived
here. `StationService
.dispatchProgram` reads the resolved action's `Work.effectiveLooping()` and calls
`stop(..., StopReason.RITUAL_COMPLETE, ...)` on a completed non-repeating program; a
non-repeating authored Steps program (e.g. the anvil's Enhance) gets INSTANT first dispatch
(`s.nextCycleAtMs = now`, no `CycleMs` latency eaten before the ritual's only cycle).
**Enhancement outcome reporting** (`StationEnhanceOutcome` on `StationSession
#enhanceOutcomes`, `StationEvents#fireEnhanceCompleted`, `StationService#enhanceLedgerRows`) is
vocabulary-agnostic - a stamper line with a `label` renders verbatim, one WITHOUT a label falls back
to the engine's plain `<statId> +N` row (`ui.station.summary.enhance_stat`), plus one engine-owned
`Durability +N` row, so a bare anvil with no registered stamper still reports its durability
enhancement. **A ledger row must never carry nothing** - its text goes straight to the client, and a
summary silent about the stats a ritual just applied reads as a ritual that did nothing; that is why
the unlabelled path has its own fixtures in `StationEnhanceLedgerRowsTest`, since `EnhanceLine.of`
(the only factory a Stamp step uses) always leaves the label unset. The stamper contract itself is
the shared `loot.stamp.Stamper`, installed through the static `StamperRegistry`; an `EnhanceLine`
carries the stat id and its points with the display label left to a consumer (see the repo's
`api/src/main/java/com/ziggfreed/rpgstations/api/CLAUDE.md`). The
shipped Anvil content lives in its own pack's repo.

## The puppet presentation engine (unchanged)

[`StationPuppetController`](StationPuppetController.java) drives ONE `ziggfreed-common`
`entity.performer.StationPerformer` (seam wave decision 47/48/55) for "mount the player, hide their
player model, and spawn/display a visual of their character model performing the steps" - sibling
to `StationEntityMountController`/`StationHoldController`. **The performer swap (decision 55):** at
engage `spawnAndHide` reads `Puppet.Look.Source` (`resolveLook`) and picks the backend
(`createBackend`): `PlayerClone`/`Model` -> `HolderPerformer` (the crowned bare-Holder puppet,
byte-parity with the pre-swap route), `NpcRole` -> `NpcRolePerformer` with an engage-time
FAIL-CLOSED fallback to the Holder (one warn) when the role id is blank/unregistered. The performer
lives on `StationSession.performer`; every later mutation threads a FRESH per-call accessor
(clip/loop/step-clip via the live `store` = a network packet; `setProp`/despawn via the tick-safe
`commandBuffer` = a Hotbar/entity mutation - the exact pre-swap split, so a `CommandBuffer` is never
captured across frames). `s.puppetRef` is RE-POINTED at `performer.ref()` every frame by
`StationPuppetController#refreshPuppetRef` (F2 part a, called from `tickFrameOnce`), covering the
`NpcRole` backend's one-tick deferred-spawn window (its `ref()` is null at engage, non-null once the
deferred spawn lands) so the direct-puppetRef readers (the `storeFor` store fallback, custody
retrieval) never go stale. The `Walk` phase itself drives the performer seam
(`s.performer.walkTo`/`WalkHandle`, F2 part b - `StationService#resolveWalkTarget` supplies the anchor
target, the backend re-solves the path), not the raw ref. Identity/reconcile: ZiggfreedCommon's own
wiring root registers `PerformerIdentityComponent` at ITS setup (not this mod's); `RpgStationsPlugin`
runs a once-per-world `PerformerReconciler.sweep(bootDespawnAll)` at first ready
(`StationService.reconcilePerformersAtBoot`); `toggle` fires a deferred `engageStale`
sweep (`reconcileStalePerformersAtEngage`, via `world.execute` so the native sweep runs outside the
processing lock). **Legacy mechanics carry over below.** **Spawn + hide, at engage**
(`spawnAndHide`, called from `toggle` AFTER the mount-attach block): resolves `Puppet.Offset` (the
shared `Vec3`) / `Puppet.Rotation` off the block-top anchor + the initial `Puppet.Prop`, spawns via
`PlayerPuppetService.spawn`; a null spawn is non-fatal (session continues in-body).
**`Offset`/`Rotation.Yaw` are
FACING-RELATIVE to the placed block's own yaw** - authored `+Z` = the
block's FRONT, `+X` = its right, `Offset.Y` vertical, block yaw folded additively into the authored
`Yaw` - the `Custody.Display` precedent applied to the puppet, because world-space `Offset`
meant which SIDE of the sawmill the worker stood on depended on how that block happened to be
placed. The block-yaw read and its trig are the ONE shared helper
[`StationBlockFacing`](StationBlockFacing.java) (`yawRadians` over the block's live
`BlockSection#getRotationIndex`, reached through `World#getChunkStore()`, try-guarded to yaw 0;
`rotateOffset` the pure horizontal-rotation core), which
`StationCustodyDisplay` now calls too - one reader, never a copy-pasted trig block. The
per-consumer composition is the PURE, unit-tested `resolveWorldOffset`/`resolveYawRadians`
(`StationPuppetControllerTest`), IDENTITY at yaw 0 so every in-game-tuned value is byte-identical
on a default-facing placement. Only the engage-time spawn resolves position/yaw; a per-step
`Puppet` override carries `Clip`/`Prop` only and never re-places the puppet. Only `Hide.Route:"Scale"`
actually hides (`hideByScale`/`revealByScale`); `"Effect"`/`"None"` apply no hide. **Reveal +
despawn** happens in the ONE idempotent `stop()` funnel (`revealAndDespawn`, right after
`returnCustody`), resolving its own store so a disconnect/shutdown stop still reveals + despawns.
**Rotation**: `Puppet.Rotation.Yaw` folds with the block facing exactly as the old scalar `Yaw` leaf
did (`resolveYawRadians`, identity at yaw 0); `Rotation.Pitch`/`.Roll` are the puppet's OWN tilt and
are NOT block-composed. The spawn context carries a yaw alone, so a non-zero tilt is applied ONE
FRAME LATER through the performer's own `presentAt(accessor, pos, yaw, pitch, roll)` overload
(`StationSession.puppetStance`/`puppetTiltPending`, drained by
`StationPuppetController.applyPendingTilt` from the same per-frame hook as `refreshPuppetRef`). That
timing is not a workaround to remove: it is also what covers the `NpcRole` backend's deferred spawn,
whose ref is honestly null at engage. A puppet authoring no tilt never sets the flag, so it keeps the
spawn placement untouched.
**Animation routing**: a puppet-active session supersedes `useActionSlotForSwing` entirely and
picks its OWN swing slot through the pure `StationPuppetController.useActionSlotForPuppetSwing`,
fed by the already-existing `resolveEffectiveClip`. An `Emote`-slot clip is the OPT-IN full-body
override and WINS whenever one resolved (`Animation.EmoteId`, or the in-flight step's own
`Puppet.Clip`) - the puppet has no sit pose to fight, so nothing forces it off that slot the way a
seat-mounted real player is forced off. With NO emote clip resolved (the shipped shape: an
`Animation` group authoring `Swing` but no `EmoteId`) the swing rides the `Action` slot instead
(`playActionSlotSwing`), firing the resolved `Animation.ActionClip` against the animation set of
the item the worker HOLDS - the SAME `StationHoldController.heldItemAnimationsId` /
`effectiveActionClip` resolution the seat-mode route uses, which lands on the right profile
precisely because the puppet mirrors that item into its own Hotbar. Before this the puppet knew
only the `Emote` route, so an emote-less station's puppet played NOTHING. Either slot keeps the
`Swing.IntervalMs` re-fire cadence (that re-fire is why a viewer arriving mid-session sees the
clip within one interval - never convert it to a play-once); both slots ride the identical engine
dispatch, whose model-lookup guard exempts `Action` and `Emote` identically. The engage-time clip
(`playLoop`) follows the SAME slot choice: an authored emote starts its `Emote`-slot loop at
engage, and with none authored the Action-slot work clip fires once immediately at engage too, so
the worker is never idle for the first `Swing.IntervalMs`. **Per-step sync**: a `StationStep.Puppet.Clip` plays once at step ITERATION ENTRY
(`StationStepDecisions.shouldPlayClipOnEntry`, resume-safe via `resumingStep` identity); the
generic engage/swing clip is suppressed for a stepped program whose steps author any clip
(`StationSession.stepProgramAuthorsClip`); a `StationStep.Puppet.Prop` override syncs at the SAME
step-entry point (`shouldSyncPropOnEntry`) and is NOT gated on the step authoring an override -
every fresh step entry syncs to that step's effective prop (its override, else the session
default), so moving past a prop-overriding step reverts the prop. **All mutation through this
engine runs via `CommandBuffer`, never a raw `store` mutation** - `hideByScale`/`revealByScale`/
`despawn`/the `Hotbar` component swap all throw `IllegalStateException("Store is currently
processing!")` if called on the live store from inside `toggle()`/the heartbeat frame drain (both
run inside the store's processing lock); route every new call site through the `CommandBuffer`
parameter these methods already thread, never add a raw-`store` mutation here. **Safety net**:
`reassertOnReady` unconditionally clears any lingering `EntityScaleComponent` and restores the
correct cosmetic model on every fresh `PlayerReadyEvent` (a restart wipes every in-memory session
by construction) - not gated on any remembered session.

## Loot + flairs, the open vocabulary (unchanged mechanism; `LootRef` terminology)

[`StationFlairs`](StationFlairs.java) resolves the per-player cosmetic overlay for a moment id
against the UNION of every registered api `FlairUnlockProvider` (persistence stays outside this
engine; it stores no per-player fact). The plugin seeds that union with its own
[`ZigFlairUnlockProvider`](ZigFlairUnlockProvider.java), a read of ziggfreed-common's persisted
`ZigFlairComponent` unlocked-flair set, so unlocks resolve with this jar and the library alone;
a mod with a genuinely foreign unlock store registers its provider beside it. The open STRING moment id vocabulary
(`MOMENT_CYCLE`/`MOMENT_SWING`/`MOMENT_IMPACT`/`MOMENT_RARE_FIND`/`MOMENT_COMPLETION`, plus
`stepMomentId(actionId, stepId)`) is unchanged. The flair map is the merge of TWO sources
([`FlairCatalog`](FlairCatalog.java)`.effectiveFlairsFor`): a station's own inline `Flairs`
(`asset.StationAsset.Flair`, `{Moments}`) UNIONED with every folded `asset.FlairAsset` whose
`Stations` list applies - a same-flair-id `FlairAsset` entry wins. `api.impl.StationViewImpl
.flairIds()` and `StationCatalog.allFlairIds()` both reuse the SAME merge point. See
`../loot/CLAUDE.md` for the `LootRef`/`Roll` evaluation engine this package calls into per cycle
and per step `Roll` phase (both routes are the SAME `loot.StationLootEngine` call - one roll engine,
whether the source is a station's implicit cycle or an authored step's `Roll` phase) - including the
SMART-CUE rule, which decides over there which `MOMENT_RARE_FIND` cues this package is ever handed
(see the `emitMoment` section above: a `Roll` carries its own top-level `Cue` beside the
per-floor one, and a cue paired with grants rides only once those grants produced something).

## Engine settings + Validation (unchanged mechanism; new checks)

[`SettingsCatalog`](SettingsCatalog.java) holds the folded `asset.RpgStationsSettingsAsset`
singleton. [`StationValidator`](StationValidator.java) keeps its two-pass structure and warn-only
posture: `validateStructural()`/`runStructuralAndLog()` runs at EVERY asset-load fold (every
check except cross-layer reference-existence ones); `validate()`/`runAndLog()` (the FULL set)
runs ONCE post-load from the first `PlayerReadyEvent` and on demand from `/rpgstations validate`.

**The result vocabulary is ziggfreed-common's, and this mod owns no copy of it.**
`com.ziggfreed.common.validation.{Finding, Severity, ValidationReport}`, imported by short name:
a finding is `{severity, code, message, sourceId, domain}` built via
`Finding.error/warning/info(DOMAIN, code, message, sourceId)`, and `ValidationReport` supplies
`summarize`/`problemCount`/`format`/`logAll`. `StationValidator.logReport` is the ONE log shape
(the headline, WARN when `problemCount > 0`, then `logAll` with `Log::warn` for errors and
`Log::info` for the rest); its per-line format is the library's
`Station validation '<sourceId>' [CODE]: message`, so nothing here formats a finding by hand.
The one thing that stays local is `atBlock(shared, label)`: it prefixes a shared loot-engine
finding's message with the authored block (`Station[sawmill].Actions[work].Bonus.Rolls[0]`), which
the loot validator cannot know, and re-files it under this engine's `DOMAIN`. Diagnostic messages
are raw English by convention (an admin/log surface, not player-facing).

The lang-key check (`langKeyKnownLive`) is a MERGED-view check: a miss against the jar's own
`i18n.RpgStationsLangKeys` falls through to a LIVE `I18nModule.getMessage` query, so a pack's own
additive `rpgstations.lang` overlay resolves correctly. **Unattended checks (decision 90)**: `UNATTENDED_WITHOUT_CUSTODY` /
`UNATTENDED_WITH_STEPS` / `UNATTENDED_WITH_ANCHORS` (all warn-only, on an effective
`Work.Unattended` that is enabled - `ActionResolver.effectiveWorkOf` resolves a `Ref` entry's
group), plus the `DONENESS_WITHOUT_PRODUCE_SOCKET` exemption (an unattended settle lands produce
in custody itself, so that warn is suppressed for an unattended-enabled action).
**Multi-station/extension checks**:
`ACTION_REF_UNKNOWN`, `EXTENSION_TARGET_UNKNOWN`, `EXTENSION_PAYLOAD_MISMATCH`,
`EXTENSION_KEY_COLLISION`, `EXTENSION_ANCHOR_MISSING`, `EXTENSION_STEP_MISSING_ID`,
`ANCHOR_STATION_UNKNOWN`, `ANCHOR_STATION_NOT_DISCOVERABLE` (see the discovery-seed section
above), `WALK_TARGET_UNKNOWN_ANCHOR`, `STEP_AT_UNKNOWN_ANCHOR`, `WALK_REQUIRES_PUPPET`.
**Action-first checks** (the restructure's own new coverage): `STATION_NO_ACTIONS` (an
empty/absent `Actions[]` leaves the station permanently inert), `ACTION_MISSING_ID`/
`ACTION_DUPLICATE_ID`/`EMPTY_ACTION_ENTRY`/`ACTION_NO_BODY` (an inline entry with neither a `Ref`
nor any of its own groups authored), `AMBIGUOUS_ACTION_INPUT`/`UNREACHABLE_ACTION` (a later
action's `Select` can never win because an earlier one's already matches every context it would),
`RECIPE_ENTRY_EMPTY` (an action's own `Recipe` group with neither `Conversions` nor
`FromCrafting` - it can never run a cycle), `LOOT_OUTPUT_ITEMS_WRONG_TRIGGER` (
an `rpgstations:output_items` reward authored under a `Completion` trigger, which has no cycle output to add
to), `LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT` (its sibling for the other way a cycle can have no output:
the action runs an authored `Steps` program - its OWN, or the one its `Ref` base authors, read
through `ActionResolver.effectiveStepsOf` - so the roll evaluates but has nothing to multiply),
and `CONTRIBUTION_SCALE_EMPTY`/`CONTRIBUTION_SCALE_FACTORS_WITHOUT_FLOORS`/
`CONTRIBUTION_SCALE_FLOORS_WITHOUT_FACTORS` (an authored `ContributionScale` group that can never
actually multiply anything). **Other schema checks**: `EXTENSION_CONTRIBUTION_DUPLICATE` (keyed
on the `(Channel, Param)` PAIR, case-folded and param-null-normalized; two arms - an extension's
`PerCycleContributions[]` re-declaring a pair its base action already declares via
`Work.PerCycleContributions`, and two extensions declaring the same pair on the same target;
deliberately NOT routed through `reportCrossExtensionCollisions`, whose "the later one wins, this
is skipped" wording is wrong here because `ExtensionCatalog#mergeContributions` APPENDS rather
than resolving a keyed collision, so every claimant's amount genuinely SUMS),
`TOOL_MIN_DURABILITY_OUT_OF_RANGE` (a `Tool.Durability.MinStartPercent`
outside `(0, 100]`, catching the fraction-vs-percent authoring slip),
`LADDER_DUPLICATE_FLOOR_MIN` (two floors of ONE ladder sharing a `Min`, in EITHER ladder consumer
through one shared check - only the LAST authored one can ever be reached, so the earlier duplicate
silently never grants), `CAMERA_RECIPE_WITHOUT_CAMERA` (a
`Camera.Recipe` under `Camera.Enabled: false`), `LOOT_BLANK_TABLE`,
`CUSTODY_SINGLE_FAMILY_REDUNDANT` (`SingleFamily: true` where the effective `MaxQuantity <= 1`
already enforces exclusivity), and `CONSUME_DUPLICATE_ITEM_REF` (one Consume's `Items` array
authoring the same item/family ref in two entries - the engine sums them, one combined entry says
it plainly; tag-route and match-any entries are exempt, they have no single ref to key on).
**Set-recipe checks**: `MATCH_ANY_INPUT_WITHOUT_CUSTODY` (a route-less input on an action with no
`Custody` - match-any draws only from placed material, so the row/step can never run; also fired
for a match-any `Consume.Items` entry whose `From` is not `Custody`), `OUTPUT_TAGS` (a `Tags` map
on an output entry, ignored like `OUTPUT_RESOURCE_TYPE`), and the two order INFOs
`RECIPE_ROW_ORDER_MISLEADING` (an `IsExactSet` row authored after a looser same-tier row, or a
match-any row authored before other same-tier rows - the file reads differently than the scan
resolves) + `CONVERSION_TIER_SHADOWED` (a row tiered behind a match-any row, which accepts any
material). `DUPLICATE_CONVERSION_INPUT` is suppressed when the earlier same-ref row authors
`IsExactSet` (the exact-then-loose ladder repeats a ref on purpose). **Retired checks** (the fields/shapes they warned about no longer exist):
`WAVE3_PENDING` (the multi-station seam executes, so the boundary warn has nothing left to gate -
the anchor/walk checks above are the live coverage), the reserved-field set from the pre-phase-2
step union (`UNIMPLEMENTED_STEP_TYPE`, `UNIMPLEMENTED_CONSUME_SOURCE`, `UNIMPLEMENTED_PRODUCE_DEST`,
`WAIT_BOTH_ROUTES`, `UNIMPLEMENTED_WAIT_BEATS`), `DEAD_POWER_SCALE`/`POWER_SCALE_*` (the group
is gone), `FACE_BLOCK_WITHOUT_CAMERA` (renamed `CAMERA_RECIPE_WITHOUT_CAMERA`), the four
`*_UNPLAYED_LEAVES` checks (the reserved `Presentation` leaves they warned about are deleted), the
pre-release sweep's `PICKER_*`/`YIELD_BONUS_*` sets (the `Picker` group and `Yield.Bonus` leaf are
both gone - all probabilistic output is a `Bonus` `Roll` now, covered by the normal `Roll`
checks), and the whole `RECIPE_SELECTION`/multi-recipe check family from the `Recipes[]`-list era
(one `Recipe` per action needs no selection-order coverage). The pure `validate(...)` core is
unit-tested.

**Contribution-channel checks**: `MISSING_CONTRIBUTION_CHANNEL` / `NONPOSITIVE_CONTRIBUTION_AMOUNT`
on a `Work.PerCycleContributions` entry, `LOOT_CONTRIBUTION_WRONG_TRIGGER`
(an `rpgstations:contribution` reward on a `Completion` roll, which fires from inside `stop()` with no cycle
event left to ride) / `LOOT_CONTRIBUTION_MISSING_CHANNEL` / `LOOT_CONTRIBUTION_NONPOSITIVE_AMOUNT`,
and `UNKNOWN_CHANNEL` - the exact mirror of `UNKNOWN_FACTOR`: a `Channel` nobody declared through
`api.ContributionChannelRegistry` warns and echoes the declared set, then forwards anyway.
FAIL-OPEN is absolute here; an undeclared channel must never block a station.

**`LOOT_DUPLICATE_FACTOR` (INFO)** fires when ONE Roll references the same `(Factor, Param)` pair
more than once across its `Conditions`, `Chance.Factors`, and `Ladder.Factors` (case-folded,
param-null-normalized). Keyed on the PAIR, never the bare factor id: every stat read carries factor
id `"hytale:stat"`, so a ladder summing two different stat channels is a legitimate composition and must
not fire. In practice only a param-less duplicate of a zero-arg engine factor
(`rpgstations:cycle_count` twice) trips it. **What it deliberately does NOT catch**: a formula that
sums an aggregate factor AND the underlying channels that aggregate is defined over. Those are
different ids, so nothing here can tell them apart - "these two specific ids overlap" is knowledge
only the factor family's OWNER has. That composition rule belongs in the owning mod's docs, or in a
check that mod registers through the api `ValidationHookRegistry`, which exists precisely so a
vocabulary's owner keeps its rules with the vocabulary. This engine holds no opinion about any
foreign id.

**The DECODE-TIME warn layer is a complement, not a replacement** (`../asset/CodecWarnValidators`,
see `../asset/CLAUDE.md`): field-local range/blank invariants and exactly-one-of contracts report
at the asset's own decode path/line during the fold, so a pack author sees them in the boot log
immediately; this validator keeps every cross-asset, cross-layer, and semantic check, and no check
here is retired because a codec validator or an Asset-Editor dropdown covers the same ground. The
never-block posture is absolute at BOTH layers: an asset always loads.

**Timing stays first-`PlayerReadyEvent`.** Moving the full pass to `AllWorldsLoadedEvent` is
allowed only once an in-game re-run confirms the three known cross-layer false positives
(`STAMP_UNKNOWN_POOL`, `LOOT_UNKNOWN_DROPLIST`, `MISSING_*_LANG`) do not recur under it - which a
build or unit-test run cannot establish. See `runAndLog()`'s own javadoc.

## Landed fix history (still-true warnings only; condensed)

Several maintainer-smoke-driven fix rounds landed across phase 1/2 (extraction through the anvil
arc, the puppet presentation build, and a round-8 facing-relative/step-sync pass). The narrative
detail lives in git history and `../../../../../../../../../.claude/plans/work-stations-mod-extraction-prompt.md`;
the load-bearing lessons that still apply going forward:

- **Every ECS mutation from inside an interaction handler or the heartbeat frame drain must go
  through a `CommandBuffer`**, never `store.addEntity`/`removeEntity`/`putComponent` directly -
  both run inside the store's processing lock and throw `IllegalStateException("Store is
  currently processing!")` otherwise (this bit the custody display entity, the entity-mount
  anchor, and three puppet-controller call sites independently; the fix is always the same
  thread-a-`CommandBuffer`-parameter shape).
- **A spawned network entity needs an explicit `NetworkId` component** unless the engine
  auto-ensures one for you (only `MinecartComponent` gets that for free via
  `MountSystems.EnsureMinecartComponents`) - a plugin-spawned anchor/prop entity that skips it
  renders invisibly to every viewer including the entity's own controller.
- **A block's item id at engage-time is `BlockType#getItem()`, never raw `BlockType#getId()`** -
  a state-variant block (e.g. `Loaded`) decodes to a distinct generated id
  (`*RPG_Station_Sawmill_Loaded`) that is not a real item id; `getItem()` resolves the base item
  through the containment chain regardless of which state the block is currently in.
  `StationService#blockItemIdAt` falls back to `getId()` only when the block has no containing
  Item at all.
- **The same rule binds the BLOCK-GONE check: compare by ITEM id, never by the raw block-type
  id.** The state flip does not annotate a block, it REPLACES it with the generated state-variant
  BlockType, so the block-type id read back changes on EVERY `Custody.States` flip this engine
  performs. A type-id compare against the engage-time snapshot therefore reads the engine's own
  `Empty`/`Loaded`/`Working` flip as "the station is gone" (the round-2 smoke regression: the
  cooking fire's own session died at its first 1s heartbeat the moment engage lit it). The
  heartbeat runs the pure `StationAnchors#blockGone(startBlockItemId, currentBlockItemId,
  startBlockTypeId, currentBlockTypeId)`: item-id compare (case-insensitive, null current = gone)
  when the session captured one at engage (`StationSession#startBlockItemId`, resolved ONCE and
  shared with the summary crest), block-type-id fallback (`StationSession#startBlockTypeId`, read
  through zc's `BlockOps.blockItemIdAt`) only for a block with no containing Item. This covers the
  latent twin by construction - a `StationStepHandlers` working-step flip at `At: "self"` writes
  the SAME primary block through the SAME check.
- **A restart-orphaned `Loaded` block state with no live claim behind it must recover, not
  dead-end.** `ActionResolver#selectActionForBlockState(asset, currentStateName)` is the THIRD
  action-selection fallback (after the live claim and the held item) - it matches the block's
  persisted interaction-state name against each action's `Custody.States.Loaded` name so a
  correct-tool press after a restart re-enters the existing not-loaded self-heal instead of
  denying `ui.station.no_action` forever.
- **A one-shot puppet/display spawn call ordering matters**: seed any render-guaranteed initial
  component (e.g. an `ActiveAnimationComponent`) using data already assigned BEFORE the spawn
  call, not after - a viewer who starts tracking the entity between the spawn and the later
  assignment sees it frozen with no initial pose.

Deprecation discipline: this package (plus `StationStepHandlers`/`StationHoldController`/
`interaction.StationUseInteraction`) is swept clean of deprecated API calls
(`ziggfreed-common`'s `inventory.PlayerAccess` DRYs the non-deprecated replacements, the one
shared primitive this mod and the MMO both call) - keep it that way per the root CLAUDE.md's
never-call-a-deprecated-API edict; the shared source's deprecation javadoc always names the
current replacement. `PlayerAccess.storage` answers with the `InventoryComponent.Storage`
COMPONENT, not its container, so `StationStepHandlers#storageContainer(Player)` is the one private
unwrap the reagent probe and drain paths read through: add a site there, never a fresh
`.getInventory()` chain.
