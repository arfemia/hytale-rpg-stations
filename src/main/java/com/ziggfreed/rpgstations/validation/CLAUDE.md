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
result shapes it (and any future validator) returns. **Two passes (fix-wave D4, timing not
checks)**: `RpgStationsPlugin.onStationAssetsLoaded`/`onFlairAssetsLoaded` call
`StationValidator.runStructuralAndLog()` at EVERY fold (every check except a cross-layer
reference-existence one - a later pack layer folding its own drop lists/roll pools/lang overlay
AFTER this layer's Station/Flair fold otherwise false-positives); the FULL
`StationValidator.runAndLog()` (every check, incl. reference existence) runs ONCE, post-load, from
`RpgStationsPlugin`'s first-`PlayerReadyEvent` hook (mirrors the MMO's own `ContentAudit`
first-PlayerReady startup-audit timing) - and on demand from
[`/rpgstations validate`](../command/CLAUDE.md) (leg P0), which was already post-load. See
`../station/CLAUDE.md`.
