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
The multi-station seam (`ActionDef.Anchors`, `StationStep.Walk`/`At`, `Produce.To:"Custody"`) fully
EXECUTES as of wave 3 - the whole authorable field set below is live, with no decode-only decoys.
See `../station/CLAUDE.md`'s boundary section for the engine half.

## Shared leaf vocabulary (the DRY layer every type reuses)

- **[`Ingredient`](Ingredient.java)** - the ONE item-quantity leaf (`{ItemId|ResourceTypeId,
  Quantity}`, exactly one of `ItemId`/`ResourceTypeId`, native-shaped like vanilla
  `MaterialQuantity`). Replaces three near-identical pre-scope-2 triples (the old nested
  `StationAsset.Ingredient`, the inline `StationStep.Consume` triple, `Stamp.Reagent`). Used by
  `StationAsset.Conversion.Input/Output`, `ExtensionAsset.Conversions`, `StationStep.Stamp
  .Reagents`, and (schema-review wave) `StationStep.Consume.Items`/`Produce.Items`. **Every one of
  those sites takes an `Ingredient` ARRAY**, mirroring native `CraftingRecipe.Input`/`Output`:
  "2 planks + 1 nail -> 1 crate" is ONE conversion and ONE atomic step-phase pair, never a step
  split, and `StationRecipeDeriver` derives multi-input native recipes instead of skipping them.
  The earlier FLAT `{ItemId,ResourceTypeId,Quantity,From}` / `{ItemId,Quantity,To}` Consume/Produce
  shape is GONE (pre-release hard break, no alias); the `From`/`To` route discriminator that
  motivated it stays at GROUP level beside `Items`, so one phase draws every item from - and writes
  every item to - the same place. Both phases are ALL-OR-NOTHING: availability is checked across
  every entry before anything is removed.
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
- **[`Contribution`](Contribution.java)** - the WRITE-side twin of `FactorRef`/`Condition`, and the
  ONE outbound numeric-post leaf: `{Channel, Param?, Amount}`. Where a `Factor` asks a registered
  provider for a number, a `Contribution` hands a number OUT - the engine forwards
  `{Channel, Param, Amount}` verbatim on `StationCycleCompletedEvent` and **never resolves a channel
  at all.** That asymmetry is the paradigm in one line: the engine owns built-in FACTORS because it
  can compute them, and owns ZERO built-in channels because it interprets none. `Channel` carries a
  `Dropdown(AssetEditorDataSets.CHANNELS)` fed LIVE off
  `api.impl.ContributionChannelRegistryImpl#registeredIds`, and an id nobody declared is a
  fail-open `UNKNOWN_CHANNEL` warn that still forwards. Used at exactly two SITES, and the site is
  the discriminator (see the `Roll` bullet): `StationAsset.Work.PerCycleContributions[]` (scaled)
  and `Roll.Grants.Contributions[]` (one-shot, verbatim).
- **[`LootRef`](LootRef.java)** - the ONE loot-reference group (`{Lootables[], Rolls[]}`),
  replacing the two divergent pre-scope-2 vocabularies (`StationAsset.Loot{Tables,Rolls}` and the
  old `StationStep.RollGroup{Lootable,Rolls}`, singular). `Lootables` are referenced
  [`LootableAsset`](LootableAsset.java) ids; `Rolls` are inline [`Roll`](Roll.java)s authored
  directly at the site; both resolve when both are authored. Reused at EVERY site a station,
  action, step, or extension references loot: `StationAsset.Loot`, `ActionDef.Loot`,
  `StationStep.Roll`, `ExtensionAsset.Loot`. A `Lootables` entry accepts an INLINE lootable body as
  well as an id (the ref-or-inline surface below).
- **[`Condition`](Condition.java)** - unchanged shape (`{Factor, Param?, Min?, Max?}`), the ONE
  GATE leaf both `Requires.Conditions` (station start gate) and every `Roll`/`StationStep`
  `Conditions` array evaluate over the api `FactorRegistry`. An unregistered factor id fails
  CLOSED (a gate on a server without the referencing mod installed stays locked, never silently
  open) - never a second condition schema.
- **REF-OR-INLINE (`CHILD_ASSET_CODEC`, schema-review wave)** - three leaves that reference one of
  THIS mod's own asset types accept either a plain id string or an inline anonymous body, via the
  engine's own `ContainedAssetCodec` (declared as a `CHILD_ASSET_CODEC` constant on each referenced
  type, the first-party `CameraShake` pattern): `LootRef.Lootables[]` ->
  [`LootableAsset`](LootableAsset.java), `StationStep.Stamp.Stats.Pool` -> [`RollPool`](RollPool.java),
  and `ActionDef.Ref` -> [`ActionAsset`](ActionAsset.java). An inline body may carry its own
  `"Parent"`, and the leaf also emits a TYPED cross-reference into the generated docsite schema
  instead of an untyped string. **Native asset references stay id-only** (`Presentation.Interaction`,
  `EffectRef`, `Grants.DropList`, `Recipe.FromCrafting.Benches[]`, ...), and three own-type leaves
  deliberately stay id-only too because an inline there is semantically dead - it would mint an
  anonymous asset nothing else can reach: `ExtensionAsset.Target.*`, `ActionDef.Anchors.*.Station`,
  `FlairAsset.Stations[]`. **Caveat, documented at both array-asset leaves:** `LootableAsset` and
  `RollPool` each have exactly ONE content array, so a `Parent` body REPLACES it wholesale rather
  than appending - to ADD rolls/entries to a shared table, ship an `ExtensionAsset` targeting it (or
  author the extras in the sibling inline `Rolls`/`Entries` leaf). `ActionAsset` has no such caveat:
  every leaf is `appendInherited`, so a `Parent` body is a genuine per-group delta.
- **[`EffectRef`](EffectRef.java)** (NEW, seam wave decision 51d) - the ONE native-EntityEffect
  reference leaf (`{Id, DurationMs?}`, id-ref-only per decision 53, never inlines the effect body).
  Reused at every altitude an effect payload lands: `Presentation.Effect` (a single per-moment
  effect group), `Roll.Grants.Effects[]` (a reward-time effect array), and `Puppet.Hide.Effect` (the
  `Hide.Route: "Effect"` arm's configuration). Two effect-shaped leaves deliberately STAY bare ids:
  `Hold.EffectId` (the movement hold's lifetime is engine-owned - a short TTL re-applied every
  heartbeat, so an authored duration would be inert or would defeat the decay-as-release safety net)
  and `Presentation.Shake.EffectId` (a `CameraEffect`, whose duration is baked inside the referenced
  asset and has no per-use override anywhere on the engine's fire-and-forget path). `Id` is the native effect
  asset id; `DurationMs` null defers to the effect asset's own TTL. The engine tracks session-scoped
  effects so `stop()` removes them (engine scope); an unresolvable id is a validator INFO + apply
  no-op.
- **`Vec3` / `Rotation` / `TagMatch` - LIFTED to `ziggfreed-common`'s `codec/` package
  (maintainer directive, 2026-08-05, the root lift paradigm): generic Hytale codec primitives, not
  station schema.** This mod imports `com.ziggfreed.common.codec.{Vec3,Rotation,TagMatch}`; the
  semantics are unchanged from when they were minted here in the schema-review wave. One line each:
  `Vec3` is the `{X, Y, Z}` nullable-double offset leaf (engine `Vector3d` leaf NAMES; deliberately
  NOT `Vector3dUtil.CODEC`, whose primitive axes + per-axis non-null validators reject the partial
  `"Offset": {"Y": -0.1}` authoring and erase null-means-inherit overlay granularity) at
  `Custody.Display.Offset`, `Puppet.Offset`, `Hold.Mount.Entity.Offset`, and
  `Presentation.ModelParticle.PositionOffset`; `Rotation` is the `{Yaw, Pitch, Roll}`
  nullable-DEGREES leaf (native rotation vocabulary; NOT `Rotation3f.CODEC`, which is
  radians-in-floats with a NaN sentinel) at `Custody.Display.Rotation` and
  `Presentation.ModelParticle.RotationOffset`; `TagMatch` is the `{"<tagFamily>": ["<value>",...]}`
  map codec + ANY-of matcher at `StationAsset.Tool.Tags` and `ActionInput.Tags` (the map is ONE
  leaf for overlay/inherit purposes - authoring it replaces the whole map). Each consumer still
  documents its own FRAME/units at its own accessor.
- **[`Picker`](Picker.java)** (NEW, seam wave decision 50) - the multi-output picker knob group
  (`{ShowLocked?}`, reader-default `true`), a top-level `StationAsset.Picker` default whole-group
  overridable per `ActionDef`. `ShowLocked` governs whether tool-gated output categories render
  greyed (via `ui.gate.locked_*`) or hide entirely. A single-category station never shows a picker,
  so the group is a no-op there.

## Content types

- **[`StationAsset`](StationAsset.java)** - an interactive work station, loaded from
  `Server/RpgStations/Stations/*.json` (id = lowercased filename). Top-level groups: `Identity`
  (name/desc/icon keys), `Work` (cycle cadence + `PerCycleContributions[]`, amounts posted to a
  namespaced [`Contribution`](Contribution.java) channel the engine never interprets, forwarded
  verbatim on `StationCycleCompletedEvent.contributions()`; optional `Idle` practice
  mode; `Looping` - `false` completes the whole SESSION after one program run instead of looping;
  named for the native boolean spelling, since the engine reserves `Repeat` for an iteration COUNT,
  which is exactly what `StationStep.Repeat` one level down means),
  `Recipe` (authored `Conversions` over `Ingredient` Input/Output, or `FromCrafting`
  derivation), `Hold` (the movement-lock effect / the `Mount` knob family - see
  `../station/CLAUDE.md`'s Mount bullet, UNCHANGED by scope-2), `Tool` (the held-tool gate +
  `PowerScale` + the schema-review wave's **`MinDurabilityPercent`** - a 0-100 wear floor checked at
  ENGAGE only, ORTHOGONAL to the three identity routes: which tool and how worn it may be are two
  independent questions, and a session already running still ends at breakage rather than at the
  threshold, so a worn tool is refused before the work starts but never mid-cycle),
  `Custody` (session-scoped placed-input custody, see [`Custody`](Custody.java)
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
  &gt; `FromCrafting.NativeTime` linear transform &gt; `Work.CycleMs`. **`Recipe.Yield`** is the
  QUANTITY counterpart of that time transform, and sits on `Recipe` rather than inside
  `FromCrafting` on purpose: what a station yields is a property of the recipe, not of how the recipe
  was discovered, so it applies to authored `Conversions` and derived ones alike. Leaves:
  `Base` (flat quantity; absent = each conversion's own authored quantity), `Scale` (multiplier,
  rounded to whole items, reader-default 1.0), `Min`/`Max` clamps, and `Bonus {Values: FactorRef[],
  Floors: [{Min, Add}]}` - the SAME weighted-sum-then-floor-lookup shape `Roll.Ladder` uses, so a
  yield bonus keys off any registered factor and an author learns one vocabulary, not two. Floors are
  NOT cumulative (the highest reached wins), and a 1-item floor is enforced under every path because
  a conversion that consumed its inputs and produced nothing is item loss, not a tuning outcome.
  **It resolves PER CYCLE** (`station.StationYield`, off the same `FactorSnapshot` the cycle's loot
  rolls use), which is exactly why the retired `FromCrafting.OutputPerInput` scalar could not do this
  job - it baked one number in at asset-fold time, and a yield keyed off the worker's held tool has
  to be re-read every cycle. See
  `../station/CLAUDE.md` for how every group drives the engine (`station.ActionResolver` is the
  resolution choke point).
- **[`ActionDef`](ActionDef.java)** - one `Actions` map entry: nullable whole-group overrides of
  every `StationAsset` group (`Input`/`Custody`/`Puppet`/`Work`/`Recipe`/`Tool`/`Hold`/`Camera`/
  `Animation`/`Presentation`/`Completion`/`Loot`/`Requires`) PLUS `Label` (an advisory display
  key), `Steps` (an authored [`StationStep`](StationStep.java) program; absent means "build the
  implicit program"), and TWO scope-2 additions: **`Ref`** (design 1.5, below) and **`Anchors`**
  (design 2.2). Every codec leaf carries `.documentation`; the E1 factory-gap is closed
  (`ActionDef.of(...)` takes every group).
  - **`Ref` - the standalone-action attachment route (design 1.5, decision 28a, BOTH forms
    survive):** `{"Ref": "<actionAssetId>"}` names a standalone [`ActionAsset`](ActionAsset.java)
    (below) as the BASE; any OTHER group authored on the SAME inline entry overlays it group-wise
    (the same whole-group-replace semantics `ActionResolver` already applies station->action,
    applied twice: `Ref`-base -> inline overlay). A dangling `Ref` is validator finding
    `ACTION_REF_UNKNOWN` (structural + full pass warn; engage denies gracefully with
    `ui.station.action_unavailable`). Native `Parent` BETWEEN `ActionAsset`s is the sibling
    "author only the delta" reuse route; `Ref` + overlay is the per-station ATTACHMENT route -
    two different reuse axes, not redundant.
  - **`Anchors` - named multi-station anchor declarations (design 2.2):**
    `{"<anchorId>": {"Station": "<stationId>", "MaxRadiusMeters": 12}}` (`MaxRadiusMeters` names
    its unit; the pre-review `MaxRadius` spelling is gone, no alias). Legal on both an inline
    `ActionDef` and a standalone `ActionAsset` (expected mostly on the latter). The reserved
    anchor id `"self"` (the primary station block) is implicit and never authored. DISCOVERY
    (nearest matching placed block within `MaxRadiusMeters`), CLAIMING, and a `StationStep.Walk`/
    `At` naming an anchor all EXECUTE - see `../station/CLAUDE.md`'s boundary section.
    `ANCHOR_STATION_UNKNOWN` warns an unknown `Station`.
- **[`ActionAsset`](ActionAsset.java)** (NEW, design 1.5) - a standalone, reusable,
  fourth-party-extendable action: `Server/RpgStations/Actions/<Name>.json` (Pattern A, id =
  lowercased filename). Its body is the EXACT SAME field set as the inline `ActionDef` (one
  schema authority - the fields live on an embedded `ActionDef` the wrapper's codec delegates to)
  PLUS `Anchors`; it deliberately OMITS `Ref` (a standalone action is itself a base and never
  references another). A station attaches it via an inline `Actions` entry's `Ref` leaf (above).
  Supports native `Parent` between `ActionAsset`s for delta reuse. See the fish exemplar
  (`Actions/PrepFish.json`, design 2.7) for the flagship authoring shape.
- **[`Custody`](Custody.java)** - session-scoped placed-input custody: `{MaxQuantity?,
  SingleFamily?, Input?, States?, Display?}` - shape UNCHANGED by scope-2 apart from the
  schema-review wave's `SingleFamily` addition (below) and the deletion of the pre-scope-2
  bare-number `Rotation` legacy-tolerant wrapper (`LegacyTolerantCodec` removed - with the whole
  surface re-authored in-wave there is no legacy JSON to tolerate; `Display.Rotation` decodes ONLY
  the nested `{Yaw,Pitch,Roll}` degrees group now). `MaxQuantity` defaults to **100**. `Input` (reusing
  [`ActionInput`](ActionInput.java)'s ItemId/ResourceTypeId/Tags/Function routes) is the explicit
  placement-acceptance matcher; absent derives acceptance from the resolved action's own `Recipe
  .Conversions` inputs instead (ANY of a multi-input conversion's materials is accepted - a
  multi-material station is loaded one material at a time). **`SingleFamily`** (schema-review wave,
  Boolean, default false) locks a non-empty claim to the FIRST-placed item's resource family: a
  later placement outside that family is refused until the claim empties again ("50 oak OR 50 pine,
  never 100 mixed"). Enforced in the ONE acceptance choke point, so both the held-item route and the
  inventory-scan fallback honour it; the pure core is `station.StationCustody#acceptsFamily`. `States` (`{Empty?, Loaded?, Working?}`) names the block's own
  `State.Definitions` entries the engine flips between; null = no visual/hint flip. **`Working`
  (AV wave) is the third, independently-nullable leaf and means "actively being worked", NOT "has
  input in it"**: the engine holds the block in it only while a work step is genuinely executing
  there, reverting to `Loaded`/`Empty` at the next NON-working step's entry, at a walk phase's
  departure, at the runtime idle transition, and at every session stop path (a working step's own
  SUCCESS deliberately does NOT darken, so a repeating program whose LAST step is the work holds a
  steady lit look across the inter-cycle gap instead of flickering) - so the cooking fire's
  burning look belongs here and raw fish on a cold fire leaves it dark until the cook beat begins. It applies to whichever block a step runs AT, so the
  primary station AND a claimed remote anchor both get it; which steps count is
  `StationStep.IsWork` (below). Omitting it is byte-identical to pre-knob behavior. `Display`
  (`{Offset: Vec3, Scale, Rotation: Rotation}`, FACING-RELATIVE to the placed block's own yaw, every
  leaf `appendInherited`; the block yaw folds into `Rotation.Yaw`) opts the placed input into a PLACED-AS-ENTITY visual - see
  `../station/CLAUDE.md`'s dedicated bullet for the full engine-side mechanism. The jar's own
  `Stations/Sawmill.json` authors `Display {Offset{Y:-0.1}, Scale:0.4}` as the shipped standalone
  default; a pack re-tunes it through an `ExtensionAsset`'s `Custody` per-leaf overlay (rule 5)
  rather than a full-file station override.
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
    primary station `"self"`), `Repeat` (`{Times}` fixed OR `{Min,Max,AddFactors}`
    ranged, resolved once at step entry via the pure `resolveCount(factorContribution)`),
    `Duration` (`{Ms}`, a post-phase hold; prop/presentation persist across it), `Puppet`
    (per-step `{Clip?, Prop?}` override, reusing `Puppet.Prop`'s exact codec), `Presentation`
    (fires once at step ITERATION entry, round-8 rule unchanged - so a `Repeat`ed step cues once
    PER BEAT, which is how the fish exemplar's 3 scale beats each get their own chop SFX + chips
    with zero engine work), and (AV wave) **`IsWork`** (a nullable Boolean: does this step drive
    its `At`-anchor block's `Custody.States.Working` look? Named `IsWork` rather than `Working` both
    because `Is*` is the native boolean idiom and because `Custody.States.Working` is a STRING one
    level over). `IsWork`'s reader-default is DERIVED,
    never a mode flag - `effectiveIsWork()` returns true for a step that both `Consume`s AND
    `Produce`s, i.e. the phase model's own atomic-transform CONVERT, so the implicit program and
    any authored convert light their block for free; every other shape (pure beat, lone Consume,
    lone Produce, walk, stamp) is the load/carry/unload scaffolding around the work and defaults
    false. Author `true` to promote a pure beat that IS the work (the fish exemplar's 2.5s `cook`
    hold), `false` to demote a convert that should not light. Zero effect unless the resolved
    `Custody.States.Working` is authored.
  - **Phase groups** (all nullable): `Walk` (`{To, SpeedMps}`), `Consume`
    (`{Items: Ingredient[], From:Inventory|Custody}` - BOTH routes executable), `Stamp` (the
    enhance-commit phase, below), `Produce` (`{Items: Ingredient[], To:Inventory|Custody}`), `Roll`
    (a `LootRef` - the SAME vocabulary a station's own `Loot` group uses), `Commands`
    (`String[]`, run through the shared `CommandRewardExecutor`). `Consume`/`Produce` take the
    native `CraftingRecipe.Input`/`Output` ARRAY shape (see the `Ingredient` bullet); both are
    all-or-nothing, and a mid-list failure is covered by the pre-existing iteration refund ledger.
  - **Execution order within ONE step iteration** (fixed, honored by `station.StationStepRegistry`
    - leg A3): Conditions gate -> `Walk` -> `Consume` -> `Stamp` -> `Produce` -> `Roll` ->
    `Commands` -> `Presentation`/`Puppet.Clip` (fire at iteration entry) -> `Duration` hold
    (suspend) -> next iteration or next step. A step combining `Consume` + `Produce` is an ATOMIC
    transform (no consumed-without-produced window); the anvil's strikes re-author as pure
    `Duration` beats (`{Id, Duration:{Ms}, Puppet:{Clip}, Presentation}`), and the stamp step's
    `Duration` + `Prop:None` closes the parked post-stamp empty-hands flourish.
  - **`authorsWave3OnlyPhase()`** (a step authoring `Walk`, `At`, or `Produce.To:"Custody"`)
    survives as a pure predicate but is UNUSED by the validator: all three phases execute, so the
    boundary warn it fed is gone. The live coverage is the anchor/walk check set
    (`ANCHOR_STATION_UNKNOWN`/`WALK_TARGET_UNKNOWN_ANCHOR`/`STEP_AT_UNKNOWN_ANCHOR`/
    `WALK_REQUIRES_PUPPET`).
  - **`Stamp`** (design 9.5/3.8, the anvil's enhance-commit step, unchanged transaction shape -
    compute-then-commit, handler-enforced): `{Reagents: Ingredient[], Durability{AddMax},
    Stats{Pool?, Entries?, Picks{Min,Max}, Unique, Caps}}`. `Reagents` are `Ingredient`s consumed
    FROM THE PLAYER'S INVENTORY (not custody). `Stats.Pool`/`Entries` share
    [`StatRollEntry`](StatRollEntry.java) with [`RollPool`](RollPool.java). **`Caps` RESHAPED for
    scope-2 (M2's binding rule kept):** `Budgets: Budget[]` (each EXACTLY one of a flat
    `{Points}` or a factor-scaled `{PointsPer, Factors[]}` = `PointsPer *
    sum(resolve(f)*f.Weight)`; the
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
- **[`Presentation`](Presentation.java)** - this mod's OWN codec, deliberately direct: a moment
  plays its sound/particles/animation here with no feedback-service indirection layer. Leaves:
  `Sound`, **`Particles`** (schema-review wave: an ARRAY of `ModelParticle`-shaped bursts matching
  native `InteractionEffects.Particles`, replacing the pre-review bare-string leaf - each entry is
  `{SystemId, Scale?, DurationSeconds?, RotationOffset{Yaw,Pitch,Roll}?, PositionOffset{X,Y,Z}?}`,
  every knob nullable with reader-defaults that reproduce the old single-string playback byte for
  byte: scale 1.0, a 4-second client-playback cap, zero rotation, no offset. `PositionOffset` is
  FACING-RELATIVE through the same `station.StationBlockFacing` reader `Custody.Display`/`Puppet`
  use; `RotationOffset` is the burst's own emission rotation in DEGREES and is NOT composed with
  the block facing. The `DurationSeconds` cap is a LEAK GUARD, not decoration - an unbounded-spawner
  system fired uncapped never stops. There is deliberately **no `Color` leaf**: the only
  colour-capable engine overload takes no playback cap, and that leak must never be reintroduced),
  `Animation`, `AnimationItem`, `AnimationSlot`, **`CameraEffect`** (renamed from `Camera` in the
  schema-review wave: it names a native `CameraEffect` asset, spelled as native
  `InteractionEffects` does, which also disambiguates it from the nested `Camera` GROUP one level up
  - `StationAsset.Camera`, the station's own third-person pull), `Shake` (nested
  `{EffectId, Intensity}`), plus TWO seam-wave native-composition groups: `Interaction`
  (`{Id}`, an inner class - fires a native RootInteraction chain by id, decision 51b) and `Effect`
  (an [`EffectRef`](EffectRef.java) - applies a native EntityEffect by id, decision 51d). Both
  id-ref-only.
- **[`Puppet`](Puppet.java)** (unchanged by scope-2) - "mount the player, hide their player
  model, and spawn/display a visual of their character model performing the steps" - a top-level
  group sibling to `Hold`/`Camera`/`Animation`/`Custody` (ORTHOGONAL to whichever `Hold.Mount`
  holds the real player, never nested under `Hold`), whole-GROUP overridable per `ActionDef`:
  `{Enabled?, Hide{Route,Effect?}, Look{Source,ModelId?,FallbackModelId?}, Offset: Vec3,
  Yaw, Prop{Source,ItemId?,Slot?}}`. `Hide.Route` is a THREE-arm union: `"Scale"` is the
  in-game-crowned default (hides the puppeteer's own body via `ziggfreed-common`'s
  `entity.PlayerPuppetService`); `"Effect"` is schema-reserved future work; `"None"` is the
  deliberate degraded fallback. `Look.Source` defaults `"PlayerClone"`, with `"Model"` an open
  performer seam. `Offset`/`Yaw` place the puppet relative to the station's block-top anchor and are
  **FACING-RELATIVE to the placed block's own yaw** (round-3 smoke, 2026-07-29): authored `+Z` is
  the block's FRONT, `+X` its right, `Offset.Y` stays vertical, and the block yaw folds additively
  into the authored `Yaw` (so `Yaw: 0` means "faces the same way the block does"). This is exactly
  the round-8 `Custody.Display` precedent applied to `Puppet` - the two now share ONE reader,
  `station.StationBlockFacing` (`yawRadians` reads `World#getBlockRotationIndex`, try-guarded to yaw
  0; `rotateOffset` is the one horizontal-rotation core), with the per-consumer composition in
  `StationPuppetController#resolveWorldOffset`/`#resolveYawRadians` and
  `StationCustodyDisplay#resolveWorldOffset`/`#resolveRotationRadians`. It supersedes the earlier
  WORLD-SPACE simplification, under which which SIDE of the station a puppet stood on depended
  entirely on how that particular block happened to be placed (the round-3 smoke defect: the
  maintainer's sawmill faced differently than the block the values were tuned on). **IDENTITY at
  yaw 0**, so every pre-existing in-game-tuned value is byte-identical on a default-facing
  placement - no re-tune, no migration. `Prop.Source` defaults
  `"MirrorHeld"`; `"ItemId"` forces a specific prop, `"None"` empties the puppet's hands. A
  `StationStep` carries its own small `{Clip, Prop}` override (`StationStep.PuppetOverride`)
  reusing this exact `Prop` codec for moment-to-moment swaps. **Seam wave (decision 47/48) - FULL
  Look nesting symmetry**: `Look.Source` is now a THREE-arm union (`PlayerClone`|`Model`|`NpcRole`);
  the flat `ModelId`/`FallbackModelId` retro-nest into the cohesive `Look.Model {ModelId,
  FallbackModelId}` group (read for `Source:"Model"`; `FallbackModelId` is the any-source resolution
  fallback), and the NpcRole performer arm's config is the parallel `Look.Role {RoleId, SkinSource
  (PlayerClone|RoleDefault), Persist, SpeedMps}` group (read for `Source:"NpcRole"`; `Model`/`Role`
  are inner classes of `Puppet`, siblings of `Hide`/`Prop`). See `../station/CLAUDE.md`'s
  puppet-engine bullet (`StationPuppetController`). The jar's own `Stations/Sawmill.json` authors the
  shipped standalone default (`Enabled true`, `Hide.Route "Scale"`, `Look.Source "PlayerClone"`,
  `Offset {0.0, -0.4, 1.0}`, `Yaw 0.0`, `Prop {MirrorHeld, Hotbar}`, the maintainer's in-game-tuned
  values); the pack-shipped Anvil authors its own. `Stations/CuttingBoard.json` authors one
  too (`Offset {0.0, -0.4, 0.6}`, station-level so the `Ref`'d `prepfish` action inherits it
  wholesale - `Actions/PrepFish.json` deliberately authors NO `Puppet`, which is what
  `WALK_REQUIRES_PUPPET` needs), and `Stations/CookingFire.json` gained one in the round-3 smoke for
  its own direct cook loop (`Offset {0.0, -0.45, 0.9}`, `Yaw 0.0` - PROVISIONAL, a low-campfire
  first pass, in-game tuning expected). A pack re-skins any of them through an `ExtensionAsset`'s
  `Puppet` per-leaf overlay (rule 5), never a full-file station override.
- **[`Roll`](Roll.java)** (REWRITTEN for weighted-factor unification, design 4.2) - the
  conditional-lootable roll: `Trigger` (`Cycle`/`Completion`), `Conditions[]`, `Chance{BasePercent,
  AddFactors[], CapPercent}`, `Ladder{Values[], Floors[]}`, `Grants{BonusOutputCopies, DropList,
  Commands[], Effects[], Contributions[]}` (top-level AND per-floor, both fire; `Effects[]` is a seam-wave
  [`EffectRef`](EffectRef.java) array applying native EntityEffects on grant, decision 51d). Scope-2 changes: `Chance.AddFactors` entries
  are now `FactorRef`s (gained `Weight`, previously bare `{Factor,Param}`); `Ladder.Value`
  (singular) is REPLACED by **`Ladder.Values[]`** (JSON key `Values`, a `FactorRef[]` summed
  BEFORE the floor lookup, so a ladder composes two `stat` channels like `YourMod_Luck` +
  `YourMod_Luck_Woods` - the loot middle path's composition exemplar; a single-factor ladder
  authors a one-element array; `getValue()` is GONE). M3's binding fixes carry forward unchanged:
  a `Ladder.Floor` has no direct `DropList` (every floor routes through its own `Grants`);
  top-level `Grants` AND the reached floor's `Grants` both apply; a failing `Chance` means the
  `Ladder` never evaluates; `BonusOutputCopies` is meaningless outside a `Cycle` trigger (warns).
  **`Grants.Contributions[]`** is a one-shot array of the SAME [`Contribution`](Contribution.java)
  record `Work.PerCycleContributions` uses - one record, two authoring SITES, and the site fixes
  the meaning: a find's grant is not "per cycle", and it BYPASSES both scalings a per-cycle entry
  goes through (the tool-power multiplier and the idle fraction), riding its own
  `StationCycleCompletedEvent.oneShotContributions()` list so a rare find is worth
  the same whatever tool the player holds. That is why the per-cycle key spells `PerCycle` out loud
  and this one does not: the two sites must never read as the same blob in an author's eye. There
  is deliberately no `Scaled` knob on the record (meaningless here, and it would let an author
  defeat the one-shot rule); the first-party precedent for site-fixed meaning is
  `EntityStatType`'s `MinValueEffects`/`MaxValueEffects`, one type whose meaning is fixed purely by
  the key it hangs under. `Cycle`-trigger only (a Completion roll fires from inside
  `stop()`, after the last cycle event; the validator warns exactly as it does for
  `BonusOutputCopies`).
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
    | Station   | `PerCycleContributions[]`, `Loot` (LootRef append), `Actions{}` (new keys only), `Conversions[]`, `Steps[]`, `Anchors{}` (new keys only), `Puppet` (per-leaf overlay), `Custody` (per-leaf overlay) |
    | Action    | `Steps[]`, `Anchors{}` (new keys), `Loot`, `Conversions[]`, `PerCycleContributions[]`, `Puppet`, `Custody` |
    | Lootable  | `Rolls[]` (append)                                                       |
    | RollPool  | `Entries[]` (append)                                                     |

  - **Merge + conflict semantics** (deterministic, ALL pure/unit-testable on this class, applied
    engine-side by `ExtensionCatalog.applyTo` - leg A3's scope):
    1. ADDITIVE ONLY - an extension never mutates/replaces/removes an existing entry.
    2. Keyed collections (`Actions`, `Anchors`): the BASE always wins a key collision
       (`EXTENSION_KEY_COLLISION`, entry skipped); among extensions, `APPLY_ORDER` decides.
    3. Unkeyed arrays (`PerCycleContributions`, `Conversions`, `Rolls`, `Entries`): pure append, ordered by
       `APPLY_ORDER`.
    4. Ordered insertion (`Steps` -> `StepInsertion{Action?, Anchor, Insert:StationStep[]}`):
       `Anchor` is exactly one of `{After:"<stepId>"|Before:"<stepId>"|AtStart|AtEnd}`
       (`effectivePlacement()` degrades to `AtEnd` + `EXTENSION_ANCHOR_MISSING` warn on a
       missing/dangling step id); inserted steps need `Id`s so LATER extensions can anchor on
       them. Co-anchored insertions from different extensions apply in `APPLY_ORDER` (m2).
    5. NESTED PER-LEAF OVERLAY (`Puppet`, `Custody`) - the two NON-collection payloads, so they
       merge leaf-wise instead of appending: recursively, at EVERY nesting depth, an AUTHORED
       extension leaf wins and a NULL one leaves the base's value intact (the
       `appendInherited`/nullable-nested-leaves convention applied ACROSS assets instead of down a
       `Parent` chain). A `Custody` overlay carrying only `Display` never touches the base's
       `States`/`MaxQuantity`/`Input`; a `Display` carrying only `Scale` never clears its `Offset`;
       a `Puppet` carrying only `Offset.Z` keeps `Offset.X`/`.Y` plus `Hide`/`Look`/`Prop`/`Yaw`.
       (Leaf-granularity note: a MAP-valued leaf - `Custody.Input.Tags` - replaces wholesale as
       ONE leaf, never per tag family.)
       Overlays apply in `APPLY_ORDER`, so the LATER (higher-priority) extension wins a same-leaf
       contest. Engine-side cores + the load-bearing test: `station.ExtensionCatalog`'s
       `overlayPuppet`/`overlayCustody`, `station.ExtensionOverlayTest`.
  - **`APPLY_ORDER`** - the ONE apply-order tuple (`Priority` ascending so a HIGHER priority
    applies LATER and wins a tie, then extension id lexicographic) - a total order over distinct
    assets, so a stable sort fully determines the result on every server. `sortedForApply(...)`
    is the pure, unit-tested, catalog-AND-validator-shared core.
  - **Composition order (m7):** extensions apply to the Parent-resolved target at READ time;
    extension additions do NOT flow down `Parent` chains. A `Target:{Action}` extension flows to
    every `Ref` user of that action; a `Target:{Station}` step-insert applies post-`Ref` to that
    station only. **The ONE Action-target identity** (adversarial-verify F4,
    `ActionResolver.actionTargetId`, shared by the Loot/PerCycleContributions appends AND the Puppet/Custody overlays):
    the `Ref`'d `ActionAsset` id when the inline entry Refs one, else the inline entry's own map
    key; the IMPLICIT action of a no-`Actions` station is deliberately unreachable by an Action
    target (no accidental global `Action:"work"` broadcast) - address it via `Station`. Overlay
    order is Action first, Station on top (the station-scoped statement wins a same-leaf
    contest).
  - **Deliberately NON-extensible** (docs state each): `Requires` (an extension must never
    tighten/loosen another author's gate), `Settings` (owner-only singleton), scalar groups
    (`Work`/`Hold`/`Camera`/`Animation` - override is load-order's job, not extension), the
    INTERNALS of an existing `Roll` (extenders add their OWN Rolls beside it), and
    `FlairAsset.Moments`.
  - **The presentation-overlay exception (`Puppet`, `Custody` incl. `Custody.States`)**: both were
    on the non-extensible list above through wave 2, on the same "override is load-order's job"
    argument. That forced a pack that only wanted to RE-SKIN a base station's presentation to ship a
    full-file station override, and deleting such an override silently dropped every group it was
    the sole author of (exactly what happened to the sawmill's `Puppet`/`Custody.Display` when the
    pack's full-file `Stations/Sawmill.json` was retired for an additive extension the codec could
    not carry them in). Rule 5's per-leaf overlay is the non-destructive replacement: a re-skinning
    pack authors only the leaves it re-tunes and inherits every other one from the base.
  - See `../station/CLAUDE.md`'s ExtensionCatalog bullet for the engine-side fold + the
    cross-pack-aware validator's `EXTENSION_APPLIED` boot summary.
- **[`RpgStationsSettingsAsset`](RpgStationsSettingsAsset.java)** - `Server/RpgStations/Settings/
  Settings.json`, a single id (`settings`), jar default + pack-overridable: `{Enabled,
  SummaryHud:{Enabled, Position, OffsetX, OffsetY, TtlMs}}` (`OffsetX` is the schema-review wave's
  horizontal sibling of the long-standing `OffsetY`; both feed the common `HudPosition`). Deliberately NON-extensible (server-global
  singleton). Unchanged by scope-2. Folded into `station.SettingsCatalog`.
- **[`Requires`](Requires.java)** - `{Permission?, Conditions?[]}`, evaluated at station start;
  any failing `Condition` denies with `ui.station.locked`. Unchanged shape by scope-2.

## The authoring layer on every codec (schema-review wave)

Three cross-cutting layers ride the SAME `FieldBuilder` chain as the field declaration itself, so
they land per-leaf and are never a parallel table that can drift from the codec:

- **`.documentation("...")` on EVERY leaf.** All 309 `KeyedCodec` leaves reachable from the seven
  Pattern A `CODEC` statics carry a description of what the leaf does plus its default/unit;
  `AssetDocumentationCoverageTest` walks `BuilderCodec#getEntries()` (unwrapping array/map codecs
  down to nested `BuilderCodec`s, identity-deduped so a shared leaf like `Vec3`/`Condition`/
  `Presentation` is checked once) and FAILS THE BUILD on a blank one. It is the input to both the
  docsite schema reference ([`../docs/SchemaDocWriter`](../docs/SchemaDocWriter.java), regenerated
  by `gradlew generateSchemaDocs`, drift-guarded by `SchemaDocDriftTest`) and the in-game Asset
  Editor's field help. **Add the `.documentation` in the same edit as a new leaf** - the coverage
  test is not a reminder you can defer past a build.
- **`.metadata(...)` for the in-game Asset Editor.** A bare `UIEditorSectionStart("<label>")` opens
  a section at each top-level group (18 across `StationAsset`, plus Engine / Summary HUD on
  `RpgStationsSettingsAsset`); the rest nest inside a `UIEditor` - `UIEditor.Dropdown("<datasetId>")`
  turns a leaf into a pick list, `UIEditor.LocalizationKeyField("<keyTemplate>")` marks
  `Identity.NameKey`/`DescKey` + `ActionDef.Label` (the template's `{assetId}` fills from the asset
  being edited), `UIEditor.Icon` marks `Identity.Icon`. The dataset VALUES are served by
  [`AssetEditorDataSets`](AssetEditorDataSets.java) (below). **Editor metadata is authoring
  convenience, never validation**: hand-written JSON never passes through the editor, so no
  `station.StationValidator` check is retired on the strength of a dropdown, and map-KEY
  vocabularies (`FlairAsset.Moments`/`Flairs` keys, `Stamp.Stats.Caps.PerStat` keys, `TagMatch` tag
  families) get no `Dropdown` at all - the metadata addresses a field VALUE only, so those stay
  validator-backed.
- **`.addValidator(...)` / `.afterDecode(...)` WARN checks.** Field-local invariants (positive
  quantities / cycle times / budgets, non-blank required ids, non-empty authored arrays) and the
  exactly-one-of cross-field contracts (`Ingredient`'s item route, `ExtensionAsset.Target`,
  `Stamp.Stats.Caps.Budget`'s route, a `StepInsertion.Anchor` authoring more than one placement)
  report at the asset's own decode path/line, so a pack author sees them in the boot log at fold
  time rather than only from the deferred full validate pass. **They come from
  [`CodecWarnValidators`](CodecWarnValidators.java), never the engine's built-in `Validators`
  factory**: every built-in there routes through `ValidationResults.fail()`, and
  `ContainedAssetCodec` gates registration on `!hasFailed()`, so attaching one would silently DROP
  the whole asset on a bad value - the exact opposite of this mod's never-block posture (an asset
  ALWAYS loads; a finding is advisory). The one legitimate use of a failing engine validator is a
  leaf without which the asset means nothing at all.

- **[`AssetEditorDataSets`](AssetEditorDataSets.java)** - one keyed
  `AssetEditorRequestDataSetEvent` handler per `Dropdown` dataset id (the first-party
  `ItemCategories` shape), registered once from `RpgStationsPlugin#setup()` inside a whole-body
  try-guard: the Asset Editor is a builtin module, and an authoring convenience must never be able
  to fail plugin startup (a failure degrades every dropdown to a free-text field). Two flavors:
  LIVE sets read straight off this mod's own runtime catalogs/registries at request time
  (`rpgstations:stations`/`actions`/`lootables`/`rollpools` off `station.StationCatalog`/
  `ActionCatalog`/`loot.LootableCatalog`/`RollPoolCatalog`, `rpgstations:factors` off
  `api.impl.FactorRegistryImpl#registeredIds`, `rpgstations:channels` off
  `api.impl.ContributionChannelRegistryImpl#registeredIds` - so an asset reload or a late
  third-party factor/channel registration simply widens the next answer, and an empty answer is
  legitimate, never an error: nothing has declared a channel yet);
  FIXED sets are the closed union-discriminator vocabularies, each sourced from the SAME constant
  the decoder compares against (`Puppet.HIDE_ROUTE_*`/`LOOK_SOURCE_*`/`SKIN_SOURCE_*`/`PROP_SOURCE_*`/
  `PROP_SLOT_*`, `StationStep.Consume.FROM_*`/`Produce.TO_*`/`OnConditionFail.RESULT_*`,
  `Roll.TRIGGER_*`, `station.StationCameraPreset#id`) so a renamed arm can never leave a stale
  dropdown behind. The three sets with no constant behind them (mount surface, camera mode, action
  input function) name their consumer in a comment beside the literals. **The dataset ids are
  declared in two places and no test cross-checks them** (`BuilderField` exposes no public metadata
  getter, so a codec walk cannot read the declared `Dropdown` ids back): adding a `Dropdown` to a
  leaf without adding its handler here yields a silently EMPTY pick list, not an error. Declare the
  id as a constant on this class and reference it from the codec's `metadata(...)` call when adding
  the next one.

**No `PackControlAsset`/`Control` map infra exists in this mod** - every fold is always additive (`replace=false`); a reload re-fires the `LoadedAssetsEvent`
and re-folds for free, no owner-override precedence layer beyond `defaults < pack` load order.
