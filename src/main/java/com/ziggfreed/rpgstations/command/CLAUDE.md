# command/ - /rpgstations admin tools

Router for `command/`. One admin-gated command group, landed leg P0 (phase-1 closeout): the
design 4.1 scope that stayed unimplemented through phase-1 legs 0-6 (the MMO's own
`MmoStationCommand` deleted its camera subgroup at the leg-5 bridge cut, pointing here - see that
class's javadoc/git history in the hyMMO root repo).

- **[`RpgStationsCommand`](RpgStationsCommand.java)** is `/rpgstations <camera|validate|puppet> [action]`,
  admin-gated with `setPermissionGroups(HytalePermissionsProvider.GROUP_ADMIN)` at the FRAMEWORK
  level (the `mmo-mob-scaling`/`kweebec-nightmare` sibling-mod idiom - `MobScalingCommand`'s own
  admin group, not a manual runtime `hasPermission` check; this mod's own `util.Permissions` stays
  unused by this command, reserved for a future finer-grained check).
  - `camera <preset>` - set the CALLING player's own [`StationCameraPreset`](../station/StationCameraPreset.java)
    tuning override in [`StationCameraPrefs`](../station/StationCameraPrefs.java) (transient,
    never persisted) for their NEXT station session. Ported (shape) from the MMO's deleted
    `MmoStationCommand` camera subgroup.
  - `camera list` - chat every known preset id, DYNAMIC over `StationCameraPreset.values()` (never
    a hand-maintained list), plus the caller's current preset.
  - `validate` - run `station.StationValidator.validate()` over the folded station/lootable
    catalog and chat the aggregate (the summary line + every finding), the SAME information
    `RpgStationsPlugin.onStationAssetsLoaded`'s boot-log audit already prints via
    `StationValidator.runAndLog()` (called again here so the log carries a matching run - the
    MMO's own `/mmoconfig validate` dual-call shape: chat a live run, log a matching one).
  - `puppet <scale|modelswap|hidden|show|off>` - **TEMPORARY P0 SPIKE HARNESS**, calling-player-only
    (delegates entirely to `puppetspike.PuppetSpikeService`, hardened round-4 for the `off`
    random-teleport bug and the puppet-not-despawning bug, see `station/CLAUDE.md`'s puppet-engine
    bullet). Its own class javadoc says the whole `puppetspike/` package is deleted once the
    production puppet route (`station.StationPuppetController`, legs P3-P5, landed) is confirmed
    working in-game - that confirm has not landed yet as of this router pass, so the package and
    this subcommand are STILL LIVE, kept for isolated route debugging if the production wiring
    needs it. Do not delete before the maintainer's in-game confirm.
- Every user-facing string (the command/arg descriptions AND every chat reply) resolves through
  [`i18n.RpgMsg`](../i18n/RpgMsg.java) against the `rpgstations.command.*` keys in
  `Server/Languages/en-US/rpgstations.lang` (en-US authored; the other 8 locales fall back to
  English per key until a translation leg fills them - see `i18n.RpgStationsLangKeys`, kept in
  lockstep). The command/arg DESCRIPTION strings passed to `super(...)`/`withRequiredArg(...)`/
  `withOptionalArg(...)` are RAW keys (`"rpgstations.command.desc"`, not routed through `RpgMsg`,
  which would double-prefix) - the engine resolves them directly, mirroring `MobScalingCommand`'s
  `"scaling.command.desc"` shape.
