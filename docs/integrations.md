# Add-ons & Integrations

The typed extension surface any mod hooks to turn station work into its own rewards.

RPG Stations carries **no progression vocabulary of its own** and depends on no other mod. Every
station runs its full loop - conditional loot, command rewards, enhancement - standalone. When another
mod IS installed alongside it, a soft extension surface lets that mod turn completed work into its own
rewards. The coupling is optional in both directions: neither mod hard-depends on the other, and each
runs unaffected when the other is absent.

The two-registry contract is the whole idea in one line: **factors read a number IN, channels post a
number OUT**, and both are namespaced ids this engine never interprets. Read
[Extension Channels](extension-channels.md) first - it teaches both directions with a complete worked
example.

## The api artifact

The extension surface is a small, typed contract published as its own jar (`rpg-stations-api`). A mod
that wants to hook the engine links against it `compileOnly` and declares RPG Stations an optional
dependency, then presence-checks the plugin at runtime before touching it. The api is split by shape,
following the same native-events convention the rest of the ecosystem uses:

**The two-step idiom is not optional.** The JVM verifies a method's whole bytecode the first time it
is invoked, so a method that both presence-checks RPG Stations AND references an api type throws
`NoClassDefFoundError` merely by being CALLED when RPG Stations is absent - even though the guard
clause would have skipped the api branch at runtime. Split it: (1) a presence check using only core
Hytale types (`PluginManager.getPlugin`, which returns null for both a missing plugin and a disabled
one), and (2) a private nested class holder that references api types, loaded only on its own first
active use, which now only happens after step 1 confirmed a live instance. Every public method the
consumer exposes follows the same shape: a thin flag-gated outer wrapper with zero api-type
references, delegating into the holder.

- **Observe-only moments are native Hytale events** a mod listens for.
- **Request/response points are typed registries** a mod registers a provider into.

## Native events (observe-only)

RPG Stations dispatches native Hytale events at each meaningful moment of a work session, on the
owning world thread, only when something is actually listening. A listener observes; it never has to
answer:

| Event | Fires when |
|---|---|
| `StationSessionStartedEvent` | A player engages a station and a work session begins. |
| `StationCycleCompletedEvent` | One work cycle finishes; carries the station's declared contributions (per-cycle and one-shot) plus the resolved `ContributionScale` multiplier (display-only - the per-cycle amounts arrive already scaled). |
| `StationSessionCompletedEvent` | The session stops (for any reason); fires after summary enrichers run. |
| `StationEnhanceCompletedEvent` | An enhancement Stamp commits; carries before/after item copies and the enhancement report. |
| `StationToolBrokeEvent` | A tool the session was using breaks. |
| `StationOutputProducedEvent` | A produce phase of an ATTENDED session commits - into a placed custody pile (the receiving socket is named) or the worker's inventory; carries fresh immutable copies of the committed stacks. An unattended settle deliberately fires nothing here: that output surfaces at gather, on the event below. |
| `StationUnattendedGatheredEvent` | A player gathers a custody pile that accrued [unattended work](unattended-work.md) cycles; carries the gatherer (never null), the granted cycle count, and the batch's already-scaled contributions. |
| `StationStructureChangedEvent` | A [multiblock structure](structures-and-sockets.md) changes standing state at its anchor - a completed build activates, or a broken one reverts; names the pattern, the block now standing, and the acting player (absent on an environment break). |

Each event's fields document which are plain data (safe to keep) and which are live world-thread
context valid only during dispatch - a listener that defers work captures the plain fields and
re-resolves the rest.

## Typed registries (request/response)

Where the engine needs an answer from another mod, it reads a typed registry on the static api holder.
Register a provider once at startup:

### Factor registry

The one extensible numeric-factor vocabulary every conditional-lootable `Roll` (its conditions,
chances, and ladders) and every station `Requires` gate evaluates over. RPG Stations ships its own
built-ins: session-scoped ones under the `rpgstations:` namespace (session seconds, cycle count) and
straight native-data reads under `hytale:` (tool power, tool quality, item level, durability, and any
native stat); an external mod registers namespace-prefixed ids. An unknown factor at runtime fails a
condition closed and resolves a value to 0, each with a one-time warning - never a crash. See
[Loot & Factors](loot-and-factors.md) for how a station author references one.

### Contribution-channel registry

The write-side twin: `declare(channelId)` registers a namespaced id a station's `Contributions` can
post to. Declaration only - there is nothing to resolve, because the engine forwards every
contribution verbatim on the cycle event and interprets none of them. Declaring feeds the
`rpgstations:channels` editor dropdown and the `UNKNOWN_CHANNEL` validator warning; an undeclared
channel still forwards. See [Extension Channels](extension-channels.md).

### Validation-hook registry

Third-party content checks that run inside the engine's own full validate pass, so a mod that owns a
factor family or a contribution channel keeps its composition rules with the vocabulary instead of
hardcoding them in this engine. A hook sees the folded stations and every roll's reference structure
and formula numbers, and reports info/warn findings. Advisory only, try-guarded, never blocking.

### Flair-unlock registry

Answers "which flair ids has this player unlocked". The engine consults the union across every
registered provider, and registers one itself: a built-in read of ziggfreed-common's persisted
per-player flair set, so unlocks work with no other mod installed. Persistence stays outside this
engine (RPG Stations stores no per-player fact); a mod keeping unlocks in its own store registers
its own provider beside the built-in one. See [Flairs](flairs.md).

### Enhance-stamper registry

The single active delegate the anvil's Stamp step calls to read and write a weapon's enhancement
state. RPG Stations owns all the roll and cap math; the stamper only encodes how a given server stores
enhancement points onto a stack, and returns a report RPG Stations renders verbatim in the summary -
so no stat vocabulary leaks into this mod. With none registered, the Stamp step still applies
durability. See [Enhancement & Stamp](enhancement-and-stamp.md).

### Summary-enricher registry

Adds extra ledger rows to the session-summary panel (prepended before the engine's own item rows) plus
an optional theming hook over the panel. See [Settings](settings.md#the-summary-panel) for what the
panel shows.

## Known integrations

Mods that ship an RPG Stations integration. Each one documents its own ids and behavior on its own
site; nothing about them is duplicated here.

- [MMO Skill Tree](https://mmo-skill-tree-docs.ziggfreed.com)

Building one? Everything a consumer needs is on this page and
[Extension Channels](extension-channels.md) - no RPG Stations code change is involved, and no listing
here is required for an integration to work.
