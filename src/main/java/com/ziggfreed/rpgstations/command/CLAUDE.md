# command/ - /rpgstations admin tools

Router for `command/`. One admin-gated command group, landed leg P0 (phase-1 closeout): the
design 4.1 scope that stayed unimplemented through phase-1 legs 0-6 (the MMO's own
`MmoStationCommand` deleted its camera subgroup at the leg-5 bridge cut, pointing here - see that
class's javadoc/git history in the hyMMO root repo).

- **[`RpgStationsCommand`](RpgStationsCommand.java)** is `/rpgstations <camera|validate> [action]`,
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
  - **`puppet <scale|modelswap|hidden|show|off>` was DELETED (cleanup pass, 2026-07-23)** along
    with the whole temporary `puppetspike.PuppetSpikeService` P0 spike-harness package, once the
    maintainer's full in-game puppet confirm landed (held-item mirror updates within a beat,
    player visible after every stop path incl. damage/death/relog, sawmill + anvil positioning
    good). The PRODUCTION puppet route (`station.StationPuppetController`, legs P3-P5) is
    unaffected - see `station/CLAUDE.md`'s puppet-engine bullet.
- Every user-facing string (the command/arg descriptions AND every chat reply) resolves through
  [`i18n.RpgMsg`](../i18n/RpgMsg.java) against the `rpgstations.command.*` keys in
  `Server/Languages/en-US/rpgstations.lang` (en-US authored; the other 8 locales fall back to
  English per key until a translation leg fills them - see `i18n.RpgStationsLangKeys`, kept in
  lockstep). The command/arg DESCRIPTION strings passed to `super(...)`/`withRequiredArg(...)`/
  `withOptionalArg(...)` are RAW keys (`"rpgstations.command.desc"`, not routed through `RpgMsg`,
  which would double-prefix) - the engine resolves them directly, mirroring `MobScalingCommand`'s
  `"scaling.command.desc"` shape.
