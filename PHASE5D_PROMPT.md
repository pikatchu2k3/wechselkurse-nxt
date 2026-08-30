# PHASE 5d: Add-currency picker → tab/chip layout (like reference image) — IMPLEMENT

Work ONLY inside this directory (cwd). Do NOT read, glob, search, or edit anything outside it.
Repo: `sal0max/currencies` (Kotlin, Views+XML). The add-currency flow is
`RatesListActivity.showAddCurrencyDialog()` + `AddCurrencyDialogAdapter.kt` + `res/layout/dialog_add_currency.xml`
(or `searchable_spinner_dialog*` reused). Reference image: `docs/design/reference_add_tabs.png`? —
if present open it; regardless, the goal layout is described below.

## Current state (read these first)
- `AddCurrencyDialogAdapter.kt` shows ALL available currencies stacked into SECTIONS with headers
  (Currencies / Precious metals / Crypto / Commodities), and `dialog_add_currency.xml` has a
  `SearchView` on top + the list below. This is the "one long grouped list" design (Phase 5c).

## Goal (per user screenshot — "Favorit hinzufügen")
Replace the stacked-section design with a **tab/chip picker**:
- **Top chips row** (selectable category tabs, horizontal): "Währungen" (Currencies),
  "Crypto", "Rohstoffe" (Commodities), and the precious-metals tab. The reference shows 4 chips:
  Währungen · Crypto · Rohstoffe · Rec… (the last is likely "Recent"/a 4th category). Use these four:
  1. Währungen (Currencies) — all fiat
  2. Crypto — BTC
  3. Rohstoffe (Commodities) — XBZ (brent oil)
  4. Edelmetalle (Precious metals) — XAU, XAG  (OR fold metals into Rohstoffe if it maps to the
     reference's 4th chip better; pick a clean 4-tab set and document it).
- Only ONE category is visible at a time (the selected chip). The list below shows ONLY that
  category's entries (no stacked headers for the others).
- A **search button/icon** (bottom-right FAB, as in the screenshot) that toggles a search field;
  when open, it filters the CURRENT category live (name + ISO, case-insensitive). The search should
  be dismissible and the category chips stay visible.
- Tapping an entry adds/removes it (star toggle) — reuse `viewModel.toggleCurrencyStar` via the
  existing `onItemToggled` callback. Selected state (star filled/empty) is shown.

## How to build
- Keep the existing `AddCurrencyDialogAdapter` classification (`groupOf`), but change `rebuild()` so
  it only renders the rows of the SELECTED category (no headers for other categories). Expose
  `setCategory(group)` + `getCategories()` so the dialog can drive the chips. Keep the search filter
  applied only to the selected category.
- Update `dialog_add_currency.xml`: a horizontal `LinearLayout`/`HorizontalScrollView` of category
  chips (or a Material `ChipGroup` / `TabLayout`) at top, the `RecyclerView` below (fill the dialog),
  and a search FAB/button bottom-right that toggles the search field.
- The chip for the active category should be visibly selected (Material You: use theme attrs /
  `?attr/colorPrimary`/`colorOnPrimary` for selected vs `?attr/colorSurfaceVariant`/outline for
  unselected). No hardcoded colors.
- Isolate the dialog into its own small controller or keep logic in `AddCurrencyDialogAdapter` +
  a lightweight host in `RatesListActivity.showAddCurrencyDialog()` (prefer the smallest change).

## Constraints
- Touched files likely: `AddCurrencyDialogAdapter.kt`, `dialog_add_currency.xml`,
  `RatesListActivity.kt`, maybe a small new category-chip layout + strings (`values/`+`values-de`:
  "Währungen", "Crypto", "Rohstoffe", "Edelmetalle", search hint). Do NOT touch providers/data sources.
- Material You: theme attrs only; no hardcoded colors.
- Preserve the behavior that closing the dialog re-renders the rates list (via existing `getRows()`).
- Verify compile: `export JAVA_HOME=/opt/jdk-21.0.6+7 ANDROID_HOME=/opt/android-sdk` THEN
  `./gradlew :app:compilePlayDebugKotlin` (must export JAVA_HOME or Gradle resolves JDK 25 and fails
  at configuration with "25.0.4.1"). Fix errors; stop.
- `docs/phase5d_notes.md`: the tab set + approach + any deviations.

Respond in English. Summarize changes + compile status.
