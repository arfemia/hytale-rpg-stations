# RPG Stations

**Hands-on work stations - place the bench, press F, and watch your character work.**

RPG Stations adds interactive work stations to your Hytale server. No menus, no instant
conversions - materials go in, your character (or a stand-in performer) visibly does the work over
real time, and results come out.

**0.1.0 ships one station: the Sawmill.**

Craft the bench at a tier 2 Workbench, load logs onto it,
press `F`, and your character saws them into that wood family's planks one cycle at a time, with a
held-tool gate, a tool-scaling yield curve, and a session summary when you stop.

The bench pays for the time it takes, and it pays by the tool you bring: a starter hatchet mills one
plank per log, a copper two, the best forgeable hatchets four, and the sawmill's own trophy hatchet
five. Mid-ladder tools land on fractional yields: two planks, and often a third. Staying at the
bench pays too: past ten cycles a decent hatchet starts shaking offcuts loose from the milled logs -
plant fibre, tree bark, tree sap and sticks - and the finds get richer the longer one session runs,
with life essence joining from the second tier and concentrated essence at the deepest. And a few
cycles into any session worked with mithril-grade steel, every cycle carries a 1-in-2500 shot at the
**Sawmiller's Hatchet**. It drops nowhere else, no bench can forge it, and it is the only tool that
reaches the top rung of the sawmill's own curve.

Every number in all of that is plain JSON, so a server can retune what better tools are worth,
change what the finds hand over, or key the whole thing off something else entirely.

The engine underneath is the full thing, not a Sawmill special case: it also supports multiblock
builds (arrange ordinary blocks in the world and the shape becomes a station), named placement
sockets, timed cooking windows, and unattended processing that keeps working while nobody stands
there, all ready for content packs and future stations to use. Multi-action stations, step
programs, multi-station walks, placed-input custody and props, the puppet performer, conditional
loot, and enhancement stamping are live and authorable the same way. Every one of them is driven
from ordinary content assets, so a pack (or your own server-side assets) can add stations this
release does not ship. Later releases will grow the shipped default set.

Full documentation (a page-by-page authoring guide and a complete schema reference for every
content type) lives in the mod's [GitHub repository](https://github.com/arfemia/hytale-rpg-stations).

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/5NFdZsUxHZ) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/ziggfreed)

---

[![Host your own Hytale server with Kinetic Hosting](https://i.imgur.com/UHn3FzW.png)](https://billing.kinetichosting.com/aff.php?aff=1262)

---

## Required dependency: ZiggfreedCommon

RPG Stations has exactly **one hard dependency**: **ZiggfreedCommon `>=2.0.1`**. Install it first -
the server loads it before RPG Stations. That is the whole list - **no other mod is required**. RPG
Stations is a complete, standalone reward loop (conditional loot, command rewards, enhancement) on
its own. See [Integrations](#integrations) below for how an add-on hooks it.

## Features

### The work loop

Place a station block, press `F`, and your character starts working: real-time cycles, a held-tool
gate, a swing animation with its own impact cue, and a session summary when you stop. Every station
is an ordinary content asset, so a server owner gets the same authoring surface the mod's own
default content ships through.

### Multi-action stations

One station block can offer several distinct jobs, picked by what you're holding and what stands at
the block. No dropdown. Each job carries its own requirements, and the press picks the first job
whose requirements actually hold. The Sawmill uses the lighter form: sneak and press `F` to pick
which cut you want from the log you're holding (planks, decorative, or ornate) if you do not want
the default.

### Step programs and multi-station walks

A station's work can be authored as a step-by-step program, not only a simple convert loop - a
sequence of beats (a hold, a swing, a sound) composed with consume/produce/loot/command phases in
one fixed order. A program can even reach out to a SECOND, separately-placed station nearby: your
character (or performer) walks over, works at the remote station, and walks back, all from one `F`
press on the primary block.

### Placed-input custody and displays

Load materials directly into a station by pressing `F` while holding them - the whole stack loads
in, a repeat press tops it up. Loaded materials can render as a real placed prop at the block (logs
stacked on the sawmill bench), retrievable with a press of `F` straight off the display. What you
place stays placed like a chest's contents: it survives logging off and server restarts, waiting
in the station until you work it, take it back, or the block is broken.

### The puppet

Optionally hide your character entirely and spawn a stand-in performer to do the work instead - a
clone of your own look, a fixed model, or a full NPC. The performer walks, swings, and holds props
exactly where the animation calls for them.

### Conditional loot

Every station can roll bonus rewards on top of its normal output: extra copies of the result, a
chance-gated drop table, a floor ladder that scales with any stat your server tracks (tool power,
session length, whatever another installed mod writes) - all built from the same small set of
weighted rolls every loot site in the mod uses. Extra copies of the result can be fractional, so a
mid-ladder tier can be worth "two and often a third" without rounding onto a neighbouring rung.
Every find can carry its own sound-and-particle celebration, and a cue only plays once its reward
actually handed something over, so a drop table that rolled nothing stays silent.

Drop tables compose too, using Hytale's own drop-list format. The sawmill's four find tiers each
combine a shared offcut list (referenced by id, so retuning what milling yields is one edit that
moves every tier at once) with their own life-essence roll, and a richer tier pulls that shared
list more times. Each of the mod's loot tables holds a single roll on purpose, so a pack can
replace exactly the one it wants to retune without inheriting the rest.

### Enhancement stamping

An authorable ritual for gear: place a weapon at a station, strike it, and roll stats onto it from a
configurable pool, capped by a composable budget model (a flat ceiling, a stat-scaled ceiling, or
both at once - the tighter one wins). Durability upgrades land with no other mod installed at all.
The engine ships this capability; 0.1.0 ships no default station that uses it, so it is here for
pack authors to build on.

### Flairs

Cosmetic overlays - fancier sounds, extra particles - that a player can unlock and that apply
automatically at specific moments of a station's work loop. Any pack can ship a flair targeting any
station without touching that station's own file.

### An extension surface for pack authors

A fourth-party pack can additively extend another pack's station, action, loot table, or roll pool
- append a contribution on a new channel, a new loot reference, an extra ritual step - without
owning or replacing the original file. See the Extending Other Packs guide in the full docs.

## The Player Experience

Approach a station block and press `F`. If it takes placed input, load your materials first with an
initial press, then press again to start the work loop - your camera pulls in, your character (or
its stand-in) starts the cycle, and results accumulate as it runs. Press `F` again, or step away, to
stop and collect a summary of what you made, what dropped, and what you earned. Some stations offer
more than one job - just hold what you want worked and the station figures out which action to run.
Sneaking and pressing `F` opens a recipe picker on a station that offers more than one output
category, previewing whatever material is currently placed in the block.

## For Server Owners

Everything here is a Hytale content asset: every station, action, lootable table, roll pool, flair,
extension, and the engine's own settings is a file a pack (or a server owner's own override layer)
ships, folded `defaults < pack < owner` exactly like every other asset the engine loads.

| Folder | What it holds |
|---|---|
| `Server/RpgStations/Stations/` | Station definitions - the work loop, its tool gate, its loot, its actions |
| `Server/RpgStations/Patterns/` | Multiblock structure shapes - the block arrangements that become stations when built |
| `Server/RpgStations/Actions/` | Standalone, reusable actions a station attaches by reference |
| `Server/RpgStations/Flairs/` | Cosmetic unlock overlays |
| `Server/RpgStations/Extensions/` | Additive extensions onto another pack's content |
| `Server/RpgStations/Settings/` | The one server-wide `Settings.json` - engine on/off, session-summary HUD |
| `Server/ZiggfreedCommon/Lootables/` | Reusable conditional-loot tables |
| `Server/ZiggfreedCommon/RollPools/` | Reusable enhancement stat-roll pools |

The last two folders belong to the shared Ziggfreed Common library rather than to this mod, because
the loot model is shared: the same table, authored once, behaves the same way at a station as it
does anywhere else a mod built on that library rolls loot.

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

1. Install **ZiggfreedCommon** (`>=2.0.1`) - drop it into your server's `Mods/` folder.
2. Drop the **RPG Stations** jar into the same `Mods/` folder.
3. Optionally add a content pack that ships station catalog content.
4. Restart the server. Confirm the boot log shows each loaded station id and no asset validation
   failures, or run `/rpgstations validate` at any time.

**Getting the Sawmill in-game.** On a bare install players craft the bench themselves at a **tier 2
Workbench**: one crude hatchet (the tool becomes part of the bench), any one log, and any four
planks. The tier gate puts it behind the first workbench upgrade, so the sawmill is something a
base earns. Admins can `/give` the block directly
(`RPG_Station_Sawmill`). A content pack that ships its own block under the same id replaces the
jar's, so a pack that authors no recipe on its copy removes that craftability and owns acquisition
its own way - a shop, a quest, whatever that pack's economy wants.

## Integrations

RPG Stations exposes a small, typed extension surface (native events plus typed registries) any mod
can hook to turn completed work cycles into its own rewards, without RPG Stations ever depending on
that mod. With no listener installed, every station still runs its full standalone loot and command
reward loop.

It works in two directions, one shape each. An add-on registers **factors** - namespaced ids a
station's loot, gate, and budget formulas read a number from - and declares **channels**, namespaced
ids a station posts amounts to on every completed cycle and on a rare find. RPG Stations resolves
nothing on the write side: it forwards each `{Channel, Param, Amount}` verbatim and lets the
channel's owner decide what it means. An add-on can also add rows to the session-summary panel,
answer which cosmetic flairs a player has unlocked, encode how enhancement points are written onto
an item, and register its own content checks that run inside RPG Stations' validate pass.

Known integrations: **MMO Skill Tree**. See the Extension Channels and Add-ons & Integrations pages
in the full docs for the contract, a complete worked example, and the presence-check idiom a
consumer needs.

## Changelog

See `CHANGELOG.md` in this mod's repository for the full version history.

## Links & Support

- [Source, docs and issues on GitHub](https://github.com/arfemia/hytale-rpg-stations)
- Questions or suggestions? Join the [Discord](https://discord.gg/5NFdZsUxHZ) or leave a comment.

---

_RPG Stations is not affiliated with Hypixel Studios or Hytale._
