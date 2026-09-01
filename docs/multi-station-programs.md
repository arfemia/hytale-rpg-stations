# Multi-Station Programs

Anchors, walking between stations, claims, and the fish-preparation walkthrough.

A step program is not limited to the block the player pressed `F` on. An action can declare named
**anchors** - other, separately-placed station blocks it discovers nearby - and a step can move its
puppet to one, read or write that anchor's own custody claim, and use its own presentation. The player
never has to interact with the anchor block directly; the whole program runs from the primary
station's press.

## Declaring anchors

`Anchors` lives on an `ActionDef` (inline or standalone), a map of anchor id to
`{Station, MaxRadiusMeters}`:

```json
"Anchors": { "Fire": { "Station": "CookingFire", "MaxRadiusMeters": 12 } }
```

`Station` is a TYPE filter (the target station's own id, e.g. `CookingFire`, matched case-insensitively
at resolve); `MaxRadiusMeters` is the horizontal block search radius from the primary station block
(defaults to 12). The anchor id `self` is reserved for the primary block itself and is never authored.

### Discovery

At engage, the engine finds the NEAREST placed block whose station id matches each declared anchor,
within its radius, in the same world - it uses a lazily-populated index of station blocks it has seen
(fed by interactions and block-place events), falling back to one bounded ring scan if the index comes
up empty. In practice: **the engine finds station blocks it has interacted with before**; a
freshly world-edited block becomes discoverable after any interaction with it. If an anchor cannot be
resolved at all, the engage denies gracefully with a localized hint naming the missing station and
radius - never a partial engage.

### Claiming

A resolved anchor is CLAIMED atomically at engage (first engager wins, world-thread serial) - the
exact same claim mechanism a session already holds on its own primary block, generalized. While
claimed, another player's press on that anchor block denies as occupied, and a second program trying
to claim the same block fails its own claim and denies the whole engage. There is no queuing.

The anchor station's OWN action is never invoked - only its block's custody claim and interaction-state
visuals matter. "Cooking at the fire" is entirely the primary program's own step, dressed with the
fire's own presentation.

## The Walk phase

A step's `Walk` phase moves the PUPPET (not the real player, who stays hidden/held at the primary
station) to a named anchor at a configurable speed:

```json
{ "Id": "WalkOut", "Walk": { "To": "Fire", "SpeedMps": 2.5 } }
```

`SpeedMps` defaults to 2.5. A station whose action authors any `Walk` step **requires** `Puppet`
enabled on that action - the validator flags a `Walk` with no active puppet, and the engine denies the
engage gracefully rather than walking an invisible nothing.

The walk is real, obstacle-aware pathing (a small bounded pathfinder solved at engage per anchor, and
re-solved at the start of each walk step - so a mid-program blockage denies that step gracefully rather
than wedging the whole program), not a straight-line pass-through. A timeout guard (distance over speed
plus a grace window) means a walk can never wedge the session even if something goes wrong mid-path.

## Custody at anchors

A step's `At` field names which anchor it runs at (absent = the primary station, `self`). A
`Consume`/`Produce` phase on a step authoring `At` reads/writes THAT anchor's own custody claim, not
the primary station's - `Produce.To: "Custody"` is exactly how a program deposits an in-progress item
onto a remote block for the anchor's own cook/process loop to work.

## Walkthrough: preparing fish

RPG Stations' own repository carries a complete, zero-progression, two-station exemplar (currently held
back from the 0.1.0 jar, which ships Sawmill-only, but usable in a pack or restored from
`unreleased/`): a **Cutting Board** (the primary station, where the player presses F) and a **Cooking
Fire** (the anchor, a fully-useful standalone station in its own right that the Cutting Board's program
reaches out to). The whole loop: scale raw fish at the board, walk the fish over to a nearby fire, cook
it, walk the cooked fish back, deposit it.

### Stations/CuttingBoard.json (primary)

```json
{
  "Identity": { "NameKey": "rpgstations.station.cuttingboard.name",
                "DescKey": "rpgstations.station.cuttingboard.desc" },
  "Actions": [
    {
      "Id": "Prep",
      "Ref": "PrepFish",
      "Worker": {
        "Hold": { "MovementLock": true, "EffectId": "RPG_Station_Hold", "InterruptOnDamage": true },
        "Camera": { "Recipe": "LookRot" },
        "Puppet": {
          "Enabled": true,
          "Hide": { "Route": "Scale" },
          "Look": { "Source": "PlayerClone" },
          "Offset": { "X": 0.0, "Y": -0.4, "Z": 0.6 }, "Rotation": { "Yaw": 0.0 },
          "Prop": { "Source": "MirrorHeld", "Slot": "Hotbar" }
        }
      }
    }
  ]
}
```

The station itself is deliberately thin - its one action, `Prep`, is a reference to a standalone
`ActionAsset` (`PrepFish`), which owns every mechanical group (`Select`/`Custody`/`Tool`/`Work`/
`Anchors`/`Steps`). `Worker` is authored HERE, on the referencing entry, and REPLACES the `Ref` base's
`Worker` wholesale (a `Ref` overlay is group-wise) - `PrepFish` authors no `Worker` at all, so this is
purely additive in practice. It must carry `Puppet`, because an action authoring any `Walk` step
requires an enabled puppet: the player stays hidden at the board while their stand-in walks to the fire
and back.

### Actions/PrepFish.json (the program)

```json
{
  "Label": "rpgstations.action.prepfish.label",
  "Tool": { "Tags": { "Family": ["Dagger"] } },
  "Work": { "CycleMs": 500, "MaxDurationMs": 600000, "Looping": true },
  "Custody": { "MaxQuantity": 100, "Input": { "ResourceTypeId": "Fish" },
               "States": { "Empty": "Default", "Loaded": "Loaded" },
               "Display": { "Offset": { "Y": 0.05 }, "Scale": 0.5 } },
  "Anchors": { "Fire": { "Station": "CookingFire", "MaxRadiusMeters": 12 } },
  "Steps": [
    { "Id": "Load",
      "Consume": { "Items": [{ "ResourceTypeId": "Fish", "Quantity": 1 }], "From": "Custody" } },
    { "Id": "Scale", "Repeat": { "Times": 3 }, "Duration": { "Ms": 600 },
      "Puppet": { "Clip": "RPG_Emote_Knife" },
      "Presentation": { "Sounds": ["SFX_Daggers_T1_Slash_Impact"],
                         "Particles": [ { "SystemId": "Block_Hit_Wood" } ] } },
    { "Id": "WalkOut", "Walk": { "To": "Fire" },
      "Puppet": { "Prop": { "Source": "ItemId", "ItemId": "Food_Fish_Raw" } } },
    { "Id": "PlaceRaw", "At": "Fire",
      "Produce": { "Items": [{ "ItemId": "Food_Fish_Raw", "Quantity": 1 }], "To": "Custody" },
      "Duration": { "Ms": 400 } },
    { "Id": "Cook", "At": "Fire", "IsWork": true, "Duration": { "Ms": 2500 },
      "Presentation": { "Sounds": ["SFX_Flame_Ignite"], "Particles": [ { "SystemId": "Smoke_Black" } ] } },
    { "Id": "Harvest", "At": "Fire",
      "Consume": { "Items": [{ "ItemId": "Food_Fish_Raw", "Quantity": 1 }], "From": "Custody" },
      "Duration": { "Ms": 400 }, "Presentation": { "Particles": [ { "SystemId": "Block_Break_Dust" } ] } },
    { "Id": "WalkBack", "Walk": { "To": "self" },
      "Puppet": { "Prop": { "Source": "ItemId", "ItemId": "Food_Fish_Grilled" } } },
    { "Id": "Deposit",
      "Produce": { "Items": [{ "ItemId": "Food_Fish_Grilled", "Quantity": 1 }], "To": "Custody" },
      "Duration": { "Ms": 300 }, "Presentation": { "Sounds": ["SFX_Player_Drop_Item"] } }
  ]
}
```

`Select` is deliberately absent - this action matches any context, since the Cutting Board offers only
this one job. Reading the program beat by beat:

- `Load` consumes one fish from the board's custody (a one-shot step, no `Repeat` - the footgun rule
  from [Actions & Step Programs](actions-and-steps.md)).
- `Scale` repeats three knife-scrape beats over that one loaded fish - no further Consume.
- `WalkOut` walks the puppet to the `Fire` anchor, swapping its held prop to the raw fish along the
  way.
- `PlaceRaw`/`Cook`/`Harvest` all run `At: "Fire"` - they read and write the Cooking Fire's OWN custody
  claim, not the Cutting Board's. `Cook` authors `IsWork: true` explicitly (a pure `Duration` beat is
  not a Consume+Produce convert, so it must opt in) - that is what puts the fire into its lit
  `Custody.States.Working` look for exactly this beat.
- `WalkBack` returns the puppet to `self` (the Cutting Board) carrying the grilled fish.
- `Deposit` produces the grilled fish back into the Cutting Board's custody, where it accumulates
  until the session stops (auto-returned to the player's inventory).

The tool gate is the native **daggers** weapon family (`Tags.Family: ["Dagger"]`), matched against
every dagger tier with zero per-tier enumeration - see [Native Composition](native-composition.md).
`Work.Looping: true` plus the engine's graceful inputs-exhausted stop is "repeats while fish remain"
with no extra mode flag.

### Stations/CookingFire.json (the anchor)

The Cooking Fire is a fully standalone-useful station on its own - place raw fish directly, press F,
get grilled fish, with its own classic convert loop in ONE self-contained action (`Cook`) and its own
`Custody.States` (`Loaded` here is a distinct non-burning has-fish state; `Working` is named `Lit`,
matching the block's own lit-campfire state variant, and shows only while a cook step actively runs).
When claimed as a remote anchor, this station's own action is never invoked - only its custody claim
and its block's state matter to the visiting program.

```json
{
  "Identity": { "NameKey": "rpgstations.station.cookingfire.name",
                "DescKey": "rpgstations.station.cookingfire.desc" },
  "Block": { "Exclusive": true },
  "Actions": [
    {
      "Id": "Cook",
      "Recipe": {
        "Conversions": [ { "Input": [{ "ItemId": "Food_Fish_Raw", "Quantity": 1 }],
                            "Output": [{ "ItemId": "Food_Fish_Grilled", "Quantity": 1 }] } ],
        "FromCrafting": { "Benches": ["Campfire"], "Types": ["Processing"],
                           "NativeTime": { "Scale": 1.0, "OffsetMs": 500 } }
      },
      "Work": { "CycleMs": 2500, "MaxDurationMs": 600000, "Looping": true },
      "Custody": { "MaxQuantity": 100,
                   "States": { "Empty": "Default", "Loaded": "Loaded", "Working": "Lit" },
                   "Display": { "Offset": { "Y": 0.4 }, "Scale": 1.0 } },
      "Worker": { /* Hold/Camera/Puppet, same shape as CuttingBoard's own */ },
      "Moments": {
        "Cycle": { "Sounds": ["SFX_Flame_Ignite"], "Particles": [ { "SystemId": "Smoke_Black" } ] }
      }
    }
  ]
}
```

## Lifecycle at the edges

A server restart mid-walk or mid-program loses the session, its anchor claims, and its puppet by
construction (the work loop is never persisted) - but any custody standing at the blocks survives
with the chunks, so the materials are still there afterwards and a stale block state settles
against them on the next interaction. If a remote anchor block is broken mid-program, the session
stops with a dedicated reason, every OTHER claimed block's custody auto-returns, and any in-flight
iteration's already-consumed inputs are refunded. An exit whose player is still present (death,
damage, walk-off) releases anchor claims and refunds the ledger before the usual custody return; a
disconnect stops the session and refunds the in-flight iteration, but leaves placed custody
standing in the world for the player's return.

---

Previous: [Actions & Step Programs](actions-and-steps.md) · Next: [Custody & Placed Display](custody-and-placed-display.md)
