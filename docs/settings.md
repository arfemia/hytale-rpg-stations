# Settings

The RpgStationsSettingsAsset server-wide Enabled flag, Summary HUD, and owner Limits.

RPG Stations has no separate config-file layer - even its server-wide toggles are an ordinary content
asset, `Server/RpgStations/Settings/Settings.json`. There is exactly one fixed id (`settings`)
regardless of the filename you use; the jar ships a complete default, and a server owner or pack
overrides it the same way any other Pattern-A asset is overridden - a pack layering its own
`Settings.json` wins over the jar default via ordinary load order.

## The shape

```json
{
  "Enabled": true,
  "SummaryHud": { "Enabled": true, "Position": "TopCenter", "OffsetY": 72, "TtlMs": 6000, "MaxRows": 12 },
  "Limits": { "MaxSessionsPerWorld": 60, "MaxPuppetsPerWorld": 40, "MaxStashesPerSection": 8,
              "UnattendedIntervalMs": 1000, "MaxUnattendedGatherCycles": 12 }
}
```

| Field | Default | What it does |
|---|---|---|
| `Enabled` | `true` | The engine-wide kill switch. `false` keeps the mod loaded but disables every station's work loop server-wide. |
| `SummaryHud.Enabled` | `true` | Whether the post-session summary panel shows at all. |
| `SummaryHud.Position` | none | A shared-library HudPosition preset id, authored PascalCase like every other id in this schema (e.g. `TopCenter`); the legacy `TOP_CENTER` spelling still resolves since matching is case- and underscore-insensitive. An unknown or omitted value falls back to the HUD's own built-in default. |
| `SummaryHud.OffsetX` / `.OffsetY` | `0` / none | A pixel offset applied on top of the position preset. |
| `SummaryHud.TtlMs` | none | How long the summary panel stays on screen before it dismisses itself, in milliseconds. A run that ended by itself with its worker still at the station holds its panel until they step away; this is then how long it lingers from that moment. |
| `SummaryHud.MaxRows` | `12` | How many ledger rows the panel may grow to before the rest fold into a single `+N more` line. The panel sizes itself to what is showing, so this is a ceiling rather than a height: a three-row session still draws a three-row panel. Twelve is also the hard ceiling the panel can draw at all, so a larger number changes nothing; lower it to keep the panel small on a crowded screen. |
| `Limits.MaxSessionsPerWorld` | unlimited | The most work sessions that may run at once in ONE world; a press past it is denied with a localized toast, the station left untouched. |
| `Limits.MaxPuppetsPerWorld` | unlimited | The most live puppets that may exist at once in ONE world; past it a session still starts and runs, it just performs in the player's own body instead of spawning a puppet - the same fallback a failed spawn already takes. |
| `Limits.MaxStashesPerSection` | unlimited | The most blocks in ONE chunk section (a 32x32x32 cube) that may hold placed station input at once; topping up material already placed always works, only a placement that would open a NEW store past the ceiling is denied, and a [multiblock structure's](structures-and-sockets.md) own activation mark never counts against it. The retired `MaxCustodyClaimsPerWorld` spelling is ignored with a boot warning naming this leaf. |
| `Limits.UnattendedIntervalMs` | `1000` | How often ONE world's [unattended pass](unattended-work.md) runs, in milliseconds - the pass that settles custody-loaded stations whose action authors `Work.Unattended`, and that rebuilds missing placed-item displays after a chunk loads. Raising it makes unattended stations settle in coarser bursts; the math is the same either way. |
| `Limits.MaxUnattendedGatherCycles` | unlimited | A server-wide ceiling on how many accrued [unattended](unattended-work.md) cycles ONE gather pays out. The effective ceiling is the SMALLER of it and each action's own `Work.Unattended.MaxCycles`, so it can only tighten what an action authors, never raise it; absent means each action's own knob alone applies. |

The three top-level knobs (`Enabled`, `SummaryHud`, `Limits`) are independent and composable -
disabling the summary HUD does not disable the engine, and vice versa. Every leaf is nullable, so a
partial owner override changes only what it mentions. `Limits` is deliberately unauthored in the jar
default: every leaf means unlimited when absent, and the right ceiling depends on a server's own
player count and hardware - a busy server sets its own numbers rather than inheriting a guess.

<a id="the-summary-panel"></a>
## What the summary panel shows

The session-summary panel renders after a work session stops: a header naming the station (its own
icon, resolved from `Identity.Icon` or the block's own item id when omitted), the session totals, any
enhancement outcome rows (durability gained, stats rolled - a bare Anvil with no other mod installed
still reports its durability gain), and whatever additional ledger rows a listening mod adds through a
registered `SummaryEnricher`. See [Add-ons & Integrations](integrations.md) for that registry and the
mods known to use it.

A long session can outrun the panel: rows past `SummaryHud.MaxRows` fold into one `+N more` line at
the bottom rather than pushing the panel off the screen. Raising the number is what makes a busy
session list everything, and twelve rows is as far as the panel can go.

A run that ends by itself waits for its worker. When the material runs out, a repeating program works
its inputs down, or a ritual finishes, the panel stays up while whoever ran it is still standing at
the station - often nobody is at the keyboard for the last few minutes of a long run, and the totals
should still be there when they come back. It dismisses on `SummaryHud.TtlMs` once they step away
from where the run left them, and starting another run at the station dismisses it the same way. A
run the worker ended themselves - walking off, crouching out, swapping tools, taking a hit - gets the
plain timed panel, since they are already leaving.

## Why an asset, not a config file

Treating settings as content rather than configuration means a server owner authors and audits it the
exact same way they author every other piece of RPG Stations content - through the Pattern-A
asset-pack pipeline, `defaults < pack < owner` precedence, and `/rpgstations validate` - rather than a
second, differently-shaped file format to learn.

---

Previous: [Extending Other Packs](extending-other-packs.md) · Next: [Localization](localization.md)
