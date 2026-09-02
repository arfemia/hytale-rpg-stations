# Unattended Work

Stations that keep working while nobody stands at them: the `Work.Unattended` opt-in, the
game-time catch-up, and the gather payout.

By default a station only works while a player holds a session at it. An action that authors the
`Work.Unattended` group keeps settling its recipe conversions against the block's PLACED custody
piles even with nobody engaged: load the pot, walk away, and the ingredients keep becoming stew.
The transform is immediate - inputs drain from their piles and outputs land in theirs - while
everything a live worker would have EARNED on top of the items (the per-cycle loot rolls, the
per-cycle contribution posts) accrues on the output pile and pays out to whoever gathers it.

## The opt-in

```json
"Work": {
  "CycleMs": 5000,
  "Unattended": { "MaxCycles": 24, "CatchUpMaxMs": 86400000 }
}
```

Authoring the group at all opts in (`"Unattended": {}` is the whole opt-in); a station that never
authors it is attended-only, byte-identical to before the knob existed.

| Field | Default | What it does |
|---|---|---|
| `Enabled` | `true` when the group is authored | Whether unattended settling applies. Exists so a native `Parent` child can author `false` to switch settling off while inheriting the rest of the group. |
| `MaxCycles` | `24` | ONE ceiling wearing both hats: the most cycles a single catch-up settle may commit, AND the most accrued cycles a single gather pays out. At the default 5000ms cycle that bounds a settle burst to about two minutes of work, and keeps an overnight pot's payout at a stack-scale number rather than thousands. Cycles beyond it are forfeited, never banked. |
| `CatchUpMaxMs` | `86400000` (24 hours) | The most elapsed world game time one settle may consume - the native processing bench's own catch-up ceiling. Elapsed time beyond it is forfeited. |

## How the settle runs

- **The clock is world GAME time** (the stash's own persisted catch-up stamp). Game time stands
  still while the server is down, so an outage cooks nothing and owes nothing - a pot mid-stew at
  shutdown is exactly as far along at the next boot.
- **A throttled per-world pass** (every `Limits.UnattendedIntervalMs`, default 1000ms - see
  [Settings](settings.md)) visits every custody-loaded block whose committed action opts in, in
  LOADED chunks only. An unloaded chunk's station simply waits; when the chunk loads again, the
  pass finds the stash, and the elapsed game time settles on the next visit (up to the catch-up
  ceiling).
- **One settle is analytic, not simulated**: `floor(elapsed / cycleMs)` whole cycles, clamped by
  what the input piles hold, the room the output piles have, and `MaxCycles` - committed as one
  batch through the same conversion selection an attended session runs (tier order, exact-set
  rows, per-socket addressing, the deterministic `Yield`). A conversion's own `DurationMs` paces
  it, else `Work.CycleMs`.
- **Clamped time forfeits; sub-cycle time banks.** When only time bounded the settle, the partial
  cycle in progress carries to the next visit. When inputs, room or `MaxCycles` clamped it, the
  un-workable backlog is dropped - topping the station up never burst-pays hours it spent unable
  to work.
- **A live session wins.** The pass skips any block a session is actually working; attended play
  is always the authority, and the two never race.
- **Only the implicit recipe transform settles.** An action authoring `Steps` or `Anchors` runs
  those attended-only (the validator says so at authoring time: `UNATTENDED_WITH_STEPS` /
  `UNATTENDED_WITH_ANCHORS`); an unattended-enabled action with no `Custody` group can never
  settle at all (`UNATTENDED_WITHOUT_CUSTODY`).
- **Doneness composes.** An unattended batch opens and re-stamps the same
  [ready window](actions-and-steps.md#doneness-the-ready-window-on-produced-output) an attended
  `To:"Custody"` produce would - one batch per settled cycle - and an expired window collapses to
  its `Overdone` items before the next settle reads the piles. A station left truly alone can
  cook AND burn.

Unattended output always lands in custody piles (there is no worker inventory to hand it to),
which is also why a `Doneness` ready window on an unattended action needs no authored
`Produce.To:"Custody"` step to be reachable.

## The gather payout

The settled cycles ACCRUE on the produce pile, per conversion, and pay out when a player takes
that pile out of the world - a press-F gather on its display prop, or the hand-back a session stop
runs. The gatherer earns it, whoever they are: the pile's owner does not gate the payout, because
the per-socket `Share` rules already decided who may gather at all.

At the gather, the accrued cycles are evaluated AS IF attended, with the GATHERING player as the
worker:

- **Contributions**: the action's `Work.PerCycleContributions`, each at the IDLE rate
  (`Work.Idle.Fraction`, the same fraction an idle practice cycle pays - its reader default 0.1
  applies when no `Idle` group is authored), times the action's `ContributionScale` ladder
  resolved against the GATHERER, times the granted cycles. They ride the
  `StationUnattendedGatheredEvent` already scaled; whichever mod owns a channel grants them
  verbatim.
- **Loot rolls**: the action's effective `Bonus` replays one `Cycle`-trigger pass per granted
  cycle against the gatherer's own factor snapshot - per-cycle chance independence and per-cycle
  pool draws included, so a table behaves exactly as it would have across that many attended
  cycles. Items, droplists, commands and effects land on the gatherer; earned cues play at the
  block; `OutputItems` grants pay extra units of the accrued conversion's own primary output.
- **The one boundary**: a replayed roll's one-shot `rpgstations:contribution` grants do NOT fire.
  They are completion-shaped posts with no cycle event to ride at a gather - only per-cycle
  contributions and per-cycle rolls accrue.
- **The ceiling**: one gather pays at most `MaxCycles` cycles (the same knob that caps a settle
  burst). Accrual beyond it is forfeited with the gather, so a pile never carries stale debt
  forward.

Breaking the station block drops the placed materials as always and forfeits the accrual with the
stash, exactly like an open doneness window: a destroyed station pays nobody.

## What other mods see

`StationUnattendedGatheredEvent` fires on the shared event bus at every gather that paid accrued
cycles, AFTER the engine's own grants landed: the gatherer (never null - it fires only at a live
gather), the station block's world and position, the station and action ids, the granted cycle
count, and the already-scaled contribution list. A listener filters the contributions by channel,
exactly as it does on `StationCycleCompletedEvent`. See
[Add-ons & Integrations](integrations.md).

---

Previous: [Custody & Placed Display](custody-and-placed-display.md) · Next: [Structures & Sockets](structures-and-sockets.md)
