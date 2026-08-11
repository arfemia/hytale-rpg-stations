# loot/ - the conditional-lootable engine (standalone-rich, not a husk)

Router for `loot/`. Native Hytale `ItemDropList`s have ZERO conditional/gating fields (scout-
verified: all 5 container types, unconditional once reached); this layer is the additive
condition/weight/command vocabulary ABOVE that native roller, never a reimplementation of item
selection itself. Binding evaluation rules: `Chance.Factors`/`Ladder.Factors` are weighted
`FactorRef` arrays, every floor reward routes through its own `Grants` (no direct floor drop-list
leaf), top-level AND per-floor `Grants` both fire, and `Chance` gates the WHOLE roll including
`Ladder`.

**The SMART-CUE rule (enforced HERE, in `LootEngine.rollAndGrant`).** A celebration never plays over
nothing. A `Roll` carries its own top-level `Presentation` and a `Ladder.Floor` carries its own; each
is paired with the `Grants` group authored BESIDE it. With no grants beside it a cue is pure
presentation and rides on the plain hit/reach; with grants authored it rides only once applying them
actually PRODUCED something (a drop-table item that genuinely landed after that table's own internal
weights resolved, a command run, an effect applied, an `OutputItems` amount tallied, a contribution
posted). This is the engine's job rather than the schema's precisely because only this package knows
what a grant produced: a `DropLists` entry names a native table with its own internal empty weight,
so "the floor was reached" and "the player got something" are genuinely different facts. The
mechanism is small and deliberate - `applyGrants` returns a BOOLEAN (produced anything?) instead of
void, the roll-level and floor-level cues are judged independently against their own groups by the
one shared `collectEarnedCue`, and both land in the SAME `GrantResult.getFloorPresentations()`
transport (roll cue first), so `StationService.applyGrantResult` needs no new plumbing at all. The
NATIVE drop roll behind it is an injected `DropListGranter` seam (`rollAndGrantDropList` in
production), which is what lets the rule be unit-tested against a PINNED table outcome rather than
live randomness - `LootEngineCuePresentationTest`.

**Concern boundary (the one rule to read first):** a `Roll` grants what ELSE a cycle handed over -
`DropLists`, `Commands`, `Effects`, `Contributions`, and `Grants.OutputItems` (ADDITIVE items of
the cycle's own primary output, fractional). It never touches HOW MUCH of the cycle's own output was produced
by the conversion itself; that lives end to end in `ActionDef.Recipe.Yield`, which is now purely
DETERMINISTIC (`Base`/`Scale`/`Min`/`Max`, no ladder, no roll, zero factor involvement - see
`../asset/CLAUDE.md` and `../station/CLAUDE.md`'s `StationYield` bullet). The retired
`Grants.BonusOutputCopies` used to overlap the two: it granted N copies of the WHOLE produced
stack, so a station whose yield already paid 4 planks silently handed out 4 more for a leaf that
read as "+1", with the two numbers living in different files under different concept names. The
leaf is deleted, not renamed; `Grants.OutputItems` is its ADDITIVE, single-item replacement,
directly comparable to `Yield`'s own number because both count the same output item.

**`Grants.OutputItems` is FRACTIONAL** (maintainer ruling): the whole part is granted every time and
the leftover fraction is the chance of exactly one more, so `1.5` pays one item always plus a second
half the time and averages exactly 1.5. A half-step tier is therefore authorable ON the ladder floor
that earns it, which a roll banded to one quality tier beside the ladder could never be - that shape
double-pays a modded tool matching the band while reaching a higher floor. The tally rides through
the engine UNROUNDED and resolves once per cycle (`OutputItemResolver`, below).

- **[`RollEvaluator`](RollEvaluator.java)** - the PURE decision core (conditions / chance roll /
  ladder floor pick), unit-tested with an injected roll source + factor lookup, zero store access.
  Read its javadoc before changing `asset/Roll`'s shape, since the schema reference and this class
  must stay in lockstep. Its `chancePasses` is PUBLIC on purpose: it is the ONE chance-gate
  authority every `Roll.Chance` site (an action's `Bonus`, a `StationStep.Roll` phase) reuses
  rather than growing a second, subtly-different percent gate.
- **[`FactorGate`](FactorGate.java)** - the ONE bound-gate core, the gate twin of `FactorMath`'s
  weighted sum: every `Conditions` array evaluated through an injected `(factorId, param) ->
  Double` lookup goes through it (a `Roll`'s gate here, a `StationStep`'s gate in
  `station.StationStepDecisions`), so identical JSON gates identically wherever it is authored. The
  bound TABLE itself is the shared leaf's own `FactorCondition.accepts`, not rewritten here; what
  this class adds is the walk - a null/empty array passes, a BLANK entry is skipped (a
  half-authored line is a validator finding, not an invisible content blackout), and an
  unresolvable factor or an out-of-bounds value fails CLOSED. The station `Requires` gate resolves
  against the registry directly, so it uses the shared array evaluator instead (which also names
  the factor that shut the gate, for the deny log).
- **[`FactorLadder`](FactorLadder.java)** - the ONE ladder core: "sum the weighted factors, pick the
  reached floor", called by EVERY ladder-shaped consumer in the schema (`RollEvaluator.highestFloor`
  here, `station.ContributionScaling.multiplier` for an action's `ContributionScale`). It exists
  because ladder consumers used to present an IDENTICAL authored shape and then diverge in three
  invisible ways - whether an empty factor array killed the ladder, whether a `Min <= 0` floor was
  reachable, and which of two equal-`Min` floors won. One core now fixes all three: an absent/empty
  `Factors` resolves `0.0`, a `Min` reader-defaults to `0` and a `Min <= 0` floor IS reachable
  (rejecting a malformed threshold is a validator's job, never an evaluator's), and an equal-`Min`
  tie goes to the LAST authored floor, matching every other later-wins rule in this schema. The
  validator warns on the duplicate rather than letting the evaluator silently pick. `StationYield`
  (the deterministic per-cycle output transform) does NOT use this core any more - it has no ladder
  left to resolve.
- **[`LootEngine`](LootEngine.java)** - the store-touching half: resolves an action's effective
  `Bonus` `Roll` list (`resolveRolls(LootRef)`), then evaluates + APPLIES every roll matching a
  trigger against ONE [`FactorSnapshot`](FactorSnapshot.java) built fresh per batch
  (`rollAndGrant`, memoizes each `(factorId, param)` resolution so an `OutputItems` `Chance` and a
  `Ladder` reading the SAME factor - e.g. `yourmod:station_luck` - see the identical resolved
  number, the "one aggregation, several consumers" invariant that also covers the deterministic
  `Yield` transform reading the SAME cycle snapshot). Every item grant routes through the shared
  `util.ItemGrantUtil` seam (hotbar-first, then backpack storage, then drop-at-block -
  `ItemGrantUtil` is a thin policy wrapper over `ziggfreed-common`'s `inventory.InventoryGrant`,
  the mod-agnostic ordering primitive) - a stack that cannot fit anywhere still lands as a ground
  item at the block, never a silent skip; never fails or stops the cycle. Droplist items AND
  `Grants.OutputItems` both tally on `GrantResult` (`getDropListItems()`/`getOutputItems()`) so the
  caller (`station.StationService`) folds them into its own session item ledger and fires the
  item-specific GOLD "what you gained" notification (`StationService#notifyItemGain`,
  `lucky=true`); `StationService#grantBonusOutputItems` resolves the `getOutputItems()` tally to
  whole items and grants that many of the cycle's own resolved primary output id
  (`s.cycleOutputItemId`), through the SAME `ItemGrantUtil` seam every other grant uses. `Grants.DropLists` is a plural ARRAY, each entry
  rolled independently in authored order, so "a guaranteed common table plus a rare one" is two
  entries rather than a synthetic merged asset or two whole duplicated `Roll`s. This class stays
  presentation-agnostic - it reports WHAT reward landed and WHICH cues were EARNED (the smart-cue
  rule above); `StationService` plays them through its OWN `emitMoment` choke point (see
  `../station/CLAUDE.md`), never a second playback path here. The public 12-arg `rollAndGrant` is a
  thin ADAPTER over a package-visible seam-driven core (`lookup`/`chanceRoll`/`placeholders`/
  `dropLists`), the same injected-seam discipline `RollEvaluator` and `OutputItemResolver` already
  keep - every engine handle reduces to a function before the pass runs, so the whole pass is
  deterministically testable.
- **[`OutputItemResolver`](OutputItemResolver.java)** - the PURE fractional-to-whole-items
  resolution behind `Grants.OutputItems`: `floor(tally)` items always, plus ONE more when an
  injected `[0,1)` sample lands under the leftover fraction (the same injected-randomness seam
  `RollEvaluator` and `station.StampCapEngine` use, so it unit-tests deterministically; production
  passes `ThreadLocalRandom`). Called ONCE PER CYCLE over the SUMMED tally, never per roll - that is
  the whole reason the tally rides through `GrantResult` as a raw `double`: two rolls paying `0.5`
  each must average one whole item, which rounding each roll separately cannot do. A whole-number
  tally never consults the sample at all, so a deterministic ladder floor stays deterministic. Its
  sibling `reportable(resolvedCount, landed)` is the OTHER half of the same decision, equally pure:
  the count a grant may be REPORTED as, which is the rolled count only when the stack genuinely
  reached the player. "How many were rolled" and "how many were received" are different facts, and
  the session ledger, the produced row's yield breakdown, and the item-gain toast all read the
  second one through this ONE fold (`StationService#grantBonusOutputItems`), never the rolled count
  sitting beside it.
- **[`LootableCatalog`](LootableCatalog.java)** - the folded `asset.LootableAsset` store
  (`Server/RpgStations/Lootables/*.json`), `defaults < pack`, referenced by ANY `LootRef.Lootables`
  entry (an action's `Bonus`, a `StationStep.Roll` phase, or an `ExtensionAsset`'s `Bonus`
  payload) - `LootRef` is the ONE loot-reference vocabulary, so this catalog has no notion of
  "which site" referenced it.
- **`Roll.Grants.Contributions[]` collection** - the engine grants items/commands/effects itself but
  NEVER resolves a contribution channel; a granted `{Channel, Param, Amount}` entry (top-level AND
  per-floor) is COLLECTED onto the roll result (`getContributions()`) for `station.StationEvents` to
  forward on `StationCycleCompletedEvent.oneShotContributions`, a list deliberately separate from
  the action's own `Work.PerCycleContributions`: a one-shot find grant BYPASSES both a per-cycle
  entry's scalings (the action's own `ContributionScale` ladder AND the idle fraction), so a rare
  find is worth the same on a practice cycle as on a real one and whatever tool the player holds.
  The two sites also keep DIFFERENT filters on purpose - the grants site gates on
  `Contribution.isPostable()` (non-blank channel AND a positive amount, because a grant either fires
  or does not), while the per-cycle site forwards any non-blank channel with a null amount read as
  `0.0`, so a zero-amount entry still reaches a listener as a visible zero row. Collection is gated on
  a `Cycle` trigger, because a `Completion` roll fires from inside `stop()` with no cycle event left
  to ride (the validator warns on that authoring).
- **[`CommandRewardExecutor`](CommandRewardExecutor.java)** - the zero-code third-party
  integration surface: a `Roll.Grants.Commands` entry runs through common's `util.CommandExecutor`
  AS THE SERVER CONSOLE (never limited to the triggering player's own permissions - an authored
  `"give {player} ..."` just works), with fixed placeholders `{player}`/`{uuid}`/`{station}`/
  `{action}`/`{cycles}` substituted first.
- **`ItemModule.get().getRandomItemDrops(id)` is the native roll boundary** - pure compute,
  world-thread-safe (`ThreadLocalRandom` internally), called from `LootEngine` once per
  `Grants.DropLists` entry. `ItemDropContainer.populateDrops(..., DoubleSupplier, ...)` (the custom-RNG seam) is a
  documented FUTURE hook for luck-weighted in-table rolls - NOT used today (parity first).
