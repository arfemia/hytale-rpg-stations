# ui/ - the session-summary panel

Router for `ui/`.

- **[`StationSummaryHud`](StationSummaryHud.java)** - the standalone-rich end-of-session summary
  (`Common/UI/Custom/Pages/RpgStationSummary.ui`), extending `ziggfreed-common`'s
  `ui.hud.KeyedCustomHud` DIRECTLY (RpgStations has no HUD base of its own - the common lift, leg
  1, IS the base every consumer mod extends). Copies the pre-extraction MMO `SessionSummaryHud`'s
  proven layout rules verbatim: outer/inner `Group` split (a bare child in `CustomUIHud.append`'s
  root always stretches full-screen-dark otherwise), explicit `Anchor` Width on the frame
  invocation, explicit Width on every `#Content` child (Hytale groups do not clip - an unwrapped
  label without a width cap grows the panel to its longest row), content-height sizing (hugs its
  title/text/ledger instead of a fixed box).
- **What this leg's panel renders**: title + crest (`Identity.Icon`, else the anchor block's own
  item id captured at engage) + the cycles line + a capped item ledger (consumed/produced/lucky
  rows over common's `ui.rows.SummaryRow`/`SummaryRowRenderer`). **NO per-skill XP rows** - those
  are MMO-policy (icon via `RewardIconResolver`, base x tool x named-factor breakdown) and live in
  the MMO bridge's OWN `StationSummaryEnricher`, reached through the api `SummaryEnricherRegistry`
  (see `../../../../../../src/main/java/com/ziggfreed/mmoskilltree/integration/stations/CLAUDE.md`).
  A RpgStations-only install therefore shows cycles + items, never XP - by design, not a gap.
  `SummaryEnricherRegistry.rows(...)` results are PREPENDED before this panel's own item rows.
- **Auto-hide**: a scheduled-clear-with-generation-token TTL, copying (not reusing) the MMO's
  `ToastController` TTL pattern - `KeyedCustomHud`'s own contract, not reimplemented here.
- **`SettingsAsset.SummaryHud`** (`Enabled`/`Position`/`OffsetY`/`TtlMs`, via
  `station.SettingsCatalog`) governs whether/where this panel shows; a disabled setting is this
  mod's OWN fallback path (unlike the MMO's classic-toast fallback, which is MMO-policy and lives
  in the bridge, not here).
