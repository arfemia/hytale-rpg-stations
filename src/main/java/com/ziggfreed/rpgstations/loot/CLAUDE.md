# loot/ - the station half of the loot pass

Router for `loot/`. **The loot MODEL and its evaluation are not here.** A `Roll`, its
`Conditions`/`Chance`/`Ladder`/`Grants`/`Cue`, the pure evaluator, the smart-cue rule, the
`Lootable` and `RollPool` asset types and their stores all live in `ziggfreed-common`'s
`com.ziggfreed.common.loot` (+ `loot.stamp`, `loot.reward`) - one loot core, so identical JSON
behaves identically at a station, in a chest, and at a quest turn-in. Read that package's router
first; this one covers only what a STATION adds on top.

## What lives here

- **[`StationLootEngine`](StationLootEngine.java)** - the station-shaped pass. Three jobs:
  - **Extension-aware table resolution** (`resolveRolls`). A referenced table's rolls are its
    EFFECTIVE ones - whatever it authors plus every `Target:{Lootable}` `ExtensionAsset`'s appended
    rolls. The merge belongs at THIS read rather than at each caller, so a table gains its extended
    rolls at every reference site (an action's `Bonus`, a step's `Roll` phase) and no site can be
    left seeing the unextended table.
  - **The station sinks.** Item and drop-list grants go hotbar-first, then backpack storage, then a
    ground drop at the station block (`util.ItemGrantUtil` over the shared `InventoryGrant`), so a
    stack that fits nowhere still lands as a ground item rather than being discarded. Commands run
    as the SERVER CONSOLE.
  - **`GrantResult`**, the pass tally: the items that landed, the EARNED cue ids, and the three
    station-only collections (`getEffectGrants`/`getContributions`/`getOutputItems`).
- **[`StationRewardKinds`](StationRewardKinds.java)** - the three payouts that mean something only
  at a station, expressed as registered reward KINDS so they compose with every other kind in one
  `Grants.Rewards` array: `rpgstations:effect` (`Id`, `DurationMs`), `rpgstations:contribution`
  (`Channel`, `Param`, `Amount`), `rpgstations:output_items` (`Count`).
- **[`OutputItemResolver`](OutputItemResolver.java)** - the PURE fractional-to-whole-items
  resolution behind the output-items reward: `floor(tally)` items always, plus ONE more when an
  injected `[0,1)` sample lands under the leftover fraction. Called ONCE PER CYCLE over the SUMMED
  tally - that is the whole reason the tally rides through `GrantResult` as a raw `double`, since
  two rolls paying `0.5` each must average one whole item, which rounding each roll separately
  cannot do. Its sibling `reportable(resolvedCount, landed)` is the other half of the same decision:
  "how many were rolled" and "how many were received" are different facts, and the session ledger,
  the produced row and the item-gain toast all read the second one.
- **[`CommandRewardExecutor`](CommandRewardExecutor.java)** - which placeholders a station offers
  (`{player}`/`{uuid}`/`{station}`/`{action}`/`{cycles}`) and the console dispatcher grants run
  through. The substitution, the `/give` positional-quantity fix and the per-command guard are the
  shared `command.CommandRunner`'s.

## Rules to keep

- **A CUE is a MOMENT ID, never a presentation body.** The loot layer names a moment and this engine
  decides what it sounds like: `StationService.applyGrantResult` emits each earned cue through the
  ONE `emitMoment` funnel, where the action's own `Moments` map and every applicable flair get their
  say. Well-known ids plus `step:<actionId>:<stepId>` and the open author-defined `cue:<name>`
  namespace all pass the typo check (`StationFlairs.isKnownMomentId`).
- **The three station kinds COLLECT, they do not act**, which is why they are a PER-PASS registry
  (`StationRewardKinds.forPass`, seeded from the process-wide vocabulary so a kind another mod
  registered still pays out here). An effect has to be tracked on the session that earned it, a
  contribution has to ride the cycle event that is about to dispatch, and an output-item amount is a
  tally the whole cycle sums first. None of that is reachable from a handler holding only a spec and
  a player.
- **A completion pass refuses the two cycle-scoped kinds.** It fires from inside `stop()`, with no
  cycle event left to ride and no cycle output left to add to, so they drop rather than queue for a
  cycle that never comes; the validator reports the authoring ahead of runtime.
- **A pass always carries a Subject**, even with no resolvable `PlayerRef`. A null subject switches
  the whole reward leaf off, which would silently cost the collecting kinds as well as the paying
  ones.
- **Every grant path owes its own notification.** The Produce phase only ever announces the recipe's
  own deterministic `Yield`, so a bonus that is not separately notified makes every toast
  under-report - a cycle paying one base plank plus four from the tool ladder announcing a single
  plank reads in game as the bonus not working at all.
- `ItemModule.get().getRandomItemDrops(id)` is the native roll boundary: pure compute,
  world-thread-safe, called once per `Grants.DropLists` entry. `ItemDropContainer.populateDrops(...,
  DoubleSupplier, ...)` (the custom-RNG seam) is a documented FUTURE hook for luck-weighted in-table
  rolls, deliberately unused today.
- Tests: `StationRewardKindsTest` (the three kinds, the completion refusal, the seeded registry),
  `OutputItemResolverTest` (the fraction resolution and the summing contract), `LootFixtures` (the
  short way to write a station grant).
