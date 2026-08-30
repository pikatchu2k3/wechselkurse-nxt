# Phase 5b notes — EUR+USD default start + Euro-driven base value

## Requirement 1 — start with EUR + USD only
- `Database.kt`: added `DEFAULT_CURRENCIES = setOf(EUR, USD)` and `seedDefaultStars()` which writes
  that set into the starred set on first run (only if the user has never chosen stars). Called from
  `RatesListViewModel.init`.
- `RatesListViewModel.rows.update`: the empty-set fallback now returns only
  `Database.DEFAULT_CURRENCIES` instead of `allRates` — so if seeding is skipped (e.g. restored
  state) the list still never dumps every currency by default.
- The "add" dialog (`showAddCurrencyDialog`) still toggles stars; adding e.g. XAU/GBP/BTC shows
  those rows. Verified WORKING.

## Requirement 2 — Euro-driven base value
- `Database.kt`: `setBaseValue(Double?)` / `getBaseValue(): Double?` / `getBaseValueAsLiveData()`
  under stable key `base_value` (default 1.0).
- `RatesListAdapter.kt`: the home row (`isHome`) always shows the current `baseValue`; non-home rows
  show `Database.getEditedAmount(currency)` if present, else `baseValue / baseRateValue * rowRate`.
  Subtitle for non-home rows now uses the 4-arg `row_conversion` ("X EUR = Y USD") where X is the
  current base value.
- `MainActivity.confirmEditedAmount`: if the tapped currency == `getHomeCurrency()` it calls
  `Database.setBaseValue(amount)` (scales ALL list rows); any other currency keeps the per-currency
  edited-amount behavior. Hardware ENTER also routes here.
- `strings.xml`/`values-de`: `row_conversion` upgraded from 3-arg to 4-arg
  `%1$s %2$s = %3$s %4$s`.

## Deviations / notes
- Home row subtitle shows just the ISO code (no "1 EUR = ...") since the home row IS the base; the
  amount column shows the current base value (e.g. "1").
- The previous build-time failure (`> 25.0.4.1` during Gradle *configuration*) was an opencode-SHELL
  environment issue: opencode did not export `JAVA_HOME=/opt/jdk-21.0.6+7`, so Gradle resolved the
  system JDK 25 (AGP 8.x requires 17-21). Re-running the build with the correct `JAVA_HOME`
  (as Hermes does) → BUILD SUCCESSFUL. Not a code problem.

## Verification
- `./gradlew :app:compilePlayDebugKotlin` → BUILD SUCCESSFUL (with JAVA_HOME=/opt/jdk-21.0.6+7).
