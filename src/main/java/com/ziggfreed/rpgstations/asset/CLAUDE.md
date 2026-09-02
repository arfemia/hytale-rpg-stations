# asset/ - the Pattern A content codecs

Router for `asset/`. Every custom asset type this mod authors: the codec IS the schema
(PascalCase keys, nested sub-object groups, every leaf `appendInherited` for native `Parent`
reuse, `.documentation("...")` on every field for the generated schema reference, `SCHEMA.md`).
`RpgStationsPlugin` registers each as its own `AssetStoreRegistrar` store + folds loaded entries
into the matching `station`/`loot` package catalog on `LoadedAssetsEvent`.

**Action-first schema (the pre-release restructure): a station is an ORDERED LIST OF
SELF-CONTAINED ACTIONS, and station-level group inheritance is DELETED.** `StationAsset` keeps
only the four things that genuinely belong to the STATION - `Identity`, `Block`, `Requires`,
`Flairs` - plus its ordered `Actions[]`. Everything about HOW a job runs (its recipe, tool gate,
work cadence, custody, worker presentation, moments) lives INSIDE an [`ActionDef`](ActionDef.java)
entry; an action reads its own groups, or the [`ActionAsset`](ActionAsset.java) its `Ref` names,
and nothing else. File/folder layout:
`Server/RpgStations/{Stations,Actions,Patterns,Flairs,Extensions,Settings}/` (loot tables and roll pools are the shared library's, at `Server/ZiggfreedCommon/{Lootables,RollPools}/`). The
multi-station seam (`ActionDef.Anchors`, `StationStep.Walk`/`At`, `Produce.To:"Custody"`) fully
EXECUTES, with no decode-only decoys anywhere in the schema. See `../station/CLAUDE.md`'s
resolution section for the engine half.

## Naming conventions this whole schema keeps (read once, applies everywhere)

- **Every id is authored in the same casing as the thing it names.** An OWN-asset id is the
  filename, so it is PascalCase (`"Lootables": ["SawmillFinds"]`, `"Target": {"Station": "Sawmill"}`,
  `"Ref": "PrepFish"`, `"Pool": "AnvilWeaponPool"`); a NATIVE id is whatever the native asset is
  called (`"DropLists": ["RPG_Station_Sawmill_T1"]`, `"EffectId": "RPG_Station_Hold"`). Internally every
  own-asset id canonicalizes to lowercase and every reference matches CASE-INSENSITIVELY, so a
  casing slip never breaks content - the convention is for readability, not for the matcher. The one
  deliberate exception is `RpgStationsSettingsAsset.SummaryHud.Position`, a foreign preset id that
  keeps its own library's SCREAMING_SNAKE spelling because that vocabulary is not this mod's to
  rename (the shared-library `HudPosition` presets, authored PascalCase like everything else here;
  the SCREAMING_SNAKE spelling still resolves since matching is case- and underscore-insensitive).
- **Closed union DISCRIMINATOR values are PascalCase, without exception** (`Consume.From:
  "Inventory"`, `Hide.Route: "Scale"`, `Prop.Source: "MirrorHeld"`, `Mount.Surface: "Entity"`,
  `Roll.Trigger: "Cycle"`, `Look.Source: "NpcRole"`, `Camera.Recipe: "LookRot"`).
- **The LANG-KEY namespace stays lowercase forever**, independent of the authoring convention above:
  `StationService` builds a fallback station-name key by lowercasing the station id
  (`rpgstations.station.cookingfire.name`), so a PascalCase authoring flip does not touch it. Do not
  "fix" a lowercase lang key to match a PascalCase asset id.
- **A collection leaf is PLURAL and an array** (`Sounds`, `DropLists`, `Steps`, `Factors`,
  `Commands`, `Particles`, `Lootables`, `Rolls`, `Budgets`), so growing from one entry to two is
  never a schema break. `Recipe` is the one deliberate SINGULAR exception: an action performs
  exactly one transform (see the `ActionDef` bullet below for why two transforms means two
  actions, never a second array entry).

## Shared leaf vocabulary (the DRY layer every type reuses)

- **[`Ingredient`](Ingredient.java)** - the ONE item-quantity leaf
  (`{ItemId|ResourceTypeId|Tags, Quantity, Socket?}`, native-shaped like vanilla
  `MaterialQuantity` incl. its `ItemTag` route). AT MOST one route per entry: `ItemId` exact,
  `ResourceTypeId` a native family, `Tags` the shared `TagMatch` map (an EMPTY value list under a
  family key = key-presence, the single-native-tag form); the family and tag routes are INPUT-only,
  and an INPUT authoring NO route is the legal MATCH-ANY entry (accepts whatever its custody pile
  holds; never drawn from a player's open inventory - the validator warns
  `MATCH_ANY_INPUT_WITHOUT_CUSTODY`). Route comparing delegates to ziggfreed-common's
  `match.ItemMatch` (one core under both this leaf and `ActionInput`, parity-tested). Used by
  `StationAsset.Conversion.Input/Output`, `ExtensionAsset.Conversions`,
  `StationStep.Stamp.Reagents` (exact/family routes only), and
  `StationStep.Consume.Items`/`Produce.Items`. **Every one of those sites takes an `Ingredient`
  ARRAY**, mirroring native `CraftingRecipe.Input`/`Output`: "2 planks + 1 nail -> 1 crate" is ONE
  conversion and ONE atomic step-phase pair, never a step split, and `StationRecipeDeriver`
  derives multi-input native recipes instead of skipping them. The `From`/`To` route
  discriminator stays at GROUP level beside `Items`, so one phase draws every item from - and
  writes every item to - the same place. Both phases are ALL-OR-NOTHING: availability is checked
  across every entry before anything is removed.
- **THE LOOT VOCABULARY IS `ziggfreed-common`'s.** A `Roll` and everything inside it -
  `Conditions`, the `Chance` formula, the `Ladder`, `Grants`, the `Cue` - plus `LootRef`, the
  `Lootable` and `RollPool` asset types, `StatRollEntry` and the whole stamp roll + budget model all
  live in `com.ziggfreed.common.loot` (+ `loot.stamp`), so identical JSON behaves identically at a
  station, in a chest, and at a quest turn-in. Read that package's routers for the model; the
  bullets below cover only where this schema EMBEDS it and what a station adds.
- **The weighted factor TERM** (`{Factor, Param?, Weight?}`, `Weight` defaults 1.0) is the shared
  `FactorFormula.Term`, spelled `Factors` at EVERY site a numeric factor channel is SUMMED so an
  author learns one key name rather than several: `Roll.Chance.Factors`, `Roll.Ladder.Factors`,
  `ContributionScale.Factors`, `StatRollEntry.Points.Factors`, a Stamp budget's `Factors`,
  `StationStep.Repeat.Factors`. Composition at every site is a flat weighted sum,
  `sum(resolve(Factor,Param) * Weight)` - no expression nesting (standing directive 3's boundary).
  It is the ADD/scale sibling of the shared gate leaf (`Conditions`, below): that leaf is a factor
  reference PLUS bounds (`Min`/`Max`), a term is one PLUS a `Weight`. A gate FAILS CLOSED on a
  factor nobody can answer; a term contributes 0 and the sum still produces a number - two rules
  because they answer different questions, and each is the safe answer to its own.
- **`Roll.Chance` is the full `{Base, Factors, Clamp}` formula**, read as a PERCENTAGE and held
  inside `0..100` whatever the terms say. `Base` is the flat chance with no bonuses; `Clamp.Max` is
  the ceiling a stacking bonus may never pass. A ladder's `Factors` stays a bare term array, because
  a ladder has no base to stand on and no ceiling to hold it and a `Base`/`Clamp` pair there would
  be two knobs that never do anything.
- **[`Contribution`](Contribution.java)** - the WRITE-side twin of the read vocabulary, and the
  ONE outbound numeric-post leaf: `{Channel, Param?, Amount}`. Where a `Factor` asks a registered
  provider for a number, a `Contribution` hands a number OUT - the engine forwards
  `{Channel, Param, Amount}` verbatim on `StationCycleCompletedEvent` and **never resolves a channel
  at all.** That asymmetry is the paradigm in one line: the engine owns built-in FACTORS because it
  can compute them, and owns ZERO built-in channels because it interprets none. `Channel` carries a
  `Dropdown(AssetEditorDataSets.CHANNELS)` fed LIVE off
  `api.impl.ContributionChannelRegistryImpl#registeredIds`, and an id nobody declared is a
  fail-open `UNKNOWN_CHANNEL` warn that still forwards. Used at exactly two SITES, and the site is
  the discriminator: `ActionDef.Work.PerCycleContributions[]` (scaled by the action's own
  `ContributionScale`, below) and a `rpgstations:contribution` REWARD inside a `Roll`'s `Grants`
  (one-shot, verbatim).
- **`LootRef`** (shared) - the ONE loot-reference group (`{Lootables[], Rolls[]}`).
  `Lootables` are `LootableAsset` ids at `Server/ZiggfreedCommon/Lootables/<Name>.json`; `Rolls` are
  inline `Roll`s authored at the site; both resolve when both are authored. Reused at EVERY site an
  action, step, or extension references loot: `ActionDef.Bonus`, `StationStep.Roll`,
  `ExtensionAsset.Bonus`. A `Lootables` entry accepts an INLINE lootable body as well as an id (the
  ref-or-inline surface below). See the `ActionDef` bullet for the `Bonus` group that embeds it.
- **[`Conditions`](Conditions.java)** - `{Factor, Param?, Min?, Max?}`, the ONE
  GATE leaf both `Requires.Conditions` (station/action start gate) and every `Roll`/`StationStep`
  `Conditions` array evaluate over the api `FactorRegistry`. The TYPE is `ziggfreed-common`'s
  shared `FactorCondition` (one gate schema across every engine reading the shared factor
  vocabulary); this class holds the single codec instance, built through that type's codec factory
  so the `Factor` field carries THIS mod's live `rpgstations:factors` dropdown. Every embed site
  references `Conditions.CODEC` - calling the factory again would publish the same shape twice.
  Evaluation is the shared `loot.FactorGate` (lookup-based sites) or the shared array evaluator (the
  `Requires` gate, which resolves against the registry directly). An unregistered factor id fails
  CLOSED (a gate on a server without the referencing mod installed stays locked, never silently
  open) - never a second condition schema.
- **REF-OR-INLINE (`CHILD_ASSET_CODEC`)** - three leaves that reference one of THIS mod's own
  asset types accept either a plain id string or an inline anonymous body, via the engine's own
  `ContainedAssetCodec` (declared as a `CHILD_ASSET_CODEC` constant on each referenced type, the
  first-party `CameraShake` pattern): `LootRef.Lootables[]` ->
  the shared `LootableAsset`, `StationStep.Stamp.Stats.Pool` -> the shared `RollPoolAsset`,
  and `ActionDef.Ref` -> [`ActionAsset`](ActionAsset.java). An inline body may carry its own
  `"Parent"`, and the leaf also emits a TYPED cross-reference into the generated schema reference
  instead of an untyped string. **Native asset references stay id-only** (`Presentation.Interaction`,
  `EffectRef`, `Grants.DropLists[]`, `FromCrafting.Benches[]`, ...), and three own-type leaves
  deliberately stay id-only too because an inline there is semantically dead - it would mint an
  anonymous asset nothing else can reach: `ExtensionAsset.Target.*`, `ActionDef.Anchors.*.Station`,
  `FlairAsset.Stations[]`. **Caveat, documented at both array-asset leaves:** `LootableAsset` and
  `RollPool` each have exactly ONE content array, so a `Parent` body REPLACES it wholesale rather
  than appending - to ADD rolls/entries to a shared table, ship an `ExtensionAsset` targeting it (or
  author the extras in the sibling inline `Rolls`/`Entries` leaf). `ActionAsset` has no such caveat:
  every leaf is `appendInherited`, so a `Parent` body is a genuine per-group delta.
- **STRING-OR-OBJECT ([`StringOrObjectCodec`](StringOrObjectCodec.java))** - the ONE dual-shape leaf
  mechanism: a value authorable EITHER as a bare string SHORTHAND or as the full nested object,
  dispatched per value on the raw JSON/BSON type. It follows the engine's own two dual-shape codecs
  exactly (`ContainedAssetCodec` and `Message`'s `ParamValue`): `bsonValue.isString()` /
  `reader.peekFor('"')` / `Schema.anyOf(string, <the object schema>)`. ONE consumer today,
  `Presentation.Sounds[]`. It implements `WrappedCodec` over the OBJECT body codec, so the
  documentation-coverage walk and the schema-reference writer both descend into that body's own
  leaves instead of stopping at an opaque terminal. Encoding round-trips the SHORTHAND whenever the
  value carries nothing the shorthand cannot express. **Generic, and a lift candidate** for
  `ziggfreed-common`'s `codec/` package the moment a second mod wants one.
- **[`EffectRef`](EffectRef.java)** - the ONE native-EntityEffect
  reference leaf (`{Id, DurationMs?}`, id-ref-only, never inlines the effect body).
  Reused at every altitude an effect payload lands: `Presentation.Effect` (a single per-moment
  effect group), a `rpgstations:effect` reward's own `Id`/`DurationMs` params, and `Puppet.Hide.Effect` (the
  `Hide.Route: "Effect"` arm's configuration). Two effect-shaped leaves deliberately STAY bare ids:
  `Hold.EffectId` (the movement hold's lifetime is engine-owned - a short TTL re-applied every
  heartbeat, so an authored duration would be inert or would defeat the decay-as-release safety net)
  and `Presentation.Shake.EffectId` (a `CameraEffect`, whose duration is baked inside the referenced
  asset and has no per-use override anywhere on the engine's fire-and-forget path). `Id` is the native effect
  asset id; `DurationMs` null defers to the effect asset's own TTL. The engine tracks session-scoped
  effects so `stop()` removes them (engine scope); an unresolvable id is a validator INFO + apply
  no-op.
- **`Vec3` / `Rotation` / `TagMatch` - LIFTED to `ziggfreed-common`'s `codec/` package**: generic
  Hytale codec primitives, not station schema. This mod imports
  `com.ziggfreed.common.codec.{Vec3,Rotation,TagMatch}`. One line each: `Vec3` is the `{X, Y, Z}`
  nullable-double offset leaf (engine `Vector3d` leaf NAMES; deliberately NOT `Vector3dUtil.CODEC`,
  whose primitive axes + per-axis non-null validators reject the partial `"Offset": {"Y": -0.1}`
  authoring and erase null-means-inherit overlay granularity) at `Custody.Display.Offset`,
  `Puppet.Offset`, `Hold.Mount.Entity.Offset`, and `Presentation.ModelParticle.PositionOffset`;
  `Rotation` is the `{Yaw, Pitch, Roll}` nullable-DEGREES leaf (native rotation vocabulary; NOT
  `Rotation3f.CODEC`, which is radians-in-floats with a NaN sentinel) at `Custody.Display.Rotation`
  and `Presentation.ModelParticle.RotationOffset`; `TagMatch` is the `{"<tagFamily>":
  ["<value>",...]}` map codec + ANY-of matcher at `Tool.Tags` and `ActionInput.Tags` (the map is
  ONE leaf for overlay/inherit purposes - authoring it replaces the whole map). Each consumer
  still documents its own FRAME/units at its own accessor.
- **`Picker` is GONE.** It was a one-boolean group (`ShowLocked`) authorable at
  two altitudes, read usefully at neither: no engine path produces a LOCKED output category, so both
  of its values rendered identically, and the per-action override was never read
  at all. Re-adding a nested group later is purely additive, so nothing is lost by not shipping a
  schema row that does nothing. The picker page keeps its own `showLocked` parameter as the seam.

## Content types

- **[`StationAsset`](StationAsset.java)** - an interactive work station, loaded from
  `Server/RpgStations/Stations/*.json` (id = lowercased filename). Its own top-level groups are
  the FOUR things that genuinely belong to the STATION rather than to a job:
  - `Identity` - name/desc/icon keys, shown at engage and any station-listing UI.
  - `Block` - `{Exclusive?}` (default true): one worker per placed block is a property of the
    block, never of the job run at it.
  - `Requires` - the STATION-entry gate (permission + factor `Conditions`), evaluated once at
    engage and ANDed with the engaged action's own `Requires`. It is deliberately NOT a default:
    an action authoring none is gated by this one alone.
  - `Flairs` - a named cosmetic flair overrides map (per-flair-id, `{Moments}`), consulted by
    every action; see [`FlairAsset`](FlairAsset.java) for the standalone route. Decoded through
    `ziggfreed-common`'s `InheritMapCodec`, so native `Parent` merges it PER FLAIR ID: a child
    restyling one flair inherits every other flair the base authored, and each entry's own
    `Moments` merges per moment id in turn (the same codec, so inline and standalone flair content
    behave identically).
  - `Actions[]` - the station's actions, in AUTHORED ORDER, which IS selection priority: the
    first entry whose `Select` matches the held/placed context wins (see the `ActionDef` bullet's
    selection rule). Every station authors at least one; an empty/absent array leaves the station
    inert (the validator reports `STATION_NO_ACTIONS`).

  **No group here is a per-action default.** An action reads its own groups, or the `Ref`/native
  `Parent` base it explicitly names, and nothing else - station-level group INHERITANCE (a
  station-wide default `Work`/`Recipe`/`Tool`/`Hold`/`Camera`/`Animation`/`Loot`/`Puppet` every
  action fell back to) is DELETED. Two actions that used to share a station-level default now
  either both `Ref` the same standalone [`ActionAsset`](ActionAsset.java), or both name the same
  native `Parent` between `ActionAsset`s - sharing is by REFERENCE, never by implicit fallback.
  Several shared group TYPES (`Recipe`/`Yield`/`FromCrafting`/`Conversion`/`Hold`/`Tool`/`Camera`/
  `Animation`/`Flair`) still live as nested static classes on this file for historical reasons,
  but every one of them is reached exclusively THROUGH an `ActionDef` today - `StationAsset`
  itself never decodes them at its own top level.
- **[`ActionDef`](ActionDef.java)** - ONE self-contained action: a complete job, readable top to
  bottom with nothing implied and nothing inherited from elsewhere in the file. Used at TWO sites
  with one schema authority: as an `Actions[]` array entry of a `StationAsset`, AND as the body of
  a standalone [`ActionAsset`](ActionAsset.java) (which wraps this exact field set with an id +
  native `Parent`). The eight concerns, in authored order:
  - **What it is**: `Id` (unique within the station, matched case-insensitively; targeted by step
    insertions and `ExtensionAsset`s), `Ref` (below), `Label` (an advisory localization key for
    admin/UI display).
  - **When it applies**: `Select` (an [`ActionInput`](ActionInput.java) - which held or placed
    material picks this action out of the station's ordered list; absent matches any context),
    `Requires` (this action's own start gate, ANDed with the station's, never inheriting it),
    `Tool` (the held-tool gate, checked at engage and every heartbeat).
  - **What it makes**: `Recipe` - the ONE transform this action performs (below).
  - **How the loop runs**: `Work` (cycle cadence, duration/exit bounds, `PerCycleContributions[]`,
    the `Looping` flag, optional `Idle` practice mode, optional `Unattended` group - the SAME group
    `StationAsset.Work` used to be, now reached only through an action), `Custody` (placed-input
    custody, chunk-persisted; see [`Custody`](Custody.java) below). **`Work.Unattended`**
    (decision 90) opts the action into settling its conversions over placed custody with nobody
    engaged - group presence is the opt-in (`{}` suffices), `Enabled` exists so a native `Parent`
    child can author `false`, `MaxCycles` (reader default 24) is ONE ceiling capping a settle
    burst AND a gather's payout, and `CatchUpMaxMs` (reader default 86400000 = 24h, the native
    processing-bench ceiling) caps the elapsed game time one settle may consume. Every leaf
    `appendInherited`; the engine half lives in `station/CLAUDE.md`'s unattended section.
  - **Where it runs**: `Anchors` (named multi-station anchor declarations), `Steps` (an authored
    [`StationStep`](StationStep.java) program; absent = "build the implicit program from
    `Recipe`").
  - **What else it hands over**: `Bonus` (the shared `LootRef` - referenced `Lootables[]`
    plus inline `Rolls[]`; `Recipe.Yield` decides how much of the thing you made, `Bonus` decides
    what else you got), `ContributionScale` (below).
  - **How the person looks doing it**: `Worker` (below).
  - **What it sounds and looks like**: `Moments` (below).

  Every codec leaf carries `.documentation`; `ActionDef.of(...)` takes every group so no
  construction path silently drops one.

  - **`Ref` - the standalone-action attachment route (BOTH forms survive):** `{"Ref":
    "<actionAssetId>"}` names a standalone [`ActionAsset`](ActionAsset.java) (below) as the BASE;
    any OTHER group authored on the SAME inline entry overlays it group-wise (whole-group replace,
    one level: the `Ref`-base's group, or the inline entry's own when authored). A dangling `Ref`
    is validator finding `ACTION_REF_UNKNOWN` (engage resolves the action as if no `Ref` existed
    and denies gracefully at whichever gate then fails, rather than throwing). Native `Parent`
    BETWEEN `ActionAsset`s is the sibling
    "author only the delta" reuse route; `Ref` + overlay is the per-station ATTACHMENT route - two
    different reuse axes, not redundant.
  - **`Anchors` - named multi-station anchor declarations:**
    `{"<anchorId>": {"Station": "<stationId>", "MaxRadiusMeters": 12}}` (`MaxRadiusMeters` names
    its unit). Legal on both an inline `ActionDef` and a standalone `ActionAsset` (expected mostly
    on the latter). The reserved anchor id `"self"` (the primary station block) is implicit and
    never authored. DISCOVERY (nearest matching placed block within `MaxRadiusMeters`), CLAIMING,
    and a `StationStep.Walk`/`At` naming an anchor all EXECUTE - see `../station/CLAUDE.md`.
    `ANCHOR_STATION_UNKNOWN` warns an unknown `Station`.
  - **`Recipe` - ONE transform, singular by design:** `{Conversions?, FromCrafting?, Yield?,
    Doneness?}`. Its
    EFFECTIVE conversions (`StationCatalog.resolvedConversions`) are authored `Conversions` FIRST,
    then any `FromCrafting`-derived ones. **One recipe per action.** Two transforms means two
    actions, which is cheap because an action carries no boilerplate to repeat - there is
    deliberately no per-recipe `Tool` override any more: the ACTION's own `Tool` is the gate, so
    "which tool" and "which transform" are answered in the same place a reader is already looking.
    **Native-recipe composition**: `FromCrafting` gains `Categories[]`, `Benches[]` (native
    `BenchRequirement` id-refs), `Types[]` (`Crafting`|`Processing`; absent = both), and
    `NativeTime {Scale, OffsetMs}` (a linear `y = Scale*x + OffsetMs` transform over a derived
    recipe's `TimeSeconds`, defaults intentionally slower than vanilla); `Conversion` gains a
    nullable `DurationMs`. Per-cycle time precedence (engine-side): authored `Conversion.DurationMs`
    &gt; `FromCrafting.NativeTime` linear transform &gt; `Work.CycleMs`.
    **Set-recipe knobs (both nullable, orthogonal)**: `Conversion.Tier` (int, reader-default 0,
    LOWER scans first, stable authored order inside a tier; derived rows are stamped
    `Conversion.DERIVED_TIER` = 1 so unauthored hand-written rows outrank derivation) and
    `Conversion.IsExactSet` (bool, default false: the row matches only while the pile(s) its
    inputs draw from hold nothing beyond those inputs, per-entry `Socket` aware; custody-only).
    Exact-first/match-any-last is an authoring convention the validator INFOs nudge
    (`RECIPE_ROW_ORDER_MISLEADING`/`CONVERSION_TIER_SHADOWED`), never an engine reordering.
  - **`Doneness` - the ready window on produced output (decision 87), TWO altitudes, per-leaf
    precedence:** `{ReadyMs?: Long, Overdone?: Ingredient[]}` on `Recipe` (the default every row
    without its own leaf inherits, derived rows included) AND on `Conversion` (that row's own
    window; each authored leaf wins, mirroring D52's chain - fold via `Doneness.resolve`). A batch
    a step produces into a custody pile sits READY for `ReadyMs` of world GAME time (game time
    stands still while the server is down - an outage cooks and burns nothing), then collapses
    ONCE to the `Overdone` items (exact-`ItemId` entries only, the output route rule; the codec
    warns and the engine ignores anything else). `ReadyMs` alone = purely presentational (Ready
    look + `ready` moment, nothing degrades); `Overdone` without a reachable `ReadyMs` never opens
    a window (`DONENESS_OVERDONE_WITHOUT_READY`); a window on an action with no
    `Produce.To:"Custody"` step has no pile to sit on (`DONENESS_WITHOUT_PRODUCE_SOCKET`).
    Unauthored anywhere = deterministic exactly as before. Engine half: `../station/CLAUDE.md`'s
    doneness bullet (`StationDoneness` + the claim's window record + `StationService`'s one lazy
    settle core).
  - **`Recipe.Yield` - purely DETERMINISTIC, four leaves:** `Base` (flat quantity; absent = each
    conversion's own authored quantity), `Scale` (multiplier, floored to a whole item,
    reader-default 1.0), `Min`/`Max` clamps. A floor of 1 output is ALWAYS enforced underneath - a
    conversion that consumed its inputs and produced nothing is item loss, never a tuning outcome.
    Reading this group tells an author exactly how much a cycle makes, with nothing left to
    chance. `Yield` sits on the RECIPE (not inside `FromCrafting`, not up on the station) because
    what a station yields is a property of the recipe that produces it, not of how that recipe was
    discovered - it applies to authored `Conversions` and derived ones alike, and is resolved PER
    CYCLE (`station.StationYield`), the one point a chosen conversion becomes a live produce phase,
    because a yield keyed off the worker's held tool cannot be baked in at asset-fold time.
    **Everything conditional or probabilistic is a `Roll` in the action's `Bonus` group instead**:
    a Roll already carries the richer vocabulary (`Trigger`, `Conditions`, `Chance`, `Ladder`,
    `Grants`), and its `Grants.OutputItems` grants ADDITIVE items of the cycle's own primary
    output, fractionally (see the `Roll` bullet below). So "a better tool yields more" and
    "sometimes you get an extra" are both authored as visible rolls beside the deterministic number,
    rather than hidden inside it. A null `Yield` is the IDENTITY (the conversion's own authored quantity, untouched).
  - **`ContributionScale`** - a factor ladder (`{Factors[], Floors[]}`, the SAME
    weighted-sum-then-highest-floor rule `Roll.Ladder` follows; the pure core is
    `station.ContributionScaling` over the shared `FactorFormula.sum`) multiplying every `Work.PerCycleContributions`
    amount before it is forwarded. **The engine PRE-SCALES**: the resolved multiplier is applied
    to each amount before `StationCycleCompletedEvent` dispatches, and the multiplier itself rides
    that event (`contributionScale()`) for DISPLAY only - a listener that forgot to multiply
    therefore cannot under-award, and one that multiplied again cannot over-award. No floor
    reached (or no ladder authored at all) is the neutral `1.0`. This is where "a better tool
    earns more" is authored for a CONTRIBUTION (the engine holds no baked tool curve of its own);
    the identical shape for OUTPUT quantity is `Recipe.Yield`'s own `Base`/`Scale` pair, deliberately
    kept separate because output and contribution are different concerns even when both key off
    the same held-tool factor.
  - **`Worker`** - GROUPING, not a new concept: the four presentation groups answering "how does
    the person look doing this" - `Hold` (the movement-lock effect / the `Mount` knob family, see
    `../station/CLAUDE.md`'s Mount bullet), `Camera` (three independent knobs, no mode: `Enabled`
    - reader-default true on an authored group -, `Locked`, and `Recipe`; authoring `Recipe` at
    all IS the fixed-look opt-in, since a second boolean gating it could only ever make an
    authored preset silently inert), `Animation` (`EmoteId` + `ActionClip` + `Swing{IntervalMs}`, pure CADENCE - a swing's
  CUES are `Moments` entries),
    `Puppet` (below). They travel together because an author tuning one almost always tunes the
    next, and nesting them keeps an action at eight readable concerns instead of fourteen flat
    siblings. Each leaf is the SAME type it always was, and each is independently nullable.
  - **`Moments`** - an OPEN `Map<String, Presentation>` keyed by MOMENT ID, the same vocabulary and
    the same shape a [`FlairAsset`](FlairAsset.java) keys its own `Moments` by (one moment
    vocabulary, whether a cue is authored by the action or overlaid by a flair). Well-known ids:
    `cycle` (every finished cycle), `swing` and `impact` (each `Animation.Swing` tick - the swing,
    and the strike landing behind it, which is late purely because its own `Presentation.DelayMs`
    says so), `rare_find`, `completion`, plus a per-step `step:<actionId>:<stepId>`. Keys are
    canonicalized to lowercase and matched case-insensitively (`StationFlairs.canonicalMomentKeys`
    is the ONE canonicalizer, shared with both flair maps), so an authored `"Cycle"` resolves.
    Decoded through `ziggfreed-common`'s `InheritMapCodec`, so native `Parent` merges the map PER
    KEY and per leaf under it - a child re-skinning one moment inherits every other one plus the
    leaves it did not mention. **SPECIFICITY WINS, resolved in exactly one place**
    (`station.StationService#emitMoment`): an entry here is the BASE for its moment id wherever the
    engine holds nothing more specific, and where it does - a `StationStep`'s own `Presentation`
    for that step's moment, a `Ladder.Floor`'s cue for `rare_find` - the site-supplied presentation
    plays and the map entry is not consulted for that emission. An unrecognized key is the same
    warn-only typo finding a flair map gets (`UNKNOWN_MOMENT_ID`), never a block.
    **`rare_find` is NOT action-authorable** (`RARE_FIND_MOMENT_NEVER_PLAYS` warns): that moment is
    only ever emitted with the earning `Roll`/`Ladder.Floor` cue in hand, so a map entry could never
    win. A FLAIR keyed `rare_find` is meaningful (it overlays the earning cue), which is why the
    check lives on the action branch and not in the shared map walk.
    **Whether a `$Comment` (or any other `$`-key) may sit directly inside a map-shaped group
    depends on which map codec backs it.** A `BuilderCodec` always ignores the editorial `$` keys.
    A map codec has to opt in, because every key there is a map KEY whose value goes straight to the
    value codec: `ziggfreed-common`'s `InheritMapCodec` skips a `$`-key on both decode paths
    (consuming its value, whatever shape it is), and the engine's own `MapCodec` does not, so under
    `MapCodec` a `$Comment` hands a String to the value codec and the whole asset fails to decode at
    server start.
    - **`$` keys are LEGAL** (backed by `InheritMapCodec`) inside every MOMENT/FLAIR map: an
      action's `Moments` (`ActionDef` and `ActionAsset` alike), `StationAsset.Flairs`, a station's
      inline `Flairs[].Moments`, and `FlairAsset.Moments`. Inline and standalone flair content is
      ONE shape, so the two `Moments` maps behave identically. Prefer the engine's reserved
      spellings (`$Comment` above all): `InheritMapCodec`'s exported schema declares only those
      ten to the in-game Asset Editor, so an exotic `$`-key (a `$Notes`, a suffixed
      `$Comment_Foo`) still decodes server-side but is undeclared to the editor's property pane.
    - **`$` keys are ILLEGAL** (backed by the engine `MapCodec`) inside `Anchors` (`ActionDef`,
      `ActionAsset`, `ExtensionAsset`), `StationStep.Stamp.Stats.Caps.PerStat`, and every `Tags`
      leaf (`Tool.Tags`, `ActionInput.Tags`, and so `Custody.Input.Tags`), which share
      `TagMatch.CODEC`.
    In an illegal spot, put the note inside one of the map's VALUES (a `Presentation` is a
    `BuilderCodec`, so `Moments.Swing.$Comment` is fine) or on the enclosing object.
    `ShippedAssetDecodeTest` decodes every shipped file through its real codec to catch exactly this.
- **[`ActionAsset`](ActionAsset.java)** - a standalone, reusable,
  fourth-party-extendable action: `Server/RpgStations/Actions/<Name>.json` (Pattern A, id =
  lowercased filename). Its body is the EXACT SAME field set as the inline `ActionDef` (one
  schema authority - the fields live on an embedded `ActionDef` the wrapper's codec delegates to);
  it deliberately OMITS `Ref` (a standalone action is itself a base and never references another)
  and `Id` (its id IS its filename). **A FIELD-SET PARITY TEST holds the two codecs in lockstep**
  (`ActionAssetCodecTest`, walking both codecs' entries with `Ref`/`Id` excluded on the `ActionDef`
  side and `Name` (the ignored, editor-display-only key every Pattern A type carries) excluded on
  this side), because two hand-mirrored key lists with nothing between them drift - and the drift
  ships as a capability a standalone action silently cannot author. A station attaches it via an
  inline `Actions[]` entry's `Ref` leaf (above). Supports native `Parent` between `ActionAsset`s
  for delta reuse. See the fish exemplar (`unreleased/Server/RpgStations/Actions/PrepFish.json`,
  held back with the CookingFire/CuttingBoard pair at 0.1.0) for the flagship authoring shape.
- **[`Custody`](Custody.java)** - placed-input custody (chunk-persisted: the placed piles live on
  the block's own chunk section and survive restarts), opted into per-action:
  `{MaxQuantity?, SingleFamily?, Input?, States?, Display?, Share?, Sockets?}`. `MaxQuantity`
  defaults to **100**.
  `Input` (reusing [`ActionInput`](ActionInput.java)'s ItemId/ResourceTypeId/Tags/Function routes)
  is the explicit placement-acceptance matcher; absent derives acceptance from the resolved
  action's own `Recipe.Conversions` inputs instead (ANY of a multi-input conversion's materials is
  accepted - a multi-material station is loaded one material at a time). **`SingleFamily`**
  (Boolean, default false) locks a non-empty claim to the FIRST-placed item's resource family: a
  later placement outside that family is refused until the claim empties again ("50 oak OR 50
  pine, never 100 mixed"). Enforced in the ONE acceptance choke point, so both the held-item route
  and the inventory-scan fallback honour it; the pure core is `station.StationCustody#acceptsFamily`.
  `States` (`{Empty?, Loaded?, Working?, Ready?, Overdone?}`) names the block's own
  `State.Definitions` entries the engine flips between; null = no visual/hint flip. **`Working`**
  is independently nullable and means "actively being worked", NOT "has input in it": the engine
  holds the block in it only while a work step is genuinely executing there, reverting to the
  RESTING look (`station.StationDoneness#restingStateName`: `Empty`/`Loaded`, or `Ready`/`Overdone`
  under the doneness pair below) at the next NON-working step's entry, at a walk phase's departure,
  at the runtime idle transition, and at every session stop path. It applies to whichever block a
  step runs AT, so the primary station AND a claimed remote anchor both get it; which steps count
  is `StationStep.IsWork` (below). **`Ready`/`Overdone` (decision 88)** are the doneness pair: a
  produced batch waiting under an open `Doneness` ready window, and a pile collapsed by an expired
  one; both inert without an authored `Recipe`/`Conversion.Doneness`. THE STATE SET IS CLOSED BY
  CODE - overlay is not extension: an extension/pack may re-skin the NAME each leaf points at,
  never add a state (`States` has no collection, so it was never in D37-conflict, unlike the
  additively-extensible `Sockets` map below). Omitting any leaf is byte-identical to pre-knob
  behavior for that flip. `Display`
  (`{Offset: Vec3, Scale, Rotation: Rotation}`, FACING-RELATIVE to the placed block's own yaw, every
  leaf `appendInherited`; the block yaw folds into `Rotation.Yaw`) opts the placed input into a
  PLACED-AS-ENTITY visual - see `../station/CLAUDE.md`'s dedicated bullet for the full engine-side
  mechanism. The jar's own `Stations/Sawmill.json` authors `Display {Offset{Y:-0.1}, Scale:0.46}`
  as the shipped standalone default; a pack re-tunes it through an `ExtensionAsset`'s `Custody`
  per-leaf overlay (rule 5 below) rather than a full-file station override.
  **`Sockets` (the multi-placement model)** is an `InheritMapCodec` map keyed by socket id (author
  ids lower-case; matched case-insensitively; merged per id under `Parent`, per leaf within a
  socket; authored order = placement priority) of `Custody.Socket`: `{Item{Match?, PlacePerPress?}
  XOR Block{At?: Vec3i, Match?}, MaxQuantity?, SingleFamily?, Required?, Display?, Share?, Label?}`
  - two nullable sibling ROUTE groups, exactly-one-of enforced deny-nothing (a both/neither socket
  warns at decode and is IGNORED at runtime, never a load failure; a future route is a third
  sibling group, additive, zero reserved fields). Effective capacity is `min(socket.MaxQuantity,
  Custody.MaxQuantity)` with the per-block TOTAL capped by the custody-level cap; `SingleFamily`
  and every `Share` leaf fall back to the custody-level value; `PlacePerPress` absent = the classic
  whole-held-stack press. A `Block` socket is a real world block at the facing-composed `At`
  offset (the `Display` frame convention) - nothing is stored for it, and caps/shares/press knobs
  are inert on one. **`Share` `{Place?, Use?, Reclaim?}`** - three orthogonal booleans, custody
  level AND per socket, all reader-default false (owner-only): `Place` opens pile CREATION in an
  empty socket (first contributor owns until drained; a non-empty pile never co-mingles), `Use`
  opens engaging over a foreign pile, `Reclaim` opens press-F retrieval of one. **The DEGENERATE
  socket**: `Custody.effectiveSockets()` with no authored `Sockets` synthesizes exactly ONE socket,
  reserved id `main` (`MAIN_SOCKET_ID`), whose effective leaves ARE the custody-level values - the
  parity contract `asset.SawmillSocketParityTest` gates (both shipped station shapes decode and
  behave identically). `ResolvedSocket` is the flat per-socket view every runtime reader consumes;
  `Ingredient.Socket` + the `Consume`/`Produce` group-level `Socket` leaves address sockets from a
  recipe row / step phase (per-entry wins; absent = the first authored Item socket).
- **[`ActionInput`](ActionInput.java)** - the diegetic action-selection matcher: `{ItemId?,
  ResourceTypeId?, Tags?, Function?}` (`Function` is `"Weapon"|"Armor"|"Tool"`, resolved against
  the held item's live shape). `isCatchAll()` = no route authored. Live selection runs through
  `station.ActionResolver.selectActionByFamily` - the FIRST action in AUTHORED ORDER whose
  effective `Select` (its own, or its `Ref` base's) is absent, catch-all, or matches.
- **[`StationStep`](StationStep.java)** - ONE step of a multi-action station's step PROGRAM: an
  ORTHOGONAL PHASE record. A step composes any combination of nullable phase groups in ONE fixed,
  documented execution order; a step with NO phase group is a pure BEAT (`isPureBeat()`). Every
  field on this type EXECUTES - there are no decode-only decoys.
  - **Base fields** (every step): `Id` (unique within one action's `Steps`; required whenever
    another step or an `ExtensionAsset` insertion anchors on it), `Conditions` +
    `OnConditionFail{Result:Skip|Fail, Goto}` (the gate + branch/skip mechanism), `At` (an anchor
    id from the action's `Anchors` map; absent = the primary station `"self"`), `Repeat`
    (`{Times}` fixed XOR `{Min,Max,Factors}` ranged, resolved once at step entry via the pure
    `resolveCount(factorContribution)`; authoring both routes is an `afterDecode` warn, matching
    the four sibling exactly-one-of groups, since the fixed `Times` silently wins), `Duration`
    (`{Ms}`, a post-phase hold; prop/presentation persist across it), `Puppet` (per-step
    `{Clip?, Prop?}` override, reusing `Puppet.Prop`'s exact codec), `Presentation` (fires once at
    step ITERATION entry, so a `Repeat`ed step cues once PER BEAT), and **`IsWork`** (a nullable
    Boolean: does this step drive its `At`-anchor block's `Custody.States.Working` look?). `IsWork`'s
    reader-default is DERIVED, never a mode flag - `effectiveIsWork()` returns true for a step
    that both `Consume`s AND `Produce`s, i.e. the phase model's own atomic-transform CONVERT, so
    the implicit program and any authored convert light their block for free; every other shape
    (pure beat, lone Consume, lone Produce, walk, stamp) is the load/carry/unload scaffolding
    around the work and defaults false. Author `true` to promote a pure beat that IS the work
    (the fish exemplar's 2.5s `cook` hold), `false` to demote a convert that should not light.
    Zero effect unless the resolved `Custody.States.Working` is authored.
  - **Phase groups** (all nullable): `Walk` (`{To, SpeedMps}`), `Consume`
    (`{Items: Ingredient[], From:Inventory|Custody}` - BOTH routes executable), `Stamp` (the
    enhance-commit phase, below), `Produce` (`{Items: Ingredient[], To:Inventory|Custody}`), `Roll`
    (a `LootRef` - the SAME vocabulary an action's own `Bonus` group uses), `Commands`
    (`String[]`, run through the shared `CommandRewardExecutor`). `Consume`/`Produce` take the
    native `CraftingRecipe.Input`/`Output` ARRAY shape (see the `Ingredient` bullet); both are
    all-or-nothing, and a mid-list failure is covered by the pre-existing iteration refund ledger.
  - **Execution order within ONE step iteration** (fixed, honored by `station.StationStepRegistry`):
    Conditions gate -> `Walk` -> `Consume` -> `Stamp` -> `Produce` -> `Roll` ->
    `Commands` -> `Presentation`/`Puppet.Clip` (fire at iteration entry) -> `Duration` hold
    (suspend) -> next iteration or next step. A step combining `Consume` + `Produce` is an ATOMIC
    transform (no consumed-without-produced window); the anvil's strikes re-author as pure
    `Duration` beats (`{Id, Duration:{Ms}, Puppet:{Clip}, Presentation}`), and the stamp step's
    `Duration` + `Prop:None` closes the parked post-stamp empty-hands flourish.
  - **`Walk`, `At`, and `Produce.To:"Custody"` run the multi-station seam for real** - the live
    coverage is the anchor/walk check set (`ANCHOR_STATION_UNKNOWN`/`WALK_TARGET_UNKNOWN_ANCHOR`/
    `STEP_AT_UNKNOWN_ANCHOR`/`WALK_REQUIRES_PUPPET`).
  - **`Stamp`** (the anvil's enhance-commit step, compute-then-commit, handler-enforced):
    `{Reagents: Ingredient[], Durability{AddMax}, Stats: StampSpec, Economics{RepeatCostMultiplier}}`.
    `Reagents` are `Ingredient`s consumed FROM THE PLAYER'S INVENTORY (not custody).
    **`Stats` is the shared `loot.stamp.StampSpec`** (`{Pool?, Entries?, Picks{Min,Max}, Unique,
    Caps{Budgets[], PerStat}}`) - which entries are candidates, how many are picked, and the
    ceilings the result is held under; each `Budgets` entry is EXACTLY one of a flat `{Points}` or a
    factor-scaled `{PointsPer, Factors[]}`, and the EFFECTIVE budget is the MIN over every entry.
    The roll + clamp is the pure shared `StampCapEngine`, and the WRITE goes through whichever
    `Stamper` this server registered.
    **`Economics` sits on `Stamp` itself, not inside `Caps`**: it scales the REAGENT cost per prior
    stamp count (`ceil(base * (1 + mult * stampCount))`) and never touches the point budget, so it
    belongs beside the reagents it prices rather than among the ceilings it does not affect.
- **`StatRollEntry`** (shared) - one candidate stat-roll entry `{Stat, Points{Min, Max,
  Factors[]}, Weight, Always}`, shared verbatim by a roll pool's `Entries` and a Stamp step's inline
  `Stats.Entries`. Rolled points are `uniform(Min,Max) + sum(resolve(f) * f.Weight)`, clamped by the
  Stamp caps - the same weighted-term vocabulary that drives loot chances drives roll magnitudes.
  **An authored `Weight: 0` means NEVER DRAWN**, not "the default 1": a zero-weight entry owns no
  band in the lottery and is stepped over. Omit `Weight` for the neutral 1.0.
- **`RollPoolAsset`** (shared) - `Server/ZiggfreedCommon/RollPools/<Name>.json` (id = lowercased
  filename), body `{Entries: [StatRollEntry, ...]}`, referenced by a Stamp step's `Stats.Pool`.
  A `Target:{RollPool}` `ExtensionAsset` appends to it, and that append is applied where the Stamp
  step READS the pool (`StationStepHandlers.StampHandler.withExtendedEntries`), so an added stat is
  a genuine candidate everywhere the pool is used.
- **[`Presentation`](Presentation.java)** - this mod's OWN codec, deliberately direct: a moment
  plays its cues here with no feedback-service indirection layer, and every leaf on it is
  genuinely PLAYED. Leaves: **`Sounds`** (a `SoundCue[]` played in authored order, so a thud plus a
  chime is two entries rather than two whole `Roll`s or a synthetic `SoundEvent` asset. Each entry
  is DUAL-SHAPE - either a bare id string, or `{EventId, DelayMs?}` - decoded by the one
  [`StringOrObjectCodec`](StringOrObjectCodec.java) and normalized to one `SoundCue` record either
  way; a shorthand entry re-ENCODES as a bare string, which is what keeps an encode-based test and a
  hand-authored file on the same bytes. `DelayMs` there holds THAT sound and ADDS to the group's own
  (the moment delay offsets the moment, the entry delay offsets that sound inside it); the engine
  splits an offset entry off into its own sound-only queued cue, so both land on the ONE scheduler.
  Still deliberately NO `Volume`/`Pitch`: the 3D-sound primitive takes neither argument, so those
  leaves would decode and then do nothing - vary them by referencing a different `SoundEvent`
  asset), **`Particles`** (an
  ARRAY of `ModelParticle`-shaped bursts matching native `InteractionEffects.Particles` - each
  entry is `{SystemId, Scale?, DurationSeconds?, RotationOffset{Yaw,Pitch,Roll}?,
  PositionOffset{X,Y,Z}?}`, every knob nullable with reader-defaults that reproduce the old
  single-string playback byte for byte: scale 1.0, a 4-second client-playback cap, zero rotation,
  no offset. `PositionOffset` is FACING-RELATIVE through the same `station.StationBlockFacing`
  reader `Custody.Display`/`Puppet` use; `RotationOffset` is the burst's own emission rotation in
  DEGREES and is NOT composed with the block facing. The `DurationSeconds` cap is a LEAK GUARD,
  not decoration - an unbounded-spawner system fired uncapped never stops. There is deliberately
  **no `Color` leaf**: the only colour-capable engine overload takes no playback cap, and that
  leak must never be reintroduced), `Shake` (nested `{EffectId, Intensity}`), plus TWO
  native-composition groups: `Interaction` (`{Id}`, an inner class - fires a native RootInteraction
  chain by id) and `Effect` (an [`EffectRef`](EffectRef.java) - applies a native EntityEffect by
  id). Both id-ref-only. Plus **`DelayMs`** (nullable `Long`), the one leaf that is not itself a
  cue: it offsets the WHOLE group in time so every cue in it stays together and lands late as one
  moment. Because it lives on the shared type, EVERY `Presentation` site can re-time itself with no
  extra schema - any of an action's own `Moments` entries, a step's own `Presentation`, a
  `Roll`'s or a `Ladder.Floor`'s, a `FlairAsset` moment. Null/zero/negative all read as "play at
  once" (`effectiveDelayMs()`), so a nonsense value degrades to the undelayed cue rather than to a
  cue that never fires. A flair overlays it like any other leaf, so a flair OMITTING it inherits the
  base moment's timing and needs an explicit `0` to cancel one. Engine side:
  `station.StationService#emitMoment` applies it AFTER the flair fold (so the winning
  `Presentation`'s timing is the one honored, and a flair can re-time as well as re-skin) onto the
  ONE due-time core every offset in this engine rides - see `../station/CLAUDE.md`'s `emitMoment`
  section for the queue, its drain, the stop policy, and the three-way "which delay to reach for"
  rule (a separate `Moments` entry when the late cue is its own flair-targetable beat, this leaf
  when a whole moment reads early, a `Sounds` entry's own `DelayMs` when one sound inside a moment
  needs staggering behind the rest).
- **[`Puppet`](Puppet.java)** - "mount the player, hide their player model, and spawn/display a
  visual of their character model performing the steps" - one of the four `ActionDef.Worker`
  groups (ORTHOGONAL to whichever `Hold.Mount` holds the real player, never nested under `Hold`),
  whole-GROUP overridable per action:
  `{Enabled?, Hide{Route,Effect?}, Look{Source,FallbackModelId?,Model?,Role?}, Offset: Vec3,
  Rotation: {Yaw,Pitch,Roll}, Prop{Source,ItemId?,Slot?}}`. `Hide.Route` is a THREE-arm union: `"Scale"` is the
  in-game-crowned default (hides the puppeteer's own body via `ziggfreed-common`'s
  `entity.PlayerPuppetService`); `"Effect"` is schema-reserved future work; `"None"` is the
  deliberate degraded fallback. `Look.Source` defaults `"PlayerClone"`, with `"Model"` an open
  performer seam. `Offset`/`Rotation` place the puppet relative to the station's block-top anchor: authored `+Z` is
  the block's FRONT, `+X` its right, `Offset.Y` stays vertical, and the block yaw folds additively
  into `Rotation.Yaw` (so `Yaw: 0` means "faces the same way the block does"). **`Rotation.Pitch`/
  `.Roll` are the puppet's OWN tilt and are NOT block-composed**, the same rule
  `Presentation.ModelParticle.RotationOffset` documents; the engine applies them through the
  performer's own re-anchor call one frame after spawn (which is also what covers the `NpcRole`
  backend's deferred spawn), so a puppet authoring no tilt keeps the untouched spawn placement. This shares ONE
  reader with `Custody.Display`, `station.StationBlockFacing` (`yawRadians` reads
  `World#getBlockRotationIndex`, try-guarded to yaw 0; `rotateOffset` is the one
  horizontal-rotation core), with the per-consumer composition in
  `StationPuppetController#resolveWorldOffset`/`#resolveYawRadians` and
  `StationCustodyDisplay#resolveWorldOffset`/`#resolveRotationRadians`. **IDENTITY at yaw 0**, so
  a default-facing placement is byte-identical to a world-space authoring. `Prop.Source` defaults
  `"MirrorHeld"`; `"ItemId"` forces a specific prop, `"None"` empties the puppet's hands. A
  `StationStep` carries its own small `{Clip, Prop}` override (`StationStep.PuppetOverride`)
  reusing this exact `Prop` codec for moment-to-moment swaps. `Look.Source` is a THREE-arm union
  (`PlayerClone`|`Model`|`NpcRole`); the fixed-model arm's config is the cohesive `Look.Model
  {ModelId}` group (read for `Source:"Model"`), **`FallbackModelId` sits on `Look` ITSELF** (it is
  the any-source resolution fallback, read for every arm), and the NpcRole performer arm's config
  is the parallel `Look.Role {RoleId, SkinSource (PlayerClone|RoleDefault), Persist, SpeedMps}`
  group (read for `Source:"NpcRole"`; `Model`/`Role` are inner classes of `Puppet`, siblings of
  `Hide`/`Prop`). See `../station/CLAUDE.md`'s puppet-engine bullet
  (`StationPuppetController`). The jar's own `Stations/Sawmill.json` authors the shipped
  standalone default (`Enabled true`, `Hide.Route "Scale"`, `Look.Source "PlayerClone"`,
  `Offset {0.0, -0.4, 1.15}`, `Rotation {Yaw: 0.0}`, `Prop {MirrorHeld, Hotbar}`, the in-game-tuned
  values); the pack-shipped Anvil authors its own. A pack re-skins any of them through an
  `ExtensionAsset`'s `Puppet` per-leaf overlay (rule 5 below), never a full-file station override.
- **`Roll`** (shared) - `{Trigger, Conditions[], Chance, Ladder{Factors[], Floors[]}, Grants, Cue}`.
  At a station `Trigger` is `Cycle` (every completed cycle) or `Completion` (once, at session stop);
  the rest is the shared model, including the SMART-CUE rule (a cue beside grants rides only once
  those grants genuinely produced something) and the stacking of top-level and reached-floor grants.
  Two things a station author needs on top of that:
  - **A `Cue` is a MOMENT ID**, not a presentation body: the loot layer names a moment and the
    station decides what it sounds like. It resolves through the SAME `emitMoment` funnel every
    other station moment does, so an action's own `Moments` entry for that id plays it and every
    applicable flair overlays it. Well-known ids (`rare_find`, `cycle`, `swing`, `impact`,
    `completion`), a per-step `step:<actionId>:<stepId>`, and the OPEN author-defined
    `cue:<yourName>` namespace all pass the typo check - mint a `cue:` id whenever a jackpot should
    sound different from an ordinary find, and author the matching key in the action's `Moments`.
    The jar's Sawmill publishes a four-cue palette (`rare_find`, `cue:find_deep`, `cue:find_apex`,
    `cue:trophy`) that a table can name with no presentation of its own.
  - **Three station payouts are registered reward KINDS** inside `Grants.Rewards`, so they compose
    with `Items`/`DropLists`/`Commands` and with anything another mod registered:
    - `rpgstations:output_items` (`{"Count": "1.5"}`) - ADDITIVE units of the cycle's own primary
      output, on top of the deterministic `Recipe.Yield` quantity. Additive is load-bearing: the two
      numbers stay directly comparable and no file can silently multiply another's. **FRACTIONAL** -
      the whole part every time plus the leftover fraction as the chance of ONE more, so `1.5` pays
      one always plus a second half the time and averages exactly 1.5. That is what makes a
      half-step ladder rung authorable ON the floor that earns it. Everything a cycle grants is
      SUMMED first and resolved to whole items exactly once (`loot.OutputItemResolver`), so two
      rolls paying `0.5` each average one whole item rather than rounding twice. `Cycle` trigger
      only, and only where the action HAS a single cycle output
      (`LOOT_OUTPUT_ITEMS_WRONG_TRIGGER`/`LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT` warn).
    - `rpgstations:contribution` (`{"Channel", "Param"?, "Amount"}`) - a ONE-SHOT post of the same
      `{Channel, Param, Amount}` shape `Work.PerCycleContributions` uses. The site fixes the
      meaning: a find's grant is not "per cycle", and it BYPASSES both scalings a per-cycle entry
      goes through (the `ContributionScale` ladder and the idle fraction), riding its own
      `StationCycleCompletedEvent.oneShotContributions()` list so a rare find is worth the same
      whatever tool the player holds. `Cycle` trigger only (the validator warns).
    - `rpgstations:effect` (`{"Id", "DurationMs"?}`) - a native `EntityEffect` applied to the
      worker. Teardown differs by trigger, deliberately: a `Cycle`-trigger effect is session-tracked
      and stripped when the session stops, while a `Completion`-trigger one applies from INSIDE that
      same stop (after teardown already ran) and persists for its own duration as a finishing
      reward.
- **`LootableAsset`** (shared) - `Server/ZiggfreedCommon/Lootables/<Name>.json` (id = lowercased
  filename), body `{Rolls: [Roll, ...]}`, referenced by a `LootRef.Lootables` entry (an action,
  step, or extension may combine any number of shared tables with its own inline `Rolls`). Three
  standalone lootables ship in this jar's resources, all alive with RpgStations alone (BUILT-IN
  factors only - `rpgstations:cycle_count` for the session-loyalty ladder, plus the
  `hytale:tool_quality`/`tool_item_level`/`tool_power` native reads the trophy gates need; nothing
  another mod has to register): `SawmillFinds` (the loyalty ladder), `SawmillTrophy` (the hatchet
  chase) and `SawmillMasterworkFinds` (the T4 tier that chase pays out). Their one roll each covers
  the whole Roll vocabulary - a conditioned chance + ladder, a roll-level cue on a plain chance
  roll, and a Conditions-only tier - see `../station/CLAUDE.md`'s Sawmill content bullet for the
  numbers. **A fold REPLACES by id at whole-FILE granularity, so which rolls share a file is an
  extension-point decision, not a filing one:** those three are one roll each precisely because a
  layering mod re-tuning any of them should never have to inherit the other two. Split by default
  when authoring a station - merging ids later is free, unpicking a shipped one is not.
- **[`StructurePatternAsset`](StructurePatternAsset.java)** - a multiblock structure pattern,
  `Server/RpgStations/Patterns/<Name>.json` (Pattern A, id = lowercased filename, native `Parent`
  per leaf): `{Identity{NameKey,DescKey}, Rotate{Yaw90 (default true), Mirror (default false)},
  Activate{Block, RevertBlock (defaults to the anchor cell's own id)}, Cells[], Requires,
  Moments{activated, broken}}`. Each `Cells[]` entry is `{Offset: Vec3i, Block: ActionInput XOR
  Empty: true (exactly one, warned), IsAnchor}` - EXACTLY ONE anchor cell (unauthored = the cell
  at offset (0,0,0), decode-warned; the anchor must author an exact `Block.ItemId` since detection
  seeds from exact ids, warned otherwise). `Activate.Block` equal to the anchor cell's own id is
  the custom-core-block style (no swap) - authoring, never a mode. **`Cells` is REPLACED WHOLESALE
  under `Parent`** (the standard array rule, documented at the leaf), and the type is deliberately
  NON-extensible (no `ExtensionAsset` target: a cell appended by another pack would deactivate
  every standing build). `Moments` is the same `InheritMapCodec<Presentation>` map every flair/
  moment surface uses ($-keys legal); its cues play AT ONCE at the anchor (no session exists to
  queue a `DelayMs`, documented at the leaf). Compiled by `station.PatternCatalog` into DETECT +
  HOLD walk forms over ziggfreed-common's `world.pattern` engine; runtime in
  `station.StationStructures` - see `../station/CLAUDE.md`'s multiblock section. **The worked
  example is the cooking pit, HELD under `unreleased/` for a later release** (the mirror's
  `Patterns/CookingPit.json` + `Stations/CookingPit.json`: a
  Rock-family ring around a `Deco_Campfire_Off` anchor, the vessel headroom cell authored `Empty`
  AND excluded from HOLD by the Stew action's Block-socket `At`, the Grill/Stew layering over
  `rpgstations:socket_filled`, exact-set rows above the match-all stew row);
  `station.HeldCookingPitPatternTest` decodes both held files through these codecs and walks the
  compiled forms, so a content edit that breaks their semantics fails the build and a restore
  ships pre-verified.
- **[`FlairAsset`](FlairAsset.java)** - a standalone, ANY-mod-authorable cosmetic flair layer,
  `Server/RpgStations/Flairs/<Name>.json` (Pattern A, id = lowercased filename): `{Stations?[],
  Moments}`. `Stations` null/empty = applies to every station; `Moments` is an OPEN
  `Map<String, Presentation>` keyed by an arbitrary moment id (well-known ids `cycle`/`swing`/
  `impact`/`rare_find`/`completion` plus a per-step `step:<actionId>:<stepId>` id) - nothing
  hardcodes the vocabulary in Java. Decoded through `ziggfreed-common`'s `InheritMapCodec`, so
  native `Parent` merges it PER MOMENT ID (and per leaf under it), exactly like an action's own
  `Moments`: a child re-skinning one moment inherits the rest. Folded into `station.FlairCatalog`
  (`effectiveFlairsFor`, UNIONS every applicable `FlairAsset` onto a station's own inline `Flairs`
  map). Deliberately NOT extension-composable via `ExtensionAsset` (the non-extensible list below)
  - extend `FlairAsset.Moments` by shipping another `FlairAsset`, the union already composes.
- **[`ExtensionAsset`](ExtensionAsset.java)** - the ONE additive-
  extension mechanism for every collection-bearing group that is NOT cosmetic (cosmetics stay
  `FlairAsset`): `Server/RpgStations/Extensions/<Name>.json` (Pattern A, id = lowercased
  filename). A fourth party extends a third party's content WITHOUT owning or replacing its
  files.
  - **`Target`** names ONE of `{Station|Action|Lootable|RollPool}` (orthogonal leaves, never a
    `Type` enum + Id pair - `hasLegalTarget()`/`resolvedType()`/`resolvedId()`; type constants
    `Target.STATION`/`ACTION`/`LOOTABLE`/`ROLLPOOL`), with the ONE legal pairing
    `{Station, Action}`: the SCOPED action target, resolving as an ACTION target carrying the full
    Action payload set but matching only where that station resolves that action id
    (`scopedStation()`/`matchesStationScope(stationId)`). It exists because an action id is not
    globally unique - a bare `Action` target reaching every station that has the id is the point
    when extending a shared `Ref`'d `ActionAsset`, and a collision when two packs name an action
    alike or only one station's copy should be tuned. No other pairing is legal: a lootable/roll-pool
    id is already globally unique, and a bare `Station` is a target rather than a qualifier.
  - **Payload groups** (all nullable; a group the target type cannot carry is validator
    `EXTENSION_PAYLOAD_MISMATCH`, checked by the pure `payloadAllowedFor(targetType, payloadKey)`):

    | Target    | Extensible payload keys                                                              |
    |-----------|----------------------------------------------------------------------------------------|
    | Station   | `Actions[]` (append whole new actions)                                               |
    | Action    | `Steps[]`, `Anchors{}` (new keys only), `Bonus` (LootRef append), `Conversions[]`, `PerCycleContributions[]`, `ContributionScale` (per-leaf overlay), `Puppet` (per-leaf overlay), `Custody` (per-leaf overlay) |
    | Action, scoped (`{Station, Action}`) | the same Action keys, applied only where THAT station resolves that action id |
    | Lootable  | `Rolls[]` (append)                                                                    |
    | RollPool  | `Entries[]` (append)                                                                  |

    **Why a Station target carries only `Actions`.** A station holds no OTHER group an extension
    could add to any more: everything a job is made of lives inside a self-contained action. So
    adding a WHOLE new action is the station-scoped statement, and every finer-grained addition
    names the ACTION it belongs to.
  - **Merge + conflict semantics** (deterministic, ALL pure/unit-testable on this class, applied
    engine-side by `ExtensionCatalog`'s read-side `applyTo*` entry points - one per payload key,
    each over a pure merge core; see `../station/CLAUDE.md` for WHERE each one is applied):
    1. ADDITIVE ONLY for every COLLECTION payload - never mutate/replace/remove an existing entry
       (replacing a whole file stays load-order's job). The three PRESENTATION/SCALE-overlay
       payloads (`Puppet`/`Custody`/`ContributionScale`, rule 5) are the deliberate exception:
       they carry no collection at all, so "additive" there means per-leaf, never a whole-group
       clobber.
    2. Keyed collections (`Actions`, `Anchors`): the BASE always wins a key collision
       (`EXTENSION_KEY_COLLISION`, entry skipped); among extensions, `APPLY_ORDER` decides.
    3. Unkeyed arrays (`PerCycleContributions`, `Conversions`, `Rolls`, `Entries`): pure append,
       ordered by `APPLY_ORDER`.
    4. Ordered insertion (`Steps` -> `StepInsertion{Anchor, Insert:StationStep[]}`):
       `Anchor` is exactly one of `{After:"<stepId>"|Before:"<stepId>"|AtStart|AtEnd}`
       (`effectivePlacement()` degrades to `AtEnd` + `EXTENSION_ANCHOR_MISSING` warn on a
       missing/dangling step id); inserted steps need `Id`s so LATER extensions can anchor on
       them. An insertion carries NO action guard of its own - the compound `{Station, Action}`
       target is the one station-specific aiming mechanism, so an extension whose insertions
       belong to two different actions is two extensions. Co-anchored insertions from different
       extensions apply in `APPLY_ORDER` (the ledger that guarantees that is keyed on the
       LOWERCASED anchor id, matching the case-insensitive anchor match). Insertions
       ADD beats to a program the target action ALREADY authors - an action with no `Steps` runs
       the recipe-driven convert loop and has no program to insert into, and no insertion can flip
       it into a step-programmed one (that flavor decision stays base-owned).
    5. NESTED PER-LEAF OVERLAY (`Puppet`, `Custody`, `ContributionScale`) - the three NON-collection
       payloads, so they merge leaf-wise instead of appending: recursively, at EVERY nesting
       depth, an AUTHORED extension leaf wins and a NULL one leaves the base's value intact (the
       `appendInherited`/nullable-nested-leaves convention applied ACROSS assets instead of down a
       `Parent` chain). A `Custody` overlay carrying only `Display` never touches the base's
       `States`/`MaxQuantity`/`Input`; a `Display` carrying only `Scale` never clears its `Offset`;
       a `Puppet` carrying only `Offset.Z` keeps `Offset.X`/`.Y` plus `Hide`/`Look`/`Prop` and every
       `Rotation` axis, and one carrying only `Rotation.Pitch` keeps the base's `Rotation.Yaw`/`.Roll`;
       a `ContributionScale` overlay authoring only `Floors` keeps the base action's own `Factors`.
       (Leaf-granularity note: a MAP-valued leaf - `Custody.Input.Tags` - replaces wholesale as
       ONE leaf, never per tag family. `Custody.Sockets` is the one KEYED collection inside an
       overlay and merges per socket id - an id the base has deep-merges per leaf, a NEW id
       appends, and the base's authored order is never disturbed; a socket's `Item`/`Block` route
       pair is the one leaf-walk exception, where an overlay authoring a route commits the socket
       to it and drops the base's other route. Standing docs wording: OVERLAY IS NOT EXTENSION -
       `States` has no collection and can only be re-skinned, `Sockets` is a keyed collection and
       IS additively extensible.)
       Overlays apply in `APPLY_ORDER`, so the LATER (higher-priority) extension wins a same-leaf
       contest. Engine-side cores + the load-bearing test: `station.ExtensionCatalog`'s
       `overlayPuppet`/`overlayCustody`/`overlaySocket`, `station.ExtensionOverlayTest`.
  - **`APPLY_ORDER`** - the ONE apply-order tuple (`Priority` ascending so a HIGHER priority
    applies LATER and wins a tie, then extension id lexicographic) - a total order over distinct
    assets, so a stable sort fully determines the result on every server. `sortedForApply(...)`
    is the pure, unit-tested, catalog-AND-validator-shared core.
  - **Composition order:** extensions apply to the Parent-resolved target at READ time;
    extension additions do NOT flow down `Parent` chains. A bare `Target:{Action}` extension flows
    to every `Ref` user of that action (the scoped `Target:{Station, Action}` form stops at the one
    station it names); a `Target:{Station}` step-insert applies post-`Ref` to that
    station only. **The ONE Action-target identity** (`ActionResolver.actionTargetId`, shared by
    the Bonus/PerCycleContributions appends AND the Puppet/Custody/ContributionScale overlays):
    the `Ref`'d `ActionAsset` id when the inline entry Refs one, else the inline entry's own map
    key; the IMPLICIT action of a no-`Actions` station is deliberately unreachable by an Action
    target (no accidental global `Action:"work"` broadcast) - address it via `Station`. Overlay
    order is Action first, Station on top (the station-scoped statement wins a same-leaf
    contest).
  - **Deliberately NON-extensible** (docs state each): `Requires` (an extension must never
    tighten/loosen another author's gate), `Settings` (owner-only singleton), scalar groups
    (`Work`/`Hold`/`Camera`/`Animation` - override is load-order's job, not extension), the
    INTERNALS of an existing `Roll` (extenders add their OWN Rolls beside it),
    `FlairAsset.Moments`, and the whole `StructurePatternAsset` (a cell list is the pattern's
    identity - an appended cell would deactivate every standing build).
  - **The presentation-overlay exception (`Puppet`, `Custody` incl. `Custody.States`,
    `ContributionScale`)**: these three carry per-leaf overlay precisely because "override is
    load-order's job" would otherwise force a pack that only wants to RE-SKIN a base station's
    presentation (or re-tune its per-cycle scaling) to ship a full-file station override, and
    deleting such an override silently drops every group it was the sole author of. Rule 5's
    per-leaf overlay is the non-destructive replacement: a re-skinning pack authors only the
    leaves it re-tunes and inherits every other one from the base.
  - See `../station/CLAUDE.md`'s ExtensionCatalog bullet for the engine-side fold + the
    cross-pack-aware validator's `EXTENSION_APPLIED` boot summary.
- **[`RpgStationsSettingsAsset`](RpgStationsSettingsAsset.java)** - `Server/RpgStations/Settings/
  Settings.json`, a single id (`settings`), jar default + pack-overridable: `{Enabled,
  SummaryHud:{Enabled, Position, OffsetX, OffsetY, TtlMs}, Limits:{MaxSessionsPerWorld,
  MaxPuppetsPerWorld, MaxStashesPerSection, UnattendedIntervalMs,
  MaxUnattendedGatherCycles}}`. `Position` is a shared-library
  `HudPosition` preset id, authored PascalCase like every other id in this schema (e.g.
  `"TopCenter"`); the legacy SCREAMING_SNAKE spelling (`"TOP_CENTER"`) still resolves since
  matching is case- and underscore-insensitive. `OffsetX` is `OffsetY`'s horizontal sibling.
  **`Limits`** - three INDEPENDENT ceilings on what the engine may have live at once, each
  nullable and each meaning "unlimited" when absent, so an owner caps one thing without implying
  anything about the others. Sessions and puppets are scoped per WORLD (the cost each caps -
  session ticking, replicated performer entities - is paid by one world's players and tick loop,
  and a busy hub must never be able to starve a quiet one); placed-input stashes are scoped per
  CHUNK SECTION (`MaxStashesPerSection` - the bound a per-section store can enforce). What
  exceeding a cap DOES is a property of the thing being capped, never a mode: `MaxSessionsPerWorld`
  and `MaxStashesPerSection` are all-or-nothing (a new session, or a NEW stash - topping up an
  existing one never counts - is denied with a localized toast); `MaxPuppetsPerWorld` gates
  pure presentation, so a session past it simply performs in the player's own body, the same
  graceful degrade a failed puppet spawn already takes. `UnattendedIntervalMs` is the one NON-ceiling leaf in the group: the
  per-world pace of the unattended pass (reader default 1000ms; a non-positive value reads as the
  default), a pure pace knob rather than a cap. `MaxUnattendedGatherCycles` is the one PAYOUT
  ceiling: how many accrued unattended cycles ONE gather pays, applied as the min of caps against
  each action's own `Work.Unattended.MaxCycles` (`Limits.clampGatherCycles` - it can only tighten
  what an action authors, never raise it; null or non-positive means each action's own knob alone
  applies). The retired `MaxCustodyClaimsPerWorld`
  leaf still decodes into a warn-only slot (the settings fold names the replacement; never a parse
  failure) and enforces nothing. The one shared predicate,
  `Limits.atCapacity(max, currentCount)`, treats a non-positive `max` as unlimited too (a ceiling of
  zero would otherwise read as "this feature is off", which is what the engine's own `Enabled`
  switch already means). Deliberately NON-extensible (server-global singleton). Folded into
  `station.SettingsCatalog`.
- **[`Requires`](Requires.java)** - `{Permission?, Conditions?[]}`, evaluated at station/action
  start; any failing condition denies with `ui.station.locked`.

## The authoring layer on every codec

Three cross-cutting layers ride the SAME `FieldBuilder` chain as the field declaration itself, so
they land per-leaf and are never a parallel table that can drift from the codec:

- **`.documentation("...")` on EVERY leaf.** Every `KeyedCodec` leaf reachable from the seven
  Pattern A `CODEC` statics carries a description of what the leaf does plus its default/unit;
  `AssetDocumentationCoverageTest` walks `BuilderCodec#getEntries()` (unwrapping array/map codecs
  down to nested `BuilderCodec`s, identity-deduped so a shared leaf like `Vec3`/`Conditions`/
  `Presentation` is checked once) and FAILS THE BUILD on a blank one. It also scans every
  `.documentation(...)` string for internal process narration (decision numbers, wave labels,
  session dates), since those strings ship in the jar schema AND the generated schema reference
  and are therefore public. It is the input to both the schema reference
  ([`../docs/SchemaDocWriter`](../docs/SchemaDocWriter.java), regenerated by
  `gradlew generateSchemaDocs` into the repo-root `SCHEMA.md`, drift-guarded by
  `SchemaDocDriftTest`) and the in-game Asset Editor's field help. **Add the `.documentation` in
  the same edit as a new leaf** - the coverage test is not a reminder you can defer past a build.
- **`.metadata(...)` for the in-game Asset Editor.** A bare `UIEditorSectionStart("<label>")` opens
  a section at each top-level group (`Identity`/`Block`/`Requires`/`Flairs`/`Actions` on
  `StationAsset`, `Identity`/`Structure`/`Requirements`/`Moments` on `StructurePatternAsset`,
  `Sharing`/`Sockets` on `Custody`, `Doneness` on `Recipe` + `Conversion`, `Unattended` on `Work`,
  plus `Engine`/`Summary HUD`/`Limits` on `RpgStationsSettingsAsset` -
  `StationAsset` lost its old per-group sections when those groups moved onto `ActionDef`, which
  carries none of its own); the rest nest inside a `UIEditor` - `UIEditor.Dropdown("<datasetId>")`
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
  `Stamp.Stats.Caps.Budget`'s route, `StationStep.Repeat`'s fixed-vs-ranged route, a
  `StepInsertion.Anchor` authoring more than one placement) report at the asset's own decode
  path/line, so a pack author sees them in the boot log at fold time rather than only from the
  deferred full validate pass. **They come from [`CodecWarnValidators`](CodecWarnValidators.java),
  never the engine's built-in `Validators` factory**: every built-in there routes through
  `ValidationResults.fail()`, and `ContainedAssetCodec` gates registration on `!hasFailed()`, so
  attaching one would silently DROP the whole asset on a bad value - the exact opposite of this
  mod's never-block posture (an asset ALWAYS loads; a finding is advisory). The one legitimate use
  of a failing engine validator is a leaf without which the asset means nothing at all.

- **[`AssetEditorDataSets`](AssetEditorDataSets.java)** - one keyed
  `AssetEditorRequestDataSetEvent` handler per `Dropdown` dataset id (the first-party
  `ItemCategories` shape), registered once from `RpgStationsPlugin#setup()` inside a whole-body
  try-guard: the Asset Editor is a builtin module, and an authoring convenience must never be able
  to fail plugin startup (a failure degrades every dropdown to a free-text field). Two flavors:
  LIVE sets read straight off this mod's own runtime catalogs/registries at request time
  (`rpgstations:stations`/`actions`/`lootables`/`rollpools` off `station.StationCatalog`/
  `ActionCatalog`/the shared `LootableConfig`/`RollPoolConfig`, `rpgstations:factors` off
  `api.impl.FactorRegistryImpl#registeredIds`, `rpgstations:channels` off
  `api.impl.ContributionChannelRegistryImpl#registeredIds`,
  `rpgstations:station-blocks` off `station.StationService#stationBlockItemIds` (the asset-derived
  discovery index, backing `StructurePatternAsset.Activate.Block`; `RevertBlock` stays free text
  since any block id is a legitimate revert, and there is deliberately no `rpgstations:patterns`
  set because no codec leaf names a pattern id) - so an asset reload or a late
  third-party factor/channel registration simply widens the next answer, and an empty answer is
  legitimate, never an error: nothing has declared a channel yet);
  FIXED sets are the closed union-discriminator vocabularies, each sourced from the SAME constant
  the decoder compares against (`Puppet.HIDE_ROUTE_*`/`LOOK_SOURCE_*`/`SKIN_SOURCE_*`/`PROP_SOURCE_*`/
  `PROP_SLOT_*`, `StationStep.Consume.FROM_*`/`Produce.TO_*`/`OnConditionFail.RESULT_*`,
  `loot.StationLootEngine.TRIGGER_*`, `station.StationCameraPreset#id`) so a renamed arm can never
  leave a stale dropdown behind. Two sets are bare literals and name their consumer in a comment
  beside them (`rpgstations:mount-surface`, `rpgstations:action-function`); a third,
  `rpgstations:hud-positions` (backing `RpgStationsSettingsAsset.SummaryHud.Position`), is a
  literal preset list re-validated entry by entry through the shared `HudPosition.isValidPreset`,
  so a preset dropped upstream warns instead of being silently offered. **The dataset ids are
  declared in two places and no test cross-checks them** (`BuilderField` exposes no public metadata
  getter, so a codec walk cannot read the declared `Dropdown` ids back): adding a `Dropdown` to a
  leaf without adding its handler here yields a silently EMPTY pick list, not an error. Declare the
  id as a constant on this class and reference it from the codec's `metadata(...)` call when adding
  the next one.

**No `PackControlAsset`/`Control` map infra exists in this mod** - every fold is always additive (`replace=false`); a reload re-fires the `LoadedAssetsEvent`
and re-folds for free, no owner-override precedence layer beyond `defaults < pack` load order.

## How this schema GROWS (additive by default, and the two windows a break is allowed)

The `api` artifact carries a WRITTEN additive growth policy (its own router's "Additive growth
policy" section, at the repo's `api/src/main/java/com/ziggfreed/rpgstations/api/CLAUDE.md`:
default-bodied interface methods, new event classes, additive getters, never a signature change).
This is its ASSET-SIDE twin, and it exists for the same reason: these seven codecs are a published
contract too. A pack author's JSON and a server owner's override files are written against them,
live in repos this one cannot see, and outlive any release here. **`SCHEMA.md` is GENERATED from
the codecs** ([`../docs/SchemaDocWriter`](../docs/SchemaDocWriter.java), `gradlew
generateSchemaDocs`), so it can only ever describe the shape the current build has - the policy for
CHANGING that shape belongs here, and an edit to `SCHEMA.md` is never where a growth decision gets
recorded.

**Additive growth has four shapes, and every one of them leaves already-authored files decoding
byte-identically.**

- **A new leaf is NULLABLE and `appendInherited`, and its `.documentation(...)` lands in the SAME
  edit.** Null means "inherit, or take the reader-default", never a value, so a file written before
  the leaf existed keeps its exact behavior and a `Parent` chain keeps per-leaf granularity through
  it. Name the reader-default in the documentation string, since that is the only place an author
  ever reads it; `AssetDocumentationCoverageTest` fails the build on a blank one, so the
  documentation is not a follow-up you can defer past a build anyway.
- **A collection leaf is PLURAL and an array from its first day** (the naming rule above), because
  one-to-many is the growth that hurts most: `Sounds`, `DropLists`, `Particles`, `Budgets`,
  `Conversions` each took a second entry with no schema change at all, where a singular scalar would
  have forced a rename on every authored file. When a leaf is genuinely singular, say WHY in its
  documentation - `Recipe` does ("two transforms means two actions").
- **A cohesive set of new knobs is a NESTED GROUP behind one key, never flat prefixed siblings.**
  The group is itself one nullable leaf on its parent, so adding it is additive at the parent as
  well, and its own leaves stay independently nullable underneath, which is what keeps a partial
  overlay meaningful. `Custody.Display`, `Look.Role`, and `FromCrafting.NativeTime` all landed this
  way; a `HoldOffsetX`/`HoldEnabled`-shaped pair is the smell this rule exists to catch.
- **A scalar array that needs PER-ENTRY knobs gets STRING-OR-OBJECT promotion, not a sidecar key.**
  [`StringOrObjectCodec`](StringOrObjectCodec.java) dispatches per VALUE on the raw JSON/BSON type,
  so every shorthand entry an author already wrote keeps decoding untouched and only an entry that
  needs more is written as the object form beside it. `Presentation.Sounds` is the precedent: a bare
  `"<eventId>"` string and `{EventId, DelayMs?}` are the same leaf, and a shorthand entry re-ENCODES
  as a bare string so a hand-authored file and an encode-based test stay on the same bytes. Reach
  for this BEFORE a parallel `SoundDelays[]`-shaped key, which would split one cue's data across two
  places and leave the two arrays to drift out of alignment.

**A HARD break - a rename or a removal with no alias - is permitted in exactly two windows, and
nowhere else.**

- **While the release the schema shipped in is still IN FLIGHT.** Nothing outside this repo is
  authored against it yet, so the honest shape is also the cheap one, and an alias kept "just in
  case" is permanent debt bought for nobody. The 0.1.0 window is the precedent and it was used
  freely, wave after wave: `Camera.FaceBlockMode` renamed to `Camera.Recipe`, `Hold.Seat.Enabled`
  replaced by `Hold.Mount`, `Loot.Tables` renamed to `Loot.Lootables`, the `StationStep.Type` union
  replaced outright by orthogonal phase groups, and `Grants.BonusOutputCopies`,
  `StationAsset.Recipes[]`, `Picker`, and station-level group inheritance all deleted rather than
  deprecated.
  **The price of the window is that the shipped JSON moves in the SAME change** - this jar's own
  assets, `unreleased/`, and the sibling stations pack - so nothing is left decoding against a key
  that no longer exists.
- **As a deliberately BATCHED break at the 1.0.0 api-freeze boundary.** The api freeze is the one
  scheduled moment consumers already expect to re-read their integration, so a break that genuinely
  cannot be expressed additively rides that boundary WITH the rest of them: collected into one wave,
  listed in `CHANGELOG.md` key by key with the replacement named, and with every shipped asset in
  this repo and the sibling pack migrated in the same change. One breaking key smuggled into a
  routine release is worse than ten announced at a boundary, because the first one an author hits
  teaches them the schema cannot be trusted.

**After 1.0.0 the default is additive, permanently.** A leaf that turned out wrong is superseded by
a new one beside it, with its own documentation saying which leaf to author instead and the engine
keeping the old one honest; it is not renamed under a pack author's feet. If a break looks
unavoidable outside those two windows, that is a design conversation with the maintainer, not a
judgment call to make inside the edit.
