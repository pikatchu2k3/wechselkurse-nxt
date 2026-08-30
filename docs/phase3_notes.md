# Phase 3 Notes — RatesListActivity (list start screen)

## What was built
- `view/main/RatesListActivity.kt` — NEW LAUNCHER. Toolbar ("Currencies"), leading hamburger
  (placeholder for a drawer in a later phase), `+`/refresh/⋮ overflow menu. Observes rates,
  isUpdating (LinearProgressIndicator), error (Snackbar). Relative-time subtitle
  ("Updated just now") via new `util/DateTimeUtils.toRelativeTimeString()`.
- `viewmodel/main/RatesListViewModel.kt` — thin wrapper delegating to the existing
  `MainViewModel`/`ExchangeRatesRepository` (getExchangeRates, isUpdating, getError,
  forceUpdateExchangeRate, getCurrentBaseValueAsNumber). No duplicated network logic.
- `view/main/RatesListAdapter.kt` — RecyclerView adapter. `icon(context)` for flag/asset icon,
  full name + ISO, secondary "1 EUR = X" line, right-aligned amount with symbol
  (fallback: unitLabel then ISO). Row click → callback with the `Currency`.
- `res/layout/activity_rates_list.xml` + `row_currency_main.xml` + `res/menu/rates_list.xml`.
- `AndroidManifest.xml` — LAUNCHER moved to RatesListActivity; MainActivity now has `parentActivityName`.
- `ic_add/ic_home/ic_menu/ic_refresh.xml` drawables; strings (en + de).

## Open items for Phase 4
- Row click currently launches the calculator via `ARG_TAPPED_CURRENCY` extra (intent defined;
  MainActivity does not yet consume it — that is Phase 4).
- "+"/add-currency flow is currently a stub (menu item wired, picker not yet implemented).
- Hamburger is a dead placeholder (no drawer yet).
- The old converter logic in MainActivity is untouched (still functions as a separate screen).

## Verification
- `./gradlew :app:compilePlayDebugKotlin` → BUILD SUCCESSFUL.
- Colors all from theme attrs (`?attr/...`, `?android:textColorPrimary`) — Material You friendly.
