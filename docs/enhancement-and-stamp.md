# Enhancement & Stamp

The Stamp step: reagents, durability, composable stat rolls, and the Budgets cap model.

`Stamp` is a step phase that rolls stats onto a placed item, consumes reagents, and optionally raises
durability - the mechanism behind the Anvil's Enhance ritual. It runs as ONE transaction: every roll
and every check is validated with zero mutation first, and only committed once everything is known to
succeed (compute-then-commit) - a Stamp step never leaves an item or an inventory half-changed.

## The shape

```json
"Stamp": {
  "Reagents": [ { "ResourceTypeId": "Metal_Bars", "Quantity": 2 } ],
  "Durability": { "AddMax": 10 },
  "Stats": {
    "Pool": "anvilweaponpool",
    "Picks": { "Min": 1, "Max": 2 },
    "Unique": true,
    "Caps": {
      "Budgets": [
        { "Points": 30 },
        { "PointsPer": 0.5, "Factors": [ { "Factor": "yourmod:skill_level", "Param": "SMITHING" } ] }
      ],
      "PerStat": { "YourMod_CritChance": 10 }
    }
  }
}
```

- `Reagents` - `Ingredient`s consumed from the player's INVENTORY at commit (never from a custody
  claim - the placed item being enhanced is what lives in custody).
- `Durability.AddMax` - raises the placed stack's max durability (and its current durability by the
  same amount) - a genuine upgrade, real with no other mod installed at all.
- `Stats` - the composable stat-roll model, below.

## Stat roll entries

A candidate roll is a `StatRollEntry`: `{Stat, Points{Min,Max,Factors?}, Weight, Always}`. `Stat`
is opaque to RPG Stations itself - the registered stamper interprets what a "stat" id means. The
rolled point value is `uniform(Min, Max)`, optionally plus a weighted `Factors` sum (the same
[factor-term](loot-and-factors.md) vocabulary loot chances use). An entry with `Always: true` is granted
unconditionally on every stamp, independent of the weighted pool - so one Stamp can mix a guaranteed
baseline stat with weighted bonus rolls.

Entries come from EITHER a reusable, shared `Server/ZiggfreedCommon/RollPools/<Name>.json` table
(referenced via `Stats.Pool`) OR inline `Stats.Entries`, or both at once - both authoring routes share
the exact same entry shape, so an engine change never has to special-case which route produced an
entry.

```json
// RollPools/AnvilWeaponPool.json
{ "Entries": [
  { "Stat": "YourMod_CritChance",      "Points": { "Min": 2, "Max": 6 },  "Weight": 1 },
  { "Stat": "YourMod_CritMultiplier",  "Points": { "Min": 5, "Max": 15 }, "Weight": 1 },
  { "Stat": "YourMod_Lifesteal",       "Points": { "Min": 2, "Max": 5 },  "Weight": 1 },
  { "Stat": "YourMod_CooldownReduction","Points": { "Min": 2, "Max": 6 }, "Weight": 1 },
  { "Stat": "YourMod_Luck",            "Points": { "Min": 3, "Max": 10 }, "Weight": 1 },
  { "Stat": "Mana",                    "Points": { "Min": 5, "Max": 15 }, "Weight": 1 }
] }
```

## Picks and Unique

`Picks: {Min, Max}` controls how many weighted-pool entries a single Stamp attempt picks (`Always`
entries are extra, not counted toward this range). `Unique: true` means a stat id is never picked
twice in the same stamp.

## Caps: the Budgets model

Rolled points are clamped by `Caps`, which composes three independent, orthogonal controls:

| Field | What it does |
|---|---|
| `Budgets` | A list of total-point-budget entries. Each entry is EITHER a flat `{Points}` OR a factor-scaled `{PointsPer, Factors}` (effective = `PointsPer * sum(resolve(f) * f.Weight)`) - never both on the same entry. **The EFFECTIVE total budget is the MINIMUM across every authored Budget entry.** |
| `PerStat` | A per-stat-id ceiling layered ON TOP of the total budget (`{"YourMod_CritChance": 10}` caps that one stat at 10 points regardless of the total budget available). |
| `Economics` | `{RepeatCostMultiplier}` scales REAGENT cost per prior stamp count on the same item (`ceil(baseQuantity * (1 + mult * priorStampCount))`) - this affects cost only, never the point budget. |

The Anvil's two `Budgets` entries compose the flat-plus-scaled pattern directly: a flat 30-point
ceiling, AND a budget scaled off a registered factor (`0.5` points per unit of whatever
`yourmod:skill_level` resolves to). The minimum of the two wins - so while the factor is low the scaled
budget is the binding constraint, and past a certain point the flat 30-point ceiling takes over. This
is the same either-of-flat-or-scaled shape a factor-based budget always follows: author multiple
`Budgets` entries to express "never above X, but also never above Y that scales with something".

## Compute-then-commit

A Stamp step validates the ENTIRE outcome (the roll, the cap clamp, reagent availability, and room for
the enhanced item to return) with zero mutation. Only once every check passes does it commit: reagents
are consumed and the item is mutated, each under its own guard that restores exactly what was consumed
if anything fails partway. This means a Stamp attempt either fully succeeds or leaves the player's
reagents and the placed item completely untouched - never a half-consumed, half-rolled state.

A Stamp step is typically the last step in a non-repeating ritual (`Work.Looping: false`, see
[Actions & Step Programs](actions-and-steps.md)), followed by a `Duration` hold so the flourish plays
out before the session completes - see the Anvil exemplar on that page.

---

Previous: [Native Composition](native-composition.md) · Next: [Flairs](flairs.md)
