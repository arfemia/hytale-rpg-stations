# validation/ - the mini content-audit core

Router for `validation/`. A small, dependency-free value-type core: `Finding`/`Severity`/`Report`
and nothing else. There is deliberately no multi-domain audit REGISTRY here; a validator logs its
own findings at fold time.

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
`RpgStationsPlugin`'s first-`PlayerReadyEvent` hook - and on demand from
[`/rpgstations validate`](../command/CLAUDE.md) (leg P0), which was already post-load. See
`../station/CLAUDE.md`.
