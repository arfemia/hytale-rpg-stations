# RPG Stations

**Adds work stations to Hytale that your character works by hand: a Sawmill and two Disenchanting Tables.**

Place a station, load it and press F. Your character sits down and does the work in real time while you look around, and the results come out cycle by cycle. No menus. No instant crafting.

It needs [Ziggfreed's CommonLib](https://www.curseforge.com/hytale/mods/ziggfreeds-commonlib) 2.3.0 or newer and a Hytale Update 7 (0.7.x) server. Nothing else.

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/5NFdZsUxHZ) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/ziggfreed)

---

[![Host your own Hytale server with Kinetic Hosting](https://i.imgur.com/UHn3FzW.png)](https://billing.kinetichosting.com/aff.php?aff=1262)

---

## Features

### The Sawmill

Craft it at a tier 2 Workbench from a Crude Hatchet, a log and four planks. Hold some logs and press F on it to load them, then press F again to start sawing. Each cycle turns a log into planks of the same wood. Sneak and press F to pick decorative or ornate cuts instead.

A better hatchet gets more planks out of each log:

- Crude: 1
- Copper: 2
- Iron: 2, and often a third
- Thorium, Cobalt or Adamantite: 3
- Onyxium or Mithril: 4
- The Sawmiller's Hatchet: 5

With an Iron hatchet or better, cycles also turn up plant fibre, bark, sap and sticks, and the longer you stay at the bench, the better the finds, up to Essence of Life. A few cycles in with a Mithril hatchet, every cycle has a 1 in 2,500 chance at the Sawmiller's Hatchet. It drops nowhere else.

### The Disenchanting Tables

Craft the Disenchanting Table at a tier 2 Workbench from six Iron Ingots, four logs, four rocks and twelve Essence of Life. Put a piece of gear on it and press F. Over about 15 seconds the piece comes apart and gives back what the Salvager's Workbench would, plus Essence of Life.

- Gear of item level 20 or higher has a 1 in 200 chance at the Disenchanter's Grimoire.
- Each of the four Disenchanter's Kit pieces has a 1.5% chance per piece of gear.
- Wearing the kit, holding the grimoire and drinking a Disenchanter's Draught from the Alchemist's Workbench each make it faster. With all of them it takes about 3 seconds.

The Greater Disenchanting Table works three pieces in a row and pays more. Make it at the Arcanist's Workbench from a Disenchanting Table, eight Thorium Ingots, six Bolts of Silk and twenty Essence of the Void.

The tables only take gear, and no station takes a bag with something still in it.

### When you stop

Press F again, or walk away, and you get a summary of what you made and what you found. Whatever you loaded stays in the station, like a chest, until you work it or take it back.

### With MMO Skill Tree

Install [MMO Skill Tree](https://www.curseforge.com/hytale/mods/mmo-skill-tree) and the [MMO Skill Stations Pack](https://www.curseforge.com/hytale/mods/mmo-skills-stations-pack) and every cycle pays skill XP.

## For Server Owners

- Admins can `/give` the stations: `RPG_Station_Sawmill`, `RPG_Station_Disenchanting_Table` and `RPG_Station_Disenchanting_Table_Greater`.
- `/rpgstations validate` checks every loaded station and loot table and reports any problem in chat. The same check runs when the first player joins after a restart, and writes what it finds to the server log.
- Every number above (planks per hatchet, find chances, ritual times) is plain JSON, so a pack can retune it.

## For Mod and Pack Authors

Stations are ordinary content assets under `Server/RpgStations/`, so a pack can add new stations, change the shipped ones, or add to another pack's station without replacing its file. The engine also supports stations built from a shape of placed blocks, a stand-in performer that does the work for you, and rolling stats onto gear.

A mod can turn finished cycles into its own rewards through events, with no dependency either way. MMO Skill Tree is the one that does today. The full authoring guide and schema reference are in the [GitHub repository](https://github.com/arfemia/hytale-rpg-stations).

## Installation

1. Drop Ziggfreed's CommonLib 2.3.0 (or newer) into your server's `Mods/` folder.
2. Drop the RPG Stations jar into the same folder.
3. Restart the server.

## Links & Support

- [Source, docs and issues on GitHub](https://github.com/arfemia/hytale-rpg-stations)
- [Patch notes for every version](https://github.com/arfemia/hytale-rpg-stations/tree/main/patch-notes)
- Questions or suggestions? Join the [Discord](https://discord.gg/5NFdZsUxHZ) or leave a comment.

---

_RPG Stations is not affiliated with Hypixel Studios or Hytale._
