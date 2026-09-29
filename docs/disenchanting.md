# Disenchanting

The Disenchanting Table, end to end: one long ritual per piece of gear, in two tiers, built from the
leaves any ritual can use.

The Disenchanting Table stands in for the Salvage bench the way the Sawmill stands in for the
Builders bench ([Derive from Any Bench](derive-from-any-bench.md)). Place a piece of gear on it,
press `F`, and the worker's double opens a grimoire over the piece while the table wakes, draws the
piece apart over a long, staged performance, and hands back what the Salvage bench would have,
plus essence and the occasional find. There is no intermediate item and no second station: the
Greater Disenchanting Table is the same ritual on a wider block, with three pieces queued side by
side and a richer payout.

This page reads the ritual from the outside in: the custody that takes the piece, the seven beats,
the two presentation layers, the beat that unmakes, the payout, and what other mods see. Every leaf
it names is generic; the table is the worked example.

## One station, two tiers

The jar ships two blocks and two station ids, `Disenchanting_Table` and
`Disenchanting_Table_Greater`, each block carrying `Default`, `Loaded`, `Working` and `Drawing`
interaction states. The ritual lives once, in a standalone `Actions/Disenchant.json` that both
stations `Ref`; the greater tier's `Disenchant_Greater` is `"Parent": "Disenchant"` and overrides
only what pays more and what looks deeper. Two transforms would be two actions; one transform in
two tiers is one action and a parent chain.

## Custody: one piece at a time

```json
"Custody": {
  "MaxQuantity": 1,
  "HeldOnly": true,
  "Preview": true,
  "Input": { "Except": { "ItemId": "RPG_Weapon_Spellbook_Grimoire_Disenchanter" } },
  "States": { "Empty": "Default", "Loaded": "Loaded", "Working": "Working" },
  "Display": { "Offset": { "Y": 1.05 }, "Scale": 0.6, "Animated": true }
}
```

- `MaxQuantity: 1` keeps the REAL stack: its wear and its stamped stats travel into custody intact,
  come back intact if the ritual is interrupted, and are what the factors read. A count pile would
  refuse such a piece.
- `HeldOnly` places what the player holds and never searches the backpack for a match: a table that
  takes one valuable piece must never pick one the player did not choose.
- `Preview` tells the player, as the piece lands, what it will give back, or that it gives nothing
  back on its own when only the essence route takes it.
- `Input` authors only `Except`: acceptance stays whatever the recipe and the fallback routes
  derive (every piece the Salvage bench knows, and whatever the fallback's own `Input` scopes),
  minus the hole. The table's own trophy is the hole. A pack's extension overlay adds its own holes
  beside this one.
- `Display.Animated` shows the placed piece turning and bobbing the way a dropped item does.

The greater table authors three single-item sockets instead of the implicit one, and
`Work.Queue: true`: the ritual runs once per filled socket, in authored order, each run reading and
consuming that socket's piece and playing its beats at that socket's prop. The session ends when
every socket is empty; an interrupted run refunds only the socket in progress.

## The seven beats

`Work.Looping: false` makes the program a ritual: one run, then the session completes. The table's
program is seven beats, four of fixed length and three that stretch with the worker's pace:

| Beat | Length | The double | The bed | Accents |
|---|---|---|---|---|
| Open | 2.0 s, fixed | opens the book | `Working` | a page-turn charge; a sigil rising from the piece |
| Kindle | elastic, 8 s untrained | raises the book | `Working` | a swell; motes on delays; tinted sparks at the piece |
| Draw | elastic, 12 s | holds the channel | `Drawing`; the piece lifts and turns | a gather on the piece; a chime |
| Surge | elastic, 9 s | winds up | `Drawing` | a beam shell; a layered charge; a light shake, delayed |
| Unmake | 1.5 s, fixed | releases | back to `Working` | the `Convert`; a shatter, a flash, a hard shake |
| Payout | 2.5 s, fixed | eases | `Working` | a bloom; the find cues about 450 ms in |
| Settle | 3.0 s, fixed | a hand on the heart | `Working` | motes; one last mote sound |

One beat, in full:

```json
{
  "Id": "Draw", "Paced": true, "IsWork": true, "State": "Drawing",
  "Duration": { "Ms": 12000 },
  "Puppet": { "Clip": "RPG_Emote_Disenchant_Channel" },
  "Display": { "Offset": { "Y": 1.4 }, "Rotation": { "Y": 45 }, "Animated": true },
  "Presentation": {
    "Target": "Display",
    "Sounds": ["SFX_MemoryRestored"],
    "Particles": [ { "SystemId": "RPG_Disenchant_Draw", "DurationSeconds": 6 } ]
  }
}
```

- `Paced: true` marks an elastic beat: the action's `Pace` multiplies its `Duration.Ms`, and the
  `DelayMs` and every burst's `DurationSeconds` of its own presentation, so its accents keep their
  place inside the beat. A beat without it holds its authored length at any speed, which keeps the
  gather, the flash and the linger crisp.
- `IsWork: true` on every beat keeps the table lit between beats. A pure beat (no `Convert`, no
  `Consume` plus `Produce`) would otherwise not count as work, and the block would fall back to its
  `Loaded` look.
- `State` holds the block in a deeper state than `Custody.States.Working` for this beat: `Drawing`
  from the Draw on, `Working` again at the Unmake. Two beats in a row naming the same state keep it
  without a flicker; a beat naming a different one re-flips in place.
- `Display` overlays the placed piece's prop for this beat only, the leaves it authors replacing the
  socket's own: here the piece lifts and turns. The socket's own look comes back after.

### Pace

```json
"Pace": {
  "Ladder": {
    "Factors": [ { "Factor": "hytale:stat", "Param": "RPG_Disenchanting_Proficiency" } ],
    "Floors": [ { "Min": 0, "Scale": 1.0 }, { "Min": 25, "Scale": 0.7 },
                { "Min": 50, "Scale": 0.45 } ]
  },
  "Clamp": { "Min": 0.375, "Max": 1.0 }
}
```

`Pace` is the `ContributionScale` ladder shape ([Loot & Factors](loot-and-factors.md)): the factors
are summed, the highest floor reached wins, and its scale multiplies every `Paced` beat. The jar's
ladder reads a native entity stat, `RPG_Disenchanting_Proficiency`, which the table's own kit and
grimoires carry through ordinary item `StatModifiers`, so a better-equipped worker finishes sooner
with nothing else installed: forty seconds untrained, fifteen at the clamp. An extension adds its
own `Pace` ladder from its own factors, and the ladders MULTIPLY; only the action's `Clamp` bounds
the product. The pace is resolved once at each beat's entry, so a stat change mid-beat lands on the
next.

## Two layers: the bed and the accents

Nothing in the protocol can stop a sound or a particle system once it is playing. The ritual splits
its presentation along that line.

**The bed lives on the block state.** A `State.Definitions` entry on the block carries a looping
ambient sound, particles (each attachable to a named part of the block model), the light, and a lit
texture with a model animation, and ALL of it starts and stops with the state flip. The lesser
table's `Working` is the vanilla Salvage bench's own processing look, arms and door animating, with
an ember effect on its fire part and a green-gold light; `Drawing` brightens the light and thickens
the sparks. The greater table's `Working` is the vanilla Arcane table's idle animation (book, gem
and candle bobbing) under a hum that ducks the music; `Drawing` adds violet motes. The step's
`State` leaf is the whole mechanism: name the state, and the block does the rest.

**The accents are one-shots.** A step's `Presentation` fires one-shot sounds (always positional) and
bounded particle bursts. `DurationSeconds` caps a burst that would otherwise never end; a system
that ends on its own needs no cap. `Color` tints a vanilla system to the beat's palette through the
engine's own colour argument, so the green-gold sparks are vanilla's `Block_Gem_Sparks` with one
leaf, not a copied system.

**Where a cue plays** is `Presentation.Target`:

- `Block` (the default): the station block's centre.
- `Display`: the placed piece's prop, the socket the queue is working. Particles attach to the prop
  entity and sounds follow it, delivered only to the players who can see it. A `Convert` beat
  drops the prop before its own entry cues, so a `Display` target on that beat resolves to the
  socket's resting position: the shatter plays where the piece was.
- `Puppet`: the worker's double. `{ "Kind": "Puppet", "Node": "<a node of its model>" }` attaches
  the particles to a named node of the double's model (the raised book, a hand).

An entity target has no playback cap on its attached particles, so an endless system there needs
a bounded copy; a fresh entity nobody's tracker has shown yet falls back to a positional play at
its place.

**A sting reaches the worker in 2D** through `Presentation.Effect` with `"Target": "Player"`: a
tiny native `EntityEffect` whose only job is a `LocalSoundEventId`, applied to the real (hidden)
player for `DurationMs`. `"Target": "Puppet"` puts an effect on the double instead, an aura or a
model visual, held for `DurationMs` and then removed; the double has no stat sheet, so the engine's
own timer never runs there and the station keeps the clock.

## The beat that unmakes

```json
{
  "Id": "Unmake", "IsWork": true, "State": "Working",
  "Duration": { "Ms": 1500 },
  "Puppet": { "Clip": "RPG_Emote_Disenchant_Release" },
  "Convert": {},
  "Presentation": {
    "Sounds": ["SFX_Crystal_Break", "SFX_Skeleton_Mage_Spellbook_Impact"],
    "Particles": [ { "SystemId": "RPG_Disenchant_Flash" } ],
    "Shake": { "EffectId": "Impact", "Intensity": 0.6 }
  }
}
```

`Convert` at a beat runs the action's `Recipe` exactly as the classic loop would: the matched row
is selected (an authored row, a salvage-derived row, or a fallback route), the piece is consumed
from custody, the outputs land in the worker's inventory, and the input event fires. It is the one
conversion code path, so a ritual and a loop never drift. `Preview` had already told the player
what this beat would return.

## What it pays

Vanilla's return comes from the derived rows, at native quantities, on the plain item row. The
table's own payout is its `Bonus`:

- **Essence, as expected output.** The Life essence roll authors `"Expected": true`, so the essence
  lands on the plain item row and the produced ledger beside the salvage return, not as a gold find.
  Its `Ladder` reads the PIECE, through `hytale:item_level` and `hytale:item_quality`: a better
  piece draws more.
- **Finds, as finds.** Void essence is a `Chance` rising with the piece's level; a Voidheart is the
  rarest, gated to high-level gear; the grimoire trophy is a flat rare chance paid as an item grant.
  Each carries a `Cue` (`Cue:Void_Find`, `Cue:Voidheart`, `Cue:Trophy`) the action's `Moments` map
  dresses, delayed about 450 ms so it lands after the shatter.
- **The kit.** Four cloth pieces roll on their own flat chances, and the set they form carries the
  proficiency stat the pace ladder reads.
- **The greater tier** references everything the lesser does plus one more table: more essence and
  better odds, one extra file rather than a copied set.

The piece in custody is what the factors read (captured on the session, so a resume reads the same
piece), so an extension's own rolls size their payout from the piece through the same item factors,
with nothing re-authored in the jar.

## What other mods see

A ritual's `Convert` fires `StationInputConsumedEvent` (the worker, the world and block, the station
and action ids, and each consumed stack with its id, count, quality and item level), and the
`STATION_INPUT` objective kind counts it beside `WORK_STATION` and `STATION_OUTPUT`
([Integrations](integrations.md)). A quest step or an achievement criterion authoring
`"Qualifier": "Disenchanting_Table"` with `"QualifierMatchMode": "PREFIX"` counts both tables under
one rung.

## Authoring your own ritual

1. Custody first: `MaxQuantity: 1` for a piece that carries wear or stamps; `HeldOnly` and `Preview`
   for a one-piece station; `Except` for the pieces the station should never eat.
2. `Work.Looping: false`; `Work.Queue: true` only with several single-item sockets.
3. Beats: mark the elastic ones `Paced`, author `IsWork: true` on every beat that should keep the
   block lit, and give a later beat its own `State` for a deeper look.
4. `Pace` with a `Clamp` at both ends; the content audit names a ladder left open.
5. Put the bed on the block states and keep every one-shot bounded; target the piece with
   `Display`, the double with `Puppet`.
6. `Convert` on the unmaking beat, never earlier than the cues that need the prop.
7. `Expected: true` on the wage, `Cue`s on the finds.

---

Previous: [Derive from Any Bench](derive-from-any-bench.md) · Next: [Actions & Step Programs](actions-and-steps.md)
