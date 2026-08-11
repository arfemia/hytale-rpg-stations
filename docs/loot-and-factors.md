# Loot & Factors

The weighted conditional-loot vocabulary, the stat factor provider, and the no-double-counting rule.

Every conditional-loot site in RPG Stations - an action's own `Bonus` group, a step's `Roll` phase, an
extension's appended loot - uses the SAME two building blocks: a **LootRef** (a bag of referenced
lootable tables plus inline rolls) and a **Roll** (one gate-and-payoff unit). Both feed off the SAME
weighted **factor** vocabulary that also drives step iteration counts and enhancement caps.

## LootRef: tables and inline rolls

```json
"Bonus": { "Lootables": ["SawmillFinds"], "Rolls": [ /* inline Roll entries */ ] }
```

`Lootables` references one or more standalone `Server/RpgStations/Lootables/<Name>.json` files (a
reusable table of rolls, shareable across stations); `Rolls` authors rolls directly at this site. Both
resolve together when both are present - an action's own inline rolls run alongside every referenced
table's rolls. `Yield` (on the action's `Recipe`) decides how much of the thing you made, deterministic
and readable at a glance; `Bonus` decides what ELSE you got, conditional and probabilistic - keeping
those two numbers in separate groups is deliberate, so neither can silently multiply the other.

## Roll: gate + payoff

```json
{
  "Trigger": "Cycle",
  "Conditions": [ { "Factor": "rpgstations:cycle_count", "Min": 3 } ],
  "Chance":    { "BasePercent": 2, "Factors": [ { "Factor": "hytale:tool_power" } ], "CapPercent": 25 },
  "Ladder":    { "Factors": [ { "Factor": "hytale:stat", "Param": "YourMod_Luck" },
                               { "Factor": "hytale:stat", "Param": "YourMod_Luck_Woods" } ],
                 "Floors": [ { "Min": 50,  "Grants": { "DropLists": ["SawmillFinds_T1"] } },
                             { "Min": 100, "Grants": { "DropLists": ["SawmillFinds_T2"] },
                               "Presentation": { "Sounds": ["SFX_Coins_Land"] } } ] },
  "Grants":    { "OutputItems": 1.5 },
  "Presentation": { "Sounds": ["SFX_Chest_Legendary_FirstOpen_Player"] }
}
```

`OutputItems` is fractional: `1.5` hands over one item every time plus a second half the time.

- `Trigger` - `Cycle` (default, once per completed work cycle) or `Completion` (once, at session
  stop). `Cycle` means THE action's cycle-completed moment whatever program shape it runs: an
  action driving the classic convert loop and one running an authored `Steps` program both fire it
  once per completed pass. (`Grants.OutputItems` is the one payload an authored program cannot
  honour, since such a program has no single cycle output to add copies of - see the table below.)
- `Conditions` - a hard gate; every entry must pass a bounded factor check before the roll is even
  considered.
- `Chance` - a probabilistic gate over the WHOLE roll (Ladder included):
  `clamp(BasePercent + sum(resolve(factor) * Weight), 0, CapPercent)`, rolled once against a uniform
  0-100 sample. A failing Chance means nothing fires, and the Ladder is never even evaluated.
- `Ladder` - an UNCAPPED, summed factor value looked up against a floor list; the HIGHEST reached
  floor's own `Grants` and `Presentation` fire (a floor above a factor's "normal" range stays
  reachable via a multi-source stack). Floors are not cumulative: exactly one floor is reached, never
  several.
- `Grants` - the reward vocabulary, below. Top-level `Grants` AND the reached floor's own `Grants` both
  apply when a Ladder is present.
- `Presentation` - the roll's own celebration, played at the station block on the rare-find moment
  when the roll HITS. A Ladder floor carries the same leaf for its own tier, so a tiered find
  celebrates per tier and a plain chance roll celebrates on the win with no Ladder involved.

### Worked example: the occasional windfall

The smallest useful roll is a bare `Chance` with a flat `Grants` and no `Ladder` at all - an
occasional surprise on top of whatever the cycle already pays:

```json
{
  "Trigger": "Cycle",
  "Chance": {
    "BasePercent": 2,
    "Factors": [ { "Factor": "hytale:tool_power" } ],
    "CapPercent": 25
  },
  "Grants": { "OutputItems": 1 }
}
```

Chance resolves to `BasePercent` plus each factor's resolved value times its `Weight` (omitted, so
`1.0`), clamped to `CapPercent`. Against the vanilla hatchets, whose Woods gather power runs `0.2` to
`0.5`, that is a real 2.2 to 2.5 percent per cycle. `CapPercent` is doing real work even though no
vanilla tool comes near it: a modded tool with an extreme power value would otherwise push a
flavourful proc towards a guarantee, and a cap is cheaper than trusting every future tool.

The Sawmill shipped exactly this roll for a while and then dropped it, which is worth knowing before
you copy it. Its `Bonus` already carried a tool ladder rewarding the same axis, so the windfall added
variance without adding a decision: no authored number changed what a player would do, and the pair
read less clearly than the ladder alone. **Reach for this shape when a station wants an outcome its
ladder genuinely cannot express** - a rare event rather than a smooth curve - and not as a second
opinion about something the ladder already prices.

## The smart-cue rule: a celebration never plays over nothing

A `Presentation` is always paired with the `Grants` group authored beside it - the roll's own
top-level `Grants` for the roll-level cue, the floor's own `Grants` for a floor cue:

- **No `Grants` beside it** - a pure cue, played on the hit (or on the floor being reached), exactly
  as written.
- **`Grants` beside it** - played only when applying them actually PRODUCED something: an item a
  referenced drop table's own internal weights really handed over, a command run, an effect applied,
  an `OutputItems` amount tallied, or a contribution posted.

The case this exists for: a `DropLists` entry points at a native table that carries its own internal
empty weight, so a reached floor regularly grants nothing at all. Without the rule, a jackpot fanfare
fires over an empty hand every time that happens. The two altitudes are judged independently, so a
roll whose command grant landed still celebrates even when its floor's table paid nothing.

A floor carrying ONLY a `Presentation` and no `Grants` is a blessed shape, not a mistake - it is the
pure floor cue, a rung that announces itself without paying anything extra. The validator agrees:
`LOOT_LADDER_FLOOR_EMPTY_GRANTS` warns only about a floor authoring NEITHER, since reaching that one
genuinely does nothing. A floor authoring no positive `Min` is likewise legal and engine-honored - it
defaults to `0` and is therefore always reached, making it the ladder's baseline tier, which
`LOOT_LADDER_FLOOR_MISSING_MIN` reports at INFO only, as a confirm-you-meant-a-baseline nudge.

## Grants: the reward vocabulary

| Field | What it grants |
|---|---|
| `OutputItems` | ADDITIVE items of THIS cycle's own primary output, on top of the deterministic `Yield` quantity - additive, never a multiplier, so the two numbers stay directly comparable. **Fractional**: the whole part is granted every time and the fraction left over is the chance of one more, so `1.5` pays one item always plus a second half the time and averages exactly 1.5 per cycle (a half-step tool tier is therefore authorable on the ladder floor that earns it). Everything the cycle grants is summed before that single resolution, so two rolls paying `0.5` each average one whole item. Only meaningful on a `Cycle` trigger (the validator warns otherwise, and the engine drops it); a stack that cannot fit drops at the station block rather than vanishing. |
| `DropLists` | Native `ItemDropList` asset ids, each rolled independently in authored order through the engine's own drop-list roller. See [Native Composition](native-composition.md), and the composition note below. |
| `Commands` | Console commands, with `{player}`/`{uuid}`/`{station}`/`{action}`/`{cycles}` placeholders substituted. |
| `Effects` | Native EntityEffects applied to the player, id-ref-only. See [Native Composition](native-composition.md). |
| `Contributions` | One-shot amounts posted verbatim when this roll grants - `Cycle` trigger only, and deliberately UNSCALED (never inherits the idle fraction or the action's `ContributionScale`). See [Extension Channels](extension-channels.md). |

## Composing drop tables

A referenced `ItemDropList` is a native asset, so it composes with Hytale's own container vocabulary
rather than anything this mod invents. Two container types do the work:

- **`Droplist`** nests another list by id (`{"Type": "Droplist", "DroplistId": "..."}`), so a shared
  vocabulary can live in one file and be pulled from many tables. Referencing it several times in the
  same container pulls it several times, which is how a richer tier yields more without restating any
  item.
- **`Multiple`** resolves EVERY child independently rather than picking one, and a child's `Weight`
  there is its own PERCENT CHANCE. That is the opposite of `Choice`, where `Weight` is a relative
  pick weight across `RollsMin`..`RollsMax` picks. The shipped sawmill tiers are each a `Multiple`
  combining N `Droplist` pulls of a shared offcut list with their own `Choice` of the tier's headline
  reward.

> **A container tree made only of `Droplist` references fails asset validation** with
> `FAIL: Container must have something to drop!`, and a failed `ItemDropList` takes the whole mod's
> load down with it. The validator does not resolve a cross-asset reference when deciding whether a
> container can produce anything, so **at least one concrete `Single` must sit somewhere in the
> tree**. Every vanilla table that uses a `Droplist` does exactly this - pair the reference with a
> real item entry, which is usually what you wanted anyway (a guaranteed base payout alongside the
> shared roll).

## FactorRef: the one weighted-sum leaf

Every place a numeric factor is SUMMED - a Chance's `Factors`, a Ladder's `Factors`, a step's
`Repeat.Factors`, an enhancement budget's `Factors`, an action's own `ContributionScale.Factors` - takes
an array of `FactorRef`:

```json
{ "Factor": "hytale:stat", "Param": "YourMod_Luck", "Weight": 1.0 }
```

Composition is ALWAYS a flat weighted sum, `sum(resolve(Factor, Param) * Weight)` - there is
deliberately no expression-tree DSL. `Weight` defaults to 1.0. An unregistered factor id resolves to 0
(fail-closed) rather than throwing, so a Roll referencing a factor from an uninstalled mod just
quietly contributes nothing.

`Condition` is the sibling GATE shape (`{Factor, Param?, Min?, Max?}`) used anywhere a factor value
must pass a bound instead of being summed - a `Requires` gate, a `Roll.Conditions` entry, a
`StationStep.Conditions` entry.

## The stat factor: any native stat works

RPG Stations registers one built-in, mod-agnostic factor id, **`hytale:stat`**. Its `Param` is any
native stat channel id, read straight off the player's live entity stat map - the same modifier-target
read the engine itself already performs:

```json
{ "Factor": "hytale:stat", "Param": "Mana" }
```

`Param` names a registered native `EntityStatType` - a vanilla one such as `Mana`, or any channel
another mod registers. Because this reads the NATIVE stat substrate directly, **any mod that writes a
native stat participates in loot formulas with zero bridge code** - RPG Stations never needs to know
that mod exists, and the `hytale:stat` factor has no allowlist. An id nothing registers cannot be
resolved at all: it contributes nothing to a summed `Factors` array, and it fails a `Conditions`
gate CLOSED, so a formula written for a mod that is not installed stays inert rather than springing
a gate open.

### Item-carried stat bonuses

`Zig_Entity_Stats` is a generic, mod-agnostic native item TAG (not a channel id) any item asset can
author, in the form `"<StatId>:<amount>"` (additive only). It applies an item's tagged stat bonus to
the same native stat channels the `hytale:stat` factor reads, while the item is held - so a tag-stat
tool and a Stamp-enhanced tool compose additively against one formula with no bespoke per-mod parser.

## Do not sum the same source twice

A mod that owns a family of stat channels often ALSO registers a convenience AGGREGATE factor - one id
that resolves to a documented weighted composition of those same channels. The rule is a hard
either-or per formula: **compose the underlying `hytale:stat` channels yourself, OR reference the
aggregate - never both in the same Roll.** Referencing both counts the same source twice, and because
both spellings are legitimate factor ids there is no way for this engine to tell them apart: the ids
differ, so nothing here can detect the overlap for you. It is an authoring discipline, and the factor
family's owner documents which aggregate maps to which channels.

A `LOOT_DUPLICATE_FACTOR` validator INFO catches the narrower, always-wrong case: the SAME
`(Factor, Param)` pair referenced more than once inside one Roll (across its `Conditions`,
`Chance.Factors`, and `Ladder.Factors`). Two `hytale:stat` references with DIFFERENT `Param`s are a
legitimate composition and never fire it:

```json
"Ladder": { "Factors": [
  { "Factor": "hytale:stat", "Param": "YourMod_Luck",       "Weight": 1.0 },
  { "Factor": "hytale:stat", "Param": "YourMod_Luck_Woods", "Weight": 1.0 }
], "Floors": [ /* ... */ ] }
```

## RPG Stations' own built-in factors

Independent of any other mod, RPG Stations registers its own session-scoped factors, plus a handful of
straight native-data reads under the `hytale:` namespace, so its jar-shipped standalone content stays
fully rewarding with nothing else installed: `rpgstations:session_seconds`, `rpgstations:cycle_count`
(cycles completed so far this session), `hytale:tool_power`, `hytale:tool_quality`,
`hytale:tool_item_level`, and `hytale:tool_durability_percent` - the tool-curve set the shipped Sawmill
lootable and its `ContributionScale` both roll against.

---

Previous: [Selection & Output Categories](selection.md) · Next: [Native Composition](native-composition.md)
