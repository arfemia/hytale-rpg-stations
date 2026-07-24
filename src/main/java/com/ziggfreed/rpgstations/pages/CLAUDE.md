# pages/ - InteractiveCustomUIPage pages

Router for `pages/` (NEW package, seam wave leg R3, sibling to `ui/`'s HUD-only content - the
same `pages/` vs `ui/` split hyMMO's own root repo uses). A page here is a real
`InteractiveCustomUIPage` the player interacts with (opens, clicks, closes); `ui/` stays HUD
overlays only (`StationSummaryHud`, no player interaction).

- **[`RpgStationPickerPage`](RpgStationPickerPage.java)** - the multi-output PICKER (decision 50,
  demoted by decision 51's native-composition amendment to the fallback route for a flow the
  native bench window cannot express). A compact icon TAB STRIP (`.ui`:
  `Pages/RpgStationPicker.ui` + the per-tab `Pages/RpgStationPickerTab.ui` template, both in
  `Common/UI/Custom/Pages/`), one tab per available output category, locked categories greyed
  (`.Enabled = false`, no click binding) + a lock-reason line naming the required tool, gated on
  the authored `Picker.ShowLocked` knob. Selection closes the page THEN calls back
  (`PickerCallback.onSelect`) with the chosen category id - the page has zero opinion on what a
  category id means; the caller (the station engine, resolving native recipe categories per
  decision 49's rider) supplies the ordered `Category` list and owns everything downstream
  (updating the session's chosen category, opening a follow-on native bench window, etc.).
  **Caller contract (binding, NOT enforced inside the page - decision 50):** open ONLY when 2+
  categories are available; NEVER auto-open (sneak+F only, per the round-3 selector-entity
  press-F pattern the ledger documents as the fallback if a sneak read misbehaves in-game).
  - **Route the engine calls**: `RpgStationPickerPage.open(ref, store, playerRef, categories,
    showLocked, callback)` - a static, try-guarded, never-throws helper (mirrors
    `StationSummaryHud.tryShow`'s fail-soft shape) that resolves the live `Player` and opens the
    page; `false` on any missing/invalid state, never a throw into the caller's interaction
    handler. `PickerCategories.Category.unlocked(id, iconItemId, label)` /
    `.locked(id, iconItemId, label, requiredToolLabel)` are the two value-type factories the
    engine leg builds its category list from (`label` is an optional `Message`; icon-only tabs
    are valid when the caller has no natural display name for a category).
- **[`PickerCategories`](PickerCategories.java)** - the `Category` record + `visibleCategories`
  filter (every category when `showLocked`, else the unlocked ones only, order preserved), kept
  in their OWN class rather than nested on the page: merely CLASSLOADING `RpgStationPickerPage`
  (it extends the engine's `InteractiveCustomUIPage`) throws `ExceptionInInitializerError` at
  `HytaleLogger` in a bare unit JVM - the same class of trap `station/CLAUDE.md`'s
  `ItemToolSpec`/`StationToolScaling` note documents for asset codecs, here for a PAGE base class
  instead. `RpgStationPickerPageTest` targets `PickerCategories` directly so the pure logic is
  unit-tested without a live server, same as every other pure-core test in this mod.
  - **HARD RULE followed**: every tab is a `Button` + inner `#Label` (never a `TextButton.Text`)
    per the `ziggfreed-common` root `CLAUDE.md`'s labeled-clickable-button rule - text pushes via
    `com.ziggfreed.common.ui.ZigRichButton.text(cmd, sel, message)` onto `#Label.TextSpans`, so a
    parameterized lock-reason message (`"Requires {0}"`) actually substitutes.
  - **New `rpgstations.` keys** (en-US only this wave, per the seam-wave edict; locale fan-out is
    a later phase): `ui.station.picker.title`, `ui.station.picker.hint`,
    `ui.station.picker.locked` (1-arg, the required tool's display name), and
    `ui.station.picker.locked_generic` (the no-tool-name fallback). Added to both
    `Server/Languages/en-US/rpgstations.lang` and `i18n.RpgStationsLangKeys.KEYS`.
