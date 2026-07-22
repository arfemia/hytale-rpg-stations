# CLAUDE.md - RPG Stations

**Status: SCAFFOLD (phase 1, leg 0).** RPG Stations is a standalone Hytale mod that will own the
diegetic interactive work-station engine (sawmill, forge, and friends) extracted out of the MMO
Skill Tree mod; it depends on `ziggfreed-common` ONLY (never the MMO jar), and the MMO reaches it
back through a soft extension surface (native events + the `api` artifact) once that surface
lands. Right now this dir holds only the gradle scaffold (`settings.gradle`/`gradle.properties`/
`build.gradle` + a stub `api` module) and a minimal `RpgStationsPlugin` (setup/shutdown logging via
its own `util.Log` facade, never the MMO's `SafeLog`). Package root `com.ziggfreed.rpgstations`;
build with `cd rpg-stations; .\build.ps1` (`-Install:$false` to build only). The real architecture,
file-by-file move plan, and leg sequence are the design doc's authority:
`../../.claude/research/raw/rpg-stations-unified-design-2026-07-21.md` (grounded by the decision
log `../../.claude/research/rpg-stations-extraction-design.md`). The full per-package router tree
lands in leg 7, once the station/loot/asset/interaction/ui packages actually exist.
