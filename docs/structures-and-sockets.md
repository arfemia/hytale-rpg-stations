# Structures & Sockets

Multiblock stations: an authored arrangement of world blocks that becomes a working station when a
player finishes building it, and reverts when the build is broken. This page covers the STRUCTURE
half - the `StructurePatternAsset` type and its runtime behavior. The SOCKET half (named placement
slots on a station, including the `Block` sockets a structure's parts often become) lives in
[Custody & Placed Display](custody-and-placed-display.md).

## What a structure pattern is

A `StructurePatternAsset` (`Server/RpgStations/Patterns/<Name>.json`) describes a shape in whole
blocks: a list of cells, each an offset plus what must stand there, with exactly one cell marked as
the ANCHOR. When a player's placement completes the shape - in any build order - the anchor block
is swapped to the pattern's station block, and from then on it is an ordinary station: press F,
place materials, claim it as a remote anchor, everything a directly-placed station block can do.
Breaking any block of the standing shape swaps the anchor back and drops anything stored there.

```json
{
  "Identity": { "NameKey": "mypack.structure.firepit.name" },
  "Rotate": { "Yaw90": true },
  "Activate": { "Block": "My_Station_FirePit" },
  "Cells": [
    { "Offset": { "X": 0, "Y": 0, "Z": 0 }, "Block": { "ItemId": "Deco_Campfire_Off" }, "IsAnchor": true },
    { "Offset": { "X": 1, "Y": 0, "Z": 0 }, "Block": { "ResourceTypeId": "Rock" } },
    { "Offset": { "X": -1, "Y": 0, "Z": 0 }, "Block": { "ResourceTypeId": "Rock" } },
    { "Offset": { "X": 0, "Y": 0, "Z": 1 }, "Block": { "ResourceTypeId": "Rock" } },
    { "Offset": { "X": 0, "Y": 0, "Z": -1 }, "Block": { "ResourceTypeId": "Rock" } },
    { "Offset": { "X": 0, "Y": 1, "Z": 0 }, "Empty": true }
  ],
  "Moments": { "activated": { "Sounds": ["SFX_Flame_Ignite"] } }
}
```

Full field reference: [SCHEMA.md](../SCHEMA.md).

## Cells

Each cell authors an `Offset` (whole blocks, any consistent frame - the anchor's offset is
subtracted out internally) plus exactly ONE of:

- `Block` - what block must stand there: an exact `ItemId`, a `ResourceTypeId` family (`"Rock"` =
  any rock-family block), or `Tags`. A state variant (a lit campfire, a loaded bench) matches
  through its base block, so the shape holds whichever state its parts are in.
- `Empty: true` - the cell must hold AIR. Author this for headroom a build genuinely needs; do not
  author it for a cell a `Block` socket will later fill (see the socket interplay below).

Two cells may share an offset (both conditions must hold there); a cell authoring both routes or
neither matches nothing and warns at load.

**Under native `Parent`, `Cells` is replaced WHOLESALE, never merged per entry.** A child pattern
re-authoring any cell re-authors the entire array. This is the standard array-under-`Parent` rule,
and for patterns it is also the safe one - a partial cell merge could silently change what standing
builds are made of.

## The anchor

Exactly one cell authors `IsAnchor: true` - any cell, wherever reads best in the shape. With none
authored, the cell at offset `(0,0,0)` stands in (a load warning names the default). The anchor
cell MUST author an exact `Block.ItemId`: detection seeds its placement index from exact ids, so a
family-matched anchor would never be discovered.

`Activate.Block` is the station block the anchor becomes. Two styles, both plain authoring:

- **Vanilla arrangement**: the anchor is a vanilla block (`Deco_Campfire_Off`) and `Activate.Block`
  is your station block - completion swaps it, carrying the placed block's rotation, so the station
  faces whatever way the builder faced the campfire.
- **Custom core block**: the anchor IS your station block and `Activate.Block` equals its id - no
  swap happens; completion simply arms the block that already stands there (until then it behaves
  like any placed station block of that type).

`Activate.RevertBlock` is what a broken shape reverts the anchor to; unauthored, it falls back to
the anchor cell's own `Block.ItemId`.

## Rotation and mirroring

`Rotate.Yaw90` (default true) recognizes the shape in all four yaw quarter-turns; `Rotate.Mirror`
(default false) additionally recognizes the X-mirrored form of each. Rotation is discrete - 0, 90,
180, 270 degrees about the vertical axis, pivoting on the anchor - and the matched orientation is
recorded on the anchor, so the standing-build re-check walks the same orientation the build was
recognized in.

## Activation

On the placement that completes the shape:

1. If the anchor position already belongs to a DIFFERENT pattern (or another mod's block store),
   the completion is refused with a toast - first build standing wins.
2. The pattern's `Requires` gate (permission and/or factor `Conditions`, the same block every
   station uses) evaluates against the PLACER. A failing gate leaves the blocks standing and
   nothing activates; the toast names the structure when `Identity.NameKey` is authored. Creative
   placement activates like any other - building is not an economy action - but the gate still
   evaluates.
3. The anchor swaps to `Activate.Block` (skipped when the id already matches), keeping the rotation
   the anchor block was placed with.
4. The `activated` moment plays at the anchor (sounds, particles, a shake for the placer - the
   standard `Presentation` shape; cues play at once, a `DelayMs` here reads as zero).

## Build order, and what survives a restart

Any build order works. Placing the anchor first (or any cell authored by exact `ItemId`) registers
the spot as a PENDING candidate; each later placement nearby re-checks just those candidates, so
finishing the ring around an already-placed campfire activates on the last stone. That pending
memory is in-memory only and bounded: **after a server restart, a half-built shape does not
self-complete on the next stone - break and re-place any exact-id block of the pattern (the anchor
is the natural one) and detection picks the build up again.** A fully ACTIVATED station needs no
such step: its mark lives on the block's own chunk and survives restarts like any placed station.

## Breaking and reverting

Breaking any block of a standing build (by hand, or by fire/explosion) re-checks the shape from the
anchor. When the shape no longer holds:

- Any session working the anchor stops gracefully (the player is told the structure broke apart;
  their own placed materials hand back to them like any present-player stop).
- Whatever else the station still stored drops at the block once, exactly like breaking a station
  block directly.
- The `broken` moment plays at the anchor.
- The anchor swaps back to `Activate.RevertBlock`, again keeping its rotation.

Breaking the ANCHOR block itself skips the swap-back (the block is gone); the stored materials
drop and the structure mark is cleaned up.

## Structures and Block sockets

A structure's activated station often authors `Block` sockets - the pot that sits ON the fire pit,
matched at a facing-relative offset (see
[Custody & Placed Display](custody-and-placed-display.md)). The standing-build re-check
deliberately IGNORES any cell whose offset coincides with one of those socket cells: placing the
pot onto the pit (or taking it off) must never read as the structure breaking. That is also why a
socket target cell should be authored `Empty` only when the pattern truly requires it clear AT
BUILD TIME - after activation the socket machinery owns that cell.

## The shipped Cooking Pit, end to end

The jar ships a complete worked example of everything above:
`Server/RpgStations/Patterns/CookingPit.json` (the shape),
`Server/RpgStations/Stations/CookingPit.json` (the two-action station), the
`RPG_Station_CookingPit` block it activates into, the craftable `RPG_Station_Cooking_Pot` vessel
block, and the `RPG_Food_Hearty_Stew` meal. Read the three JSON files side by side - each carries
`$Comment`s explaining its own knobs - and play the loop like this:

1. **Build the pit.** Place an unlit campfire (`Deco_Campfire_Off`) and ring it with any
   stone-family blocks (`ResourceTypeId: "Rock"` cells - cobblestone, stone, bricks and marble mix
   freely), keeping the cell above the fire open (`Empty: true`). The campfire becomes the
   `RPG_Station_CookingPit` station on the last stone, in any of the four orientations.
2. **Grill on the bare pit.** Press use holding raw meat, a raw vegetable or a raw fish piece: the
   `Grill` action derives its recipes from the engine's own Campfire processing recipes
   (`FromCrafting`), accepts those same raw foods into the classic single pile, and cooks
   unattended. Its recipe-level `Doneness` chars uncollected food into charcoal.
3. **Mount the pot.** Craft the pot (three iron bars at any workbench) and place it in the open
   cell. That cell is the `Stew` action's `vessel` Block socket - and because the standing-build
   re-check excludes Block-socket cells, mounting or removing the pot never breaks the pit.
4. **Stew.** The two actions gate on the same `rpgstations:socket_filled` factor in opposite
   directions (`Max: 0` on Grill, `Min: 1` on Stew), so the same press grills on a bare pit and
   feeds the pot once it stands. Ingredients go in one per press (`PlacePerPress: 1`, up to six);
   the three recipe rows demonstrate the ruled ordering - exact-set rows first (meats plus
   vegetables to a kebab, vegetables alone to a salad), the route-less match-all row last (three of
   anything to the stew).
5. **Walk away, come back.** Both actions author `Work.Unattended`, so loaded piles keep settling
   on world game time; the kebab and the stew carry per-row `Doneness` windows and collapse to
   charcoal when ignored, while the salad (no window) waits forever. The block's five states
   (`Default`/`Loaded`/`Lit`/`Ready`/`Smoking`) narrate the whole cycle from across the camp.

## Not extensible, by design

`StructurePatternAsset` is NOT an `ExtensionAsset` target, and this is deliberate: the cell list IS
the pattern's identity. A cell appended by another pack would instantly invalidate every standing
build of the original shape on its next re-check - a player's fire pit would collapse because an
unrelated mod updated. To VARY a shape, author a new pattern (native `Parent` shares the
`Identity`/`Rotate`/`Activate`/`Requires`/`Moments` groups; remember `Cells` replaces wholesale).

---

Previous: [Unattended Work](unattended-work.md) · Next: [Puppet & Performers](puppet-presentation.md)
