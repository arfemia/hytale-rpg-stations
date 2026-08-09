# RPG Stations

Diegetic interactive work stations for Hytale - place a block, press `F`, and watch your character
(or a stand-in performer) do the work over real time instead of an instant menu conversion.

A standalone server mod, package root `com.ziggfreed.rpgstations`. It ships its own minimal
jar-default content (a Sawmill, craftable at a tier 2 Workbench) so it is playable with no content
pack at all, and its engine underneath - multi-action stations, step programs, multi-station walks,
placed-input custody and props, the puppet performer, conditional loot, and enhancement stamping - is
the full thing, all driven from ordinary content assets a pack (or a server's own assets) can extend.

RPG Stations carries no progression vocabulary of its own and depends on no other mod. A soft
extension surface (native events plus a typed api artifact) lets another mod turn completed station
work into its own rewards, without either mod hard-depending on the other. See
[Add-ons & Integrations](docs/integrations.md).

## Install

Requires **ZiggfreedCommon** (`>=1.4.0`), the one hard dependency - drop its jar into your server's
`Mods/` folder first, then drop the RPG Stations jar into the same folder and restart. See
[Getting Started](docs/getting-started.md) for the full walkthrough.

## Build from source

```powershell
.\build.ps1                  # build the jar, install it if a Mods folder is known
.\build.ps1 -Install:$false  # build only
```

Java 25. The Hytale server jar path is configured in `gradle.properties`. See [CLAUDE.md](CLAUDE.md)
for the developer guide.

## Documentation

- [Getting Started](docs/getting-started.md) and [Concepts](docs/concepts.md) - install and the core
  vocabulary (station, session, action, step, custody, puppet).
- [Your First Station](docs/your-first-station.md) - a worked walkthrough authoring one station end
  to end, followed by [Actions & Step Programs](docs/actions-and-steps.md),
  [Multi-Station Programs](docs/multi-station-programs.md),
  [Custody & Placed Display](docs/custody-and-placed-display.md),
  [Puppet & Performers](docs/puppet-presentation.md),
  [Selection & Output Categories](docs/selection.md), [Loot & Factors](docs/loot-and-factors.md),
  [Native Composition](docs/native-composition.md),
  [Enhancement & Stamp](docs/enhancement-and-stamp.md), [Flairs](docs/flairs.md),
  [Extending Other Packs](docs/extending-other-packs.md), [Settings](docs/settings.md), and
  [Localization](docs/localization.md).
- [Commands](docs/commands.md) - the admin-gated `/rpgstations` command group.
- [Extension Channels](docs/extension-channels.md) and
  [Add-ons & Integrations](docs/integrations.md) - the typed extension surface any mod hooks to
  reward station work.
- [SCHEMA.md](SCHEMA.md) - the codec-generated field reference for every content type, regenerated
  via `gradlew generateSchemaDocs`.

See also [CHANGELOG.md](CHANGELOG.md) for the developer changelog.
