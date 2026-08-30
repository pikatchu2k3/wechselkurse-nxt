# PHASE 5b: Start = EUR+USD only + Euro-driven base-value schema — IMPLEMENT

Work ONLY inside this directory (cwd). Do NOT read, glob, search, or edit anything outside it.
Repo: `sal0max/currencies` (Kotlin, Views+XML). The list start screen is
`view/main/RatesListActivity.kt`, backed by `viewmodel/main/RatesListViewModel.kt` and rendered by
`view/main/RatesListAdapter.kt` (row layout `res/layout/row_currency_main.xml`). The editable
amount keypad is `view/main/MainActivity.kt` + `viewmodel/main/MainViewModel.kt`
(`confirmEvent` in MainActivity stores a per-currency edited amount via
`Database.setEditedAmount(...)`; `Database.getEditedAmount(currency)` is read in the adapter).

Read these files first to understand the current wiring, then implement the two requirements below.
Do NOT change Phase 1-3 data sources/providers or the fiat providers.

## Requirement 1 — start with only EUR + USD, add the rest selectively
Default currency set must be just EUR and USD. Other currencies, precious metals and crypto are
added later by the user via the "+" (add-currency) flow.

Current behavior (`RatesListViewModel.rows.update`, lines ~40-50): if `starredCurrencies` is empty
it shows ALL rates; otherwise it filters to starred. Change it so:
- A "default set" = `{Currency.EUR, Currency.USD}` shown on first run (when the user has not
  chosen any stars yet).
- Decide the cleanest implementation: EITHER seed `Database`'s starred set with EUR+USD on first
  launch (only if empty/never-initialized) so the existing starred-filter produces exactly EUR+USD,
  OR change the empty-set fallback in `RatesListViewModel` to return only EUR+USD instead of allRates.
  Pick ONE and make it robust (no crash, no stale all-rates dump). Prefer the approach that keeps
  `toggleCurrencyStar` and the "add" dialog working unchanged. Whichever you choose, document it.
- The "add" dialog (`showAddCurrencyDialog` in RatesListActivity) already toggles stars via
  `toggleCurrencyStar` — after this change, using it to add e.g. XAU/GBP/BTC makes those rows appear.
  Verify the dialog lists a sensible subset (it currently uses all rates; that is fine).

## Requirement 2 — Euro-driven base value: type amount + Enter → all rows scale
The Euro row (the home/base row) is the editable base. When the user taps it and enters an amount
(e.g. "1") and confirms with Enter, EVERY other row must show the converted value based on that
amount: "1 EUR = X USD", "1 EUR = Y GBP", etc. Typing "100" → all rows show "100 EUR = X".

Current behavior: `RatesListAdapter.setBaseValue(baseValue)` already computes
`baseValue / baseRateValue * row.rate.value` for the amount, and `MainActivity.confirmEvent`
stores a per-currency edited amount via `Database.setEditedAmount`. The adapter shows the edited
amount for a currency if present, else the base-value-derived amount.

Change so the BASE (home) row's confirm drives the conversion for ALL rows:
- When the user confirms an amount for the BASE/home currency (the row whose currency ==
  `Rows.base`), store it as the BASE VALUE — not as a per-row edited amount — and have the list
  use it as `setBaseValue(...)` so every non-home row recomputes.
- Concretely: in `MainActivity`, if the tapped/edited currency equals the base currency, on
  confirm call something like `RatesListViewModel`/`Database` store a "base value" and `finish()`;
  in `RatesListActivity.onResume`/adapter, read that base value into `adapter.setBaseValue(...)`.
  Add `Database.setBaseValue(Double?)` / `getBaseValue(): Double?` (mirror the edited-amount
  accessors, stable key e.g. `base_value`). Default 1.0 when null.
- The home/EUR row should show the CURRENT base value (e.g. big "1" with a EUR marker, or the
  amount it currently represents) and be what the user edits. Non-home rows keep showing
  `baseValue / baseRateValue * rowRate`.
- Keep `editNonfiat` working: any currency row (EUR, USD, gold, BTC, oil) tapping opens the keypad;
  confirm on a NON-base row may keep the existing per-currency edited-amount behavior, but the
  requirement is specifically that confirming on the BASE row scales everything. Implement that
  base-row behavior cleanly and don't break the existing "edited amount per currency" for non-base rows.
- The row_conversion subtitle ("1 EUR = 1,1481 USD") should reflect the same base context. Keep it
  consistent: when base value != 1, consider whether to show "X EUR = Y" vs the per-1 rate. The
  primary requirement is the AMOUNT column scaling (right side); keep the subtitle readable.

## Constraints
- Touched files likely: `RatesListViewModel.kt`, `RatesListAdapter.kt`, `RatesListActivity.kt`,
  `MainActivity.kt`, `MainViewModel.kt`, `Database.kt`, `row_currency_main.xml` (only if needed for
  the home-row amount display), `strings.xml`+`values-de` (only if new strings are required). Do NOT
  touch providers/data sources/build files.
- Material You: all colors via theme attrs; no hardcoded colors.
- Verify compile with `./gradlew :app:compilePlayDebugKotlin` ONCE at the end; fix any errors; stop.
- `docs/phase5b_notes.md`: what you changed, which approach you chose for req 1, and any deviations.

Respond in English. Summarize changes + compile status.
