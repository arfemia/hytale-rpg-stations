# loot/

The loot model, its evaluator and the `Lootable`/`RollPool` stores are ziggfreed-common's (`com.ziggfreed.common.loot`); read its router, `zc-loot/src/main/java/com/ziggfreed/common/loot/CLAUDE.md` in ziggfreed-common, first. This package is only the station pass, and the one home of the station's grant-reporting rules.

- Extension rolls merge inside `StationLootEngine.resolve`, the one table read, so every reference site sees the extended table; never merge at a call site.
- The three station reward kinds are ziggfreed-common `CollectingRewardKind`s registered ONCE into `RewardKinds.shared()` at plugin setup (`StationRewardKinds.registerInto`); a pass layers its `GrantResult` (the `Sink`) onto the subject with `Subject.withFacets`, so each grant, a nested `Lootable` table's included, collects onto that pass. They need the session and the cycle, so they never act from a handler, and outside a pass they count lost. Never build a registry per pass.
- A test that drives a pass with station kinds calls `StationRewardKinds.registerInto(RewardKinds.shared())` in `@BeforeEach`: the shared registry is process-wide and `RewardKinds.clear()` empties it.
- A pass always carries a non-null `Subject` (anonymous without a `PlayerRef`): a null subject switches off the whole reward leaf, the collecting kinds included. It reads its handles only through `handleAs` (the Player, then the layered `PlayerRef` and `Sink`).
- `rpgstations:output_items` is a fractional tally summed over the cycle and resolved once by `OutputItemResolver`; everything downstream reports the landed count (`OutputItemResolver.reportable`), never the rolled one.
- Every grant path counts what it landed on the HUD itself (`StationService.notifyItemGain`), never on the notification feed: the Produce phase reports only the recipe's deterministic `Yield`, so an uncounted bonus reads in game as not working.
- A `Commands` payout is invisible to every listener and never counts as `STATION_OUTPUT`; author a payout that must be counted as `Items`.
