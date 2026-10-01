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
stations `Ref`; the greater tier's `Disenchant_Greater` is `"Parent": "Disenchant"` and authors
only what the greater tier changes: the queue, its three sockets, the one extra payout table and
where the double stands. The deeper violet look of that tier is not in the action at all; it lives
on the greater block's own `Working` and `Drawing` states. Two transforms would be two actions;
one transform in two tiers is one action and a parent chain.

## Custody: one piece at a time

```json
"Custody": {
  "MaxQuantity": 1,
  "HeldOnly": true,
  "Preview": true,
  "Input": {
    "Except": [
      { "ItemId": "RPG_Weapon_Spellbook_Grimoire_Disenchanter" },
      { "Tags": { "Family": ["Disenchanter"] } }
    ]
  },
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
  minus the holes: the table's own trophy and its kit (the `Disenchanter` family tag the four
  pieces carry). The fallback's `Input` takes every gear class by `Type`: weapons, armor, tools and
  utility gear (backpacks, the quiver, the helipack). What has a salvage recipe but is not gear
  (rocks, plants), what is gear-shaped but not gear (ammunition, bait, deployables, the tagless
  repair kits, fertilizers and capture crate) and what carries a gear type without being gear (the
  Trork spawner egg, the frag grenade, the developer seed bag, every `Developer`-family item and
  every item of a creative or test quality (`Developer`, `Debug`, `Technical`, `Template` or
  `Tool`), such as the trooper armor, the debug sticks, the mana prototype tools and the test guns) is refused by the jar's protect-list file, `ProtectLists/Disenchanting_Tables.json`,
  one file scoped to both tables, so those holes are written once rather than on every matcher. A
  piece whose stack carries data no mod declared disposable, such as a bag with something in it, is
  refused by the metadata guard at placement, at either table and in any of the greater table's
  sockets, since the ritual consumes every piece it takes; no salvage row or fallback route will
  consume one either, and the greater table's queue works around one already sitting in a socket.
  Placement is the one gate, so the fallback's own `Input` needs only its routes. A pack's
  extension overlay adds its own holes beside these.
- `Display.Animated` shows the placed piece turning and bobbing the way a dropped item does.

The greater tier's action (`Disenchant_Greater`, a child of `Disenchant`) authors three single-item
sockets in its own `Custody` instead of the implicit one, and `Work.Queue: true`. The `Custody`
merges per leaf under `Parent`, so `HeldOnly`, `Preview` and `States` are inherited; an authored
socket judges placement by its own `Match` alone, so each socket's `Match` carries the same two
`Except` holes and otherwise derives its acceptance exactly as the lesser table's `Input` does. The
ritual then runs once per filled socket, in authored order, each run reading and consuming that
socket's piece and playing its beats at that socket's prop. The session ends when every socket is
empty; an interrupted run refunds only the socket in progress.

## The seven beats

`Work.Looping: false` makes the program a ritual: one run, then the session completes. The table's
program is seven beats, four of fixed length and three that stretch with the worker's pace:

| Beat | Length | The double | The bed | Accents |
|---|---|---|---|---|
| Open | 2.0 s, fixed | opens the book | `Working` | a page-turn charge; a sigil rising from the piece |
| Kindle | elastic, 8 s untrained | raises the book into the charging pose | `Working` | a swell; mote chimes on delays; green-gold sparks and motes riding the piece |
| Draw | elastic, 12 s | holds that pose (no clip of its own) | `Drawing`; the piece lifts and turns | after a 400 ms hold, a gather riding the lifted piece; a chime; the channel aura on the double |
| Surge | elastic, 9 s | winds up | `Drawing` | a beam shell; a layered charge; a light shake, delayed |
| Unmake | 1.5 s, fixed | releases | back to `Working` | the `Convert`; a shatter, a flash, a hard shake |
| Payout | 2.5 s, fixed | holds the release (no clip of its own) | `Working` | a bloom; the find cues about 450 ms in |
| Settle | 3.0 s, fixed | a hand on the heart | `Working` | motes where the piece stood; one last mote sound |

One beat, in full:

```json
{
  "Id": "Draw", "Paced": true, "IsWork": true, "State": "Drawing",
  "Duration": { "Ms": 12000 },
  "Display": { "Offset": { "Y": 1.4 }, "Rotation": { "Yaw": 45 }, "Animated": true },
  "Presentation": {
    "Target": "Display",
    "DelayMs": 400,
    "Sounds": ["SFX_MemoryRestored"],
    "Particles": [ { "SystemId": "RPG_Disenchant_Draw" } ],
    "Effect": { "Id": "RPG_Disenchant_Channel_Aura", "Target": "Puppet" }
  }
}
```

- `Paced: true` marks an elastic beat: the action's `Pace` multiplies its `Duration.Ms`, and the
  `DelayMs` and every burst's `DurationSeconds` of its own presentation, so its accents keep their
  place inside the beat. A beat without it holds its authored length at any speed, which keeps the
  gather, the flash and the linger crisp.
- No `Puppet` clip: the double keeps the charging pose the Kindle beat put it in. A beat authors a
  clip only where the pose changes.
- `IsWork: true` on every beat keeps the table lit between beats. A pure beat (no `Convert`, no
  `Consume` plus `Produce`) would otherwise not count as work, and the block would fall back to its
  `Loaded` look.
- `State` holds the block in a deeper state than `Custody.States.Working` for this beat: `Drawing`
  from the Draw on, `Working` again at the Unmake. Two beats in a row naming the same state keep it
  without a flicker; a beat naming a different one re-flips in place.
- `Display` overlays the placed piece's prop from this beat on, the leaves it authors replacing the
  socket's own: here the piece lifts and turns, and it stays that way until another beat overlays
  it or the piece leaves, so the Surge still shows the lifted piece and the Unmake's shatter plays
  where it stood. The overlay
  respawns the prop at the beat's entry, and a prop spawned this tick has been shown to nobody yet,
  so a cue aimed at it in the same tick would play at its position instead of riding it: the
  presentation's `DelayMs` holds the whole group past the next tick (400 ms here, and 84 ms at the
  fastest pace, since a paced beat's delay scales with it).
- `RPG_Disenchant_Draw` authors its own `LifeSpan`, so it rides the lifted piece and ends on its
  own; it needs no `DurationSeconds`, which only caps a burst played at a position.
- `Effect` with `"Target": "Puppet"` puts the channel aura on the worker's double at the same
  moment, and it stays for the rest of the session.

### Pace

```json
"Pace": {
  "Ladder": {
    "Factors": [ { "Factor": "hytale:stat", "Param": "RPG_Disenchanting_Proficiency" } ],
    "Floors": [ { "Min": 0, "Scale": 1.0 }, { "Min": 10, "Scale": 0.9 },
                { "Min": 20, "Scale": 0.8 }, { "Min": 30, "Scale": 0.7 },
                { "Min": 45, "Scale": 0.55 }, { "Min": 60, "Scale": 0.42 },
                { "Min": 75, "Scale": 0.3 }, { "Min": 90, "Scale": 0.21 } ]
  },
  "Clamp": { "Min": 0.21, "Max": 1.0 }
}
```

`Pace` is the `ContributionScale` ladder shape ([Loot & Factors](loot-and-factors.md)): the factors
are summed, the highest floor reached wins, and its scale multiplies every `Paced` beat. The jar's
ladder reads a native entity stat, `RPG_Disenchanting_Proficiency`, which the table's own kit, its
set tiers, its grimoire and its draught carry through ordinary item `StatModifiers`, set bonuses
and an effect's `RawStatModifiers`, so a better-equipped worker finishes sooner with nothing else
installed. Only the three elastic beats are paced (29 of the ritual's 38 seconds; the other 9 are
fixed), which is why the clamp's floor is 0.21 rather than the ratio of the two lengths: at the
floor the elastic beats shrink to about 6 seconds and the whole ritual to 15. An extension adds
its own `Pace` ladder from its own factors, and the ladders MULTIPLY; only the action's `Clamp`
bounds the product. The pace is resolved once at each beat's entry, so a stat change mid-beat lands
on the next.

## Two layers: the bed and the accents

Nothing in the protocol can stop a sound or a particle system once it is playing. The ritual splits
its presentation along that line.

**The bed lives on the block state.** A `State.Definitions` entry on the block carries a looping
ambient sound, particles (each attachable to a named part of the block model), the light, and a lit
texture with a model animation, and ALL of it starts and stops with the state flip. The lesser
table's `Working` is the vanilla Salvage bench's own processing look, arms and door animating, with
an ember effect on its fire part and a green-gold light; `Drawing` brightens the light and thickens
the sparks. The greater table's `Working` is the vanilla Arcane table's idle animation (book, gem
and candle bobbing) under a soft violet light and a hum that ducks the music; `Drawing` adds violet
motes under a stronger violet light. The step's `State` leaf is the whole mechanism: name the state,
and the block does the rest.

**The accents are one-shots.** A step's `Presentation` fires one-shot sounds (always positional) and
bounded particle bursts. `DurationSeconds` caps a burst played at a position that would otherwise
never end; a system that ends on its own needs no cap. `Color` tints a vanilla system to the beat's
palette through the engine's own colour argument, one leaf instead of a copied system, and it tints
a burst riding an entity as well as one played at a position. The ritual's own accents are derived
copies anyway, because each has to end on its own to ride the piece (a vanilla system with no
`LifeSpan` of its own plays at a position under a cap), so each copy carries its green-gold in its
own keyframes.

**Where a cue plays** is `Presentation.Target`:

- `Block` (the default): the station block's centre.
- `Display`: the placed piece's prop, the socket the queue is working. Sounds follow the prop,
  delivered only to the players who can see it, and particles ride it. A `Convert` beat drops the
  prop before its own entry cues, so a `Display` target on that beat resolves to where the piece
  last stood, lifted or not: the shatter plays where the piece was.
- `Puppet`: the worker's double, or the block when no double stands.
  `{ "Kind": "Puppet", "Node": "<a node of its model>" }` attaches the riding particles to a named
  node of the double's model (the raised book, a hand).

A particle system rides an entity only when it ends on its own: its own `LifeSpan` is positive. An
attached system has no playback cap, so it lives until that lifetime ends or the entity is removed
(the prop when the piece is consumed, the double when the session ends). A system with no
`LifeSpan` plays at the entity's position under `DurationSeconds` instead, and
`/rpgstations validate` warns about it; which is why each of the ritual's derived systems authors a
`LifeSpan`. A fresh entity nobody's tracker has shown yet falls back to a positional play at its
place.

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
    "Target": "Display",
    "Sounds": ["RPG_SFX_Disenchant_Crystal_Break", "SFX_Skeleton_Mage_Spellbook_Impact"],
    "Particles": [ { "SystemId": "RPG_Disenchant_Flash" } ],
    "Shake": { "EffectId": "Impact", "Intensity": 0.6 }
  }
}
```

`Convert` at a beat runs the action's `Recipe` exactly as the classic loop would: the matched row
is selected (an authored row, a salvage-derived row, or a fallback route), the piece is consumed
from custody, the outputs land in the worker's inventory, and the input event fires. It is the one
conversion code path, so a ritual and a loop never drift. `Preview` had already told the player
what this beat would return. The prop is gone by the time the beat's cues play, so the `Display`
target resolves to where the lifted piece last stood: `RPG_Disenchant_Flash` folds the magic hit,
the glass break and the crystal break into one system with its own `LifeSpan`, so the whole shatter
lands there. The crystal break plays through `RPG_SFX_Disenchant_Crystal_Break`, a one-line copy of
the vanilla event with `BypassDucking` set, because the greater table's hum ducks the block-sounds
category and would otherwise swallow it.

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
  dresses, delayed about 450 ms so it lands after the shatter. The two void cues play their creature
  sounds through `BypassDucking` copies, for the same reason the crystal break does.
- **The kit.** Four cloth pieces roll on their own flat chances (`Cue:Kit_Find` is their cue; the
  engine's own `Rare_Find` id is never taken from an action's `Moments`, so a find that should
  sound like this table names a cue of the table's own), and the
  set they form with the grimoire (`Server/ZiggfreedCommon/GearSets/RPG_Disenchanters_Kit.json`)
  carries the proficiency stat the pace ladder reads: a little at two pieces, more at four with the
  set's look, the most at four with the grimoire in hand.
- **The greater tier** references everything the lesser does plus one more table: more essence and
  better odds, one extra file rather than a copied set. The tables are `Disenchant_Essence`,
  `Disenchant_Void`, `DisenchantTrophy`, `Disenchant_Kit` and `Disenchant_Greater_Bonus`, each
  under `Server/ZiggfreedCommon/Lootables/`, so a pack re-tunes one concern by shipping one file.

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
