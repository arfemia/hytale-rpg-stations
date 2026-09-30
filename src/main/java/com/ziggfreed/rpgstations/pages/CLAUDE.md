# pages/

- Open the recipe picker only on an explicit sneak+F and only when 2+ rows are available: the page does not check this, its caller (`StationService`) does. `showLocked` has no authored knob because nothing produces a locked category yet, so the caller passes `true`.
- Keep testable picker logic in `PickerCategories`: classloading `RpgStationPickerPage` (an `InteractiveCustomUIPage`) throws `ExceptionInInitializerError` in a unit JVM.
- Every card is a `Button` with an inner `#Label` set through `ZigRichButton.text`, never a `TextButton`'s `.Text`, so a parameterized message substitutes.
- `#Cost` and `#LockReason` stay direct children of the card group: the page addresses them as `"<card> #Cost"`.
- `"../Common/..."` in a page's `.ui` resolves to `Common/UI/Custom/Common/**`; pick textures from `hytale-shared-source/HytaleAssets/Common/UI/Custom/Common/`, whose files ship as `<name>@2x.png` and are referenced without the `@2x`.
