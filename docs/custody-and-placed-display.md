# Custody & Placed Display

Placed-input custody claims, block states, and the facing-relative placed-as-entity display.

`Custody` opts one action into a state-dependent `F` interaction: press `F` while holding a matching
stack and it loads into the block instead of starting a session (a repeat press tops it up with
further matching stacks); the owner pressing `F` on a loaded block starts working FROM that placed
pile instead of the live backpack. The claim itself is in-memory only - never persisted, and it
auto-returns to the owner (or drops at the block) on every session-stop path and on server shutdown.

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
| `States` | `{Empty?, Loaded?, Working?}` - the block's OWN interaction-state names custody flips between (hint-only: swaps the interaction-hint text, no visual model swap). `Working` is nullable and shows ONLY while a work step is actively executing at this block (see `IsWork` in [Actions & Step Programs](actions-and-steps.md)), reverting to `Loaded`/`Empty` on step exit and every session stop. Omit any of them and custody still works mechanically, just with no state flip on that leaf. |
| `Display` | Opts the placed input into a rendered prop entity at the block. See below. |

A single-item placement (`MaxQuantity: 1`) preserves the placed stack's full metadata (durability,
enhancement rolls) rather than collapsing it to a bare fresh stack - this is what makes placing an
already-enhanced weapon on the Anvil safe.

## The placed-as-entity display

Authoring `Custody.Display` spawns a real, network-replicated, pickup-immune, physics-free prop entity
rendering the placed item at the station's block-top anchor - the same point every cycle/swing/rare-
find presentation moment already targets. Block-shaped custody items (the Sawmill's placed logs)
render as the real block model; everything else (the Anvil's placed weapon or bar) renders as the
generic dropped-item prop shape. The display entity is never persisted - it despawns on return or
block break, matching the claim's own lifecycle exactly.

```json
"Display": {
  "Offset": { "X": 0.4, "Y": 0.55 },
  "Scale": 1.0,
  "Rotation": { "Yaw": 0.0, "Roll": 90.0 }
}
```

| Field | What it does |
|---|---|
| `Offset` | `{X,Y,Z}` shift off the block-top anchor. `Y` is vertical; `X`/`Z` are horizontal. |
| `Scale` | Resizes the prop (default 1.0). |
| `Rotation` | `{Yaw,Pitch,Roll}` in DEGREES. Every leaf defaults to 0. |

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
block) hands the placed contents back to the owner and despawns the display, provided no session is
actively working that block - a session actively working the station always wins over a retrieval
attempt.

## Acceptance precedence at a claimed block

A block already busy with its own session, or already holding a non-empty custody claim, refuses an
incoming claim from a different program (relevant when the same block is also declared as a
[multi-station anchor](multi-station-programs.md) by some OTHER action) - a restart's self-heal
consults the live custody claim, not just the session map, so this precedence holds even across a
server restart.

---

Previous: [Multi-Station Programs](multi-station-programs.md) · Next: [Puppet & Performers](puppet-presentation.md)
