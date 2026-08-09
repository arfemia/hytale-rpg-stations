# Your First Station

A worked walkthrough authoring one RPG Stations station end to end.

A station is four pieces working together: a `StationAsset` JSON, a block item, a `RootInteraction`
pointing the block's `Use` at RPG Stations' one registered interaction type, and a pair of
localization keys. This walkthrough authors a simplified Sawmill-shaped station - the shipped jar
default is a good reference to compare against once you are done.

## 1. The StationAsset

Drop a file at `Server/RpgStations/Stations/<Name>.json`. The filename, lowercased, becomes the
station id every other reference (the block's interaction, an `Extension`, an anchor declaration)
uses.

A station is an ORDERED array of self-contained **actions** - even a single-purpose station like this
one authors exactly one. Nothing about an action is inherited from the station; everything mechanical
lives on the action entry itself:

```json
{
  "Identity": {
    "NameKey": "rpgstations.station.sawmill.name",
    "DescKey": "rpgstations.station.sawmill.desc"
  },
  "Block": {
    "Exclusive": true
  },
  "Actions": [
    {
      "Id": "Mill",
      "Tool": {
        "Gather": { "GatherType": "Woods", "MinPower": 0.1 },
        "Tags": { "Family": ["Hatchet"] }
      },
      "Recipe": {
        "FromCrafting": { "Categories": ["WoodPlanks"] },
        "Yield": { "Base": 1 }
      },
      "Work": {
        "CycleMs": 4665,
        "MaxDurationMs": 600000,
        "PerCycleContributions": [
          { "Channel": "yourmod:crop_quality", "Param": "OAK", "Amount": 8.0 }
        ]
      },
      "Custody": {
        "MaxQuantity": 100,
        "States": { "Empty": "Default", "Loaded": "Loaded" }
      },
      "Worker": {
        "Hold": {
          "MovementLock": true,
          "EffectId": "RPG_Station_Hold",
          "InterruptOnDamage": true
        },
        "Animation": { "Swing": { "IntervalMs": 933 } }
      },
      "Moments": {
        "Cycle": { "Sounds": ["SFX_Wood_Break"] }
      }
    }
  ]
}
```

Every group on an action is optional and nullable - omit `Bonus`, `Worker.Puppet`, `Worker.Camera`,
and `Custody` entirely for the simplest possible action. The example above:

- `Work.PerCycleContributions` posts an amount to a namespaced channel on every completed cycle. The
  channel id is opaque here; whichever mod declared it decides what the number means, and the station
  works exactly the same with nobody listening. Omit the whole array for an action that posts nothing;
  add a `ContributionScale` group to scale the posted amounts by a factor ladder before they go out.
  See [Extension Channels](extension-channels.md).
- `Recipe.FromCrafting` derives conversions from every native crafting recipe in the `WoodPlanks`
  category, instead of hand-listing every wood species; `Yield.Base` is the deterministic quantity one
  cycle makes, before any `Bonus` rolls add extra copies. See
  [Native Composition](native-composition.md).
- `Custody` opts this action into placed-input material loading (press `F` holding logs to place
  them, then press again to start working the pile). See
  [Custody & Placed Display](custody-and-placed-display.md).
- `Tool` requires holding a hatchet-family/Woods-gathering tool to start or keep working.

### Reuse with native Parent

A variant station can inherit from another by authoring `"Parent": "Sawmill"` and overriding only the
leaves it wants to change - every leaf in every group is registered for native inherit-on-omit, so a
partial override never silently drops a sibling leaf you did not mention. The same `Parent` mechanism
works between two standalone `ActionAsset` files, for sharing an action's own delta with another.

## 2. The block

The station's block is an ordinary Hytale item/block asset. Reuse a vanilla model where possible (the
shipped Sawmill reuses the Lumbermill model, the Anvil reuses the vanilla Anvil bench model - no new
art needed). Point its `BlockType.Interactions.Use` at a `RootInteraction` id you author next. If the
station uses `Custody`, give the block a matching pair of interaction states so the engine has
something to flip between:

```json
{ "State": { "Definitions": {
  "Default": { "InteractionHint": "items.RPG_Station_MyBlock.hint.empty" },
  "Loaded":  { "InteractionHint": "items.RPG_Station_MyBlock.hint.loaded" }
} } }
```

The state names here (`Default`/`Loaded`) are just a convention - they only have to match whatever
your action's own `Custody.States.Empty`/`.Loaded` name.

## 3. The RootInteraction

RPG Stations registers exactly ONE Java interaction type, `rpg_station_use`, that backs every station
block in every installed pack. One interaction JSON per block, in the **object form** so the same Java
class serves any number of stations with zero extra code:

```json
{
  "Cooldown": { "Id": "BlockInteraction", "Cooldown": 0.278, "ClickBypass": true },
  "Interactions": [ { "Type": "rpg_station_use", "Station": "sawmill" } ]
}
```

The `Station` field is the id your `StationAsset` file decodes to (the lowercased filename). Pressing
`F` on the block fires this interaction, which calls the engine's `toggle` to start or stop the
pressing player's session.

## 4. Localization keys

`Identity.NameKey`/`DescKey` above point at two keys in your own
`Server/Languages/<bcp47>/rpgstations.lang` overlay (RPG Stations reads this additively over its own
shipped file, so a pack never needs to duplicate the jar's keys):

```
station.sawmill.name = Sawmill
station.sawmill.desc = Saw logs from your inventory into planks, one cycle at a time.
```

The block's own name/description/interaction hint live in the NATIVE `items.lang` namespace instead -
see [Localization](localization.md) for the full three-family picture.

## 5. Validate

Run `/rpgstations validate` in-game or from console after a restart. It runs the full content audit
over every folded station/lootable/action/extension and chats a summary line plus every finding
(color-coded ERROR/WARNING/INFO) - the exact audit RPG Stations already runs once at boot. Every
finding is warn-only by design (a content mistake never blocks the server from starting); read the
finding codes to catch typos and missing references before a player does.

That is a complete station. From here, [Actions & Step Programs](actions-and-steps.md) covers giving
one station multiple actions and authoring a step-by-step ritual instead of the classic convert loop.

---

Previous: [Concepts](concepts.md) · Next: [Actions & Step Programs](actions-and-steps.md)
