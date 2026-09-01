# Concepts

Stations, sessions, actions, steps, custody, and the puppet - the RPG Stations vocabulary.

RPG Stations models a diegetic work loop: a player interacts with a placed block, their character
performs the work in-world (swinging a saw, hammering an anvil, scaling a fish), and materials convert
into results over real time instead of an instant menu transaction. Six concepts cover the whole
engine.

## Stations and sessions

A **station** is one `StationAsset` JSON, one in-world block, and one registered interaction handler
tying them together - a Sawmill, a Cutting Board, an Anvil. The station asset itself stays thin:
`Identity` (name/desc/icon), `Block` (is the placed block exclusive to one worker), `Requires` (the
station-entry gate), `Flairs`, and `Actions` - an ORDERED array of self-contained jobs. Everything
mechanical (`Recipe`, `Work`, `Tool`, `Custody`, `Bonus`, `Worker`, `Moments`, and the rest) lives on
each action, not on the station.

A **session** is the transient, in-memory work loop a player starts by pressing `F` on a station
block. Sessions are never persisted - a server restart or crash loses every in-flight session by
construction, and the volatile state around one (the puppet, display entities, anchor claims)
self-heals on the next interaction. Placed custody is the exception: it lives on the block's own
chunk and survives restarts (see [Custody & Placed Display](custody-and-placed-display.md)). One
entry point (`toggle`) starts or stops a
session; one exit funnel (`stop`) handles every reason a session ends (re-press, crouch, walk off,
damage, death, disconnect, tool broke, ritual complete, inputs exhausted, an anchor block was broken,
a path was blocked, or server shutdown) and always fires a completion event, silent stops included.

## Actions

A station runs one or more self-contained **actions**, authored as an ORDERED array on `Actions`. A
single-purpose station like the Sawmill authors exactly one action; nothing about it is inherited from
the station - the action carries its own `Recipe`/`Work`/`Tool`/`Custody`/etc. in full.

A **multi-action station** (the Anvil is the flagship example) authors two or more entries in that
array, each its own complete action. Which action a session runs is decided **diegetically**: at
engage, the engine matches the player's held item (or a loaded custody claim) against each action's
`Select` matcher, IN AUTHORED ORDER, and picks the first that fits - no menu, no dropdown, just "what
are you holding". An action with no `Select` matches any context (its custody acceptance derives from
its own `Recipe` inputs instead), so authoring order also doubles as fallback priority. A standalone,
reusable action can also live in its own file (an `ActionAsset`) and be attached to a station by
reference (`Ref`). See [Actions & Step Programs](actions-and-steps.md).

## Steps

An action either runs the **implicit program** (the classic convert-consume-produce-roll-present loop
every plain station uses) or an authored **step program** - an ordered array of `StationStep`s. Each
step composes any combination of independent phases (Walk, Consume, Stamp, Produce, Roll, Commands) in
one fixed order, plus a post-phase `Duration` hold and an iteration `Repeat` count. A step authoring no
phase at all is a pure **beat** - just a clip, a presentation cue, and a hold, used for the anvil's
hammer strikes. See [Actions & Step Programs](actions-and-steps.md).

## Custody

**Custody** is a placed-input claim: press `F` while holding a matching stack and it loads the
whole stack into the block (a repeat press tops it up), then a press by the owner starts working
from that placed pile instead of the live backpack. Placed custody is stored on the block's own
chunk, so it survives a logoff, a restart and a chunk unload - materials stay in the station until
worked, retrieved, or the block is broken - and can optionally render as a real placed-as-entity
prop at the block. See [Custody & Placed Display](custody-and-placed-display.md).

## The puppet

By default a session holds the real player in place and plays the work animation on their own body.
An optional **puppet** route instead hides the player entirely and spawns a stand-in performer (a
clone of the player's own look, a fixed model, or a Role-driven NPC) that visibly does the work -
including walking between a primary station and a nearby anchor station in a multi-station program.
See [Puppet & Performers](puppet-presentation.md).

## Content, not config files

Every one of the concepts above is authored as a Hytale asset-pack JSON under
`Server/RpgStations/<Type>/*.json` - stations, standalone actions, lootables, roll pools, flairs,
extensions, and settings. There is no separate config-file layer; adding or changing content is adding
or editing an asset, and a server owner or pack author gets the exact same authoring surface RPG
Stations itself ships its default content through.

---

Previous: [Getting Started](getting-started.md) · Next: [Your First Station](your-first-station.md)
