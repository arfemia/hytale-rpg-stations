# Getting Started

Install RPG Stations on your Hytale server.

RPG Stations is a standalone Hytale server mod that adds diegetic interactive work stations - place a
block, press `F`, and watch your character (or a stand-in performer) do the work. It is a server
plugin, package root `com.ziggfreed.rpgstations`, and it ships as its own jar with no other mod
involved.

## Dependencies

RPG Stations has exactly **one hard dependency**: **ZiggfreedCommon** (`>=1.4.0`). ZiggfreedCommon is
a shared, mod-agnostic Hytale primitive library (camera/sound/HUD primitives, the puppet/performer
engine, the generic cast-step kernel) that RPG Stations builds its work loop on top of. That is the
whole list - RPG Stations runs a complete, rewarding work loop (conditional loot, command rewards,
enhancement) with **no other mod required**.

**Other mods are optional add-ons.** When one is installed alongside RPG Stations, a soft surface
(native events and typed registries - never a hard code dependency in either direction) lets it read
numbers into a station's formulas and receive the amounts a station posts out on each completed
cycle. See [Extension Channels](extension-channels.md) for that contract and
[Add-ons & Integrations](integrations.md) for the rest of the api surface.

## Installing

1. Install **ZiggfreedCommon** first - drop its jar into your server's `Mods/` folder.
2. Drop the **RPG Stations** jar into the same `Mods/` folder.
3. Optionally add a content pack that ships station content - RPG Stations ships its own minimal
   jar-default content (a Sawmill) so it is playable with no pack at all, but a pack is where most
   server owners will get their station catalog from.
4. Restart the server.

Confirm the mod loaded by checking the boot log for a Station asset layer fold line naming each loaded
station id (for example `sawmill`), and no `Asset validation FAILED` entries. Run
`/rpgstations validate` in-game (or from console) at any time to re-check the full content audit.

## Enabling

RPG Stations has no traditional on/off config file toggle - it is governed by an ordinary content
asset, the **Settings asset** (`Server/RpgStations/Settings/Settings.json`), the same way every other
piece of RPG Stations content is authored. The jar ships a default with `Enabled: true`, so the engine
is live out of the box; a server owner (or a pack) can layer their own `Settings.json` over it to
disable the engine entirely or retune the session summary HUD. See [Settings](settings.md) for the
full shape.

## What's next

Read [Concepts](concepts.md) for the vocabulary (station, session, action, step, custody, puppet),
then follow [Your First Station](your-first-station.md) to author one station end to end.

---

Next: [Concepts](concepts.md)
