# Puppet & Performers

Hide the player, spawn a performer that does the work instead.

By default a session holds the real player in place and plays the work animation directly on their
body. The optional `Puppet` group flips that: it hides the player entirely and spawns a stand-in
**performer** that visibly does the work - the maintainer's own framing is "mount the player, hide
their player model, and spawn a visual of their character model performing the steps." A station whose
action authors any `Walk` step (see [Multi-Station Programs](multi-station-programs.md)) REQUIRES a
puppet - there is nothing else to walk.

`Puppet` lives nested inside `Worker`, alongside `Hold`/`Camera`/`Animation` - orthogonal to whichever
mount mechanism holds the real player, never nested under `Hold` itself. `Custody` stays its own
top-level action group (it is data-plane, placed-input state, not presentation). `Worker` is
whole-group overridable per action, exactly like every other action group.

## The Puppet group

```json
"Worker": {
  "Puppet": {
    "Enabled":  true,
    "Hide":     { "Route": "Scale" },
    "Look":     { "Source": "PlayerClone" },
    "Offset":   { "X": 0.0, "Y": -0.4, "Z": 0.6 },
    "Rotation": { "Yaw": 0.0 },
    "Prop":     { "Source": "MirrorHeld", "Slot": "Hotbar" }
  }
}
```

`Offset`/`Rotation` place the puppet relative to the station's block-top anchor - the same convention
`Custody.Display` follows: `X`/`Z` are in the placed block's own horizontal frame (`+Z` its front),
`Y` is vertical and never rotated, and the block's own facing yaw folds additively into
`Rotation.Yaw`, so `Yaw: 0` means "faces the same way the block does". `Rotation.Pitch` and
`Rotation.Roll` are the puppet's OWN tilt about its own axes and are NOT composed with the block
facing (the same rule `Presentation.Particles[].RotationOffset` follows); both default to 0, so a
puppet that only wants a facing authors `Rotation.Yaw` alone.
At a default-orientation placement (block yaw 0) the local frame equals the world frame, so a station
that only authors a vertical `Offset.Y` behaves exactly as a naive world-space offset would and needs
no re-tuning. Presence of the group with `Enabled` not explicitly `false` activates the whole route -
spawn and hide together; `false` keeps the classic in-body worker regardless of what the other leaves
author (useful for flipping a puppet off on a `Parent`-inherited variant while keeping the rest of the
group).

## Hide.Route

`Hide.Route` is a union discriminator, not a mode - three structurally different arms:

- `"Scale"` (the default, proven in-game) - fully hides the puppeteer's own rendered body, including
  the held item, in both first- and third-person.
- `"Effect"` - schema-reserved for a future native-effect-based hide route; not implemented yet.
- `"None"` - the degraded fallback: the puppet spawns but the real player stays visible too.

## Look.Source

`Look.Source` is a three-arm union deciding who/what the performer looks like:

| Source | Group read | Behavior |
|---|---|---|
| `PlayerClone` (default) | none | The puppet clones the live player's own skin - "their character model." |
| `Model` | `Look.Model {ModelId, FallbackModelId}` | A fixed authored model, regardless of who is working (e.g. an apprentice or golem stand-in). |
| `NpcRole` | `Look.Role` (below) | A Role-driven NPC entity performs the work instead of a player-shaped puppet. |

`Look.Model.FallbackModelId` doubles as the resolution-ladder fallback for the WHOLE Look group, any
source: an unresolvable primary look (a clone failure, a dangling `ModelId` or role id) falls to it,
then to the engine's own default rig - never a red-X or a crash.

### The NpcRole performer

```json
"Look": { "Source": "NpcRole", "Role": {
  "RoleId": "SomeRole",
  "SkinSource": "PlayerClone",
  "Persist": false,
  "SpeedMps": 2.5
} }
```

| Field | What it does |
|---|---|
| `RoleId` | The native Role asset id backing this performer (an id-ref-only reference - see [Native Composition](native-composition.md)). Required for this source; a dangling id falls back to the bare player-shaped puppet with one warning. |
| `SkinSource` | `PlayerClone` (default, the Role NPC wears a clone of the working player's own skin) or `RoleDefault` (the role asset's own model). |
| `Persist` | `false` (default) spawns the NPC as a transient, non-serialized entity, matching the bare-puppet posture. `true` is reserved for a future persistent-hireling posture. |
| `SpeedMps` | The performer's walk-speed override; null defers to the role asset's own configured walk speed (2.5 m/s parity with the default puppet). |

At engage the engine reads `Look.Source` and picks the backend: `PlayerClone`/`Model` use the proven
player-shaped puppet; `NpcRole` uses the Role-driven backend, with an engage-time fail-closed fallback
to the player-shaped puppet (one warning logged) when the role id is blank or unregistered. Every later
mutation during the session (clip changes, prop swaps, despawn) goes through the same per-call accessor
regardless of which backend is active, so step programs never need to know which performer is running.

## Prop

```json
"Prop": { "Source": "MirrorHeld", "Slot": "Hotbar" }
```

`Prop.Source` defaults to `MirrorHeld` (the puppet holds a live copy of whatever the player is
holding). `ItemId` forces a specific held prop (a ritual can hand the puppet a specific tool regardless
of what the player holds); `None` empties the puppet's hands. This exact shape is reused verbatim by a
per-step `Puppet.Prop` override for a moment-to-moment swap (the fish exemplar swaps the carried prop
to the raw fish while walking out, then to the grilled fish while walking back).

## The walk mechanism

A step's `Walk` phase (see [Multi-Station Programs](multi-station-programs.md)) moves the PUPPET, not
the real player, along an obstacle-aware path toward a declared anchor, and toggles its movement state
so the walk renders as an actual walking gait rather than a slide.

## Identity and reconcile

Every spawned performer carries a marked identity component so the engine can find and clean up any
performer left over from a crash or restart: at server boot, one reconcile sweep despawns every
performer with no live session behind it, and a fresh engage also runs a targeted stale-performer sweep
for the block being interacted with. Combined with the "nothing here is persisted" rule (see
[Concepts](concepts.md)), this means a puppet or NPC performer never survives a restart as an orphaned
entity, even after an ungraceful shutdown.

---

Previous: [Custody & Placed Display](custody-and-placed-display.md) · Next: [Selection & Output Categories](selection.md)
