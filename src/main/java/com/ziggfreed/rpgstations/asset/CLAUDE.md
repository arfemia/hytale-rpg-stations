# asset/ - the Pattern A content codecs

Router for `asset/`. Every custom asset type this mod authors: the codec IS the schema
(PascalCase keys, nested sub-object groups, every leaf `appendInherited` for native `Parent`
reuse). `RpgStationsPlugin` registers each as its own `AssetStoreRegistrar` store + folds loaded
entries into the matching `station`/`loot` package catalog on `LoadedAssetsEvent`.

- **[`StationAsset`](StationAsset.java)** (1241 lines) - an interactive work station, loaded from
  `Server/RpgStations/Stations/*.json`. Ported from the MMO's pre-extraction `asset.type.StationAsset`
  with two binding schema deltas (design section 4.4): `requires` is this mod's OWN
  [`Requires`](Requires.java) codec (the MMO's `content.gate.Requirements` dependency severed),
  and the old `Luck` group is REPLACED by `loot` (`{Tables, Rolls}` over the shared
  [`Roll`](Roll.java) codec - see `../loot/CLAUDE.md`). Groups: `Identity` (name/desc/icon keys),
  `Work` (cycle cadence + `Xp[]` progression declarations the engine never interprets, forwarded
  verbatim on `StationCycleCompletedEvent.xpAsks`; optional `Idle` practice mode), `Recipe`
  (authored `Conversions` or `FromCrafting` derivation), `Hold` (the movement-lock effect / seat
  mount), `Tool` (the held-tool gate + `XpScale`), `Loot` (`Tables`/`Rolls`), `Camera` (third-person
  pull + `FaceBlock`/`FaceBlockMode`), `Animation` (`EmoteId` + `Swing`/`Impact`/`ActionClip`),
  `Presentation` (per-cycle moment), `Completion` (session-end moment, a second `Presentation`
  instance), `Requires` (permission + factor-`Condition` gate), `Flairs` (per-flair-id cosmetic
  overrides). See `../station/CLAUDE.md` for how every group drives the engine.
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
