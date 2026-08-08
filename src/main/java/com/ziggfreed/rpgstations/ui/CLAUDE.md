# ui/ - the session-summary panel

Router for `ui/`.

- **[`StationSummaryHud`](StationSummaryHud.java)** - the standalone-rich end-of-session summary
  (`Common/UI/Custom/Pages/RpgStationSummary.ui`), extending `ziggfreed-common`'s
  `ui.hud.KeyedCustomHud` DIRECTLY (RpgStations has no HUD base of its own - the common class IS
  the base every consumer mod extends). Its layout rules are load-bearing, each one the fix for a
  real defect: outer/inner `Group` split (a bare child in `CustomUIHud.append`'s
  root always stretches full-screen-dark otherwise), explicit `Anchor` Width on the frame
  invocation, explicit Width on every `#Content` child (Hytale groups do not clip - an unwrapped
  label without a width cap grows the panel to its longest row), content-height sizing (hugs its
  title/text/ledger instead of a fixed box).
- **Ledger rows are TWO-LINE capable.** Each `#RpgStationSummaryItem0..5` slot is an icon plus a
  `FlexWeight: 1` vertical text column holding the headline `#Name` (`@LedgerRowStyle`, 15) and a
  smaller `#Sub` (`@LedgerSubStyle`, 13) that ships `Visible: false`; the row's own `Anchor` omits
  Height so it sizes to content and collapses back to one line whenever `#Sub` is hidden. The Java
  side just names `#Sub` to common's `SummaryRowRenderer.render(..., subLabelId)`, which shows it
  only for a row carrying a `SummaryRow.subText`. A second Label rather than a `\n` because the
  native markup set has no font-size tag. **Every slot must keep declaring `#Sub`** - the renderer
  addresses it on every visible row, and a command against an undeclared selector crashes the
  client. This mod's own item rows never set a second line; an enricher's may (what a second line
  says belongs to whichever mod owns that contribution channel's vocabulary).
- **What this leg's panel renders**: title + crest (`Identity.Icon`, else the anchor block's own
  item id captured at engage) + the cycles line + a capped item ledger (consumed/produced/lucky
  rows over common's `ui.rows.SummaryRow`/`SummaryRowRenderer`, plus `ENHANCE` rows -
  one per stamped stat, rendered verbatim from the provider's label, plus an engine-owned
  `Durability +N` row; `buildItemRow`'s `ENHANCE` case never recolors, the line arrives pre-styled).
  **NO channel-specific rows** - what a contribution channel's amount MEANS is the channel owner's
  policy (its own icon, its own breakdown), so those rows come from a registered `SummaryEnricher`
  reached through the api `SummaryEnricherRegistry`. A RpgStations-only install therefore shows
  cycles + items and nothing else - by design, not a gap.
  `SummaryEnricherRegistry.rows(...)` results are PREPENDED before this panel's own item rows.
- **Auto-hide**: a scheduled-clear-with-generation-token TTL - `KeyedCustomHud`'s own contract, not
  reimplemented here.
- **`RpgStationsSettingsAsset.SummaryHud`** (`Enabled`/`Position`/`OffsetX`/`OffsetY`/`TtlMs`, via
  `station.SettingsCatalog`) governs whether/where this panel shows; a disabled setting leaves this
  mod's own toast path as the only feedback surface. A listening mod that wants a different
  fallback owns that itself, outside this package.
