# RPG Stations

**Diegetic work stations - place, press F, watch your character work.**

RPG Stations adds interactive work stations to your Hytale server: a Sawmill that saws logs into
planks one cycle at a time, an Anvil that sharpens bars and rolls stats onto your gear, a
fish-preparation counter that walks your character over to a nearby fire and back. No menus, no
instant conversions - materials go in, your character (or a stand-in performer) visibly does the
work over real time, and results come out.

Full documentation, including a page-by-page authoring guide and the complete schema reference for
every content type, ships alongside this mod's release.

## Required dependency: ZiggfreedCommon

RPG Stations has exactly **one hard dependency**: **ZiggfreedCommon `>=1.4.0`**. Install it first -
the server loads it before RPG Stations. There is **no dependency on any progression mod** - RPG
Stations is a complete, standalone reward loop (conditional loot, command rewards, enhancement) on
its own. See [Integrations](#integrations) below for the optional MMO Skill Tree pairing.

## Features

### The work loop

Place a station block, press `F`, and your character starts working - a real-time cycle cadence,
a held-tool gate, a swing animation with its own impact cue, and a session summary when you stop.
Every station is authored as an ordinary content asset, not a hardcoded Java class - a server owner
gets the exact same authoring surface RPG Stations' own default content ships through.

### Multi-action stations

One station block can offer several distinct jobs, picked diegetically by what you're holding - no
dropdown, no menu. The Anvil is the flagship example: hold a metal bar and it sharpens it, hold a
weapon and it opens the enhancement ritual instead.

### Step programs and multi-station walks

A station's work can be authored as a step-by-step program instead of a simple convert loop - a
sequence of beats (a hold, a swing, a sound) composed with consume/produce/loot/command phases in
one fixed order. A program can even reach out to a SECOND, separately-placed station nearby: your
character (or performer) walks over, works at the remote station, and walks back, all from one `F`
press on the primary block.

### Placed-input custody and displays

Load materials directly into a station by pressing `F` while holding them - the whole stack loads
in, a repeat press tops it up. Loaded materials can render as a real placed prop at the block (logs
stacked on the sawmill, a bar or weapon resting on the anvil), retrievable with a press of `F`
straight off the display.

### The puppet

Optionally hide your character entirely and spawn a stand-in performer to do the work instead - a
clone of your own look, a fixed model, or a full NPC. The performer walks, swings, and holds props
exactly where the animation calls for them.

### Conditional loot

Every station can roll bonus rewards on top of its normal output: extra copies of the result, a
chance-gated drop table, a floor ladder that scales with any stat your server tracks (tool power,
skill luck, whatever a progression mod writes) - composed from a small, weighted vocabulary shared
across every loot site in the mod.

### Enhancement stamping

The Anvil's flagship ritual: place a weapon, strike it, and roll stats onto it from a configurable
pool, capped by a composable budget model (a flat ceiling, a stat-scaled ceiling, or both at once -
the tighter one wins). Durability upgrades land even with no progression mod installed at all.

### Flairs

Cosmetic overlays - fancier sounds, extra particles - that a player can unlock and that apply
automatically at specific moments of a station's work loop. Any pack can ship a flair targeting any
station without touching that station's own file.

### An extension surface for pack authors

A fourth-party pack can additively extend another pack's station, action, loot table, or roll pool
- append a new skill's XP grant, a new loot reference, an extra ritual step - without owning or
replacing the original file. See the Extending Other Packs guide in the full docs.

## The Player Experience

Approach a station block and press `F`. If it takes placed input, load your materials first with an
initial press, then press again to start the work loop - your camera pulls in, your character (or
its stand-in) starts the cycle, and results accumulate as it runs. Press `F` again, or step away, to
stop and collect a summary of what you made, what dropped, and what you earned. Some stations offer
more than one job - just hold what you want worked and the station figures out which action to run.
Sneaking and pressing `F` opens a selection menu on a station that offers more than one output
category, or the familiar crafting-bench window on a station built around one.

## For Server Owners

RPG Stations content is **asset types, not config files** - every station, action, lootable table,
roll pool, flair, extension, and the engine's own settings is an ordinary Hytale content asset a
pack (or a server owner's own override layer) ships, folded `defaults < pack < owner` exactly like
every other asset the engine loads.

| Folder | What it holds |
|---|---|
| `Server/RpgStations/Stations/` | Station definitions - the work loop, its tool gate, its loot, its actions |
| `Server/RpgStations/Actions/` | Standalone, reusable actions a station attaches by reference |
| `Server/RpgStations/Lootables/` | Reusable conditional-loot tables |
| `Server/RpgStations/RollPools/` | Reusable enhancement stat-roll pools |
| `Server/RpgStations/Flairs/` | Cosmetic unlock overlays |
| `Server/RpgStations/Extensions/` | Additive extensions onto another pack's content |
| `Server/RpgStations/Settings/` | The one server-wide `Settings.json` - engine on/off, session-summary HUD |

The Settings asset (`Server/RpgStations/Settings/Settings.json`) is the mod's on/off switch and its
session-summary HUD tuning, layered like any other asset - there is no separate config file for it.

### Commands Reference

`/rpgstations <camera|validate> [action]` - admin-gated.

- `/rpgstations camera <preset>` - set your own station-camera tuning override for your next
  session (transient, never persisted).
- `/rpgstations camera list` - list every known camera preset and your current one.
- `/rpgstations validate` - run the full content audit over every loaded station, action, lootable,
  and extension, and report every finding in chat (the same audit that runs once automatically at
  server boot).

## Installation

1. Install **ZiggfreedCommon** (`>=1.4.0`) - drop it into your server's `Mods/` folder.
2. Drop the **RPG Stations** jar into the same `Mods/` folder.
3. Optionally add a content pack that ships station catalog content.
4. Restart the server. Confirm the boot log shows each loaded station id and no asset validation
   failures, or run `/rpgstations validate` at any time.

## Integrations

RPG Stations exposes a small, typed extension surface (native events plus a registry) any
progression mod can hook to turn completed work cycles into its own rewards, without RPG Stations
ever depending on that mod. No installed listener, and every station still runs its full standalone
loot/command reward loop with zero XP granted.

**MMO Skill Tree** is the first mod to pair with it: install both, and each completed work cycle
forwards its declared XP asks to the MMO's skill system, station loot formulas can read any of the
MMO's stat channels (luck, skill level, and more), and the session summary panel gains XP rows
alongside RPG Stations' own totals. Neither mod hard-depends on the other - RPG Stations runs a
complete standalone experience without MMO Skill Tree installed, and MMO Skill Tree runs unaffected
without RPG Stations installed.

## Changelog

See `CHANGELOG.md` in this mod's repository for the full version history.

## Links & Support

Report issues or ask questions through this mod's CurseForge page or its author's support channel.

---

_RPG Stations is not affiliated with Hypixel Studios or Hytale._
