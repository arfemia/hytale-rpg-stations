# pages/ - InteractiveCustomUIPage pages

Router for `pages/` (NEW package, seam wave leg R3, sibling to `ui/`'s HUD-only content - the
same `pages/` vs `ui/` split hyMMO's own root repo uses). A page here is a real
`InteractiveCustomUIPage` the player interacts with (opens, clicks, closes); `ui/` stays HUD
overlays only (`StationSummaryHud`, no player interaction).

- **[`RpgStationPickerPage`](RpgStationPickerPage.java)** - the multi-output PICKER (decision 50;
  decision 51 once demoted it behind a native bench window, and **decision 65 retired that route
  entirely - this is now the ONLY sneak+F selection surface**). A vertical RECIPE LIST, one
  full-width card per available output category (`.ui`: `Pages/RpgStationPicker.ui` + the per-card
  `Pages/RpgStationPickerTab.ui` template, both in `Common/UI/Custom/Pages/`), locked categories
  greyed (`.Enabled = false`, no click binding) + a lock-reason line naming the required tool,
  gated on the authored `Picker.ShowLocked` knob. Selection closes the page THEN calls back
  (`PickerCallback.onSelect`) with the chosen category id - the page has zero opinion on what a
  category id means; the caller (the station engine, resolving native recipe categories per
  decision 49's rider) supplies the ordered `Category` list and owns everything downstream
  (updating the session's chosen category, etc.).
  **Caller contract (binding, NOT enforced inside the page - decision 50):** open ONLY when 2+
  categories are available; NEVER auto-open (sneak+F only, per the round-3 selector-entity
  press-F pattern the ledger documents as the fallback if a sneak read misbehaves in-game).
  - **Route the engine calls**: `RpgStationPickerPage.open(ref, store, playerRef, categories,
    showLocked, callback)` - a static, try-guarded, never-throws helper (mirrors
    `StationSummaryHud.tryShow`'s fail-soft shape) that resolves the live `Player` and opens the
    page; `false` on any missing/invalid state, never a throw into the caller's interaction
    handler. `PickerCategories.Category.unlocked(id, iconItemId, label, costLine)` /
    `.locked(id, iconItemId, label, costLine, requiredToolLabel)` are the two value-type factories
    the engine leg builds its category list from (`label`/`costLine` are optional `Message`s).
  - **Maintainer picker-smoke fix (2026-07-28): NAME + COST LINE, never icon-only.**
    `StationService#buildPickerCategories` populates `label` with the representative output item's
    own native item name (`common.i18n.NativeNames#itemNameMsg`, client-resolved, no
    hand-authored per-category lang key) and `costLine` with that SAME representative conversion's
    input-to-output shape ("1x Trunk -> 4x Planks", `StationService#pickerCostLine`) - both derive
    from ONE scan (`StationService#representativeConversionFor`) so the icon/name and the cost line
    never describe two different conversions. The input side resolves through `itemNameMsg` too
    even when the conversion authors a `ResourceTypeId` family instead of an exact item id (the
    sawmill's "any Trunk of this species" input) - a resource-type id has no native item-name key,
    so `itemNameMsg`'s existence-probe safely falls to its prettified-raw fallback.
  - **Maintainer picker-smoke fix: COMPACT panel, not full-screen.** `RpgStationPicker.ui`
    previously appended its sized panel (`#RpgStationPickerRoot`, Width-only, no Height) AS the
    document root, which force-stretches to fill the viewport regardless of its own Anchor (the
    same trap `RpgStationSummary.ui` documents for a HUD document root). Fixed by wrapping it in
    the native `$C.@PageOverlay { LayoutMode: Middle; ... }` - the OFFICIAL first-party small-dialog
    pattern (`PrefabEditorExitConfirm.ui`/`NameRespawnPointPage.ui` in the shared source): `Middle`
    centers the child instead of stretching it, so the Width-only panel hugs its own content height.
  - **Maintainer picker-smoke fix (decision 66, 2026-07-29): PREVIEW THE PLACED MATERIAL.** Every
    card's icon/name/cost used to describe the alphabetically-first conversion in its category, so
    a sawmill loaded with any log species previewed three BLACKWOOD cards. `StationService
    #buildPickerCategories` now threads a preferred input (`#pickerPreviewInputItemId`: the block's
    custody claim, else the held stack, else null) into the 4-arg `#representativeConversionFor`,
    which returns the first conversion in that category whose INPUT matches. Display-only - which
    conversion RUNS was always correct. See the ledger's decision 66 for the full rationale.
  - **Maintainer picker-smoke fix (decision 66): NOT GREYED OUT, BIGGER, WIDER.** Three
    evidence-grounded `.ui` changes, each measured against the client's own modding texture set
    (`Assets.zip` `Common/UI/Custom/Common/**`, what `"../Common/..."` resolves to from a custom
    page): (a) the card was `Buttons/Secondary.png` (already dark navy, centre pixel
    `rgba(33,52,75,255)`) tinted `#41506a`, which MULTIPLIES it darker - an enabled recipe read as
    disabled; it is now `Buttons/Primary_Square.png` (`rgba(68,112,164,255)`, what the game's own
    `@DefaultButtonStyle` uses) at a NEUTRAL `#ffffff` tint, keeping the tint leaf so `UiRetint` can
    still theme it. (b) icons 32 -> 64 with `SlotBackground` moved off the 20%-alpha black
    `ContainerPanelPatch.png` filler onto the modding set's real near-transparent
    `BlockSelectorSlotBackground.png`, and `DefaultItemIcon` onto the real `UnknownItemIcon.png`.
    (c) the two-up 170px strip becomes full-width 604px rows in a 640px panel (`LayoutMode: Top`),
    the first-party `Pages/Inventory/RecipeCatalogueRecipeButton.ui` shape. **Do NOT re-point the
    remaining `ContainerPanelPatch.png` leaves** (broken/durability/cursed overlays) - they are
    inert required-field filler for this non-draggable, quantity-less grid.
- **[`PickerCategories`](PickerCategories.java)** - the `Category` record + `visibleCategories`
  filter (every category when `showLocked`, else the unlocked ones only, order preserved), kept
  in their OWN class rather than nested on the page: merely CLASSLOADING `RpgStationPickerPage`
  (it extends the engine's `InteractiveCustomUIPage`) throws `ExceptionInInitializerError` at
  `HytaleLogger` in a bare unit JVM - the same class of trap `station/CLAUDE.md`'s
  `ItemToolSpec`/`StationToolScaling` note documents for asset codecs, here for a PAGE base class
  instead. `RpgStationPickerPageTest` targets `PickerCategories` directly so the pure logic is
  unit-tested without a live server, same as every other pure-core test in this mod.
  - **HARD RULE followed**: every card is a `Button` + inner `#Label` (never a `TextButton.Text`)
    per the `ziggfreed-common` root `CLAUDE.md`'s labeled-clickable-button rule - text pushes via
    `com.ziggfreed.common.ui.ZigRichButton.text(cmd, sel, message)` onto `#Label.TextSpans`, so a
    parameterized lock-reason message (`"Requires {0}"`) actually substitutes. `#Cost` is a plain
    (non-Button) `Label` below the button, set directly via `.TextSpans` like `#LockReason`.
    **Keep `#Cost`/`#LockReason` as DIRECT children of the card group** - the page addresses them
    as `"<card> #Cost"`, one selector step from the card, so nesting them inside `#TabBtn` would
    add a step to a path the page does not write.
  - **New `rpgstations.` keys**: `ui.station.picker.title`, `ui.station.picker.hint`,
    `ui.station.picker.locked` (1-arg, the required tool's display name),
    `ui.station.picker.locked_generic` (the no-tool-name fallback), and (2026-07-28)
    `ui.station.picker.cost` (4-arg, `"{0}x {1} -> {2}x {3}"`). Registered in
    `i18n.RpgStationsLangKeys.KEYS` and present in ALL NINE locale `rpgstations.lang` files (a
    wordless format string, identical in each locale - the quantities are `{N}` args and the item
    names nested client-resolved Messages, so nothing needed translating).
