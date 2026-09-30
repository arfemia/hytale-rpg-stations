# Derive from Any Bench

A station that stands in for a vanilla bench: the same recipes the bench runs, performed in the
world instead of picked from a menu.

A vanilla bench is instant: open it, pick a recipe, take the result. A station is the same recipe
with a body around it: the material is placed on the block, a worker (or their double) performs the
work, the block lights and hums, and the result lands when the work is done. The engine gives you
the body; the recipes come from the game's own catalog, so a station that stands in for a bench
never restates a single recipe. It names the native category, or the native bench, and every recipe
the engine knows there becomes a row of its own.

Two shipped stations do exactly this. The Sawmill stands in for the Builders bench: three category
words derive 33 conversions across 11 wood species. The Disenchanting Table stands in for the
Salvage bench: it derives every one of vanilla's standalone salvage recipes, so a piece of gear
placed on it returns exactly what the Salvage bench would return, with the table's own payout on
top. This page is the recipe half of both; [Disenchanting](disenchanting.md) is the ritual half of
the second.

## Two routes into the native catalog

`Recipe.FromCrafting` reaches the native recipe index two ways, and a station may author both.

**By category.** A Crafting-type bench arranges its recipes in categories (the tabs the bench
shows), and an item's inline recipe carries the category it sits under. The Sawmill authors:

```json
"Recipe": {
  "FromCrafting": { "Categories": ["WoodPlanks", "DecorativePlanks", "OrnatePlanks"] },
  "Yield": { "Base": 1 }
}
```

**By bench.** A recipe that ships as its own file carries a `BenchRequirement` and often no category
at all; vanilla's salvage set is the large example (several hundred files, each "one piece in, its
materials out"). `Benches` names the native bench id:

```json
"Recipe": { "FromCrafting": { "Benches": ["Salvagebench"] } }
```

Vanilla's iron sword salvage recipe is one input and three outputs (two iron ore, a light hide, a
linen scrap). The derived row carries the WHOLE list at native quantities, each input on the route
the recipe itself used (an exact item id, a resource family, or a native item tag), so nothing about
the row is guessed. `Types` narrows either route to `Crafting` or `Processing` recipes; absent takes
both ([Native Composition](native-composition.md)).

A derived row runs at selection tier 1. An authored `Conversions` row runs at tier 0 and wins, so a
station (or a pack, through an `ExtensionAsset`'s `Conversions` payload) overrides any one piece by
writing that one row and leaves the rest derived. Another mod's gear that ships its own salvage
recipe is picked up with nothing written for it: the index covers standalone files and item-inline
recipes alike, from every loaded pack, and rebuilds when assets reload.

## What one cycle makes

`Recipe.Yield` decides quantity, and it composes with native quantities rather than replacing them:

- `Base` sets the quantity of the PRIMARY (first) output only; every other output of a multi-output
  row keeps its native count. A salvage row's hide and scrap are never flattened by a `Base`.
- `Scale` multiplies every output, floored to a whole item.
- `Min` / `Max` clamp every output.
- A cycle that consumed its inputs always produces at least one item.

The Sawmill authors `Base 1` because every plank recipe is one trunk to one plank. The Disenchanting
Table authors no `Yield` at all: vanilla's own return is the point. Everything conditional (a
windfall, a find, an extra on a lucky day) is a `Roll` in the action's `Bonus` group, never a yield
leaf ([Loot & Factors](loot-and-factors.md)).

## The tempo is the station's

A native recipe carries its own craft time, tuned for an instant menu. `FromCrafting.NativeTime`
(`Scale * recipeTimeMs + OffsetMs`) stretches it into a work cadence; an authored
`Conversion.DurationMs` beats it, and `Work.CycleMs` is the fallback when neither applies. A station
whose work is a `Steps` ritual takes its length from the beats instead, and can scale the elastic
ones with a `Pace` ladder ([Disenchanting](disenchanting.md)).

## The pieces no recipe covers

A bench stand-in meets pieces the bench never listed: a modded sword with no salvage file, a piece
whose only recipe is the one that made it. `Recipe.Fallback` gives a placed piece a route when no
row, authored or derived, covers it:

```json
"Recipe": {
  "FromCrafting": { "Benches": ["Salvagebench"], "Types": ["Processing"] },
  "Fallback": {
    "Input": { "Tags": { "Type": ["Weapon", "Armor", "Tool"] } },
    "CraftingShare": { "Share": 0.3 },
    "EssenceOnly": {}
  }
}
```

- `Input` scopes the routes: only a piece it matches is offered one (the same `ItemId` /
  `ResourceTypeId` / `Tags` / `Function` routes an action's other matchers use). Its `Except` can
  carve holes out of that scope; the Disenchanting Table leaves it out, because it refuses the
  same things one gate earlier, at placement, through its protect-list file (below), which also
  covers the salvage-derived rows for rocks and plants that this scope never reaches. A route never
  sees a piece that could not be placed, so the arrows, bombs and bait a wide family would
  otherwise sweep in are refused before any route is tried.
- `CraftingShare` gives back a share of the piece's OWN crafting recipe. Each exact-item line
  becomes `floor(Quantity x Share / OutputQuantity)`, `OutputQuantity` being how many pieces that
  recipe makes per craft, so a batch recipe pays per piece; a line that rounds to nothing is
  dropped, and a recipe with no line left does not take the route. Family and tag lines name no
  item and are dropped.
- `EssenceOnly` consumes the piece and produces nothing from the recipe: the action's `Bonus` rolls
  are its whole payout. It is the one row shape allowed an empty output.

A piece is offered ONE route, the first that applies, in a fixed order: authored row, derived row,
crafting share, essence only. A stack carrying metadata no mod declared disposable is refused
before any route is tried, so a bench stand-in never destroys another mod's data by accident. The
routes are custody-only: the piece has to be placed.

## What the station will not take

Three rules sit in front of every placement, and a bench stand-in leans on all three, since "every
weapon" is a wide door ([Custody & Placed Display](custody-and-placed-display.md)):

- `Custody.Input.Except` names what THIS station refuses: one matcher or an array of them. An
  `Input` that authors only `Except` keeps everything the recipe and the fallback routes derive
  and carves the holes out of that, so a table that takes every weapon but its own trophy authors
  one hole, never a list of every weapon. The Disenchanting Table's holes are its own pieces: the
  trophy by id and the kit by the `Disenchanter` family tag, on the lesser table's `Input` and on
  each of the greater table's three socket `Match`es (an authored socket reads only its own
  `Match`). A pack's extension overlay ADDS its holes beside the jar's.
- The protect-list (`Server/RpgStations/ProtectLists/`): every file in it counts, whichever layer
  ships it, each optionally scoped to certain stations or actions, and no station in scope may take
  what it protects. The jar ships one, `Disenchanting_Tables.json`, scoped to the two tables, for
  the non-gear the Salvage bench knows recipes for: rocks and plants by `Type`, ammunition, bait
  and deployables by `Family`, and the tagless repair kits, fertilizers and capture crate by id.
  One file serves both tables and every socket, which is why those holes live there and not on
  the matchers.
- A count pile (`MaxQuantity` above one) refuses a stack that tracks wear or carries metadata,
  because it could only hand it back as a bare fresh stack. A single-item socket keeps the real
  stack and takes it.

A hole or the protect-list answers `Refused:Protected`, its own reason with its own line, so the
player learns the station would have taken the piece but for the rule. At a station already
holding material the held item is judged as a tool instead, so a protected trophy tool still works
there.

## Telling the player before they commit

`Custody.Preview: true` shows, the moment a piece is placed, what it will give back: the row the
recipe would run for it right now, listed as count and name, or that it gives nothing back on its
own when only the essence route takes it. `Custody.HeldOnly: true` places only what the player
holds, never a match dug out of the backpack, which is the right default for a station that takes
one valuable piece at a time.

## The checklist

1. The block, the station file and the interaction, as in
   [Your First Station](your-first-station.md).
2. `Recipe.FromCrafting` with `Categories` or `Benches` (or both), and `Types` if only one kind.
3. `Yield` only when native quantities need scaling; otherwise leave it out.
4. `Fallback` when pieces beyond the recipe set should be accepted, scoped by its `Input`.
5. `Custody.Input.Except` for the holes, `HeldOnly` and `Preview` for a one-piece station.
6. `/rpgstations validate`: `FALLBACK_WITHOUT_CUSTODY`, `CUSTODY_PREVIEW_WITHOUT_RECIPE` and the
   `PROTECT_LIST_*` findings name the usual slips.

---

Previous: [Your First Station](your-first-station.md) · Next: [Disenchanting](disenchanting.md)
