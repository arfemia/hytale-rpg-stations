# Commands

The `/rpgstations` admin command group: camera preset tuning and the content validator.

RPG Stations registers one command group, `/rpgstations <camera|validate> [action]`. It is
**admin-gated** at the framework level (the calling player is an operator when permissions are off,
otherwise holds the admin permission group); a non-admin never sees it. There is no per-station or
per-player configuration command - stations are content, authored and audited as assets, so these two
subcommands cover the whole runtime surface.

## /rpgstations camera

Sets the CALLING player's own station-camera tuning override for their next station session. The
override is transient - held in memory, never persisted - and applies to that player only (a console
sender has no camera preference to set). It overrides whatever `Camera.Recipe` default the station
asset itself declares, so it is the quick way to preview a camera feel in-world without editing and
reloading an asset.

| Command | Effect |
|---|---|
| `/rpgstations camera <preset>` | Set your own camera preset override for your next session. |
| `/rpgstations camera list` | Chat every known preset id (listed dynamically, never a hand-maintained list) plus your current one. |

An unknown preset id is rejected with the list of valid ids, so `camera list` is the discovery path -
run it first to see what your build offers.

## /rpgstations validate

Runs the full content audit over every loaded station, action, lootable, roll pool, and extension,
then reports the aggregate in chat: a summary line followed by every finding, each coloured by
severity (error / warning / info). This is the **same audit** that runs once automatically at server
boot and prints to the log - `validate` just lets you re-run it on demand and read it in chat, and it
re-logs a matching run at the same time.

The audit is advisory: warnings never stop a station from loading or running. Use it after authoring
or editing content to catch typos (an unknown factor id, a moment id that matches no known moment, a
flair naming a station that does not exist) before players hit them. See
[Getting Started](getting-started.md) for where it fits in a first-boot check.
