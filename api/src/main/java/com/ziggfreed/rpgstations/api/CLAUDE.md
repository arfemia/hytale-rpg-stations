# api/ - the extension surface (frozen at 1.0.0 release)

Router for the `api` Gradle submodule (`additional-mods/rpg-stations/api/`), package
`com.ziggfreed.rpgstations.api` (+ `.api.event`). This is the ONE contract another mod (the MMO
Skill Tree bridge, today's only consumer) compiles against to reach the station engine - a typed
`compileOnly` link, per the maintainer's bridge-route decision (design section 3.3, critique M1
resolved: typed `compileOnly` api jar + manifest `OptionalDependencies`, reflective adapter only
if that classloader-visibility path fails verification). Bundled into the runtime `RpgStations-*.jar`
(minus `META-INF/services`, the kweebec api-bundling mechanic) AND published standalone as
`rpg-stations-api-<version>.jar` for a compile-time consumer. **Everything here is FROZEN once
RpgStations 1.0.0 releases** - until then it is free to reshape; do not treat it as stable yet.

Split by shape (root hyMMO CLAUDE.md's native-events rule): **observe-only moments are native
Hytale events** (`event/` subpackage); **request/response points are typed registries** on the
static [`RpgStationsApi`](RpgStationsApi.java) holder.

- **[`RpgStationsApi`](RpgStationsApi.java)** - the static holder (`get()`/`set()`, mirroring
  `MMOSkillTreeAPI`'s singleton discipline via [`RpgStationsApiHolder`](RpgStationsApiHolder.java)).
  `get()` before RpgStations finishes `setup()` (or when it is simply not installed) throws
  `IllegalStateException` - a caller MUST presence-check the plugin first; this method performs no
  detection of its own. Exposes `factors()`, `flairUnlocks()`, `summaryEnrichers()`, and a
  read-only `stations()` catalog view.
- **[`FactorRegistry`](FactorRegistry.java)** / **[`StationFactorProvider`](StationFactorProvider.java)**
  / **[`FactorContext`](FactorContext.java)** - the ONE extensible numeric-factor vocabulary every
  conditional-lootable `Roll` (Conditions/Chance/Ladder) and every station `Requires` gate
  evaluates over. `register(factorId, provider)` is last-write-wins, id lowercased.
  `StationFactorProvider.resolve(ctx, param)` runs synchronously on the world thread and must not
  retain `ctx`. RpgStations registers its own built-ins under the `rpgstations:` namespace
  (`session_seconds`/`cycle_count`/`tool_power`/`tool_durability_percent`); an external id is
  namespace-prefixed by convention (`mmoskilltree:station_luck`/`skill_level`/`combat_level`, the
  MMO bridge's three providers). An unknown factor at runtime fails a `Condition` CLOSED (roll does
  not fire) and resolves a `Chance`/`Ladder` value to 0, each with a one-time warn.
- **[`FlairUnlockRegistry`](FlairUnlockRegistry.java)** / **[`FlairUnlockProvider`](FlairUnlockProvider.java)**
  - `unlockedFlairIds(playerId)` returns a `Set<String>`; the engine's flair overlay resolution
  consults the UNION across every registered provider. No provider registered = empty set = base
  presentations only. Persistence is the REGISTERING mod's own concern - RpgStations never stores
  a per-player fact.
- **[`EnhanceStamperRegistry`](EnhanceStamperRegistry.java)** / **[`EnhanceStamper`](EnhanceStamper.java)**
  / **[`StampInspection`](StampInspection.java)** / **[`StatRoll`](StatRoll.java)** (design section
  9.5, phase 2 leg E) - the anvil Stamp step's `Stats`-leaf delegate: a SINGLE active slot
  (last-registration-wins, `FactorRegistry`'s discipline, NOT `FlairUnlockRegistry`'s union-of-all
  shape - there is one "how does this server encode enhancement points" answer at a time).
  `EnhanceStamper` is a lean 2-method contract RpgStations' own `station.StampCapEngine` calls:
  `inspect(stack)` reads the stack's CURRENT enhancement state (format-opaque to RpgStations - the
  MMO's registered stamper reads `item.ItemStatsMeta`) BEFORE any roll/cap math runs (zero
  mutation); `apply(stack, entries)` writes the ALREADY rolled + cap-clamped entries (RpgStations
  never re-derives a cap here) and returns the new stack, called only after every compute-phase
  validation already passed. `null` from `active()` = no stamper registered = the Stats leaf
  no-ops (Durability still lands). Deliberately simpler than the design doc's literal
  `StampContext`/`StampResult` prose - unfrozen pre-1.0.0, free to reshape.
- **[`SummaryEnricherRegistry`](SummaryEnricherRegistry.java)** / **[`SummaryEnricher`](SummaryEnricher.java)**
  / **[`SummaryContext`](SummaryContext.java)** / **[`SummaryDecorateContext`](SummaryDecorateContext.java)**
  - `rows(ctx)` returns extra ledger rows PREPENDED before the engine's own item rows
  (registration order); the optional `decorate(ctx)` default method is a post-build hook over the
  summary panel's `UICommandBuilder` for theming (the MMO bridge's `ThemeRetint` reach-in).
  Enrichers run in `station.StationService#stop()` BEFORE `StationSessionCompletedEvent` fires -
  see `../../station/CLAUDE.md`'s exit-hooks bullet for the ordering guarantee.
- **[`StationView`](StationView.java)** - read-only per-station projection (`id()`, `nameKey()`,
  `xpSkills()`, `flairIds()`) used by a bridge for objective target names / soft-warns without
  reaching into the live engine catalog.
- **[`XpAsk`](XpAsk.java)** - `(skillId, perCycleBase)` record; the engine forwards a station's
  authored `Work.Xp` declarations verbatim on `StationCycleCompletedEvent` and never interprets
  them itself. Whatever progression mod listens decides what an ask means.
- **`event/`** - the four `IEvent<Void>` POJOs (`StationSessionStartedEvent`/
  `StationCycleCompletedEvent`/`StationSessionCompletedEvent`/`StationToolBrokeEvent`), immutable,
  dispatched via `HytaleServer.get().getEventBus().dispatchFor(...)` + `hasListener()` on the
  owning world thread - see `../../station/CLAUDE.md` for the concrete firing rules and
  `com.ziggfreed.rpgstations.station.StationEvents` (the implementation). Each event's javadoc
  states which fields are plain data (safe to retain) vs. live world-thread context (`Store`/`Ref`/
  `CommandBuffer` - valid ONLY synchronously during dispatch; a listener that defers work must
  capture the plain fields and re-resolve).

api `compileOnly` deps: the Hytale server jar (`IEvent`, `Store`/`Ref`/`CommandBuffer`,
`UICommandBuilder`) + the `ziggfreed-common` jar (`SummaryRow`). jsr305 ships `api` (a consumer's
`@Nonnull`/`@Nullable` annotations resolve without a separate dependency).
