# Selection & Output Categories

Sneak+F routing: the multi-output picker and the plain-F default.

A plain `F` press on a station block always toggles the diegetic work loop. A **sneak (crouch) + F**
press instead opens the multi-output picker, when one applies - the SAME crouch flag the engine already
reads as the diegetic crouch-to-exit signal, read fresh at the moment of the press.

## The routing decision

The routing is a small, pure decision tree, in priority order:

1. Not sneaking -> always the plain work toggle.
2. Sneaking, and the resolved action derives 2 or more distinct output CATEGORIES, or carries 2 or
   more hand-authored `Recipe.Conversions` rows -> open the multi-output PICKER.
3. Sneaking, otherwise -> falls through to the plain work toggle (a single-category, single-recipe
   action never shows anything for sneak+F).

Sneak+F is picker-or-toggle everywhere - there is no separate native-crafting-window route; every
station's diegetic work loop stays its block's primary interaction.

## The multi-output picker

When an action's effective conversions span more than one distinct source CATEGORY, sneak+F opens a
picker page instead: a vertical list, one full-width card per category, each showing the category's
representative output item as its icon plus its name and an input-to-output cost line (e.g. "1x Trunk
-> 4x Planks") - a category id has no display name of its own, so the card is built from one real
conversion, never a hand-authored per-category label. Selecting a card commits the session to that
category's conversions only.

The Sawmill is the flagship multi-category exemplar: its `Recipe.FromCrafting.Categories` spans
`WoodPlanks`, `DecorativePlanks`, and `OrnatePlanks` - one held log, three genuinely different valid
outputs. Each derived conversion is stamped with its source category automatically (the same
native-recipe-composition machinery in [Native Composition](native-composition.md) that derives the
conversions in the first place), so no per-conversion category authoring is needed beyond widening the
`Categories` list.

A conversion can also be tagged explicitly by hand-authoring its own `Category` field, for an action
whose conversions are not native-derived.

### Hand-authored recipe rows in the picker

An action carrying 2 or more hand-authored `Recipe.Conversions` rows (a set-recipe station: the
exact kebab beside the anything-goes stew) opens the picker too, with one card PER AUTHORED ROW,
listed first, each labeled and iconed by that row's own output item and showing its input-to-output
cost line. Derived conversions keep the category rule above - a station deriving 33 species still
shows three category cards, never 33 rows. Selecting an authored row commits the next session to
exactly that recipe; each row's preview draws from the custody pile its own first input addresses
(its `Socket`, else the first Item socket), so a multi-socket station's cards describe the material
actually placed where each recipe looks.

## The plain-F default: first-authored priority

Once a station has more than one category, plain `F` (no sneak, no explicit picker choice) needs SOME
default. The rule: with no explicit choice recorded, a multi-category station defaults to the FIRST
entry in its authored `FromCrafting.Categories` array that actually produced at least one derived
conversion - authors control the plain-F default purely by ORDERING that array, never by a separate
default flag. For a hand-authored (non-derived) multi-category station, the default falls to the first
category present among its own conversions.

On the Sawmill, `Categories: ["WoodPlanks", "DecorativePlanks", "OrnatePlanks"]` means plain `F` always
defaults to plain planks, exactly matching the station's behavior before it ever grew a picker -
reordering the array is the one authoring lever to change the default; sneak+F still offers all three
regardless of the array order.

### The pending-selection flow

An EXPLICIT choice made through the picker always wins verbatim over the first-authored default, for
that next session only - picking a category, then pressing plain `F`, starts a session filtered to
exactly that category's conversions. There is no persisted "remembered" choice across sessions; each
fresh engage without an explicit picker pick falls back to the first-authored default again.

---

Previous: [Puppet & Performers](puppet-presentation.md) · Next: [Loot & Factors](loot-and-factors.md)
