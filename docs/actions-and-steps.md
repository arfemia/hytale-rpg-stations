# Actions & Step Programs

Phases, beats, Repeat, Duration, and the fixed step execution order.

## Actions

A station's `Actions` is an ORDERED array of `ActionDef` entries - the authored order IS the selection
priority, so the first entry whose `Select` matches the held or placed material runs. Each entry is
**self-contained**: `Id`, `Ref`, `Label`, `Select`, `Requires`, `Tool`, `Recipe`, `Work`, `Custody`,
`Anchors`, `Steps`, `Bonus`, `ContributionScale`, `Worker` (`Hold`/`Camera`/`Animation`/`Puppet`), and
`Moments` (`Cycle`/`Completion`). Nothing here is inherited from the station or from a sibling action -
a station-level `Requires` gate is the only exception, and even that ANDs with the action's own rather
than supplying it.

The Anvil's two actions - `Convert` (sharpen a bar) and `Enhance` (roll stats onto a placed weapon) -
each fully author their own groups, including their own `Tool`/`Worker`:

```json
"Actions": [
  {
    "Id": "Convert",
    "Select": { "ResourceTypeId": "Metal_Bars" },
    "Custody": { "MaxQuantity": 100, "States": { "Empty": "Default", "Loaded": "BarsPlaced" } },
    "Work": { "CycleMs": 3800,
              "PerCycleContributions": [ { "Channel": "yourmod:craft_quality", "Param": "IRON", "Amount": 6.0 } ] },
    "Recipe": { "Conversions": [ /* ...one entry per metal... */ ] }
  },
  {
    "Id": "Enhance",
    "Select": { "Function": "Weapon" },
    "Custody": { "MaxQuantity": 1, "Input": { "Function": "Weapon" },
                 "States": { "Empty": "Default", "Loaded": "WeaponPlaced" } },
    "Work": { "CycleMs": 600, "Looping": false,
              "PerCycleContributions": [ { "Channel": "yourmod:craft_quality", "Param": "IRON", "Amount": 25.0 } ] },
    "Steps": [ /* the ritual - see below */ ]
  }
]
```

A field authored twice across sibling actions (like `Tool` above, omitted from both because neither
needs a tool gate) is never shared between them - repeat it on every action that needs it. There is no
station-level group to fall back to.

### Diegetic selection

Which action a session runs is picked by **what the player is holding** (or by a loaded custody claim,
which always commits to its own action rather than being re-selected), IN AUTHORED ORDER. Each action's
`Select` is an `ActionInput` matcher: `ItemId`, `ResourceTypeId`, native item `Tags`, or a
**functional** route - `Function: "Weapon"|"Armor"|"Tool"`, tested against the held item's live shape. A
match is ANY route satisfied; an ABSENT `Select` matches any context (its custody acceptance derives
from its own `Recipe` inputs instead) - the validator flags an unreachable catch-all authored before a
more specific action.

### Standalone actions and Ref

An action can also live in its own reusable file, `Server/RpgStations/Actions/<Name>.json` (an
`ActionAsset`, id = lowercased filename) - the EXACT same field set an inline action body carries. A
station attaches it by naming it on `Ref`:

```json
"Actions": [ { "Id": "Prep", "Ref": "PrepFish" } ]
```

Any OTHER group authored alongside `Ref` on that same entry overlays the referenced action group-wise
(referenced action -> inline overlay, whole-group replace). A dangling `Ref` is a validator finding and
denies the engage gracefully rather than throwing. Native `Parent` BETWEEN `ActionAsset`s is the
"author only the delta" reuse route between two standalone actions - a different axis from `Ref`, which
is the per-station *attachment* route.

## The implicit program

An action with no authored `Steps` gets the classic convert loop for free: one implicit step running
Consume -> Produce -> Roll -> Presentation. This is exactly what every plain single-purpose station (a
Sawmill authoring one `Mill` action and no `Steps` on it) runs, and it is byte-identical behavior to a
hand-authored one-step program.

## The step record

An authored program is an ordered array of `StationStep`s. Every field on a step is nullable; a step
composes any combination of independent **phase groups** plus a handful of base fields that apply to
every step regardless of which phases it authors.

| Field | What it does |
|---|---|
| `Id` | Unique within one action's `Steps`; required whenever another step or an extension insertion anchors on it. |
| `Conditions` / `OnConditionFail` | A gate re-checked at each iteration entry. A failing check either `Result:"Skip"`s (no-op, continue) or `Result:"Fail"`s (default); `Goto` jumps to another step's Id on a passing/skipping result. |
| `Repeat` | A fixed `Times`, or a factor-resolved `{Min,Max,Factors}` range, resolved once at step entry. |
| `Duration` | `{Ms}` - a post-phase hold; the puppet's prop/clip and any presentation persist across the hold. |
| `Puppet` | A per-step `{Clip?, Prop?}` override for the moment-to-moment animation/held item, played once at iteration entry. |
| `Presentation` | A sound/particle/etc. cue played once at iteration entry. |
| `Walk` | Move the puppet to a named anchor. See [Multi-Station Programs](multi-station-programs.md). |
| `Consume` | `{Items: [Ingredient...], From: Inventory\|Custody}` - drain from the player's backpack or the block's placed-input claim; all-or-nothing across the whole `Items` list. |
| `Stamp` | The enhance-commit phase (reagents, durability, stat rolls). See [Enhancement & Stamp](enhancement-and-stamp.md). |
| `Produce` | `{Items: [Ingredient...], To: Inventory\|Custody}` - grant to the backpack, or deposit into a custody claim (the primary station's or a claimed anchor's). |
| `Roll` | A `LootRef` - the same weighted loot vocabulary an action's own `Bonus` group uses. See [Loot & Factors](loot-and-factors.md). |
| `Commands` | Console commands run with the usual placeholder substitutions. |
| `IsWork` | Does this step count as WORK at its `At`-anchor block (driving that block's `Custody.States.Working` look)? Defaults to true for a `Consume`+`Produce` convert step, false otherwise - author it explicitly true on a pure beat that IS the work. |

### Execution order

Every step iteration runs the SAME fixed order, regardless of which phases it authors:

```
Conditions gate -> Walk -> Consume -> Stamp -> Produce -> Roll -> Commands
  -> Presentation / Puppet clip (fire at iteration entry)
  -> Duration hold (suspend)
  -> next iteration or next step
```

A step combining `Consume` and `Produce` in the same step is an **atomic transform** - there is no
window where inputs are consumed but nothing has been produced yet. When a program deliberately needs
to split consuming and producing across a delay or a walk (the fish exemplar splits "place raw fish"
from "harvest cooked fish" across a 2.5-second cook), the engine's iteration refund ledger covers the
gap: an iteration that consumed inputs but stopped before its matching `Produce` committed (session
stopped mid-walk, damage interrupt, disconnect) refunds that iteration's consumed tally when the
session stops.

### Pure beats

A step authoring NO phase group at all is a pure **beat** - just a hold, a clip, and a presentation
cue. The Anvil's hammer-strike ritual is three beats followed by the stamp:

```json
"Steps": [
  { "Id": "strike1", "Duration": { "Ms": 650 }, "Puppet": { "Clip": "RPG_Emote_Saw" },
    "Presentation": { "Sounds": ["SFX_Metal_Hit"], "Particles": [ { "SystemId": "Block_Gem_Sparks" } ] } },
  { "Id": "strike2", "Duration": { "Ms": 650 }, "Puppet": { "Clip": "RPG_Emote_Saw" },
    "Presentation": { "Sounds": ["SFX_Metal_Hit"], "Particles": [ { "SystemId": "Block_Gem_Sparks" } ] } },
  { "Id": "settle",  "Duration": { "Ms": 900 } },
  { "Id": "stamp",   "Duration": { "Ms": 800 }, "Puppet": { "Prop": { "Source": "None" } },
    "Stamp": { /* ...see Enhancement & Stamp... */ },
    "Presentation": { "Sounds": ["SFX_Chest_Legendary_FirstOpen_Player"], "Particles": [ { "SystemId": "Block_Gem_Sparks" } ] } }
]
```

The `stamp` step's own `Puppet.Prop.Source: "None"` empties the puppet's hands for the enchant-flourish
beat (it authors a Prop override but no Clip, so no hammer swing fires on it), and its 800ms `Duration`
is the post-stamp flourish hold - the ritual visibly pauses with empty hands after the stat roll
commits, before the session completes.

### The Consume-inside-Repeat footgun

A step that both `Consume`s AND authors a `Repeat` count re-runs its Consume phase on **every**
iteration - a step meant to consume ONE input then perform several repeated beats over it will
over-drain the source. Split it: a one-shot step with `Consume` and no `Repeat`, followed by a
separate pure-beat step with `Repeat` and no `Consume`:

```json
{ "Id": "Load",  "Consume": { "Items": [{ "ResourceTypeId": "Fish", "Quantity": 1 }], "From": "Custody" } },
{ "Id": "Scale", "Repeat": { "Times": 3 }, "Duration": { "Ms": 600 },
  "Puppet": { "Clip": "RPG_Emote_Knife" }, "Presentation": { "Sounds": ["SFX_Daggers_T1_Slash_Impact"] } }
```

This is the exact shape the fish-preparation exemplar uses (three knife scrapes over one loaded fish,
not three consumed fish) - see [Multi-Station Programs](multi-station-programs.md) for the full
program.

### Repeat-while-inputs

There is no separate "repeat while inputs remain" flag. A repeating action (`Work.Looping: true`, the
default) whose `Consume` phase finds insufficient inputs at its source simply ends the session
gracefully once none remain - the session summary shows the completed totals, and custody auto-returns
everywhere. A **non-repeating** program (`Work.Looping: false`, the Anvil's `Enhance` ritual shape)
completes the whole session after one program run instead of looping, and gets an instant first
dispatch - no cycle-cadence latency eaten before its one and only cycle.

---

Previous: [Your First Station](your-first-station.md) · Next: [Multi-Station Programs](multi-station-programs.md)
