# api/impl/ - the concrete extension-surface implementation

Router for `api/impl/` (part of the MAIN module's source tree, package `com.ziggfreed.rpgstations.api.impl`
- distinct from the `api/` Gradle submodule that defines the interfaces this package implements;
see `../../../../../../api/CLAUDE.md` for the type contract). `RpgStationsPlugin#setup()` injects
[`RpgStationsApiImpl`](RpgStationsApiImpl.java) via `RpgStationsApi.set(...)` before any registry
is used.

- **[`RpgStationsApiImpl`](RpgStationsApiImpl.java)** - the ONE `RpgStationsApi` implementation, a
  thin delegator to each concrete singleton below. Engine-internal code reads those singletons
  DIRECTLY (not back through this narrow public interface) for the engine-only extension reads
  each class's own javadoc documents (e.g. `FactorRegistryImpl.resolve`/`isKnown`,
  `StationValidator`'s known-factor check).
- **[`FactorRegistryImpl`](FactorRegistryImpl.java)** - this mod's `FactorRegistry` surface over
  the SHARED factor vocabulary in `ziggfreed-common` (`common.factor`), reached through
  [`CoreFactorVocabulary`](CoreFactorVocabulary.java): the shared registry brings owner
  attribution, per-provider failure counting, warn-once on an unregistered id, and the fail-closed
  null sentinel, so none of that is re-derived here. Registration is last-write-wins, id
  lowercased. `registerBuiltins()` (called once from `RpgStationsPlugin#setup()`) registers every
  built-in - RpgStations dogfoods its OWN registry rather than special-casing them. **A factor's
  NAMESPACE names the vocabulary's owner, not the registrant**: `rpgstations:session_seconds`/
  `cycle_count` are session concepts (they exist only because a session does),
  `rpgstations:socket_filled` is the station-shaped world fact only this engine can compute (is
  the custody socket named by `Param` satisfied at the evaluated block - the engine pre-resolves
  the readings into the api `FactorContext` at the engage gate over EVERY action's sockets, and
  the provider is a plain-data read that fails closed on an id the context carries no reading
  for; the held-back cooking-pit exemplar gates Grill `Max: 0` / Stew `Min: 1` on it), while every
  `hytale:` one is a straight native read that means the same thing with no station involved -
  `tool_power` (an `ItemToolSpec` power, its native `GatherType` passed as the `Param` so the
  addressing is explicit, defaulting to the station's own when omitted), `tool_quality`,
  `tool_item_level`, `tool_durability_percent`, and `stat`. So two mods converging on a `hytale:`
  id is agreement, not a collision, and an author can tell portability from the id alone. See
  `registerBuiltins()`'s javadoc for the full rule. `resolve(...)` never propagates a throwing
  provider - a bad third-party factor provider must never crash a loot roll or a station gate
  check. `info()` exposes the ledger's owner + failure snapshot for an admin listing.
  **`tool_power` registers through the NULLABLE core seam** (not the primitive station-provider
  one), so a gather type the held tool has no spec for answers "cannot tell" rather than a
  substituted `0` - the one rule the whole vocabulary rests on. Its no-Param form deliberately stays
  the STATION's own effective gather type rather than the portable library's best-of-any-type
  aggregate: same id, context-appropriate number, which is why the registry is per consumer at all.
  **`snapshotFor(ctx)`** is the memoized per-batch reading set every loot pass and step gate
  evaluates against (the shared `loot.FactorSnapshot`), so two formulas reading one factor inside a
  single cycle can never disagree about it; build one per moment and discard it.
- **[`CoreFactorVocabulary`](CoreFactorVocabulary.java)** - the ONE file that speaks both the
  shared vocabulary and this mod's api types, because their simple names collide; everything else
  names just one of them. It owns the shared registry instance, publishes a station evaluation as
  the shared question (the station `FactorContext` rides as the PAYLOAD, with the store and the
  acting entity also published as the shared subject leaves), and keeps a SECOND registry holding
  the portable `hytale:` standard library so an id can be adopted wholesale
  (`registerPortable`) without that library replacing the four tool ids this engine answers from
  the SESSION's own tool snapshot. Same vocabulary, context-appropriate number: `hytale:stat`
  forwards to the shared provider (which reads the acting entity's own stat map), the tool ids do
  not.
- **[`ContributionChannelRegistryImpl`](ContributionChannelRegistryImpl.java)** - the WRITE-side
  twin: a set of declared channel ids, lowercased, `registeredIds()` feeding the LIVE
  `rpgstations:channels` Asset-Editor dropdown exactly as `FactorRegistryImpl` feeds
  `rpgstations:factors`. **It has no `resolve`, and it ships ZERO built-ins** - the engine can
  compute a factor, so it owns built-in factors; it interprets no channel, so it owns none.
  `isDeclared` answering `false` is advisory ONLY: an undeclared channel still forwards on the
  event, and `UNKNOWN_CHANNEL` is a warn that never blocks.
- **[`ValidationHookRegistryImpl`](ValidationHookRegistryImpl.java)** - registered third-party
  content checks, run inside `StationValidator`'s FULL pass over
  `station.StationValidationScope`. Every hook invocation is try-guarded and its findings are
  info/warn only, so a throwing or opinionated hook can never block an asset.
- **[`FlairUnlockRegistryImpl`](FlairUnlockRegistryImpl.java)** - a `CopyOnWriteArrayList` of
  registered providers; the union read iterates all of them per resolution. The plugin registers
  the built-in `station.ZigFlairUnlockProvider` (the shared flair-component read) at setup, so the
  union is never empty on a running server.
- **[`SummaryEnricherRegistryImpl`](SummaryEnricherRegistryImpl.java)** - same shape, registration
  order preserved (drives the "prepended before the engine's own rows, registration order" rule).
- **[`StationViewImpl`](StationViewImpl.java)** - the read-only per-station projection built from
  the live `station.StationCatalog` entry at query time (never cached/stale). `flairIds()` reuses
  `station.FlairCatalog.effectiveFlairsFor` - the SAME inline-`Flairs`-UNION-`FlairAsset` merge
  `StationFlairs` resolves at moment-playback time - rather than a narrower inline-only view.
  `contributions()`/`contributionChannels()`/`contributionParams(channel)` project the EFFECTIVE
  union of every resolved action's own `Work.PerCycleContributions` plus the Action-targeted
  `ExtensionAsset` appends (`ExtensionCatalog#applyToActionContributions`), de-duplicated on the
  `(Channel, Param, Amount)` triple (two actions authoring an identical entry collapse to one) -
  the same reads the cycle event forwards from, so a hook validating "which channels does this
  station post" can never miss a per-action or extension-appended entry. A station holds no
  contributions of its own; a Station-targeted extension reaches this view only by appending a
  whole new action (`ActionResolver#effectiveActions`). The RAW list is exposed alongside the two derived views
  precisely because a blank/absent `Param` cannot appear in the derived ones, and only a channel's
  own owner knows whether its `Param` is required.
- **[`PatternViewImpl`](PatternViewImpl.java)** - the read-only per-structure-pattern projection
  behind `RpgStationsApi.patterns()` (the `stationCount()`-style default-bodied addition the
  shipped impl overrides), built at query time from `station.PatternCatalog.all()` (the raw
  id-sorted folded assets - a cell-less pattern the compile skipped is still visible to a lint).
  Everything is snapshotted at construction into plain immutable values; cell offsets are
  normalized ANCHOR-relative (the frame the runtime walk matches in), and a cell's matcher
  summary reports its dominant route (`ItemId` > `ResourceTypeId` > `Tags`, with `Empty`/`None`
  for the air and malformed cells) - see the api `PatternView` javadoc for the vocabulary.

Every registry follows the same guard discipline as `RpgStationsApi.get()`'s own contract:
cheap, side-effect-free reads/registrations; nothing here retains a live world-thread object past
the call that handed it in.
