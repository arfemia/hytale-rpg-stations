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
  `StationCycleCompletedEvent.xpAsks`; optional `Idle` practice mode; `Repeat` - phase 2 leg B,
  read only by the `station.step` engine's program-completion handling, unused by any shipped
  content yet), `Recipe` (authored `Conversions` or `FromCrafting` derivation), `Hold` (the
  movement-lock effect / the `Mount` knob family - phase 2 leg D, design 9.2, see
  `../station/CLAUDE.md`'s Mount bullet for the Block/Entity surface breakdown), `Tool` (the
  held-tool gate + `XpScale`), `Custody` (phase 2
  leg C - session-scoped placed-input custody, see [`Custody`](Custody.java) below), `Loot`
  (`Tables`/`Rolls`), `Camera` (third-person pull + `FaceBlock`/`Recipe`), `Animation` (`EmoteId` +
  `Swing`/`Impact`/`ActionClip`), `Presentation` (per-cycle moment), `Completion` (session-end
  moment, a second `Presentation` instance), `Requires` (permission + factor-`Condition` gate),
  `Flairs` (per-flair-id cosmetic overrides), and (phase 2 leg B) `Actions` - a named,
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
  `{ItemId?, ResourceTypeId?, Tags?, Function?}` (`Function` is `"Weapon"|"Armor"|"Tool"`, the new
  functional route; its live resolution against the held item is phase-2 leg E scope, unimplemented
  this leg). `isCatchAll()` = no route authored (matches anything; `StationValidator` flags an
  unreachable LATER catch-all).
- **[`StationStep`](StationStep.java)** (design 9.3) - ONE step of a multi-action station's step
  PROGRAM: a `Type` discriminator (`Consume`/`Produce`/`Wait`/`Roll`/`Command` - executable this
  leg via `station.StationStepKernel`; `Stamp`/`Mount` - schema-reserved, decode fine, no handler
  yet) + base fields every type shares (`Id`, `Conditions`, `OnConditionFail{Result,Goto}` - the
  "Branch is NOT a step type" branch mechanism, `Presentation`) + ONE per-type nested group.
  `Consume` supports `From` `"Inventory"` OR `"Custody"` (phase 2 leg C - drains the block's placed-
  input claim); `Produce` supports only `To` `"Inventory"` this leg (`"Custody"` decodes,
  schema-reserved for a future output-stays-in-custody leg, fails cleanly at runtime until then).
  `Roll` shares the SAME `Roll`/`LootableAsset` vocabulary a station's own `Loot` group uses (DRY,
  one roll engine).
- **[`Presentation`](Presentation.java)** - RpgStations' OWN codec (design section 4.1: a
  deliberate small divergence from the MMO's copy of the same shape, not a `ziggfreed-common`
  lift - the two authoring-side vocabularies diverge, this one drops the MMO's `Feedback` leaf
  since there is no `FeedbackService` indirection here). Leaves: `Sound`, `Particles`,
  `Animation`, `AnimationItem`, `AnimationSlot`, `Camera`. The design's planned `Shake` leaf
  (common `CameraShakeService`) is NOT yet landed as of leg 7A.
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
