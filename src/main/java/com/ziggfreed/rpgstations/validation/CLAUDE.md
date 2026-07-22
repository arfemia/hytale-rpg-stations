# validation/ - the mini content-audit core

Router for `validation/`. A small, dependency-free value-type core ported verbatim from the MMO's
`validation.Finding`/`Severity` mini-core (RPG Stations extraction leg 2) - NOT the MMO's full
`ContentAudit` registry (this mod has no equivalent multi-domain audit registrar; a validator here
just logs its own findings at fold time).

- **[`Severity`](Severity.java)** - `ERROR`/`WARN`/`INFO`.
- **[`Finding`](Finding.java)** - one result: `{severity, domain, code, message, subjectId}`.
  Diagnostic messages are raw English by convention (an admin/log surface, not player-facing - the
  no-em-dashes/localization rules apply to PLAYER text, not this diagnostic channel).
- **[`Report`](Report.java)** - a findings collector.

The one real validator, `station.StationValidator`, lives in `../station/` (not here) because it
is entangled with `StationCatalog`/`StationAsset` internals; this package holds only the shared
result shapes it (and any future validator) returns. `RpgStationsPlugin.onStationAssetsLoaded`
calls `StationValidator.runAndLog()` at every fold - findings surface via the boot log only; no
`/rpgstations validate` command exists yet (see `../station/CLAUDE.md`).
