# PHASE 5c: Add-currency dialog → 3 grouped sections + live search — IMPLEMENT

Work ONLY inside this directory (cwd). Do NOT read, glob, search, or edit anything outside it.
Repo: `sal0max/currencies` (Kotlin, Views+XML). The current add-currency flow is
`RatesListActivity.showAddCurrencyDialog()` (~lines 140-154): a plain `AlertDialog.setMultiChoiceItems`
over ALL rates — no search, no grouping. The list start screen is `RatesListActivity` +
`RatesListViewModel` + `RatesListAdapter`. There is ALSO a reusable picker in the repo:
`view/main/spinner/SearchableSpinner.kt` + `SearchableSpinnerDialog` + `SearchableSpinnerDialogAdapter`
with `searchable_spinner_dialog.xml` (a SearchView + RecyclerView with favorites filter). READ both
before deciding how to build the new dialog — prefer reusing existing patterns/widgets.

## Requirement
Replace the add-currency dialog so the user can pick currencies/metals/crypto from a list that is:
1. **Grouped into three sections**, each with its own header:
   - **Währungen (Currencies)** — fiat ISO currencies (all standard fiat, e.g. EUR, USD, GBP, TRY).
   - **Edelmetalle (Precious metals)** — XAU, XAG (gold, silver). (XPD/XPT stay hidden — no data source.)
   - **Krypto (Crypto)** — BTC (bitcoin). (Optionally include XBZ/Brent under a "Rohstoffe" grouping
     or fold it into Krypto if you prefer — but keep XBZ reachable; see note below.)
2. **Live search**: a text field/SearchView that filters the items AS THE USER TYPES
   (substring match against full name + ISO code, case-insensitive). No submit button required.
3. Tapping an item adds/removes it (star toggle) — reuse `viewModel.toggleCurrencyStar(currency)`.
   The list should reflect current starred state (show selected state, e.g. a checkmark).
4. Closing the dialog re-renders the rates list (it already observes stars via `getRows()`).

## Notes
- Decide how to classify each `Currency` into a group. Implement a small helper
  (e.g. `groupOf(currency): AddGroup`) based on the `Currency` enum, e.g.:
  - `XAU`, `XAG` (and XPD/XPT if you choose to surface them) → METALS
  - `BTC` → CRYPTO (or a combined "Krypto & Rohstoffe" if XBZ belongs there)
  - `XBZ` (Brent/oil) → decide: put it in its own "Rohstoffe (Commodities)" section OR with crypto.
    Pick the cleaner design and document it. Don't lose XBZ from the picker.
  - everything else → CURRENCIES.
- Live search should match against the localized full name AND the ISO code, case-insensitive,
  so e.g. "go" matches "gold"/"XAU" and "währ" or "doll" matches appropriately.
- Use Material You theme attrs for colors. Reuse `row_currency_dropdown.xml` style rows if practical.
- Keep the dialog self-contained and robust (no crash on empty classes, smooth RecyclerView update
  on each keystroke). Use a `TextWatcher` or SearchView `setOnQueryTextListener`.

## Constraints
- Touched files likely: `RatesListActivity.kt`, plus new/edited layout + adapter for the dialog
  (e.g. `res/layout/dialog_add_currency.xml`, a small `AddCurrencyDialogAdapter`/`Model`), and
  possibly `strings.xml`/`values-de` for the section headers and search hint. Do NOT touch providers,
  data sources, or build files beyond nothing. Do NOT break the existing `showAddCurrencyDialog`
  entry point in the menu.
- Material You: colors via theme attrs; no hardcoded colors.
- Reuse existing favorites/star state (`Database.getStarredCurrencies`, `toggleCurrencyStar`).
- Verify compile with `./gradlew :app:compilePlayDebugKotlin` ONCE at the end; fix errors; stop.
- `docs/phase5c_notes.md`: which approach you chose (reuse SearchableSpinnerDialog vs new dialog),
  the grouping logic, and any deviations.

Respond in English. Summarize changes + compile status.
