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
  "Grants":    { "OutputItems": 1 }
}
```

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
  floor's own `Grants` fires (a floor above a factor's "normal" range stays reachable via a
  multi-source stack).
- `Grants` - the reward vocabulary, below. Top-level `Grants` AND the reached floor's own `Grants` both
  apply when a Ladder is present.

## Grants: the reward vocabulary

| Field | What it grants |
|---|---|
| `OutputItems` | N ADDITIVE items of THIS cycle's own primary output, on top of the deterministic `Yield` quantity - additive, never a multiplier, so the two numbers stay directly comparable. Only meaningful on a `Cycle` trigger (the validator warns otherwise, and the engine drops it); silently skipped if the inventory is full. |
| `DropLists` | Native `ItemDropList` asset ids, each rolled independently in authored order through the engine's own drop-list roller. See [Native Composition](native-composition.md). |
| `Commands` | Console commands, with `{player}`/`{uuid}`/`{station}`/`{action}`/`{cycles}` placeholders substituted. |
| `Effects` | Native EntityEffects applied to the player, id-ref-only. See [Native Composition](native-composition.md). |
| `Contributions` | One-shot amounts posted verbatim when this roll grants - `Cycle` trigger only, and deliberately UNSCALED (never inherits the idle fraction or the action's `ContributionScale`). See [Extension Channels](extension-channels.md). |

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
that mod exists, and the `hytale:stat` factor has no allowlist. An id nothing registers resolves to 0
(fail-closed), so a formula written for a mod that is not installed just contributes nothing.

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
