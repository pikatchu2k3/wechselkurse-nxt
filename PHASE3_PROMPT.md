# PHASE 3: New list start screen (→ reference_main.png) — IMPLEMENT

Work ONLY inside this directory (cwd). Do NOT read, glob, search, or edit anything outside it.
Repo: `sal0max/currencies` (Kotlin, classic Views + XML, NOT Compose). Material3 `DynamicColors.DayNight`.
Reference image: `docs/design/reference_main.png` — OPEN it with your vision and mirror its LAYOUT.

## What the reference shows (layout template only — colors come from Material You, NOT the image)
A start screen that is a scrollable list (NOT the old converter):
- Top app bar: title "Wechselkurse", a hamburger/leading menu, a "+" action (add currency),
  a refresh action, and a "⋮" overflow. Below the bar a subtitle line: "Aktualisiert: Gerade eben".
- A vertical list of currency rows. Each row (left→right):
   1. a flag / asset icon,
   2. a primary line with the currency full name (e.g. "US-Dollar") and an ISO code (e.g. "USD"),
   3. a secondary line "1 EUR = 1,1481 USD",
   4. on the far RIGHT a large right-aligned amount with the currency symbol (e.g. "$3,11").
  Example rows: Euro (home marker), US-Dollar, Britisches Pfund, Schweizer Franken, Türkische Lira
  (shows the edited amount), Rohöl (Brent), Goldunze.
- Rows are tappable → (Phase 4 wires the calculator; for THIS phase, wire click to open the existing
  `MainActivity` with the tapped currency as destination).

## Context: what already exists
- `view/main/MainActivity.kt` + `activity_main.xml` (converter+keypad) — becomes the "Betrag ändern"
  screen in Phase 4. DO NOT touch it this phase.
- `model/Currency.kt`: now has `fullName(context)`, `iso4217Alpha()`, `symbol()`, `unitLabel()`,
  and `icon(context)` (new: returns flag for fiat, asset icon for BTC/XAU/XAG/XBZ). Use `icon()`
  for the list rows so non-flag assets render correctly.
- `repository/ExchangeRatesRepository` exposes `getExchangeRates(): LiveData<ExchangeRates?>`,
  `isUpdating(): LiveData<Boolean>`, `getError(): LiveData<String?>`; `Database` has
  `getStarredCurrencies()`, `toggleCurrencyStar()`, `isFilterStarredEnabled()`.
- Row-layout patterns: `res/layout/row_currency_dropdown.xml` (flag + name + ISO + preview, the
  closest existing row idiom). Menu patterns: `res/menu/main.xml`.
- Number formatting: `util/TextUtils` has `toHumanReadableNumber(...)`; `MainViewModel` has
  `getSignificantDecimalPlaces()` / percent formatting logic you can read (don't refactor it).

## Deliverables (this phase)

1. `view/main/RatesListActivity.kt` (new) — the LAUNCHER. In `AndroidManifest.xml` move the
   `LAUNCHER` intent-filter from MainActivity to RatesListActivity (MainActivity keeps its class,
   just no longer the launcher; add `android:parentActivityName` so up-navigation resolves).
   - `onCreate`: `setContentView(R.layout.activity_rates_list)`, own a `RatesListViewModel`
     (see #2), toolbar setup, RecyclerView + adapter, SwipeRefreshLayout + LinearProgressIndicator.
   - Observe `getExchangeRates()` (recompute rows), `isUpdating()` (progress bar), `getError()`
     (snackbar). Show "Aktualisiert: Gerade eben" using relative-time formatting (add a small helper
     in `util/DateTimeUtils.kt` — "Gerade eben", "vor X Min.", "vor X Std.", else a date).
   - Row click → launch `MainActivity` (existing, as a normal activity) with an extra for the tapped
     currency ISO code (reuse the existing `ARG_FROM`/`ARG_TO` extra names used by TimelineActivity
     if convenient, or a new `ARG_TAPPED_CURRENCY`). Phase 4 will consume it.
   - Menu (`res/menu/rates_list.xml`): "+" (open a currency-add flow), "refresh"
     (viewModel.forceUpdate...), overflow: settings, converter (open MainActivity directly),
     historical-date (reuse the existing date-picker dialog pattern from MainActivity.kt).
     For v1, "+" can open a simple dialog listing the starred/available currencies to add; wire it
     minimally — full picker is a later improvement.

2. `viewmodel/main/RatesListViewModel.kt` (new) — thin wrapper. Either extend `MainViewModel` or
   delegate: expose `getExchangeRates()`, `isUpdating()`, `getError()`,
   `forceUpdateExchangeRate()`, and `getCurrentBaseValueAsNumber()` (for amounts) by reusing the
   existing `MainViewModel`/`ExchangeRatesRepository` (do NOT duplicate the network logic).

3. `res/layout/activity_rates_list.xml` (new) — CoordinatorLayout/ConstraintLayout:
   - MaterialToolbar (title "Wechselkurse" via a new string `rates_list_title`), hamburger icon,
     the subtitle TextView under the bar, `SwipeRefreshLayout` wrapping a `RecyclerView @+id/listRates`,
     `LinearProgressIndicator`, and a `Snackbar` anchor. All colors from theme attrs (`?attr/...`,
     `?android:textColorPrimary`) — NO hardcoded colors (Material You).

4. `res/layout/row_currency_main.xml` (new) — the list row: `ShapeableImageView` (flag/icon via
   `Currency.icon(context)`), a column with the full-name line + ISO, and the secondary
   "1 EUR = X" line, plus a right-aligned big amount TextView with the symbol. Use theme attrs.

5. `view/main/RatesListAdapter.kt` (new) — `RecyclerView.Adapter<...>`:
   - Binds a row: flag/icon, full name + ISO, "1 EUR = X" secondary line (use
     `toHumanReadableNumber` with enough decimals, e.g. 4 significant, trim trailing zeros), and the
     right amount = `baseValue / baseRate.value * rowRate.value` where `baseValue` is the user's
     current base amount (use `getCurrentBaseValueAsNumber()`, default 1.0). Format amount with the
     currency symbol on the right.
   - `onBindViewHolder` click listener → callback to launch calculator with the row currency.
   - Use the new `Rate` values incl. BTC/XAU/XAG/XBZ.

6. `strings.xml` (default English): `rates_list_title` = "Currencies" (note: Phase 5 adds German),
   `updated_just_now` = "Updated just now", `updated_x_minutes_ago`="Updated %1$d min ago",
   `updated_x_hours_ago`="Updated %1$d h ago", `change_amount`="Change amount", `add_currency`="Add
   currency". Add to `values-de/strings.xml` too if trivial, else defer to Phase 5.

## Constraints
- Only these new files + `AndroidManifest.xml` + `strings.xml` + `util/DateTimeUtils.kt` may be
  created/modified. Must NOT modify `MainActivity.kt`, `MainViewModel.kt`, any layout other than the
  ones above, any provider/repository model beyond adding dead-simple read-only calls.
- Reuse existing widgets/theme classes; no new dependencies (no extra libraries).
- Verify compile using `./gradlew :app:compilePlayDebugKotlin` ONLY at the very end (it's allowed to
  build once here). Fix any compile errors, then stop.
- `docs/phase3_notes.md`: what you built + open items for Phase 4.

Respond in English. Summarize changes + compile status.
