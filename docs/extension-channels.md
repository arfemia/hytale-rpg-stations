# Extension Channels

One vocabulary, two directions: factors read a number in, contributions post a number out.

RPG Stations carries no progression vocabulary of its own. It has exactly one extension idea, applied
in both directions: **content names a namespaced id, and some other mod owns what that id means.**
Reading a number in and posting a number out are the same shape, mirrored.

| | READ - factors | WRITE - contributions |
|---|---|---|
| Authored leaf | `{ "Factor": "<ns>:<id>", "Param": "<opaque>" }` | `{ "Channel": "<ns>:<id>", "Param": "<opaque>", "Amount": <number> }` |
| Registry | `FactorRegistry.register(id, provider)` | `ContributionChannelRegistry.declare(id)` |
| Api accessor | `RpgStationsApi.factors()` | `RpgStationsApi.channels()` |
| Editor dropdown | `rpgstations:factors` | `rpgstations:channels` |
| Validator warn | `UNKNOWN_FACTOR` | `UNKNOWN_CHANNEL` |
| What the engine does | Asks a registered provider for a `double`. | Forwards `{Channel, Param, Amount}` on the cycle event. Nothing else. |

The one asymmetry is deliberate: the engine ships built-in FACTORS (`rpgstations:session_seconds`,
`rpgstations:cycle_count`, `hytale:tool_power`, `hytale:tool_quality`, `hytale:tool_item_level`,
`hytale:tool_durability_percent`, and `hytale:stat`) because it can compute them. It ships **zero**
built-in channels, because it interprets none.

## Reads: Factor and Param

A factor id resolves to a number a formula sums or gates on. See [Loot & Factors](loot-and-factors.md)
for every site that accepts one. A mod registers a provider once at startup:

```java
RpgStationsApi api = RpgStationsApi.get();
api.factors().register("yourmod:reputation", (ctx, param) -> reputationOf(ctx.playerId(), param));
```

`Param` is opaque to this engine - whatever the provider documents. An unregistered factor resolves to
0 and fails a `Condition` closed, with a one-time warning; content written for a mod that is not
installed degrades quietly instead of breaking.

## Writes: Contributions

A contribution is an amount a station posts OUT to a named channel. The engine never resolves a
channel; it forwards the entry verbatim on `StationCycleCompletedEvent` and lets the channel's owner
decide what it means. A mod declares its channel ids at startup:

```java
api.channels().declare("yourmod:crop_quality");
```

Declaring is optional in the sense that it never blocks anything: an undeclared channel is still
forwarded, and the validator only emits an `UNKNOWN_CHANNEL` warning. Declaring is worth it anyway,
because it puts the id in the in-game Asset Editor's `rpgstations:channels` dropdown and turns a typo
from a silent no-op into a boot-log line.

### Two authoring sites, two meanings, one record

Scaling is decided by WHERE a contribution is authored, never by a flag on the entry. Same leaf shape,
different documented semantics per owning group:

| Site | Fires | Scaling |
|---|---|---|
| `Work.PerCycleContributions[]` | Every completed cycle. | Pre-scaled by the action's own `ContributionScale` factor ladder BEFORE the engine forwards it; on an idle cycle, ALSO pre-scaled by `Work.Idle.Fraction`. The resolved multiplier is reported on the event display-only - a listener grants the forwarded amount verbatim. |
| `Roll.Grants.Contributions[]` | Once, when that roll grants. | None. Posted verbatim - a rare find is worth the same whatever tool the player holds, and it never inherits `ContributionScale` or the idle fraction. `Cycle`-trigger rolls only. |

The per-cycle key says `PerCycle` out loud precisely so the two never read as the same blob in an
author's eye. There is no `Scaled` knob: it would be meaningless at the grants site and would let
content defeat the one-shot rule.

## A worked example

A fictitious `yourmod` rewards farming quality. It declares one channel and reads one factor, and the
station content names both by id:

```json
{
  "Id": "Farm",
  "Work": {
    "CycleMs": 4000,
    "PerCycleContributions": [
      { "Channel": "yourmod:crop_quality", "Param": "WHEAT", "Amount": 6.0 }
    ]
  },
  "Bonus": {
    "Rolls": [ {
      "Trigger": "Cycle",
      "Chance": { "BasePercent": 3,
                  "Factors": [ { "Factor": "yourmod:reputation", "Param": "guild" } ],
                  "CapPercent": 20 },
      "Grants": {
        "DropLists": ["RPG_Station_Sawmill_T1"],
        "Contributions": [
          { "Channel": "yourmod:crop_quality", "Param": "WHEAT", "Amount": 25.0 }
        ]
      }
    } ]
  }
}
```

The listener side filters by channel and posts whatever the amount means to it:

```java
eventBus.register(StationCycleCompletedEvent.class, event -> {
    for (StationContribution c : event.contributions()) {
        if (!"yourmod:crop_quality".equalsIgnoreCase(c.channel())) continue;
        creditQuality(event.playerId(), c.param(), c.amount());   // already scaled; grant verbatim
    }
    for (StationContribution c : event.oneShotContributions()) {
        if (!"yourmod:crop_quality".equalsIgnoreCase(c.channel())) continue;
        creditQuality(event.playerId(), c.param(), c.amount());   // verbatim, never scaled
    }
});
```

**Filtering by channel is mandatory.** Both lists carry every channel the station authored, including
ones belonging to other mods; a listener that consumes an entry it did not declare is reading someone
else's vocabulary. `event.contributionScale()` reports the multiplier the engine already applied to
`contributions()` - it exists purely so a listener can SHOW why a cycle was worth what it was worth (a
"x2.5 tool" summary line); multiplying by it again double-counts.

## One channel, many Params

Prefer ONE channel id with a meaningful `Param` over one channel per thing being credited. A
channel-per-thing design explodes the declared set, makes the editor dropdown useless, and gives an
author a new convention to learn for every new value. `Param` exists for exactly this.

## Next

[Add-ons & Integrations](integrations.md) covers the rest of the api surface (native events, the
flair-unlock, enhance-stamper, summary-enricher and validation-hook registries) and the two-step
presence check a consumer must use before touching any of it.
