# Custody & Placed Display

Placed-input custody claims, block states, and the facing-relative placed-as-entity display.

`Custody` opts one action into a state-dependent `F` interaction: press `F` while holding a matching
stack and it loads into the block instead of starting a session (a repeat press tops it up with
further matching stacks); the owner pressing `F` on a loaded block starts working FROM that placed
pile instead of the live backpack.

Placed custody is stored on the block's own chunk and saved with it, so it survives a restart, a
chunk unload and a logoff alike: logging off leaves placed materials standing in the world for the
owner to collect on return, exactly like leaving them in a chest. A session stop while the player
is still present (re-press, walk-off, damage, death, running out of inputs) still hands the
materials back to the owner's inventory (or drops them at the block); breaking the block still
drops everything at the block once, whether a player or an explosion broke it. A single-item
placement's full metadata (durability, enhancement rolls) survives a restart with the rest.

## The Custody group

```json
"Custody": {
  "MaxQuantity": 100,
  "Input": { "ResourceTypeId": "Fish" },
  "States": { "Empty": "Default", "Loaded": "Loaded" },
  "Display": { "Offset": { "Y": 0.55 }, "Scale": 1.0 }
}
```

| Field | What it does |
|---|---|
| `MaxQuantity` | The cap on the claim (default 100). The Anvil's weapon-placement custody uses `1` - a single, metadata-preserving item, not a stack. |
| `Input` | The placement-acceptance matcher, reusing the same `ItemId`/`ResourceTypeId`/`Tags`/`Function` routes an action's diegetic `Select` uses. When absent, acceptance derives from the resolved action's own `Recipe.Conversions` inputs - zero extra authoring for a plain convert station (the "logs by ResourceTypeId family" fallback). |
| `States` | `{Empty?, Loaded?, Working?, Ready?, Overdone?}` - the block's OWN interaction-state names custody flips between. A flip swaps the block to that state's own variant, so everything the state authors comes on with it and the previous state's stops: its texture, its model animation, its light, its looping ambient sound and its particles, as well as its interaction hint. `Working` is nullable and shows ONLY while a work step is actively executing at this block (see `IsWork` in [Actions & Step Programs](actions-and-steps.md)), reverting to the resting look on step exit and every session stop; a step's own `State` names a deeper look for one beat of a ritual in its place. `Ready` shows while a produced batch waits in a custody pile under an open [doneness window](actions-and-steps.md#doneness-the-ready-window-on-produced-output) (the `Working` look wins while work actually runs); `Overdone` shows after a window expired and the pile collapsed, until it is gathered or reloaded. Omit any of them and custody still works mechanically, just with no state flip on that leaf. The state SET is closed by the engine - a pack or extension may re-point each leaf at its own block-state NAME, never add a sixth state. |
| `Display` | Opts the placed input into a rendered prop entity at the block. See below. |
| `HeldOnly` | `true` places only what the player HOLDS: the hotbar and backpack are never searched for a match, so a press at a station that takes one valuable piece can never pull gear out of the bag unasked. Default `false`, the classic held-else-inventory placement. |
| `Preview` | `true` tells the player, the moment a piece is placed, what it will give back: the outputs of the row the recipe would run for it right now, or that it gives nothing back on its own when only the essence-only route takes it. Default `false`. See [the placed-piece preview](#the-placed-piece-preview). |

A single-item placement (`MaxQuantity: 1`) preserves the placed stack's full metadata (durability,
enhancement rolls) rather than collapsing it to a bare fresh stack - this is what makes placing an
already-enhanced weapon on the Anvil safe.

## The placed-as-entity display

Authoring `Custody.Display` spawns a real, network-replicated, pickup-immune, physics-free prop entity
rendering the placed item at the station's block-centre anchor - the same point every cycle/swing/rare-
find presentation moment already targets. Block-shaped custody items (the Sawmill's placed logs)
render as the real block model; everything else (the Anvil's placed weapon or bar) renders as the
generic dropped-item prop shape. The display entity itself is never persisted - it despawns on
return or block break, and after a restart or a chunk reload the prop respawns from the persisted
contents within moments of the chunk loading (the engine's background pass walks freshly loaded
chunks and rebuilds every missing prop, a handful per second per world so a big load never
stutters); using the station rebuilds it immediately either way. The placed contents are there the
whole time - only the visual is ever rebuilt.

```json
"Display": {
  "Offset": { "X": 0.4, "Y": 0.55 },
  "Scale": 1.0,
  "Rotation": { "Yaw": 0.0, "Roll": 90.0 }
}
```

| Field | What it does |
|---|---|
| `Offset` | `{X,Y,Z}` shift off the block-centre anchor. `Y` is vertical; `X`/`Z` are horizontal. |
| `Scale` | Resizes the prop (default 1.0). |
| `Rotation` | `{Yaw,Pitch,Roll}` in DEGREES. Every leaf defaults to 0. |
| `Animated` | `true` gives the prop the client's own dropped-item motion, a slow turn and bob; default `false`, a still prop. It is set when the prop spawns, so a beat that switches it on mid-ritual (a step's own `Display` overlay, see [Actions & Step Programs](actions-and-steps.md)) respawns the prop. |

### Facing-relative, not absolute world-space

`Offset` and `Rotation` are authored RELATIVE TO THE PLACED BLOCK'S OWN FACING, not absolute world
axes: an authored `+Z` offset always lands toward the same face of the block regardless of how a
server owner rotated it when placing it, and the block's own facing yaw is folded into `Rotation.Yaw`
so a rotated placement carries the prop's position AND facing around with it. At a default-orientation
placement (block yaw 0) the local frame equals the world frame, so an authored value there behaves
exactly like a naive world-space offset - every station that only authors a vertical `Offset.Y` (the
Sawmill's placed logs, the Anvil's placed bar) is completely unaffected by this convention and needs
no re-tuning.

Rotation composes through the engine's yaw-then-pitch-then-roll order. For a real weapon mesh prop,
tune it empirically in three questions: is the blade spun the wrong way in the horizontal plane
(adjust `Rotation.Yaw`)? Is it standing on its edge instead of lying flat (check `Rotation.Roll`,
typically `90` to lay flat)? Is it sunk into or floating above the block (nudge `Offset.Y` in small
steps)?

## Press-F retrieval

A placed-as-entity display is directly retrievable: pressing `F` on the display entity itself (not the
block) hands that display's placed contents back and despawns it, provided no session is actively
working that block - a session actively working the station always wins over a retrieval attempt. At a
multi-socket station each socket's prop retrieves ITS OWN pile only, gated by that pile's own owner
(or the socket's `Share.Reclaim`, below).

## Sockets: named placement slots

`Custody.Sockets` grows one claim into NAMED, independently addressed slots - a stew pot's meat rack
beside its herb basket beside its output shelf - each with its own pile, owner, matcher, capacity,
display prop and share posture. Authored order is placement priority: a press offers the held stack to
the sockets in the order the file writes them, and the first Item socket that accepts it (and has
room) receives it. **Omit `Sockets` entirely and nothing changes**: the custody-level leaves act as
ONE implicit socket (reserved id `main`) holding the whole claim, which is every classic station.

```json
"Custody": {
  "MaxQuantity": 40,
  "Sockets": {
    "vessel": { "Block": { "At": { "Y": 1 }, "Match": { "ItemId": "RPG_Station_Cooking_Pot" } },
                "Required": true, "Label": "yourpack.socket.pot" },
    "ingredients": { "Item": { "PlacePerPress": 1 }, "MaxQuantity": 9, "Share": { "Place": true } },
    "output": { "Item": { "Match": { "ResourceTypeId": "Food" } }, "Display": { "Offset": { "Y": 1.4 } } }
  }
}
```

| Field | What it does |
|---|---|
| `Item` / `Block` | The socket's route - exactly one of the two. `Item` holds a pile of placed stacks (`Match` = what it accepts, absent derives from the recipe like the custody-level `Input`; `PlacePerPress` = how much one press moves, absent = the whole held stack, `1` = one at a time). `Block` is a REAL world block at `At` (a whole-block offset in the station block's own facing frame, the `Display` convention - it rotates with the placed block) whose base item identity satisfies `Match` (absent = any block); nothing is stored for it, and caps/shares/displays are meaningless on one. A socket authoring both routes, or neither, is ignored with a warning. |
| `MaxQuantity` | This socket's own cap; the effective capacity is the SMALLER of it and the custody-level `MaxQuantity`, and the custody-level cap also bounds the block's TOTAL across every socket. |
| `SingleFamily` | Locks THIS socket's pile to its first-placed family; absent inherits the custody-level value. |
| `Required` | Gates engage: an Item socket needs a non-empty pile, a Block socket its matching world block - and a required block is re-checked while working, so breaking the pot off the fire ends the session gracefully. |
| `Display` | This socket's own prop (same knobs as the custody-level `Display`); a socket without one renders nothing. |
| `Share` | Per-socket overrides of the custody-level `Share` (below). |
| `Label` | A lang key naming the socket in refusals ("the pot"); omit for the generic wording. |

Author socket ids lower-case; they are matched case-insensitively, and `main` is reserved for the
implicit socket. A step program addresses sockets by id: a `Consume`/`Produce` phase carries a
group-level `Socket`, and any single `Items` entry can name its own - so one recipe row draws meat
from the meat rack and greens from the basket. An entry naming no socket draws from the first
authored Item socket.

## Sharing placed materials (`Share`)

Every pile belongs to exactly ONE player - whoever put the first item in it (a produced pile belongs
to whoever did the work). `Share` is three independent booleans, authorable at the custody level (the
default for every socket) and per socket, all defaulting false (owner-only, the classic behavior):

| Leaf | What `true` opens |
|---|---|
| `Place` | A non-owner may START a pile in that socket while it is EMPTY - the first contributor then owns it until it drains empty again. It never opens a non-empty pile: materials never co-mingle, a communal station is several sockets with several owners, not several owners in one pile. |
| `Use` | A non-owner may engage work that consumes from that socket's pile. |
| `Reclaim` | A non-owner may take that socket's pile back out (press-F on its prop). |

A stop that hands materials back returns only the piles the stopping player OWNS; someone else's
piles stay standing in the world. An interrupted work iteration refunds what it consumed back into
each originating pile, so a shared worker's interruption never walks off with the owner's materials.

## What a station will not take

Three rules sit in front of every placement, whatever the station's own matcher says.

- **The server-wide protect-list.** The files under `Server/RpgStations/ProtectLists/` name what
  no consuming station may take as placed input (see [The protect-list](#the-protect-list) below).
  A press that offers a protected piece is turned away with its own reason, `Refused:Protected`,
  before any socket is offered the piece.
- **A station's own holes.** `Custody.Input.Except` (or a socket's `Match.Except`) names what THIS
  station refuses. An `Input` that authors only `Except` keeps everything the station derives from
  its recipe and its fallback routes and carves the holes out of that, so a station that takes
  every weapon but its own trophy authors one `Except`, never a list of every weapon. `Except` is
  one matcher or an array of them, and a pack's extension overlay ADDS its entries beside the
  jar's, so the pack protects its own trophy without restating the jar's. A piece a hole refused
  is answered `Refused:Protected` too, since the station would have taken it but for the hole.
- **A count pile keeps ids and counts only.** A socket whose capacity is above one refuses a
  stack that tracks wear or carries metadata (a worn tool, an enhanced piece): a pile could only
  hand it back as a bare fresh stack, which would repair it for free or lose what was on it. A
  single-item socket (`MaxQuantity: 1`) keeps the real stack and takes it.

A `Refused:Protected` answer, like every other placement refusal, comes only from a station that
was empty before the press. At a station that already holds material, the held item is judged as
a TOOL by the usual gates, never as input, so protecting a trophy tool never stops its owner
working a loaded station with it.

<a id="the-protect-list"></a>
### The protect-list

Each file under `Server/RpgStations/ProtectLists/` is one list: what it protects, and optionally
where.

```json
{
  "Protects": [
    { "ItemId": "Weapon_Sword_Heirloom" },
    { "Tags": { "Type": ["Trophy"] }, "Except": { "ItemId": "Trophy_Common_Plaque" } }
  ],
  "Stations": ["Disenchanting_Table"]
}
```

| Field | What it does |
|---|---|
| `Protects` | What the file protects: one input matcher or an array of them, the same `ItemId` / `ResourceTypeId` / `Tags` / `Function` routes (match = ANY route) and `Except` holes a `Custody.Input` uses. An entry protects what its routes match, minus its own holes; an entry authoring no route protects nothing (it is never read as "everything"). |
| `Stations` | The station ids the file applies to. Absent or empty: every consuming station. |
| `Actions` | The action ids the file applies to, at any station in scope. Absent or empty: every action. |

Both scope lists are matched without regard to case, and when both are authored both must match:
`"Stations": ["Disenchanting_Table"], "Actions": ["Disenchant"]` protects only at that station's
`Disenchant` action.

**Every file counts.** The files ADD UP into one server-wide list, whichever layer each comes from:
this jar, a content pack, or the server owner's own pack. A pack's list never silently replaces the
jar's or another pack's, so each can protect its own pieces without restating anyone else's. To
take an entry back, override its file BY ID: a later layer that ships the same file name replaces
the earlier file whole, so the owner copies it, drops the entries to lift, and keeps the rest. The
jar ships one file, `Disenchanting_Tables.json`, scoped to its two Disenchanting Tables: the rocks,
plants, ammunition, bait, deployables and tagless odds and ends the Salvage bench knows recipes
for, so neither table takes them; a station that must refuse one of its OWN pieces says so in its
own `Custody.Input.Except` (or a socket's `Match.Except`) instead. `/rpgstations validate`
warns about a file that protects nothing, an entry with no route, and a `Stations` or `Actions` id
that names nothing in scope.

## The placed-piece preview

A station with `Custody.Preview` authored `true` tells the player what the piece they just placed
will give back, before any work starts: the row the recipe would run for it right now (an authored
row, a derived one, or a fallback route's), listed as count and name, or that the piece gives
nothing back on its own when only the essence-only route takes it. A piece no row runs for right
now (a full bag, nothing matching) says nothing, since there is nothing true to promise. The
station needs a `Recipe` for there to be anything to preview; `/rpgstations validate` warns about
a preview on an action with none.

## Acceptance precedence at a claimed block

A block already busy with its own session, or already holding a non-empty custody claim, refuses an
incoming claim from a different program (relevant when the same block is also declared as a
[multi-station anchor](multi-station-programs.md) by some OTHER action). The check reads the
block's persisted contents, so someone else's placed materials refuse an anchor claim even across a
server restart.

---

Previous: [Multi-Station Programs](multi-station-programs.md) · Next: [Unattended Work](unattended-work.md)
