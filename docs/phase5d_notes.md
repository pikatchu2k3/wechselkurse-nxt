# Phase 5d notes — Add-currency dialog: tab/chip picker

## Tab set (4 chips, in this order)
1. **Währungen / Currencies** — all fiat (`AddGroup.CURRENCIES`)
2. **Crypto** — BTC (`AddGroup.CRYPTO`)
3. **Rohstoffe / Commodities** — XBZ brent oil (`AddGroup.COMMODITIES`)
4. **Edelmetalle / Precious metals** — XAU, XAG (`AddGroup.METALS`)

Chose the 4-category set over folding metals into Rohstoffe: it mirrors the existing `groupOf()`
classification exactly, and the reference's 4th chip is covered cleanly. Chip labels reuse the
existing section strings (`add_currency_section_*`, values + values-de) — no new strings needed;
the existing `add_currency_search_hint` doubles as the FAB content description.

## Approach — smallest change, adapter-driven chips
- `AddCurrencyDialogAdapter.kt`:
  - `AddGroup` enum is now public (reordered to chip order: currencies → crypto → commodities →
    metals). `groupOf()` classification unchanged.
  - New: `setCategory(group)` renders ONLY the selected category's entries; `getCategories()`
    returns the categories that currently have ≥1 entry (in chip order) — categories without data
    (e.g. a provider that doesn't offer XBZ, or crypto/commodity sources failing independently)
    get no chip. `setRates()` falls back to the first available category if the selected one
    vanished.
  - `rebuild()` no longer emits `Row.Header`s — the chip row replaces the section headers, so the
    sealed `Row` class, the header view holder and `getItemViewType()` were dropped. The list is a
    plain `List<Currency>` now.
  - Live search unchanged (`matches()`): case-insensitive substring on localized name + ISO code,
    but applied to the selected category only; the filter text survives category switches.
  - `row_add_currency_header.xml` deleted (unused after this).
- `dialog_add_currency.xml`:
  - Top: `HorizontalScrollView` + single-line `ChipGroup` (`singleSelection` + `selectionRequired`)
    with the 4 category chips.
  - Below: `FrameLayout` (weight 1) with the `RecyclerView` (fills the dialog, 72dp bottom padding
    so the last row clears the FAB), an initially-GONE `SearchView` overlay (rounded
    `bg_search_field`: `?attr/colorSurface` + `?attr/colorOutline` stroke), and the bottom-right
    mini `FloatingActionButton` (`bottom|end`).
- `RatesListActivity.showAddCurrencyDialog()`: still a lightweight host — maps chip ids →
  `AddGroup`, hides empty-category chips, preselects the first available one, then drives the
  adapter via `ChipGroup.setOnCheckedStateChangeListener`. The FAB toggles the search field
  (icon swaps search ↔ close, query cleared + filter reset on close). Star observer, OK button,
  and dismiss handling unchanged.
- New drawables: `ic_search.xml`, `ic_close.xml` (vectors, tinted via theme attr like the existing
  icons), `bg_search_field.xml` (shape with theme-attr colors).

## Material You
- Chip style `AppTheme.Chip.Category` (`Widget.Material3.Chip.Filter`, checkmark hidden): checked
  chip = `colorSecondaryContainer`/`colorOnSecondaryContainer` → mapped to
  `?attr/colorPrimary`/`?attr/colorOnPrimary` by the app theme; unchecked = `colorSurfaceVariant`
  background + `colorOutline` stroke (theme-mapped). FAB icon tint `?attr/colorOnPrimary`. No
  hardcoded colors.

## Behavior
- Exactly one category visible at a time; tapping an entry (or its star) still toggles via
  `viewModel.toggleCurrencyStar` through `onItemToggled`; stars stay in sync via the temporary
  observer; the rates list re-renders on dismiss via `getRows()` (unchanged).
- Search: dismissible via the FAB (now a close icon) — closing clears the query and the filter;
  chips remain visible while searching.

## Deviations
- No "Rec…/Recent" chip — the reference's 4th chip is interpreted as the precious-metals category
  (the prompt's suggested 4-tab set).
- No animated search-field transition (plain visibility toggle) to keep LayoutTransition away from
  the RecyclerView.

## Verification
- `JAVA_HOME=/opt/jdk-21.0.6+7 ANDROID_HOME=/opt/android-sdk ./gradlew :app:compilePlayDebugKotlin`
  → BUILD SUCCESSFUL (explicit JAVA_HOME needed, AGP fails on JDK 25 otherwise).
