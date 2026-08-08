# api/ - the extension surface (frozen at 1.0.0 release)

Router for the `api` Gradle submodule (`additional-mods/rpg-stations/api/`), package
`com.ziggfreed.rpgstations.api` (+ `.api.event`). This is the ONE contract another mod compiles
against to reach the station engine - a typed `compileOnly` api jar + a manifest
`OptionalDependencies` entry. Bundled into the runtime `RpgStations-*.jar`
(minus `META-INF/services`, the kweebec api-bundling mechanic) AND published standalone as
`rpg-stations-api-<version>.jar` for a compile-time consumer. **Everything here is FROZEN once
RpgStations 1.0.0 releases** - until then it is free to reshape; do not treat it as stable yet.

Split by shape (root hyMMO CLAUDE.md's native-events rule): **observe-only moments are native
Hytale events** (`event/` subpackage); **request/response points are typed registries** on the
static [`RpgStationsApi`](RpgStationsApi.java) holder.

## THE PARADIGM: one vocabulary, two directions

The read side and the write side are the SAME shape, mirrored. A content asset names a namespaced
id; some other mod owns what that id means. Nothing else about extension needs explaining.

| | READ - factors | WRITE - contributions |
|---|---|---|
| authored leaf | `{"Factor": "<ns>:<id>", "Param": "<opaque>"}` | `{"Channel": "<ns>:<id>", "Param": "<opaque>", "Amount": <double>}` |
| asset type | `asset/FactorRef`, `asset/Condition` | `asset/Contribution` |
| api type | [`StationFactorProvider`](StationFactorProvider.java) + [`FactorContext`](FactorContext.java) | [`StationContribution`](StationContribution.java) |
| registry | [`FactorRegistry`](FactorRegistry.java)`.register(id, provider)` | [`ContributionChannelRegistry`](ContributionChannelRegistry.java)`.declare(id)` |
| api accessor | `RpgStationsApi.factors()` | `RpgStationsApi.channels()` |
| editor dropdown | `rpgstations:factors` (LIVE) | `rpgstations:channels` (LIVE) |
| validator | `UNKNOWN_FACTOR` (WARN, fail-open) | `UNKNOWN_CHANNEL` (WARN, fail-open) |
| engine knowledge | resolves the id to a `double` via the registered provider | **never resolves anything** - forwards `{channel, param, amount}` on the event |

The one asymmetry is deliberate and is the whole ruling in a line: **the engine owns built-in
FACTORS because it can compute them, and owns ZERO built-in channels because it interprets none.**

**Three rules resolve every case.** (1) A foreign mod's name, id, type, or domain concept may never
appear in a schema key, an api type, an engine identifier, a validator id, a lang value, or a
shipped jar asset - forwarding a value without interpreting it is NOT a defense (that exact
argument is what the pre-1.0.0 `Work.Xp`/`XpAsk` shape was retired for). (2) Shipped javadoc and
`.documentation()` may name the ENGINE's own namespaces (`EntityStatType`, `DamageCause`,
`ItemDropList`) and this mod's own ids; for a third-party example use the fictitious `yourmod:`
namespace. (3) The docsite may keep ONE short "Known integrations" line naming a consumer with an
outbound link; it may NOT host that mod's reference tables. Enforcement is
`src/test/.../MmoAgnosticismTest`, which scans `src/main/java`, `api/src/main/java`, and
`src/main/resources` and fails the build on a hit, with an empty allowlist. `src/test` is
deliberately out of scope (fixture values are author-owned and ship nothing); prose surfaces
(docsite, `CHANGELOG.md`, `CURSEFORGE.md`, these routers) are reviewed as documentation, which is
why an in-repo router like this one may name a consumer by class where it is genuinely the
reference for an idiom.

**Promotion trigger.** `StationContribution` + `ContributionChannelRegistry` live HERE, api-local,
beside their read-side twin - not in `ziggfreed-common`. Neither type references an rpg-stations or
consumer type, so when a genuine SECOND mod needs the identical write-side relay (a namespaced,
per-cycle-scaled-or-one-shot numeric post), lift both to `ziggfreed-common` as a pure COPY-MOVE and
make this package a re-export. That is the repo's standing prove-it-in-two-places bar. Do not
pre-lift on speculation: today there is exactly one consumer relationship, the record is
inseparable from `StationCycleCompletedEvent` (an api class that can never move), and common's own
`stats/` primitives all REQUIRE their id to resolve to a registered native `EntityStatType`, which
is the exact inverse of a permanently-opaque channel.

## Types

- **[`RpgStationsApi`](RpgStationsApi.java)** - the static holder (`get()`/`set()` via
  [`RpgStationsApiHolder`](RpgStationsApiHolder.java)).
  `get()` before RpgStations finishes `setup()` (or when it is simply not installed) throws
  `IllegalStateException` - a caller MUST presence-check the plugin first; this method performs no
  detection of its own. Exposes `factors()`, `channels()`, `validationHooks()`, `flairUnlocks()`,
  `summaryEnrichers()`, `enhanceStampers()`, and a read-only `stations()` catalog view.
- **[`FactorRegistry`](FactorRegistry.java)** / **[`StationFactorProvider`](StationFactorProvider.java)**
  / **[`FactorContext`](FactorContext.java)** - the ONE extensible numeric-factor vocabulary every
  conditional-lootable `Roll` (Conditions/Chance/Ladder) and every station `Requires` gate
  evaluates over. `register(factorId, provider)` is last-write-wins, id lowercased.
  `StationFactorProvider.resolve(ctx, param)` runs synchronously on the world thread and must not
  retain `ctx`. RpgStations registers its own built-ins under the `rpgstations:` namespace
  (`rpgstations:session_seconds`/`rpgstations:cycle_count`) plus the native-vocabulary ones
  (`hytale:tool_power`/`hytale:tool_quality`/`hytale:tool_item_level`/
  `hytale:tool_durability_percent`) and the mod-agnostic
  `stat` factor, whose `Param` addresses any registered native `EntityStatType`; an external id is
  namespace-prefixed by convention (`yourmod:reputation`). An unknown factor at runtime fails a
  `Condition` CLOSED (roll does not fire) and resolves a `Chance`/`Ladder` value to 0, each with a
  one-time warn.
  **The three TOOL factors are deliberately three, not one.** None of them subsumes another, and a
  formula ranking a full tool family generally wants all three summed with different weights:
  `tool_power` is the FUNCTIONAL read (an `ItemToolSpec` power for a native `GatherType`, named by
  the `Param`, defaulting to the station's own) but it SATURATES across a family's upper tiers, so it cannot separate the top rungs; `tool_quality`
  is the native `ItemQuality.QualityValue`, the authored number that ORDERS quality tiers (so a pack
  shipping its own tier participates with no engine change), but it cannot separate two tools inside
  one tier; `tool_item_level` is the native `ItemLevel`, which separates same-tier tools but does NOT
  track rarity (a vanilla item can be top-rarity at a very low level), so it belongs as a small
  tiebreaker term and never as the leading one. Weighting them is the author's call; the engine holds
  no opinion.
- **[`ContributionChannelRegistry`](ContributionChannelRegistry.java)** / **[`StationContribution`](StationContribution.java)**
  - the write side. `declare(channelId)` is DECLARATION-ONLY: there is no `resolve`, because the
  engine forwards a contribution verbatim and interprets nothing. Declaring buys two things and
  neither is enforcement: the LIVE `rpgstations:channels` Asset-Editor dropdown, and an
  `UNKNOWN_CHANNEL` warn that turns a typo from a silent forever-no-op into one boot-log line. An
  UNDECLARED channel still forwards - fail-open is absolute. `StationContribution
  (channel, param, amount)` is what rides `StationCycleCompletedEvent`'s two lists.
- **[`ValidationHookRegistry`](ValidationHookRegistry.java)** / **[`ValidationHook`](ValidationHook.java)**
  / **[`ValidationScope`](ValidationScope.java)** / **[`RollView`](RollView.java)** /
  **[`FactorRefView`](FactorRefView.java)** / **[`FindingSink`](FindingSink.java)** - third-party
  content checks that run inside `StationValidator`'s FULL validate pass, so a mod owning a factor
  family or a channel keeps its composition rules WITH the vocabulary instead of this engine
  hardcoding them. The views expose both the reference structure and the formula numbers
  (`Chance.BasePercent`/`CapPercent`, ladder floor thresholds and values, factor `Weight`s,
  contribution `Amount`s). `FindingSink` is info/warn only; every hook is try-guarded; nothing here
  can block an asset. This is the correct home for a rule like "do not sum this aggregate factor
  AND the channels it aggregates" - the engine cannot know that, and must not pretend to.
- **[`FlairUnlockRegistry`](FlairUnlockRegistry.java)** / **[`FlairUnlockProvider`](FlairUnlockProvider.java)**
  - `unlockedFlairIds(playerId)` returns a `Set<String>`; the engine's flair overlay resolution
  consults the UNION across every registered provider. No provider registered = empty set = base
  presentations only. Persistence is the REGISTERING mod's own concern - RpgStations never stores
  a per-player fact.
- **[`EnhanceStamperRegistry`](EnhanceStamperRegistry.java)** / **[`EnhanceStamper`](EnhanceStamper.java)**
  / **[`StampInspection`](StampInspection.java)** / **[`StatRoll`](StatRoll.java)** /
  **[`StampResult`](StampResult.java)** / **[`EnhanceLine`](EnhanceLine.java)** - the anvil Stamp step's
  `Stats`-leaf delegate: a SINGLE active slot (last-registration-wins, `FactorRegistry`'s discipline,
  NOT `FlairUnlockRegistry`'s union-of-all shape - there is one "how does this server encode
  enhancement points" answer at a time). `EnhanceStamper` is a lean 2-method contract RpgStations'
  own `station.StampCapEngine` calls: `inspect(stack)` reads the stack's CURRENT enhancement state
  (format-opaque to RpgStations - only the registered stamper knows the encoding) BEFORE
  any roll/cap math runs (zero mutation); `apply(stack, entries)` writes the ALREADY rolled +
  cap-clamped entries (RpgStations never re-derives a cap here) and returns a **`StampResult`** (the
  mutated stack PLUS a `List<EnhanceLine>` enhancements-metadata report - one line per stat actually
  written, each a `{statId, points, Message label}` the provider composes and RpgStations renders
  VERBATIM in the session summary, so no stat vocabulary leaks into this mod; empty = durability-only
  / silent), called only after every compute-phase validation already passed. `null` from
  `active()` = no stamper registered = the Stats leaf no-ops (Durability still lands).
- **[`SummaryEnricherRegistry`](SummaryEnricherRegistry.java)** / **[`SummaryEnricher`](SummaryEnricher.java)**
  / **[`SummaryContext`](SummaryContext.java)** / **[`SummaryDecorateContext`](SummaryDecorateContext.java)**
  - `rows(ctx)` returns extra ledger rows PREPENDED before the engine's own item rows
  (registration order); the optional `decorate(ctx)` default method is a post-build hook over the
  summary panel's `UICommandBuilder` for theming (a consumer's own retint reach-in).
  Enrichers run in `station.StationService#stop()` BEFORE `StationSessionCompletedEvent` fires -
  see `../../station/CLAUDE.md`'s exit-hooks bullet for the ordering guarantee.
- **[`StationView`](StationView.java)** - read-only per-station projection (`id()`, `nameKey()`,
  `contributions()`, `contributionChannels()`, `contributionParams(channel)`, `flairIds()`) used by
  a consumer for target names / soft-warns without reaching into the live engine catalog.
  `contributions()` returns the RAW list on purpose: `contributionParams(channel)` cannot surface a
  blank/absent `Param`, so a consumer that needs to flag one (an author who wrote a `Channel` and
  an `Amount` but forgot the `Param` its channel requires) iterates the raw list. The engine cannot
  make that check itself - whether `Param` is required is the channel owner's contract.
- **`StationCycleCompletedEvent`'s two lists** - `contributions()` carries the station's own
  `Work.PerCycleContributions` and a listener multiplies each amount by `toolMultiplier()` (on an
  idle cycle the amounts arrive ALREADY pre-scaled by `Work.Idle.Fraction`, with the multiplier
  forced to 1.0); `oneShotContributions()` carries `Roll.Grants.Contributions` find grants, kept
  SEPARATE because they are DELIBERATELY UNSCALED - post each at its stated amount, never applying
  `toolMultiplier()`. **A listener MUST filter both by `StationContribution#channel()`**: both
  lists carry every channel the station authored, so consuming an entry you did not declare is
  reading another mod's vocabulary.
- **`event/`** - the five `IEvent<Void>` POJOs (`StationSessionStartedEvent`/
  `StationCycleCompletedEvent`/`StationSessionCompletedEvent`/`StationToolBrokeEvent`/
  `StationEnhanceCompletedEvent`), immutable,
  dispatched via `HytaleServer.get().getEventBus().dispatchFor(...)` + `hasListener()` on the
  owning world thread - see `../../station/CLAUDE.md` for the concrete firing rules and
  `com.ziggfreed.rpgstations.station.StationEvents` (the implementation). Each event's javadoc
  states which fields are plain data (safe to retain) vs. live world-thread context (`Store`/`Ref`/
  `CommandBuffer` - valid ONLY synchronously during dispatch; a listener that defers work must
  capture the plain fields and re-resolve). **`StationEnhanceCompletedEvent`** fires
  from the Stamp path AFTER the mutated item is committed to custody, carrying BOTH reporting
  shapes without this engine learning any stat vocabulary: the provider's own opaque
  `List<EnhanceLine>` metadata report AND immutable `before`/`after` `ItemStack` copies (the engine
  snapshots them around the apply, so a consumer diffs/inspects them itself), plus the native
  `durabilityAdded` delta.

api `compileOnly` deps: the Hytale server jar (`IEvent`, `Store`/`Ref`/`CommandBuffer`,
`UICommandBuilder`, `Message`, `ItemStack`) + the `ziggfreed-common` jar (`SummaryRow`). jsr305
ships `api` (a consumer's `@Nonnull`/`@Nullable` annotations resolve without a separate dependency).

## Additive growth policy (post-1.0.0)

Set by the 2026-08-05 pre-release schema/DX review (ruling 77, `stations-schema-dx-review.md`
proposal P4 as amended). Everything reachable from `RpgStationsApi` is FROZEN once RpgStations
1.0.0 releases - after that point a change here must be one of exactly three shapes, or it is not
a valid post-freeze addition:

1. A new **default-bodied** interface method on `RpgStationsApi` (or any other api interface). A
   method with no default body forces every existing implementation (there is exactly one,
   `RpgStationsApiImpl`) to change in lockstep with every consumer's compiled expectations of the
   interface shape - a signature change in spirit even when the method is new.
2. A new **event class** under `com.ziggfreed.rpgstations.api.event` (a new `IEvent<Void>` POJO).
   Existing event classes never gain a removed/renamed field.
3. A new **additive getter** on an existing event class or record type (e.g. a new field on
   `SummaryContext`), never a change to an existing getter's return type or removal of one.

**No signature changes to an existing method, ever, post-freeze.** No removed methods, no renamed
methods, no changed parameter or return types. `apiVersion()` (added pre-freeze, this same round)
exists specifically so a consumer can detect which additive members are present without
reflection: bump it by exactly one integer per addition batch that lands under this policy (not
per individual method - a coordinated wave of additions is one bump), never on its own.
`apiVersion()` itself is exempt from "default-bodied only" since it shipped before the freeze; it
will never change again once RpgStations reaches 1.0.0.

`RpgStationsApi.isAvailable()`/`find()` (added the same round) are convenience, not a way around
this policy - see their own javadoc for what they do and do not solve.

## Two-step consumer idiom (presence check)

A consumer mod MUST NOT let a `com.ziggfreed.rpgstations.api.*` type
appear in a method whose bytecode can be reached while RpgStations is absent or disabled - the JVM
verifies a method's WHOLE bytecode (every branch, including ones an early return skips at runtime)
together, the first time the method is invoked. Co-locating a presence check with the
api-touching registration code in one method throws `NoClassDefFoundError` merely by CALLING it,
even when the guard clause would have skipped the api-touching branch at runtime. The fix is two
steps: (1) a presence check using ONLY core Hytale types (never an api type), and (2) a private
nested "class holder" that references api types, loaded (and thus verified) only on its own first
active use per JLS 12.4.1 - which now only happens once step 1 has already confirmed RpgStations
is genuinely loaded.

The reference consumer is `RpgStationsBridge` (a consumer-side class, in its own repo, outside this
mod). Its `install()` method does the presence check with zero api-type references:

```java
public static synchronized void install() {
    if (present) {
        return;
    }
    PluginManager pm = PluginManager.get();
    PluginIdentifier id = new PluginIdentifier(GROUP_NAME, PLUGIN_NAME); // "Ziggfreed", "RpgStations"
    PluginBase loaded = pm.getPlugin(id);
    if (loaded == null) {
        SafeLog.info("[RpgStationsBridge] RpgStations not installed - stations are unavailable.");
        return;
    }
    try {
        int stationCount = Extensions.install();
        present = true;
        SafeLog.info("[RpgStationsBridge] RpgStations detected - bridge installed ("
                + stationCount + " station(s) folded).");
    } catch (Throwable t) {
        SafeLog.severe("[RpgStationsBridge] install failed: " + t.getMessage());
    }
}
```

`PluginManager.getPlugin` returns `null` for BOTH a genuinely missing plugin and one merely
listed-but-disabled by server config, so a single check covers both cases. Only once `loaded` is
confirmed non-null does the class holder (line ~219) get touched, and every api-type reference in
the consumer lives inside it:

```java
private static final class Extensions {

    private Extensions() {
    }

    static int install() {
        RpgStationsApi api = RpgStationsApi.get();
        // ... register factor providers, flair-unlock provider, summary enricher, etc.
        return api.stations().size();
    }
}
```

Every OTHER public method the consumer exposes (`stationNameKeyOrNull`, `knownFlairIds`,
`isStationsAvailable` in the reference consumer) follows the same shape: a thin `present`-gated
outer wrapper with zero api-type references, delegating to a same-holder method. This is what
keeps the outer class's public methods callable and verify-clean with RpgStations absent or
disabled - `RpgStationsApi.isAvailable()`/`find()` cannot replace this idiom (see their javadoc);
they are only safe to call from INSIDE the class holder, after step 1 above already ran.
