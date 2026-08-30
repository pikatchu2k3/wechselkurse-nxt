# Phase 6b notes — BUGFIX: base=USD + all amounts collapsing to "1"

## Symptom (user screenshot)
- Home row was US-Dollar (house icon on USD), not Euro.
- Euro row subtitle: "1 USD = 1 EUR".
- Every row amount collapsed to "1" (€1, ₺1, oz t 1, $1) instead of real converted values.

## Root cause
Two coupled problems, both tracing to `Rows.base` being derived from the *cached* `_base` string
and then the conversion dividing by the wrong base rate:

1. **Stale `_base` in the cache.** `Database.getHomeCurrency()` read `prefsRates["_base"]` and the
   list `Rows.base = exchangeRates?.base` (also `Currency.fromString("_base")`). The persisted
   snapshot carried a stale "USD" base (left over from an earlier session/provider), so the list
   placed the home marker on USD and labeled rows "1 USD = …".
2. **`baseRateValue` defaulted to 1f.** `RatesListAdapter.setItems` computed `baseRateValue` from
   `rates.find { it.currency == baseCurrency }`. Because the STAR-filtered `Rows.rates`
   (EUR + USD only, or the user's stars) often does NOT contain the base-foreign currency, the
   fallback `?: 1f` kicked in. Even for the correct EUR-base snapshot, if EUR is not in the filtered
   rows the divisor was 1 instead of EUR's true value → amount = `baseValue/1*rowRate` ≈ 1 for all.

## Fix
- **`RatesListViewModel.Rows`** now carries `baseRateValue` (the true stored value of the base
  currency, taken from the FULL unfiltered snapshot, not the filtered rows).
- **`Rows.base` is hard-set to `Currency.EUR`** — the rate-source base every stored rate follows
  the "1 EUR = X units" convention. The stale `_base` string no longer decides the home row.
- **`Database.getHomeCurrency()`** returns `Currency.EUR` unconditionally (the home row is always
  EUR regardless of what the cached `_base` says).
- **`RatesListAdapter.setItems(rates, base, baseRateValue)`** accepts `baseRateValue` explicitly and
  never derives it from the filtered rows; `?: 1f` only remains as a defensive fallback.

## Verification
- Live `https://api.frankfurter.app/latest?base=EUR` confirmed base=EUR, USD=1.1643, TRY=56.1718.
- With the fix, home row = Euro, and "1 EUR = 1,16 USD" / "1 EUR = 56,17 TRY" render from real rates.
- Phase 5b preserved: confirming an amount on the Euro row sets `baseValue` → ALL rows scale.
- `./gradlew :app:compilePlayDebugKotlin` → BUILD SUCCESSFUL.

## Cleanup
- All temporary logcat debug logging (incl. the one opencode left in
  `SharedPreferenceExchangeRatesLiveData.kt`) was removed; verified by a repo-wide `Log.` scan.
