# loot/ - the conditional-lootable engine (standalone-rich, not a husk)

Router for `loot/`. Native Hytale `ItemDropList`s have ZERO conditional/gating fields (scout-
verified: all 5 container types, unconditional once reached); this layer is the additive
condition/weight/command vocabulary ABOVE that native roller, never a reimplementation of item
selection itself. Design authority: `../../../../../../.claude/research/raw/rpg-stations-unified-design-2026-07-21.md`
section 4.5, tightened by critique M3 (all fixes binding: `AddFactors` is an array, every floor
reward routes through its own `Grants` - no direct floor `DropList` leaf, top-level AND per-floor
`Grants` both fire, `Chance` gates the WHOLE roll including `Ladder`).

- **[`RollEvaluator`](RollEvaluator.java)** - the PURE decision core (conditions / chance roll /
  ladder floor pick), unit-tested with an injected roll source + factor lookup, zero store access.
  This is where the M3 schema ambiguities were resolved into concrete behavior - read its javadoc
  before changing `asset/Roll`'s shape, since the schema doc and this class must stay in lockstep.
- **[`LootEngine`](LootEngine.java)** - the store-touching half: resolves a station's effective
  `Roll` list, then evaluates + APPLIES every roll matching a trigger against ONE
  [`FactorSnapshot`](FactorSnapshot.java) built fresh per batch (memoizes each `(factorId, param)`
  resolution so a bonus-copy `Chance` and a `Ladder` reading the SAME factor - e.g.
  `yourmod:station_luck` - see the identical resolved number, the "one aggregation, two
  consumers" invariant). Every grant routes through the shared
  `util.ItemGrantUtil` seam (round-5, 2026-07-22: hotbar-first, then backpack storage, then
  drop-at-block - `ItemGrantUtil` is a thin policy wrapper over `ziggfreed-common`'s
  `inventory.InventoryGrant`, the mod-agnostic ordering primitive) - a stack that cannot fit
  anywhere still lands as a ground item at the block, never a silent skip; never fails or stops the
  cycle. Bonus-copy items and droplist items tally SEPARATELY (`GrantResult`) so the caller
  (`station.StationService`) folds both into its own session item ledger; both grant kinds now
  fire the SAME item-specific GOLD "what you gained" notification (round-5, `StationService
  #notifyItemGain`, `lucky=true`) - REPLACING the old two generic `ui.station.lucky`/
  `ui.station.rare_find` toasts. This class stays
  presentation-agnostic - it reports WHAT reward landed; `StationService` plays it through its OWN
  `emitMoment` choke point (see `../station/CLAUDE.md`), never a second playback path here.
- **[`LootableCatalog`](LootableCatalog.java)** - the folded `asset.LootableAsset` store
  (`Server/RpgStations/Lootables/*.json`), `defaults < pack`, referenced by a station's
  `Loot.Lootables`.
- **`Roll.Grants.Contributions[]` collection** - the engine grants items/commands/effects itself but
  NEVER resolves a contribution channel; a granted `{Channel, Param, Amount}` entry (top-level AND
  per-floor) is COLLECTED onto the roll result (`getContributions()`) for `station.StationEvents` to
  forward on `StationCycleCompletedEvent.oneShotContributions`, a list deliberately separate from
  the station's own `Work.PerCycleContributions`: a one-shot find grant BYPASSES both the tool-power
  multiplier and the idle fraction, so a rare find is worth the same whatever tool the worker holds.
  The two sites also keep DIFFERENT filters on purpose - the grants site gates on
  `Contribution.isPostable()` (non-blank channel AND a positive amount, because a grant either fires
  or does not), while the per-cycle site forwards any non-blank channel with a null amount read as
  `0.0`, so a zero-amount entry still reaches a listener as a visible zero row. Collection is gated on
  a `Cycle` trigger, because a `Completion` roll fires from inside `stop()` with no cycle event left
  to ride (the validator warns on that authoring, exactly as it does for `BonusOutputCopies`).
- **[`CommandRewardExecutor`](CommandRewardExecutor.java)** - the zero-code third-party
  integration surface: a `Roll.Grants.Commands` entry runs through common's `util.CommandExecutor`
  AS THE SERVER CONSOLE (never limited to the triggering player's own permissions - an authored
  `"give {player} ..."` just works), with fixed placeholders `{player}`/`{uuid}`/`{station}`/
  `{action}`/`{cycles}` substituted first.
- **`ItemModule.get().getRandomItemDrops(id)` is the native roll boundary** - pure compute,
  world-thread-safe (`ThreadLocalRandom` internally), called from `LootEngine` for a `Grants.DropList`
  grant. `ItemDropContainer.populateDrops(..., DoubleSupplier, ...)` (the custom-RNG seam) is a
  documented FUTURE hook for luck-weighted in-table rolls - NOT used in phase 1 (parity first).
