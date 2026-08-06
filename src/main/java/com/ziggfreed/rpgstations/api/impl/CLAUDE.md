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
- **[`FactorRegistryImpl`](FactorRegistryImpl.java)** - `ConcurrentHashMap<String,
  StationFactorProvider>`, last-write-wins, id lowercased. `registerBuiltins()` (called once from
  `RpgStationsPlugin#setup()`) registers the `rpgstations:` built-ins plus the mod-agnostic `stat`
  factor - RpgStations dogfoods its OWN registry rather than special-casing its built-ins.
  `resolve(...)` swallows a throwing provider (FINE log, returns `null`) - a bad third-party factor
  provider must never crash a loot roll or a station gate check.
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
  registered providers; the union read iterates all of them per resolution.
- **[`SummaryEnricherRegistryImpl`](SummaryEnricherRegistryImpl.java)** - same shape, registration
  order preserved (drives the "prepended before the engine's own rows, registration order" rule).
- **[`EnhanceStamperRegistryImpl`](EnhanceStamperRegistryImpl.java)** - a single
  `volatile` active slot, last-registration-wins (mirrors `FactorRegistryImpl`'s discipline, NOT
  the union-of-all shape the list registries use). Read by
  `station.StationStepHandlers.StampHandler` directly (not back through `RpgStationsApi`).
- **[`StationViewImpl`](StationViewImpl.java)** - the read-only per-station projection built from
  the live `station.StationCatalog` entry at query time (never cached/stale). `flairIds()` reuses
  `station.FlairCatalog.effectiveFlairsFor` - the SAME inline-`Flairs`-UNION-`FlairAsset` merge
  `StationFlairs` resolves at moment-playback time - rather than a narrower inline-only view.
  `contributions()`/`contributionChannels()`/`contributionParams(channel)` project the EFFECTIVE
  union of `Work.PerCycleContributions` - the station's own group, every resolved action's own
  group (a whole-group override can author its own entries), and the Station-/Action-targeted
  `ExtensionAsset` appends, de-duplicated on the `(Channel, Param, Amount)` triple (an action that
  merely inherits the station `Work` re-yields the same entries) - the same reads the cycle event
  forwards from, so a hook validating "which channels does this station post" can never miss a
  per-action or extension-appended entry. The RAW list is exposed alongside the two derived views
  precisely because a blank/absent `Param` cannot appear in the derived ones, and only a channel's
  own owner knows whether its `Param` is required.

Every registry follows the same guard discipline as `RpgStationsApi.get()`'s own contract:
cheap, side-effect-free reads/registrations; nothing here retains a live world-thread object past
the call that handed it in.
