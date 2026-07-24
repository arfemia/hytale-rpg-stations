# asset/ - the Pattern A content codecs

Router for `asset/`. Every custom asset type this mod authors: the codec IS the schema
(PascalCase keys, nested sub-object groups, every leaf `appendInherited` for native `Parent`
reuse, `.documentation("...")` on every field for the docsite schema-reference autogen).
`RpgStationsPlugin` registers each as its own `AssetStoreRegistrar` store + folds loaded entries
into the matching `station`/`loot` package catalog on `LoadedAssetsEvent`.

**Scope-2 redesign (design `raw/rpg-stations-scope2-unified-design-2026-07-23.md`, decisions
33-41): the whole package was reshaped around one DRY leaf vocabulary, orthogonal-phase steps
instead of a step `Type` union, and two new additive-composition asset types.** File/folder
layout: `Server/RpgStations/{Stations,Actions,Lootables,RollPools,Flairs,Extensions,Settings}/`.
**WAVE BOUNDARY**: the multi-station seam (`ActionDef.Anchors`, `StationStep.Walk`/`At`,
`Produce.To:"Custody"`) DECODES and VALIDATES this wave but does not EXECUTE - see each bullet's
own `[wave 3]` marker and `../station/CLAUDE.md`'s boundary section.

## Shared leaf vocabulary (the DRY layer every type reuses)

- **[`Ingredient`](Ingredient.java)** - the ONE item-quantity leaf (`{ItemId|ResourceTypeId,
  Quantity}`, exactly one of `ItemId`/`ResourceTypeId`, native-shaped like vanilla
  `MaterialQuantity`). Replaces three near-identical pre-scope-2 triples (the old nested
  `StationAsset.Ingredient`, the inline `StationStep.Consume` triple, `Stamp.Reagent`). Used by
  `StationAsset.Conversion.Input/Output`, `ExtensionAsset.Conversions`, and `StationStep.Stamp
  .Reagents`. `StationStep.Consume`/`Produce` deliberately stay FLAT (`{ItemId,ResourceTypeId,
  Quantity,From}` / `{ItemId,Quantity,To}`) rather than nesting an `Ingredient` - the design
  2.1 exemplar's shape, kept even though it duplicates the item/quantity pair, since `Consume`/
  `Produce` also carry a `From`/`To` route discriminator `Ingredient` has no room for.
- **[`FactorRef`](FactorRef.java)** - the ONE weighted factor reference (`{Factor, Param?,
  Weight?}`, `Weight` defaults 1.0), reused everywhere a numeric factor channel is SUMMED:
  `Roll.Chance.AddFactors`, `Roll.Ladder.Values`, `StatRollEntry.Points.AddFactors`,
  `StationStep.Stamp.Stats.Caps.Budgets[].Factors`, `StationStep.Repeat.AddFactors`.
  Composition at every site is a flat weighted sum, `sum(resolve(Factor,Param) * Weight)` - no
  expression nesting (standing directive 3's boundary). `FactorRef.stat(statId)` is the
  convenience for the `{"Factor":"stat","Param":"<StatId>"}` shape the loot-formula middle path
  uses (the `stat` factor provider itself is `loot/`'s scope, registered by `RpgStationsPlugin`).
  `FactorRef` is the ADD/scale sibling of [`Condition`](Condition.java) (below): `Condition` is a
  factor reference PLUS gate bounds (`Min`/`Max`); `FactorRef` is a factor reference PLUS a
  `Weight`. An unregistered factor id resolves to 0 (fail-closed), never a throw.
- **[`LootRef`](LootRef.java)** - the ONE loot-reference group (`{Lootables[], Rolls[]}`),
  replacing the two divergent pre-scope-2 vocabularies (`StationAsset.Loot{Tables,Rolls}` and the
  old `StationStep.RollGroup{Lootable,Rolls}`, singular). `Lootables` are referenced
  [`LootableAsset`](LootableAsset.java) ids; `Rolls` are inline [`Roll`](Roll.java)s authored
  directly at the site; both resolve when both are authored. Reused at EVERY site a station,
  action, step, or extension references loot: `StationAsset.Loot`, `ActionDef.Loot`,
  `StationStep.Roll`, `ExtensionAsset.Loot`.
- **[`Condition`](Condition.java)** - unchanged shape (`{Factor, Param?, Min?, Max?}`), the ONE
  GATE leaf both `Requires.Conditions` (station start gate) and every `Roll`/`StationStep`
  `Conditions` array evaluate over the api `FactorRegistry`. An unregistered factor id fails
  CLOSED (a gate on a server without the referencing progression mod stays locked, never silently
  open) - never a second condition schema.
- **[`EffectRef`](EffectRef.java)** (NEW, seam wave decision 51d) - the ONE native-EntityEffect
  reference leaf (`{Id, DurationMs?}`, id-ref-only per decision 53, never inlines the effect body).
  Reused at BOTH altitudes an effect payload lands: `Presentation.Effect` (a single per-moment
  effect group) and `Roll.Grants.Effects[]` (a reward-time effect array). `Id` is the native effect
  asset id; `DurationMs` null defers to the effect asset's own TTL. The engine tracks session-scoped
  effects so `stop()` removes them (engine scope); an unresolvable id is a validator INFO + apply
  no-op.
- **[`Picker`](Picker.java)** (NEW, seam wave decision 50) - the multi-output picker knob group
  (`{ShowLocked?}`, reader-default `true`), a top-level `StationAsset.Picker` default whole-group
  overridable per `ActionDef`. `ShowLocked` governs whether tool-gated output categories render
  greyed (via `ui.gate.locked_*`) or hide entirely. A single-category station never shows a picker,
  so the group is a no-op there.

## Content types

- **[`StationAsset`](StationAsset.java)** - an interactive work station, loaded from
  `Server/RpgStations/Stations/*.json` (id = lowercased filename). Top-level groups: `Identity`
  (name/desc/icon keys), `Work` (cycle cadence + `Xp[]` progression declarations the engine never
  interprets, forwarded verbatim on `StationCycleCompletedEvent.xpAsks`; optional `Idle` practice
  mode; `Repeat` - `false` completes the whole SESSION after one program run instead of looping),
  `Recipe` (authored `Conversions` over `Ingredient` Input/Output, or `FromCrafting`
  derivation), `Hold` (the movement-lock effect / the `Mount` knob family - see
  `../station/CLAUDE.md`'s Mount bullet, UNCHANGED by scope-2), `Tool` (the held-tool gate +
  `XpScale`), `Custody` (session-scoped placed-input custody, see [`Custody`](Custody.java)
  below), **`Loot`** (scope-2: a [`LootRef`](LootRef.java) - `Lootables[]` + inline `Rolls[]`,
  REPLACES the pre-scope-2 `{Tables,Rolls}` shape, field renamed `Tables`->`Lootables`), `Camera`
  (third-person pull + `FaceBlock`/`Recipe`), `Animation` (`EmoteId` + `Swing`/`Impact`/
  `ActionClip`), `Presentation` (per-cycle moment), `Completion` (session-end moment, a second
  `Presentation` instance), `Requires` (permission + factor-`Condition` gate), `Flairs`
  (per-flair-id cosmetic overrides, an authoring convenience - see [`FlairAsset`](FlairAsset.java)
  for the standalone route), `Puppet` (unchanged by scope-2 - see below), and `Actions` - a
  named, authored-order map of [`ActionDef`](ActionDef.java) whole-GROUP overrides, absent/empty
  meaning the single implicit `"work"` action built from this asset's own groups, plus (seam wave,
  decision 50) `Picker` (a [`Picker`](Picker.java), whole-group overridable per action). **Seam-wave
  native-recipe composition (decisions 51c/52)**: `Recipe.FromCrafting` gains `Benches[]` (native
  BenchRequirement id-refs), `Types[]` (`Crafting`|`Processing`; absent = both), and `NativeTime
  {Scale, OffsetMs}` (the decision-52 linear `y = Scale*x + OffsetMs` transform over a derived
  recipe's `TimeSeconds`, defaults intentionally slower than vanilla); `Recipe.Conversion` gains a
  nullable `DurationMs`. Per-cycle time precedence (engine-side): authored `Conversion.DurationMs`
  &gt; `FromCrafting.NativeTime` linear transform &gt; `Work.CycleMs`. See
  `../station/CLAUDE.md` for how every group drives the engine (`station.ActionResolver` is the
  resolution choke point).
- **[`ActionDef`](ActionDef.java)** - one `Actions` map entry: nullable whole-group overrides of
  every `StationAsset` group (`Input`/`Custody`/`Puppet`/`Work`/`Recipe`/`Tool`/`Hold`/`Camera`/
  `Animation`/`Presentation`/`Completion`/`Loot`/`Requires`) PLUS `Label` (an advisory display
  key), `Steps` (an authored [`StationStep`](StationStep.java) program; absent means "build the
  implicit program"), and TWO scope-2 additions: **`Ref`** (design 1.5, below) and **`Anchors`**
  (design 2.2, `[wave 3]`). Every codec leaf is now `.documentation`-annotated; the E1 factory-gap
  is closed (`ActionDef.of(...)` takes every group).
  - **`Ref` - the standalone-action attachment route (design 1.5, decision 28a, BOTH forms
    survive):** `{"Ref": "<actionAssetId>"}` names a standalone [`ActionAsset`](ActionAsset.java)
    (below) as the BASE; any OTHER group authored on the SAME inline entry overlays it group-wise
    (the same whole-group-replace semantics `ActionResolver` already applies station->action,
    applied twice: `Ref`-base -> inline overlay). A dangling `Ref` is validator finding
    `ACTION_REF_UNKNOWN` (structural + full pass warn; engage denies gracefully with
    `ui.station.action_unavailable`). Native `Parent` BETWEEN `ActionAsset`s is the sibling
    "author only the delta" reuse route; `Ref` + overlay is the per-station ATTACHMENT route -
    two different reuse axes, not redundant.
  - **`Anchors` - named multi-station anchor declarations (design 2.2, `[wave 3]` execution):**
    `{"<anchorId>": {"Station": "<stationId>", "MaxRadius": 12}}`. Legal on both an inline
    `ActionDef` and a standalone `ActionAsset` (expected mostly on the latter). The reserved
    anchor id `"self"` (the primary station block) is implicit and never authored. This wave: the
    codec decodes/validates (`ANCHOR_STATION_UNKNOWN` warns an unknown `Station`); DISCOVERY
    (nearest matching placed block within `MaxRadius`), CLAIMING, and a `StationStep.Walk`/`At`
    naming an anchor are ALL `[wave 3]` - see `../station/CLAUDE.md`'s boundary section.
- **[`ActionAsset`](ActionAsset.java)** (NEW, design 1.5) - a standalone, reusable,
  fourth-party-extendable action: `Server/RpgStations/Actions/<Name>.json` (Pattern A, id =
  lowercased filename). Its body is the EXACT SAME field set as the inline `ActionDef` (one
  schema authority - the fields live on an embedded `ActionDef` the wrapper's codec delegates to)
  PLUS `Anchors`; it deliberately OMITS `Ref` (a standalone action is itself a base and never
  references another). A station attaches it via an inline `Actions` entry's `Ref` leaf (above).
  Supports native `Parent` between `ActionAsset`s for delta reuse. See the fish exemplar
  (`Actions/PrepFish.json`, design 2.7) for the flagship authoring shape.
- **[`Custody`](Custody.java)** - session-scoped placed-input custody: `{MaxQuantity?, Input?,
  States?, Display?}`, UNCHANGED shape by scope-2 except the deletion of the pre-scope-2
  bare-number `Rotation` legacy-tolerant wrapper (`LegacyTolerantCodec` removed - with the whole
  surface re-authored in-wave there is no legacy JSON to tolerate; `Display.Rotation` decodes ONLY
  the nested `{X,Y,Z}` degrees group now). `MaxQuantity` defaults to **100**. `Input` (reusing
  [`ActionInput`](ActionInput.java)'s ItemId/ResourceTypeId/Tags/Function routes) is the explicit
  placement-acceptance matcher; absent derives acceptance from the resolved action's own `Recipe
  .Conversions` inputs instead. `States` (`{Empty?, Loaded?}`) names the block's own
  `State.Definitions` entries the engine flips between; null = no visual/hint flip. `Display`
  (`{Offset{X,Y,Z}, Scale, Rotation{X,Y,Z}}`, FACING-RELATIVE to the placed block's own yaw, every
  leaf `appendInherited`) opts the placed input into a PLACED-AS-ENTITY visual - see
  `../station/CLAUDE.md`'s dedicated bullet for the full engine-side mechanism.
- **[`ActionInput`](ActionInput.java)** - the diegetic action-selection matcher: `{ItemId?,
  ResourceTypeId?, Tags?, Function?}` (`Function` is `"Weapon"|"Armor"|"Tool"`, resolved against
  the held item's live shape). UNCHANGED shape by scope-2 (docs-only diff). `isCatchAll()` = no
  route authored. Live selection runs through `station.ActionResolver.selectActionByFamily`.
- **[`StationStep`](StationStep.java)** (REWRITTEN, design 2.1, decisions 34/38) - ONE step of a
  multi-action station's step PROGRAM, reshaped from a `Type`-discriminated union into an
  ORTHOGONAL PHASE record. **The `Type` union, the `Wait` type, the reserved `Mount` type, and
  `Wait.Beats` are ALL GONE** - no decoy fields the engine does not execute. A step composes any
  combination of nullable phase groups in ONE fixed, documented execution order; a step with NO
  phase group is a pure BEAT (`isPureBeat()`).
  - **Base fields** (every step): `Id` (unique within one action's `Steps`; required whenever
    another step or an `ExtensionAsset` insertion anchors on it), `Conditions` +
    `OnConditionFail{Result:Skip|Fail, Goto}` (the gate + branch/skip mechanism, unchanged
    semantics from pre-scope-2), `At` (an anchor id from the action's `Anchors` map; absent = the
    primary station `"self"`, `[wave 3]`), `Repeat` (`{Times}` fixed OR `{Min,Max,AddFactors}`
    ranged, resolved once at step entry via the pure `resolveCount(factorContribution)`),
    `Duration` (`{Ms}`, a post-phase hold; prop/presentation persist across it), `Puppet`
    (per-step `{Clip?, Prop?}` override, reusing `Puppet.Prop`'s exact codec), `Presentation`
    (fires once at step ITERATION entry, round-8 rule unchanged).
  - **Phase groups** (all nullable): `Walk` (`{To, SpeedMps}`, `[wave 3]`), `Consume`
    (`{ItemId|ResourceTypeId, Quantity, From:Inventory|Custody}` - BOTH routes executable),
    `Stamp` (the enhance-commit phase, below), `Produce` (`{ItemId, Quantity, To:Inventory|
    Custody}` - only `To:Inventory` executes this wave; `To:Custody` decodes, `[wave 3]`), `Roll`
    (a `LootRef` - the SAME vocabulary a station's own `Loot` group uses), `Commands`
    (`String[]`, run through the shared `CommandRewardExecutor`).
  - **Execution order within ONE step iteration** (fixed, honored by `station.StationStepRegistry`
    - leg A3): Conditions gate -> `Walk` -> `Consume` -> `Stamp` -> `Produce` -> `Roll` ->
    `Commands` -> `Presentation`/`Puppet.Clip` (fire at iteration entry) -> `Duration` hold
    (suspend) -> next iteration or next step. A step combining `Consume` + `Produce` is an ATOMIC
    transform (no consumed-without-produced window); the anvil's strikes re-author as pure
    `Duration` beats (`{Id, Duration:{Ms}, Puppet:{Clip}, Presentation}`), and the stamp step's
    `Duration` + `Prop:None` closes the parked post-stamp empty-hands flourish.
  - **`authorsWave3OnlyPhase()`** flags a step authoring `Walk`, `At`, or `Produce.To:"Custody"` -
    the validator WARNs (`WAVE3_PENDING`-style) and engage denies gracefully; NO shipped wave-2
    content authors any of them.
  - **`Stamp`** (design 9.5/3.8, the anvil's enhance-commit step, unchanged transaction shape -
    compute-then-commit, handler-enforced): `{Reagents: Ingredient[], Durability{AddMax},
    Stats{Pool?, Entries?, Picks{Min,Max}, Unique, Caps}}`. `Reagents` are `Ingredient`s consumed
    FROM THE PLAYER'S INVENTORY (not custody). `Stats.Pool`/`Entries` share
    [`StatRollEntry`](StatRollEntry.java) with [`RollPool`](RollPool.java). **`Caps` RESHAPED for
    scope-2 (M2's binding rule kept):** `Budgets: Budget[]` (each EXACTLY one of a flat
    `{Points}` or a factor-scaled `{PointsPer, Factors[]}` = `PointsPer *
    sum(resolve(f)*f.Weight)` - REPLACES the old `PerItemBudget`/`SkillScaledBudget` pair; the
    EFFECTIVE total budget is the MIN over every `Budgets` entry), `PerStat: Map<String,Double>`
    (a per-stat-id ceiling layered ON TOP, unchanged), `Economics{RepeatCostMultiplier}` (reagent
    cost scaling per prior stamp count, reads `StackStats`/the registered stamper's count,
    unchanged). Resolved end to end by the PURE `station.StampCapEngine` (unit-tested, fixture
    caps re-anchored on the new shape).
- **[`StatRollEntry`](StatRollEntry.java)** - one candidate stat-roll entry `{Stat, Points{Min,
  Max, AddFactors[]}, Weight, Always}`, shared verbatim by `RollPool.Entries` and inline
  `StationStep.Stamp.Stats.Entries`. Scope-2 addition: `Points.AddFactors` (a `FactorRef[]`) -
  rolled points = `uniform(Min,Max) + sum(resolve(f)*f.Weight)`, clamped by the Stamp caps as
  today. The same `FactorRef` vocabulary that drives loot chances now drives roll magnitudes.
- **[`RollPool`](RollPool.java)** - `Server/RpgStations/RollPools/<Name>.json` (id = lowercased
  filename), body `{Entries: [StatRollEntry, ...]}`, referenced by a Stamp step's `Stats.Pool`.
  Folded into `loot.RollPoolCatalog`. Unchanged shape by scope-2.
- **[`Presentation`](Presentation.java)** - RpgStations' OWN codec (a deliberate small divergence
  from the MMO's copy of the same shape - no `FeedbackService` indirection here). Leaves:
  `Sound`, `Particles`, `Animation`, `AnimationItem`, `AnimationSlot`, `Camera`, `Shake` (nested
  `{EffectId, Intensity}`), plus TWO seam-wave native-composition groups: `Interaction`
  (`{Id}`, an inner class - fires a native RootInteraction chain by id, decision 51b) and `Effect`
  (an [`EffectRef`](EffectRef.java) - applies a native EntityEffect by id, decision 51d). Both
  id-ref-only.
- **[`Puppet`](Puppet.java)** (unchanged by scope-2) - "mount the player, hide their player
  model, and spawn/display a visual of their character model performing the steps" - a top-level
  group sibling to `Hold`/`Camera`/`Animation`/`Custody` (ORTHOGONAL to whichever `Hold.Mount`
  holds the real player, never nested under `Hold`), whole-GROUP overridable per `ActionDef`:
  `{Enabled?, Hide{Route,EffectId?}, Look{Source,ModelId?,FallbackModelId?}, Offset{X,Y,Z},
  Yaw, Prop{Source,ItemId?,Slot?}}`. `Hide.Route` is a THREE-arm union: `"Scale"` is the
  in-game-crowned default (hides the puppeteer's own body via `ziggfreed-common`'s
  `entity.PlayerPuppetService`); `"Effect"` is schema-reserved future work; `"None"` is the
  deliberate degraded fallback. `Look.Source` defaults `"PlayerClone"`, with `"Model"` an open
  performer seam. `Offset`/`Yaw` place the puppet relative to the station's block-top anchor in
  WORLD SPACE (unlike `Custody.Display`, which is facing-relative). `Prop.Source` defaults
  `"MirrorHeld"`; `"ItemId"` forces a specific prop, `"None"` empties the puppet's hands. A
  `StationStep` carries its own small `{Clip, Prop}` override (`StationStep.PuppetOverride`)
  reusing this exact `Prop` codec for moment-to-moment swaps. **Seam wave (decision 47/48) - FULL
  Look nesting symmetry**: `Look.Source` is now a THREE-arm union (`PlayerClone`|`Model`|`NpcRole`);
  the flat `ModelId`/`FallbackModelId` retro-nest into the cohesive `Look.Model {ModelId,
  FallbackModelId}` group (read for `Source:"Model"`; `FallbackModelId` is the any-source resolution
  fallback), and the NpcRole performer arm's config is the parallel `Look.Role {RoleId, SkinSource
  (PlayerClone|RoleDefault), Persist, SpeedMps}` group (read for `Source:"NpcRole"`; `Model`/`Role`
  are inner classes of `Puppet`, siblings of `Hide`/`Prop`). See `../station/CLAUDE.md`'s
  puppet-engine bullet (`StationPuppetController`); both shipped stations author `Puppet` in
  `content-packs/skill-stations-pack`.
- **[`Roll`](Roll.java)** (REWRITTEN for weighted-factor unification, design 4.2) - the
  conditional-lootable roll: `Trigger` (`Cycle`/`Completion`), `Conditions[]`, `Chance{BasePercent,
  AddFactors[], CapPercent}`, `Ladder{Values[], Floors[]}`, `Grants{BonusOutputCopies, DropList,
  Commands[], Effects[]}` (top-level AND per-floor, both fire; `Effects[]` is a seam-wave
  [`EffectRef`](EffectRef.java) array applying native EntityEffects on grant, decision 51d). Scope-2 changes: `Chance.AddFactors` entries
  are now `FactorRef`s (gained `Weight`, previously bare `{Factor,Param}`); `Ladder.Value`
  (singular) is REPLACED by **`Ladder.Values[]`** (JSON key `Values`, a `FactorRef[]` summed
  BEFORE the floor lookup, so a ladder composes `stat` channels like `MMO_Luck` +
  `MMO_Luck_WOODCUTTING` - the loot middle path's composition exemplar; a single-factor ladder
  authors a one-element array; `getValue()` is GONE). M3's binding fixes carry forward unchanged:
  a `Ladder.Floor` has no direct `DropList` (every floor routes through its own `Grants`);
  top-level `Grants` AND the reached floor's `Grants` both apply; a failing `Chance` means the
  `Ladder` never evaluates; `BonusOutputCopies` is meaningless outside a `Cycle` trigger (warns).
  Shared by `LootRef.getRolls()` (every LootRef site) and `LootableAsset.getRolls()`.
- **[`LootableAsset`](LootableAsset.java)** - `Server/RpgStations/Lootables/<Name>.json` (id =
  lowercased filename), body `{Rolls: [Roll, ...]}`, referenced by a `LootRef.Lootables` entry
  (a station, action, or extension may combine any number of shared tables with its own inline
  `Rolls`). Unchanged shape by scope-2. The standalone `SawmillFinds` lootable ships in this
  jar's resources, alive with RpgStations alone (built-in `rpgstations:` factors only).
- **[`FlairAsset`](FlairAsset.java)** - a standalone, ANY-mod-authorable cosmetic flair layer,
  `Server/RpgStations/Flairs/<Name>.json` (Pattern A, id = lowercased filename): `{Stations?[],
  Moments}`. `Stations` null/empty = applies to every station; `Moments` is an OPEN
  `Map<String, Presentation>` keyed by an arbitrary moment id (well-known ids `cycle`/`swing`/
  `impact`/`rare_find`/`completion` plus a per-step `step:<actionId>:<stepId>` id) - nothing
  hardcodes the vocabulary in Java. Unchanged shape by scope-2. Folded into `station.FlairCatalog`
  (`effectiveFlairsFor`, UNIONS every applicable `FlairAsset` onto a station's own inline `Flairs`
  map). Deliberately NOT extension-composable via `ExtensionAsset` (design 1.8's non-extensible
  list) - extend `FlairAsset.Moments` by shipping another `FlairAsset`, the union already
  composes.
- **[`ExtensionAsset`](ExtensionAsset.java)** (NEW, design 1.8, decision 27) - the ONE additive-
  extension mechanism for every collection-bearing group that is NOT cosmetic (cosmetics stay
  `FlairAsset`): `Server/RpgStations/Extensions/<Name>.json` (Pattern A, id = lowercased
  filename). A fourth party extends a third party's content WITHOUT owning or replacing its
  files.
  - **`Target`** is EXACTLY ONE of `{Station|Action|Lootable|RollPool}` (orthogonal exactly-one-
    of leaves, never a `Type` enum + Id pair - `hasExactlyOneTarget()`/`resolvedType()`/
    `resolvedId()`; type constants `Target.STATION`/`ACTION`/`LOOTABLE`/`ROLLPOOL`).
  - **Payload groups** (all nullable; a group the target type cannot carry is validator
    `EXTENSION_PAYLOAD_MISMATCH`, checked by the pure `payloadAllowedFor(targetType, payloadKey)`):

    | Target    | Extensible payload keys                                                 |
    |-----------|--------------------------------------------------------------------------|
    | Station   | `Xp[]`, `Loot` (LootRef append), `Actions{}` (new keys only), `Conversions[]`, `Steps[]`, `Anchors{}` (new keys only) |
    | Action    | `Steps[]`, `Anchors{}` (new keys), `Loot`, `Conversions[]`, `Xp[]`        |
    | Lootable  | `Rolls[]` (append)                                                       |
    | RollPool  | `Entries[]` (append)                                                     |

  - **Merge + conflict semantics** (deterministic, ALL pure/unit-testable on this class, applied
    engine-side by `ExtensionCatalog.applyTo` - leg A3's scope):
    1. ADDITIVE ONLY - an extension never mutates/replaces/removes an existing entry.
    2. Keyed collections (`Actions`, `Anchors`): the BASE always wins a key collision
       (`EXTENSION_KEY_COLLISION`, entry skipped); among extensions, `APPLY_ORDER` decides.
    3. Unkeyed arrays (`Xp`, `Conversions`, `Rolls`, `Entries`): pure append, ordered by
       `APPLY_ORDER`.
    4. Ordered insertion (`Steps` -> `StepInsertion{Action?, Anchor, Insert:StationStep[]}`):
       `Anchor` is exactly one of `{After:"<stepId>"|Before:"<stepId>"|AtStart|AtEnd}`
       (`effectivePlacement()` degrades to `AtEnd` + `EXTENSION_ANCHOR_MISSING` warn on a
       missing/dangling step id); inserted steps need `Id`s so LATER extensions can anchor on
       them. Co-anchored insertions from different extensions apply in `APPLY_ORDER` (m2).
  - **`APPLY_ORDER`** - the ONE apply-order tuple (`Priority` ascending so a HIGHER priority
    applies LATER and wins a tie, then extension id lexicographic) - a total order over distinct
    assets, so a stable sort fully determines the result on every server. `sortedForApply(...)`
    is the pure, unit-tested, catalog-AND-validator-shared core.
  - **Composition order (m7):** extensions apply to the Parent-resolved target at READ time;
    extension additions do NOT flow down `Parent` chains. A `Target:{Action}` extension flows to
    every `Ref` user of that action; a `Target:{Station}` step-insert applies post-`Ref` to that
    station only.
  - **Deliberately NON-extensible** (docs state each): `Requires` (an extension must never
    tighten/loosen another author's gate), `Settings` (owner-only singleton), `Custody.States`
    (one visual pair per action), scalar groups (`Work`/`Hold`/`Camera`/`Animation`/`Puppet` -
    override is load-order's job, not extension), the INTERNALS of an existing `Roll` (extenders
    add their OWN Rolls beside it), and `FlairAsset.Moments`.
  - See `../station/CLAUDE.md`'s ExtensionCatalog bullet for the engine-side fold + the
    cross-pack-aware validator's `EXTENSION_APPLIED` boot summary.
- **[`RpgStationsSettingsAsset`](RpgStationsSettingsAsset.java)** - `Server/RpgStations/Settings/
  Settings.json`, a single id (`settings`), jar default + pack-overridable: `{Enabled,
  SummaryHud:{Enabled, Position, OffsetY, TtlMs}}`. Deliberately NON-extensible (server-global
  singleton). Unchanged by scope-2. Folded into `station.SettingsCatalog`.
- **[`Requires`](Requires.java)** - `{Permission?, Conditions?[]}`, evaluated at station start;
  any failing `Condition` denies with `ui.station.locked`. Unchanged shape by scope-2.

**No `PackControlAsset`/`Control` map infra exists yet in this mod** (unlike the MMO's pack
system) - every fold is always additive (`replace=false`); a reload re-fires the `LoadedAssetsEvent`
and re-folds for free, no owner-override precedence layer beyond `defaults < pack` load order.
