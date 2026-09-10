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
- **Ledger rows are TWO-LINE capable.** Each `#RpgStationSummaryItem0..11` slot is an icon plus a
  `FlexWeight: 1` vertical text column holding the headline `#Name` (`@LedgerRowStyle`, 15) and a
  smaller `#Sub` (`@LedgerSubStyle`, 13) that ships `Visible: false`; the row's own `Anchor` omits
  Height so it sizes to content and collapses back to one line whenever `#Sub` is hidden. The Java
  side just names `#Sub` to common's `SummaryRowRenderer.render(..., subLabelId)`, which shows it
  only for a row carrying a `SummaryRow.subText`. A second Label rather than a `\n` because the
  native markup set has no font-size tag. **Every slot must keep declaring `#Sub`** - the renderer
  addresses it on every visible row, and a command against an undeclared selector crashes the
  client. This mod's own PRODUCED rows DO use the second line, for the per-cycle yield
  decomposition (`StationService#yieldBreakdownLine`, null - so the row collapses back to one
  line - when the yield did not actually change the number); CONSUMED / LUCKY / ENHANCE rows
  never set one, and an enricher's rows may (what a second line says belongs to whichever mod
  owns that contribution channel's vocabulary).
- **What this leg's panel renders**: title + crest (`Identity.Icon`, else the anchor block's own
  item id captured at engage) + the cycles line + a capped item ledger (consumed/produced/lucky
  rows over common's `ui.rows.SummaryRow`/`SummaryRowRenderer`, plus `ENHANCE` rows -
  one per stamped stat, rendered verbatim from the provider's label, plus an engine-owned
  `Durability +N` row; `buildItemRow`'s `ENHANCE` case never recolors, the line arrives pre-styled).
  **NO channel-specific rows** - what a contribution channel's amount MEANS is the channel owner's
  policy (its own icon, its own breakdown), so those rows come from a registered `SummaryEnricher`
  reached through the api `SummaryEnricherRegistry`. A RpgStations-only install therefore shows
  cycles + items and nothing else - by design, not a gap.
  Each registered enricher's `SummaryEnricher.rows(...)` results are PREPENDED before this panel's
  own item rows, in registration order.
- **`ROOT_SELECTOR` (`#RpgStationSummaryRoot`) is a FROZEN api contract.** An enricher's optional
  `SummaryEnricher.decorate` writes `UICommandBuilder` theming commands against that exact
  selector cross-jar, handed to it as `api.SummaryDecorateContext#rootSelector()`, so a `.ui`
  restructure MUST keep the id; `PANEL_WIDTH_PX` (528) must likewise keep matching that same
  element's static-fallback `Anchor` Width.
- **Auto-hide**: this class's OWN scheduled-clear-with-generation-token TTL (the `ToastController`
  pattern) - every `showSummary` bumps an `AtomicLong generation` and schedules
  `hideIfCurrent(gen)` on `HytaleServer.SCHEDULED_EXECUTOR`, so a stale hide from an earlier
  summary is a no-op against a newer one. `KeyedCustomHud` supplies the position / throttle /
  register-and-lookup base only; it has no TTL.
- **A held panel schedules nothing until someone says so.** `showSummary(..., holdOpen)` parks its
  generation in `heldGeneration` instead of arming a hide, and `releaseHold(gen)` (statically,
  `tryRelease(playerRef, gen)`) arms the authored `TtlMs` from the moment it is called; the CAS
  against `heldGeneration` is what keeps a stale releaser from cutting short the panel a newer run
  put up, and every `showSummary` clears the field first so a newer summary always cancels an older
  hold. WHEN a hold ends is not this class's call: `station.StationService` holds the panel of a run
  that ended itself (`holdsSummaryOpen`) and releases it when the worker steps away from where they
  finished, engages again, or leaves the world.
- **`RpgStationsSettingsAsset.SummaryHud`** (`Enabled`/`Position`/`OffsetX`/`OffsetY`/`TtlMs`/`MaxRows`/`Color`, via
  `station.SettingsCatalog`) governs whether/where this panel shows; a disabled setting leaves this
  mod's own toast path as the only feedback surface. A listening mod that wants a different
  fallback owns that itself, outside this package.
- **The panel is a HUD card and wears the colour every card shares.** `Color` is this panel's own
  tint over the look Ziggfreed Common ships for every HUD card on its base
  (`Server/ZiggfreedCommon/HudCards/Default.json`, owner layer `mods/ziggfreedcommon/hud-cards.json`):
  one hex multiplied over the frame, eight digits carrying a transparency in the last two, resolved
  per push by `StationSummaryHud.cardLook()` through the library's `HudCardLook` (a malformed value
  warns once and falls through) and pushed through `UiRetint.retintColor` on `FRAME_SEL`
  (`#RpgStationSummaryRoot #Content`, the frame's element; the root carries no background) AFTER
  the decorate hook, so an owner's authored colour is the last word over a listening mod's theme
  and an absent one keeps that theme. The identity pushes nothing.
- **Row cap: the `.ui` slot count is the HARD ceiling, `MaxRows` draws fewer.** A HUD update can
  only repaint elements the document already declares, never append one, so `MAX_LEDGER_ROWS` (12)
  and the `#RpgStationSummaryItem0..11` run must move together; `StationSummaryHud#ledgerRowCap`
  clamps the authored `SummaryHud.MaxRows` to it and is read per push, so a settings reload lands on
  the next summary. `renderLedger` cuts the row list to that cap and still hands the renderer ALL 12
  slots, so a slot the cap no longer admits is hidden rather than left showing what a longer session
  painted in it; whatever the cut dropped is what the keyed `+N more` row reports.
