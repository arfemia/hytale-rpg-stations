# asset/ - the Pattern A content codecs

Router for `asset/`. Every custom asset type this mod authors: the codec IS the schema
(PascalCase keys, nested sub-object groups, every leaf `appendInherited` for native `Parent`
reuse). `RpgStationsPlugin` registers each as its own `AssetStoreRegistrar` store + folds loaded
entries into the matching `station`/`loot` package catalog on `LoadedAssetsEvent`.

- **[`StationAsset`](StationAsset.java)** - an interactive work station, loaded from
  `Server/RpgStations/Stations/*.json`. Ported from the MMO's pre-extraction `asset.type.StationAsset`
  with binding schema deltas: `requires` is this mod's OWN [`Requires`](Requires.java) codec (the
  MMO's `content.gate.Requirements` dependency severed, design 4.4), the old `Luck` group is
  REPLACED by `loot` (`{Tables, Rolls}` over the shared [`Roll`](Roll.java) codec - see
  `../loot/CLAUDE.md`), and (phase 2 leg B) `Camera.FaceBlockMode` is RENAMED `Camera.Recipe`
  (design 9.7, no alias). Groups: `Identity` (name/desc/icon keys), `Work` (cycle cadence + `Xp[]`
  progression declarations the engine never interprets, forwarded verbatim on
  `StationCycleCompletedEvent.xpAsks`; optional `Idle` practice mode; `Repeat` - `false` completes
  the whole SESSION after one program run instead of looping, `station.StationService.dispatchProgram`
  reads it - the anvil's Enhance action ships the first real `Repeat: false` content, phase 2 leg E),
  `Recipe` (authored `Conversions` or `FromCrafting` derivation), `Hold` (the
  movement-lock effect / the `Mount` knob family - phase 2 leg D, design 9.2, see
  `../station/CLAUDE.md`'s Mount bullet for the Block/Entity surface breakdown), `Tool` (the
  held-tool gate + `XpScale`), `Custody` (phase 2
  leg C - session-scoped placed-input custody, see [`Custody`](Custody.java) below), `Loot`
  (`Tables`/`Rolls`), `Camera` (third-person pull + `FaceBlock`/`Recipe`), `Animation` (`EmoteId` +
  `Swing`/`Impact`/`ActionClip`), `Presentation` (per-cycle moment), `Completion` (session-end
  moment, a second `Presentation` instance), `Requires` (permission + factor-`Condition` gate),
  `Flairs` (per-flair-id cosmetic overrides, an authoring convenience - see
  [`FlairAsset`](FlairAsset.java) below for the standalone, third-party-authorable route), and
  (phase 2 leg B) `Actions` - a named,
  authored-order map of [`ActionDef`](ActionDef.java) whole-GROUP overrides (design 9.1; native
  `Parent` inherits the WHOLE map, same as `Flairs` - no per-key merge), absent/empty meaning the
  single implicit `"work"` action built from this asset's own groups. See `../station/CLAUDE.md`
  for how every group drives the engine (`station.ActionResolver` is the resolution choke point).
- **[`ActionDef`](ActionDef.java)** (design 9.1) - one `Actions` map entry: nullable overrides of
  every `StationAsset` group (`Input`/`Custody`/`Work`/`Recipe`/`Tool`/`Hold`/`Camera`/`Animation`/
  `Presentation`/`Completion`/`Loot`/`Requires`) PLUS `Label` (an advisory display key) and
  `Steps` (an authored [`StationStep`](StationStep.java) program; absent means "build the
  implicit program" - see `../station/CLAUDE.md`'s `ImplicitProgram`).
- **[`Custody`](Custody.java)** (design 9.4, phase 2 leg C) - session-scoped placed-input custody:
  `{MaxQuantity?, Input?, States?}`. `MaxQuantity` defaults to **100** (maintainer decision,
  overriding the design doc's draft 64 - `DEFAULT_MAX_QUANTITY`). `Input` (reusing
  [`ActionInput`](ActionInput.java)'s ItemId/ResourceTypeId/Tags routes) is the explicit
  placement-acceptance matcher; when absent, `station.StationCustody.matchesAnyConversionInput`
  derives acceptance from the resolved action's own `Recipe.Conversions` inputs instead (the
  sawmill's "logs by ResourceTypeId family" - zero extra authoring on top of an existing `Recipe`
  group; `station.StationValidator`'s `CUSTODY_NO_INPUT_MATCHER` flags authoring neither). `States`
  (`{Empty?, Loaded?}`) names the block's own `State.Definitions` entries the engine flips between
  (`world.setBlockInteractionState`); null = custody works mechanically with no visual/hint flip.
  Whole-GROUP overridable on `ActionDef` same as every other group. See `../station/CLAUDE.md` for
  the full engine-side behavior (`StationCustody`/`StationCustodyClaim`/`StationCustodyBreakSystem`).
- **[`ActionInput`](ActionInput.java)** (design 9.1) - the diegetic action-selection matcher:
  `{ItemId?, ResourceTypeId?, Tags?, Function?}` (`Function` is `"Weapon"|"Armor"|"Tool"`, resolved
  against the held item's live shape - `station.StationService.liveFunctionOf`, phase 2 leg E).
  `isCatchAll()` = no route authored (matches anything; `StationValidator` flags an unreachable
  LATER catch-all). Live selection itself runs through `station.ActionResolver.selectActionByFamily`
  (a DIFFERENT NAME, never an overload of `selectAction` - see that method's own javadoc), which
  matches against the held item's FULL resolved `ResourceTypeId` family array, not a single id.
- **[`StationStep`](StationStep.java)** (design 9.3/9.5) - ONE step of a multi-action station's step
  PROGRAM: a `Type` discriminator (`Consume`/`Produce`/`Wait`/`Roll`/`Command`/`Stamp` - all six
  executable via `station.StationStepKernel`; `Mount` stays schema-reserved, decodes fine, no
  handler yet) + base fields every type shares (`Id`, `Conditions`, `OnConditionFail{Result,Goto}` -
  the "Branch is NOT a step type" branch mechanism, `Presentation`) + ONE per-type nested group.
  `Consume` supports `From` `"Inventory"` OR `"Custody"` (phase 2 leg C - drains the block's placed-
  input claim); `Produce` supports only `To` `"Inventory"` this leg (`"Custody"` decodes,
  schema-reserved for a future output-stays-in-custody leg, fails cleanly at runtime until then).
  `Roll` shares the SAME `Roll`/`LootableAsset` vocabulary a station's own `Loot` group uses (DRY,
  one roll engine). **`Stamp`** (design 9.5, phase 2 leg E, the anvil's enhance-commit step) is
  nested `{Reagents[], Durability{AddMax}, Stats{Pool?, Entries?, Picks{Min,Max}, Unique, Caps{
  PerItemBudget, PerStat, SkillScaledBudget{Factor,Param,PointsPerLevel}, Economics{RepeatCostMultiplier}}}}`
  - `Reagents` are consumed FROM THE PLAYER'S INVENTORY (not a second custody claim; see the
  class's own javadoc for why), `Stats.Pool`/`Entries` share [`StatRollEntry`](StatRollEntry.java)
  with [`RollPool`](RollPool.java) (below), and `Caps`' composition rule (M2's binding fix -
  effective total budget = MIN of every authored total-budget cap, `PerStat` layers on top) is
  documented on `Caps`' own javadoc, resolved by the PURE `station.StampCapEngine`
  (unit-tested, `StampCapEngineTest`). The Stats leaf delegates the actual item-format write to a
  registered `api.EnhanceStamper` (see `../api/CLAUDE.md`); `Durability` is RpgStations-native
  (real without the MMO).
- **[`StatRollEntry`](StatRollEntry.java)** (design 9.5) - ONE candidate stat-roll entry
  `{Stat, Points{Min,Max}, Weight, Always}`, shared verbatim by `RollPool.Entries` and inline
  `StationStep.Stamp.Stats.Entries` - never duplicated per authoring route.
- **[`RollPool`](RollPool.java)** (design 9.5) - `Server/RpgStations/RollPools/<Name>.json`
  (id = lowercased filename, mirrors `LootableAsset`'s exact shape), body `{Entries: [StatRollEntry, ...]}`,
  referenced by a Stamp step's `Stats.Pool`. Folded into `loot.RollPoolCatalog`.
- **[`Presentation`](Presentation.java)** - RpgStations' OWN codec (design section 4.1: a
  deliberate small divergence from the MMO's copy of the same shape, not a `ziggfreed-common`
  lift - the two authoring-side vocabularies diverge, this one drops the MMO's `Feedback` leaf
  since there is no `FeedbackService` indirection here). Leaves: `Sound`, `Particles`,
  `Animation`, `AnimationItem`, `AnimationSlot`, `Camera`, `Shake` (nested `{EffectId, Intensity}`,
  landed - critique m6's verified `CameraShakeService` shape; corrects this doc's earlier
  "not yet landed as of leg 7A" note, which was stale).
- **[`FlairAsset`](FlairAsset.java)** (design 9.6, phase 2 leg F) - a standalone, ANY-mod-authorable
  cosmetic flair layer, `Server/RpgStations/Flairs/<Name>.json` (Pattern A, id = lowercased
  filename, mirrors `LootableAsset`/`RollPool`'s exact shape): `{Stations?[], Moments}`. `Stations`
  null/empty = applies to every station; `Moments` is an OPEN `Map<String, Presentation>` keyed by
  an arbitrary moment id (the engine's well-known ids - `cycle`/`swing`/`impact`/`rare_find`/
  `completion` - plus a per-step `step:<actionId>:<stepId>` id, see `station.StationFlairs`'s
  constants) - nothing hardcodes the vocabulary in Java. Folded into `station.FlairCatalog`, which
  UNIONS every applicable `FlairAsset` ONTO a station's own inline `Flairs` map at
  `effectiveFlairsFor` (a same-id `FlairAsset` wins over an inline entry). `StationAsset.Flair`
  (the inline map's value type) mirrors this EXACT shape (`{Moments}`, no more fixed
  `Swing`/`Cycle`/`RareFind`/`Completion` leaves) - one flair-content schema, whether authored
  inline or in a standalone file.
- **[`Requires`](Requires.java)** - `{Permission?, Conditions?[]}`, evaluated at station start;
  any failing `Condition` denies with `ui.station.locked`. An unregistered factor id fails CLOSED
  (a gate on a server without the referencing progression mod stays locked, never silently open).
- **[`Condition`](Condition.java)** - `{Factor, Param?, Min?, Max?}`, the ONE shared codec both
  `Requires.Conditions` and every `loot/Roll` group evaluate over the api `FactorRegistry`
  (`FactorContext` in the `api/` module) - never a second condition schema.
- **[`Roll`](Roll.java)** (337 lines) - the conditional-lootable roll: `Trigger` (`Cycle`/
  `Completion`), `Conditions[]`, `Chance{BasePercent, AddFactors[], CapPercent}`,
  `Ladder{Value, Floors[]}`, `Grants{BonusOutputCopies, DropList, Commands[]}` (top-level AND
  per-floor, both fire per critique M3's tightened schema). Shared by `StationAsset.Loot.Rolls`
  and `LootableAsset.Rolls` - see `../loot/CLAUDE.md` for the evaluation engine.
- **[`LootableAsset`](LootableAsset.java)** - `Server/RpgStations/Lootables/<Name>.json`
  (id = lowercased filename), body `{Rolls: [Roll, ...]}`, referenced by a station's
  `Loot.Tables`. The standalone `SawmillFinds` lootable ships in this jar's resources, alive with
  RpgStations alone (built-in `rpgstations:` factors only).
- **[`SettingsAsset`](SettingsAsset.java)** - `Server/RpgStations/Settings/Settings.json`, a
  single id (`settings`), jar default + pack-overridable: `{Enabled, SummaryHud:{Enabled,
  Position, OffsetY, TtlMs}}`. Folded into `station.SettingsCatalog`.

**No `PackControlAsset`/`Control` map infra exists yet in this mod** (unlike the MMO's pack
system) - every fold is always additive (`replace=false`); a reload re-fires the `LoadedAssetsEvent`
and re-folds for free, no owner-override precedence layer beyond `defaults < pack` load order.
