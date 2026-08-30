# Phase 5c notes — Add-currency dialog: grouped sections + live search

## Approach — new self-contained dialog, reusing existing widgets/patterns
- NOT reusing `SearchableSpinnerDialog` directly: it is hard-wired to `MainViewModel` and
  converter-only features (current sum + preview conversion, "another API" hint row, starred-filter
  button, single-select click-to-pick-and-dismiss semantics). Forcing it into the add flow would
  have meant adding a mode flag and untangling its observers.
- Instead: same building blocks — layout pattern of `searchable_spinner_dialog.xml` (SearchView +
  RecyclerView), rows reused verbatim (`row_currency_dropdown.xml`), the star drawables
  (`ic_favorite`/`ic_favorite_empty`) as selected indicator, and the same
  `SearchView.setOnQueryTextListener` + `fullName/iso4217Alpha` contains-filter idiom as
  `SearchableSpinnerDialogAdapter`.
- New files:
  - `view/main/AddCurrencyDialogAdapter.kt` — grouped/live-search list adapter
  - `res/layout/dialog_add_currency.xml` — dialog content (SearchView + RecyclerView)
  - `res/layout/row_add_currency_header.xml` — section header row
- `RatesListActivity.showAddCurrencyDialog()` keeps its name/call site; body replaced with the new
  dialog (custom view, OK button, no per-item checkboxes anymore).

## Grouping logic
- `AddCurrencyDialogAdapter.groupOf(currency): AddGroup` (private enum, display order):
  - `XAU`, `XAG` → **Edelmetalle/Precious metals**
  - `BTC` → **Krypto/Crypto**
  - `XBZ` (Brent) → **Rohstoffe/Commodities** — own section, NOT folded into Krypto: oil is not
    crypto, and the data sources differ (BrentOil vs CoinGecko), so a separate section is the
    cleaner design. XBZ stays reachable.
  - everything else → **Währungen/Currencies**
- Picker items are still derived from the provider's current `rates` (like the old dialog): only
  assets that actually have rate data are pickable. `XPD`/`XPT` are filtered out defensively
  (no data source). Within a group entries are sorted by ISO code; groups always render in the
  fixed order currencies → metals → crypto → commodities.

## Behavior
- Live search: filters on every keystroke (`onQueryTextChange`), case-insensitive substring match
  against the localized full name AND the ISO code ("go" → Gold Ounce/XAU, "doll" → US Dollar/USD).
  A group's header only shows while that group has ≥1 matching entry; empty result = empty list
  (no crash).
- Tapping a row (or its star) toggles the star via `viewModel.toggleCurrencyStar(currency)` — same
  star persistence (`Database.getStarredCurrencies`/`toggleCurrencyStar`) as before. Selected state
  = filled star (consistent with the app's star metaphor, hence no separate checkmark drawable).
  Star state is kept in sync via a temporary `Observer` on `getStarredCurrencies()`, removed on
  dismiss.
- Closing the dialog: nothing to do — the rates list already re-renders because it observes stars
  via `getRows()`.
- Colors: header uses `?attr/colorPrimary` (Material You dynamic color); rows/dialog reuse the
  themed `row_currency_dropdown.xml`/AlertDialog styling — no hardcoded colors.
- Entry icons use `Currency.icon(context)` (bitcoin/gold/silver/oil marks for non-flag assets
  instead of the gray `flag_unknown` globe; regular fiat flags unchanged).
- Search hint string added (`add_currency_search_hint`); section headers localized (values +
  values-de). Other translations fall back to English.

## Deviations
- 4 sections instead of the "3 + optional" phrasing: Currencies / Precious metals / Crypto /
  Commodities (XBZ decision above).
- `notifyDataSetChanged()` on each keystroke (existing pattern in `SearchableSpinnerDialogAdapter`);
  list is small enough that this is smooth and it keeps the adapter robust/stateless.
- No "no results" empty view — the empty RecyclerView is the (acceptable) empty state, matching the
  existing searchable spinner dialog.

## Verification
- `JAVA_HOME=/opt/jdk-21.0.6+7 ./gradlew :app:compilePlayDebugKotlin` → BUILD SUCCESSFUL
  (see phase5b notes: explicit JAVA_HOME needed in this shell, AGP requires JDK 17–21).
