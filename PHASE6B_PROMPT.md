# PHASE 6b: BUGFIX — rates list shows base=USD and all amounts collapse to "1" — IMPLEMENT

Work ONLY inside this directory (cwd). Do NOT read, glob, search, or edit anything outside it.
Repo: `sal0max/currencies` (Kotlin, Views+XML). List start screen: `RatesListActivity` +
`RatesListViewModel` + `RatesListAdapter`; persistence via `repository/Database.kt` (SharedPreferences).
The editable-amount keypad is `MainActivity` + `MainViewModel` (Phase 4/5b/6).

## User-reported bug (from a real device screenshot)
The rates list shows:
- The HOME row is US-Dollar (house icon on USD), NOT Euro.
- The Euro row subtitle reads "1 USD = 1 EUR".
- EVERY row amount collapses to "1" (€1, ₺1, oz t 1, $1) instead of the real converted value
  (which should be e.g. "1 EUR = 1,16 USD", "1 EUR = 56,17 TRY").

So: base = USD (despite all providers returning base EUR), and all amounts = 1.

## Verified facts (do NOT re-investigate)
- `https://api.frankfurter.app/latest?base=EUR` returns base=EUR with real values
  (USD 1.1643, TRY 56.1718, …). The fiat data source is CORRECT.
- The app's `FrankfurterApp` provider uses `baseUrl = "https://api.frankfurter.app"` and requests
  `?base=EUR`; `FrankfurterAppRatesAdapter` adds `Rate(base, 1f)` for the base (EUR) if missing.
- `Database.insertExchangeRates()` writes `_base = <base.iso4217Alpha()>` and one float key per
  currency; `SharedPreferenceExchangeRatesLiveData.getValueFromPreferences()` reads `_base` back
  into `ExchangeRates.base` and each key-float into a `Rate`.
- The merge-sources (Phase 2) only ADD BTC/XAU/XAG/XBZ; they do NOT overwrite fiat values.
- `RatesListAdapter.onBindViewHolder`: home row amount = `baseValue`; non-home amount =
  `Database.getEditedAmount(currency) ?: (baseValue / baseRateValue * row.rate.value)`.
  `baseRateValue` = `rates.find { it.currency == baseCurrency }?.value ?: 1f`.
  Subtitle uses `row_conversion` = "%1$s %2$s = %3$s %4$s" with args
  (baseValue, baseCurrency, amount, currency).

## Likely root causes to investigate (order by probability)
1. **`baseCurrency`/`Rows.base` is USD.** `RatesListViewModel.Rows.base = exchangeRates?.base`.
   Trace where that base could become USD despite providers always returning EUR. Candidates:
   `MainViewModel.currentBaseCurrency` (sourced from `Database.getLastBaseCurrency()` whose default
   is "USD", DB line ~86) vs `ExchangeRates.base`. Confirm which the LIST actually uses and whether
   a stale "USD" lives in `last_state`/`rates` prefs and leaks into the list base.
2. **All amounts collapse because `baseRateValue` is wrong** (e.g. base=USD, so the EUR amount =
   baseValue/baseRate*EUR.rate ends up ≈1) OR because all stored rate values are 1.0.
   Add TEMPORARY debug logging in `onBindViewHolder` (log currency, baseCurrency, baseValue,
   baseRateValue, row.rate.value, computed amount) so the actual values are visible in logcat. Do
   NOT ship the debug logs — remove before finishing.
3. **The "1" comes from `getBaseValue()` defaulting to 1.0** combined with the base being USD so the
   division collapses. Confirm by the debug log.

## Requirement (what the user wants)
- The rates list MUST present EUR as the base/home currency and show real converted values:
  "1 EUR = 1,16 USD", "1 EUR = 56,17 TRY". When the user enters an amount on the Euro row + Enter,
  ALL other rows scale to that EUR amount (Phase 5b behavior must be preserved).
- The fix must be robust: the list base should come from the actual rate-source base (EUR), not from
  a stale "USD" default. Ensure home row is always EUR (the source base), and amounts use the real
  rates.

## Deliverables
- Fix the base determination so the home row is EUR and amounts are correct.
- Preserve Phase 4/5b/6: confirming the Euro row sets `baseValue` (scales all rows); other rows may
  keep per-currency edited amounts.
- Remove ALL temporary debug logging before finishing (they must not ship).
- `docs/phase6b_notes.md`: the root cause you found + what you changed (with file/line refs).

## Constraints
- Do NOT touch providers/data sources/build files beyond what's needed for the fix.
- Material You: theme attrs only; no hardcoded colors.
- Verify compile: `export JAVA_HOME=/opt/jdk-21.0.6+7 ANDROID_HOME=/opt/android-sdk` THEN
  `./gradlew :app:compilePlayDebugKotlin` (must export JAVA_HOME or Gradle resolves JDK 25 and fails
  at configuration with "25.0.4.1"). Fix errors; stop.
- Prefer the SMALLEST correct change. If you must change `RatesListViewModel.getRows()`/base source,
  keep `Database.DEFAULT_CURRENCIES` and the seeded-stars behavior intact.

Respond in English. Summarize the root cause + changes + compile status.
