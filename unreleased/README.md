# unreleased/ - content held back from the 0.1.0 release

**Nothing here is deleted, abandoned, or broken.** These assets are finished, reviewed content that
is simply not shipping in 0.1.0, which is a deliberate Sawmill-only release. This directory is a
byte-exact mirror of `src/main/resources/`, so restoring is a move, not a rewrite.

Gradle only treats `src/main/resources` as a resource root, so nothing under `unreleased/` reaches
the built jar. The files stay tracked in git and their history followed them here (moved with
`git mv`, so `git log --follow` still works on every one).

## Restore everything

```powershell
cd 'D:\dev\business\hyMMO\additional-mods\rpg-stations'
.\unreleased\restore.ps1              # moves it all back into src/main/resources
.\unreleased\restore.ps1 -WhatIf      # preview without touching anything
```

Restore one station instead of all of them:

```powershell
.\unreleased\restore.ps1 -Only CookingFire,CuttingBoard
```

## What is held back, and why

| Station | Files | Why it is held back |
| --- | --- | --- |
| `CookingFire` | `Stations/CookingFire.json`, `Item/Items/RPG_Station_CookingFire.json`, `Item/RootInteractions/RPG_Station_CookingFire_Use.json` | Half of the two-station fish-prep exemplar; ships with `CuttingBoard` or not at all. |
| `CuttingBoard` | `Stations/CuttingBoard.json`, `Actions/PrepFish.json`, `Item/Items/RPG_Station_CuttingBoard.json`, `Item/RootInteractions/RPG_Station_CuttingBoard_Use.json`, `Emote/RPG_Emote_Knife.json` | The other half. Its `prepfish` program claims the cooking fire as a remote anchor, so the pair is one feature. |
| `MountSpike` | `Stations/MountSpike.json`, `Item/Items/RPG_Station_MountSpike.json`, `Item/RootInteractions/RPG_Station_MountSpike_Use.json` | An `Hold.Mount.Surface: "Entity"` standing-mount experiment. Its own `$Comment` records the Entity mount as in-game unverified. |
| NPC performer harness | `NPC/Roles/RPG_Performer_Spike.json` | Drives the throwaway `/rpgstations npcspike` dev harness, which is unwired for 0.1.0 (see below). |

## The one code change that goes with this

`command/RpgStationsCommand.java` had its `npcspike` subcommand unwired: the `npcSpike` field, the
`case "npcspike"` dispatch line, and the `npcspike(CommandContext)` method were removed, and a
comment at the old field site says so. `command/NpcPerformerSpike.java` itself is **untouched and
still in git**, just unreferenced. Restoring means putting those three sites back (they are one
`git log -p` away) after running `restore.ps1`.

## What deliberately did NOT move

- **Every `.lang` key stayed in `src/main/resources/Server/Languages/`.** All 9 locales keep their
  `station.cookingfire.*`, `station.cuttingboard.*`, `station.mountspike.*`, `action.prepfish.label`,
  and emote keys. An unreferenced lang key is invisible at runtime, so holding them back would have
  bought nothing and risked losing translation work. `i18n/RpgStationsLangKeys.java` and
  `LangFileIntegrityTest` therefore need no change either, in either direction.
- Shared assets the Sawmill also uses: `Emote/RPG_Emote_Saw.json`,
  `Entity/Effects/RPG/RPG_Station_Hold.json`, `Item/RootInteractions/RPG_Station_Retrieve.json`,
  `RpgStations/Settings/Settings.json`, and every `Common/UI/` page.

## Companion change in the stations pack

`content-packs/skill-stations-pack` has its own `unreleased/` holding the Anvil, the cooking
progression, and the Smithing/Cooking skills, held back in lockstep for the same release. Restore
the two together: this jar's `CookingFire` is what the pack's `CookingProgression` extension
targets.
