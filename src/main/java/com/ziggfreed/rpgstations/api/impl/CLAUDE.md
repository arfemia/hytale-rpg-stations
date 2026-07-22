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
  `RpgStationsPlugin#setup()`) registers the four `rpgstations:` built-ins - RpgStations dogfoods
  its OWN registry rather than special-casing its built-ins. `resolve(...)` swallows a throwing
  provider (FINE log, returns `null`) - a bad third-party factor provider must never crash a loot
  roll or a station gate check.
- **[`FlairUnlockRegistryImpl`](FlairUnlockRegistryImpl.java)** - a `CopyOnWriteArrayList` of
  registered providers; the union read iterates all of them per resolution.
- **[`SummaryEnricherRegistryImpl`](SummaryEnricherRegistryImpl.java)** - same shape, registration
  order preserved (drives the "prepended before the engine's own rows, registration order" rule).
- **[`StationViewImpl`](StationViewImpl.java)** - the read-only per-station projection built from
  the live `station.StationCatalog` entry at query time (never cached/stale).

All four registries follow the same guard discipline as `RpgStationsApi.get()`'s own contract:
cheap, side-effect-free reads/registrations; nothing here retains a live world-thread object past
the call that handed it in.
