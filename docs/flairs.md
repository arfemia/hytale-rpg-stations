# Flairs

The open flair/moment vocabulary and the standalone FlairAsset.

A **flair** is a cosmetic `Presentation` overlay - a fancier sound, extra particles, a camera shake -
that applies at a specific moment of a station's work loop, gated behind a player unlocking it
(through whatever mod registers a `FlairUnlockProvider`; RPG Stations itself never decides who has
unlocked what). Flairs are purely presentational: they never change what a station produces, only how
it looks and sounds while doing it.

## The open moment vocabulary

A flair is keyed by a **moment id** - an open string, never a fixed enum, so a future engine version
can emit a new moment without a schema break. Five well-known ids cover the built-in engine moments:

| Moment id | Fires when |
|---|---|
| `cycle` | Each completed work cycle. |
| `swing` | Each per-swing animation beat. |
| `impact` | Each swing too, one moment later - it is the strike landing, and what makes it late is its own `Presentation.DelayMs`. |
| `rare_find` | A `Roll` or a reached `Ladder.Floor` pays out with a cue of its own. |
| `completion` | The session ends (non-silent, at least one completed cycle). |

**An action authors its own cues under the same ids**, in its `Moments` map (see
[Actions and Steps](actions-and-steps.md)) - one open `momentId -> Presentation` map holding
everything that station action sounds and looks like. A flair then overlays whatever is there, per
leaf, for the moment ids it names. Where the engine already holds a more specific presentation for an
emission - a step's own `Presentation`, a loot floor's cue - that one plays and the map entry is not
consulted for it.

`rare_find` is the one moment an action does NOT author: it fires only with the earning `Roll` or
`Ladder.Floor` cue already in hand, so author the presentation there (the validator warns on an
action `Moments` entry keyed `rare_find`, which could never play). A flair still overlays it by that
id like any other moment.

A step program adds a SIXTH kind of moment id automatically for every step:
`step:<actionId>:<stepId>`, letting a flair target one specific beat of a specific ritual (for example
`step:enhance:stamp` for the Anvil's enhance-commit beat specifically). An unrecognized moment id is a
content-audit note (typo detection), never an error - an older-authored flair never breaks against a
newer engine that has grown new moment ids.

## The standalone FlairAsset

A flair lives in its own file, `Server/RpgStations/Flairs/<Name>.json` (Pattern A, id = lowercased
filename) - ANY installed mod or pack can ship one, targeting any station, without touching that
station's own file at all:

```json
{
  "Stations": ["sawmill"],
  "Moments": {
    "swing": { "Particles": [ { "SystemId": "Petal_Burst" } ] },
    "step:enhance:stamp": { "Sounds": ["SFX_Choir_Hit"] }
  }
}
```

`Stations` null or empty means "applies to every station"; `Moments` is a plain moment id ->
`Presentation` map. A grantor unlocks this asset's own id (the filename) for a player through
whichever unlock mechanism it uses; RPG Stations just consults the result per moment.

## A station's own inline flairs

A station can also author its own `Flairs` map directly, the SAME open `{Moments}` shape as a
standalone `FlairAsset` - a pure authoring convenience for a flair that will only ever apply to this
one station and does not need to be shareable across packs.

## How they combine

The effective flair set for a station is the UNION of its own inline `Flairs` map with every folded
`FlairAsset` whose `Stations` list applies to it. When a standalone asset ships the same flair id as
the station's own inline entry, the standalone asset's `Moments` win for that id. This means multiple
packs can each ship their own flair asset targeting the same station with no coordination needed, as
long as they use distinct flair ids.

## Not extension-composable, by design

Flairs are deliberately excluded from the [Extending Other Packs](extending-other-packs.md) mechanism
- a cosmetic overlay never needs to be additively merged into an existing flair's `Moments`, because
the union above already composes any number of separately-authored flairs cleanly. To add a new
cosmetic to an existing station, ship another `FlairAsset` naming that station; there is nothing to
extend.

---

Previous: [Enhancement & Stamp](enhancement-and-stamp.md) · Next: [Extending Other Packs](extending-other-packs.md)
