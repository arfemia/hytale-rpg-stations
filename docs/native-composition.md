# Native Composition

The id-ref-only principle: RPG Stations references native Hytale content, never inlines it.

RPG Stations leans on the native Hytale asset catalog wherever it can, instead of re-implementing a
parallel system: interactions, entity effects, drop tables, crafting recipes, item tags, emotes,
sounds, and particles are all referenced **by id only**. A leaf that composes with native content NEVER
inlines the native asset's own body - it holds a string id (and sometimes a small override like a
duration), and the engine resolves that id against the live asset catalog at the moment it is needed.

The payoff: any native content a server owner or another pack already ships (a custom `EntityEffect`,
a modded weapon family, a new drop table) is immediately usable from RPG Stations content with zero new
Java code - and an unresolvable id degrades gracefully (a warning at content audit time, a silent no-op
at runtime) rather than crashing a session.

## Presentation.Interaction - firing a native interaction chain

```json
{ "Interaction": { "Id": "SomeRootInteractionId" } }
```

Any moment that carries a `Presentation` (a station's per-cycle moment, a step's entry cue, a Roll's
own celebration or a reached ladder floor's, a flair overlay) can also fire a native `RootInteraction`
chain by id - a one-leaf reference, never an inlined interaction body. An unresolvable id is a
content-audit note (typo detection) and a no-op at fire time.

## Presentation.Effect and Roll.Grants.Effects - native EntityEffects

```json
"Presentation": { "Effect": { "Id": "SomeEntityEffect", "DurationMs": 3000 } }
"Grants": { "Effects": [ { "Id": "SomeEntityEffect" } ] }
```

The same `{Id, DurationMs?}` reference shape applies a native `EntityEffect` asset by id in two places:
a single per-moment effect on a `Presentation`, or an array of reward-time effects on a Roll's
`Grants`. `DurationMs` is an OPTIONAL override - omit it and the effect runs for whatever duration the
referenced effect asset itself declares.

Teardown follows the trigger that granted the effect, deliberately. An effect applied while the
session is running - a per-moment `Presentation` effect, or a `Cycle`-trigger Roll's grant - is
tracked and removed automatically when the session stops, so nothing lingers on a worker who walked
away. A `Completion`-trigger Roll's grant is applied from inside that same stop, after teardown has
already run, and therefore PERSISTS for its own authored (or asset-declared) duration: it is a
finishing reward for the work, not part of the work.

## Grants.DropLists - native item drop tables

```json
"Grants": { "DropLists": ["RPG_Station_Sawmill_T1", "RPG_Station_Sawmill_T2"] }
```

A Roll's (or a Ladder floor's) `DropLists` names one or more native `ItemDropList` asset ids, each
rolled independently in authored order through the engine's own drop-list roller - the exact same
mechanism a mob loot table or a chest uses (a guaranteed common table plus a rare one is two entries).
Author drop tables the ordinary Hytale way and reference them here; RPG Stations never defines its own
drop-table format.

## Recipe.FromCrafting - deriving from native recipes

```json
"Recipe": { "FromCrafting": {
  "Categories": ["WoodPlanks"],
  "Benches": ["Campfire"],
  "Types": ["Processing"],
  "NativeTime": { "Scale": 1.0, "OffsetMs": 500 }
} }
```

Instead of hand-listing every conversion, a station can derive its convert recipe straight from the
engine's own crafting/processing recipe catalog:

- `Categories` - native recipe categories to pull from (e.g. every wood species that shares the
  `WoodPlanks` category derives its own conversion, with zero per-species authoring). The route is
  bench-type-agnostic: a Crafting-type bench's category rows (the Cookingbench's `Prepared` /
  `Baked` / `Ingredients` tabs) derive exactly like a Processing bench's.
- `Benches` - native `BenchRequirement` bench ids to scope to (a second derivation route for recipes
  that carry a bench requirement but no category at all).
- `Types` - which recipe kinds to derive, `Crafting` and/or `Processing`; absent means both.

A derived conversion carries the native recipe's WHOLE input list, each entry on the route the
recipe itself used: an exact `ItemId`, a `ResourceTypeId` family, or a native `ItemTag` (which
derives onto the `Ingredient.Tags` route, so a "any item tagged CookingIngredient" recipe works at
a station too). Derived rows run at selection tier 1, so any hand-authored row outranks the whole
derived block by default (see [Recipe rows](actions-and-steps.md)).

A hand-authored `Recipe.Conversions` entry can also carry an optional `Category` tag directly - a
derived conversion is stamped with its source category automatically, which is what powers the
multi-output picker (see [Selection & Output Categories](selection.md)).

### NativeTime: native recipes drive WHAT, a station owns the PACE

A native recipe carries its own instant (or near-instant) craft time - that is fine for a vanilla
bench, but a diegetic work loop is supposed to take longer; the pacing IS the value a station adds.
`FromCrafting.NativeTime` is a linear transform (`Scale * recipeTimeMs + OffsetMs`) applied over a
derived recipe's own native time, with defaults that deliberately keep a station slower than vanilla
even when authored as an empty `{}` group. Per-cycle time resolution follows a fixed precedence: an
authored `Conversion.DurationMs` (highest) beats the `NativeTime` transform, which beats the station's
own flat `Work.CycleMs` (lowest, the fallback when neither of the others applies).

## Tool.Tags and Custody.Input.Tags - native tag-family matching

```json
"Tool": { "Tags": { "Family": ["Dagger"] } }
```

A gate can match the held item's native tags directly - an ANY-of match per key against the item's own
raw tag data. Because most vanilla item families share tags through their native parent asset (every
dagger tier inherits `Tags: {Type:["Weapon"], Family:["Dagger"]}` from a shared template), one
`Tags.Family` reference matches every tier of that family with **zero per-tier enumeration** - and a
future new tier of the same family just works, with no content update needed here at all. This is the
preferred route over an `Ids` list whenever the target items share a native tag; `Ids` stays as the
fallback for items (often modded) that carry no shared tag to match on, as the Anvil's hammer gate
does.

## Worker.Animation.EmoteId and Worker.Puppet.Prop - native emotes and items

An action's work animation (`Worker.Animation.EmoteId`) and a puppet's held prop (`Prop.ItemId`) are
both plain native asset ids - an emote authored the ordinary Hytale way, or any item id in the catalog.
There is no RPG Stations-specific emote or item format.

## Sounds and Particles - native audio/VFX assets

Every `Presentation`'s `Sounds` (an array of one-shot sounds, played in authored order - never a
looping event, since nothing can stop one once fired) and `Particles` (an array of `{SystemId, ...}`
bursts) are native asset ids too - the shipped content reuses already-existing vanilla ids wherever one
fits (the fish exemplar's knife scrape reuses the vanilla dagger swing-impact sound; the Anvil reuses
the vanilla metal-hit sound and gem-sparks particle) rather than authoring new audio/VFX assets for a
mechanic that already has a natural-sounding native match.

A `Sounds` entry is normally just the id string. Authoring it as `{"EventId": "...", "DelayMs": 140}`
instead holds that ONE sound behind the rest of the moment - useful for staggering a thud and a chime
inside a single cue. Volume and pitch are deliberately not authorable: the engine's positional
one-shot call takes neither, so either leaf would decode and then do nothing. Vary them by
referencing a different `SoundEvent` asset, which is where they live.

A `Presentation` also carries `DelayMs`, which is not a cue but a timing knob over the whole group:
it holds every sound, particle, shake, interaction and effect in that moment together for the given
number of milliseconds before they play. A per-sound `DelayMs` ADDS to it. Reach for it when a cue
reads as slightly early - the jar's Sawmill holds its cycle break sound by `100` so the plank lands
just after the cut looks finished, and holds its whole `impact` moment by `140` so the wood-hit
lands part-way through the swing.
Omit it (or author zero) to play at once. Playback resolves on the server tick, so the cue fires on
the first tick at or after the delay, never before it.

Keep a delay well under the window it plays inside - a cycle cue held for a whole `Work.CycleMs`, or
a step cue held for its step's whole `Duration.Ms`, lands after the next cycle or the next step has
already taken over, and `/rpgstations validate` warns about both. One thing to watch when a flair
re-skins a delayed moment: a flair that omits `DelayMs` inherits the base moment's timing, so a
flair that wants its cue at once has to author `DelayMs: 0` rather than leave the leaf out.

## The principle, stated plainly

Every reference leaf above shares one shape: **a string id (plus, occasionally, a small override),
never an inlined native asset body.** This keeps RPG Stations content forward-compatible with anything
a server adds to the native catalog later, keeps authoring DRY (one native asset, referenced from as
many stations as want it), and keeps every native-reference site content-audited the same way - an
unresolvable id is always a warn-only finding, never a hard failure.

---

Previous: [Loot & Factors](loot-and-factors.md) · Next: [Enhancement & Stamp](enhancement-and-stamp.md)
