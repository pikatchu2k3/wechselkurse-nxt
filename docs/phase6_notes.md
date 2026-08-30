# Phase 6 notes — calculator computes ONLY in the tapped currency

## Interpretation (prompt §2)
Chosen: the **simpler/truer reading** — the keypad inputs the amount **in the tapped currency**
(one big amount field), and the **result line shows the EUR equivalent** (`≈ € 150,00` below the
big number). This matches `docs/design/reference_calc.png` ("Betrag ändern" with one currency and
a keypad, no From/To pickers, no swap button). The currency is identified by the **fixed label =
action-bar subtitle** ("Türkische Lira (TRY)"), exactly as set in Phase 4 — the reference shows no
additional in-body currency label, so none was added. No EUR input, no From/To concept anywhere.

## Menu decision (prompt §4): option (a)
The standalone converter no longer exists, so the rates-list overflow item "converter" was
**removed**:
- `res/menu/rates_list.xml`: item `R.id.converter` deleted.
- `RatesListActivity.onOptionsItemSelected`: `R.id.converter -> ...` handler deleted.
- Strings `menu_converter` deleted from `values/strings.xml` + `values-de/strings.xml` (no other
  references). No crash paths: `MainActivity` is `exported="false"` and was only ever started by
  the list (now always with `ARG_TAPPED_CURRENCY`) or the removed menu item.

## What changed

### 1. `res/layout/main_display.xml`
- Removed `spinnerFrom` + `spinnerTo` (both `SearchableSpinner`) and the `btn_toggle` swap FAB.
- `scrollViewTextFrom` (big amount) and `scrollViewTextTo` (EUR-equivalent line) now span the full
  width (`start` anchored to parent instead of the removed spinners). Vertical geometry, ids
  (`textFrom`/`textTo`), styles and the fee/info texts are unchanged; `tools:text` updated.
- `textFrom` = the amount entered in the tapped currency; `textTo` = its EUR equivalent
  (repurposed, per prompt §1 "removed or repurposed").
- No hardcoded colors introduced.

### 2. `MainActivity.kt`
- Removed: `spinnerFrom`/`spinnerTo` fields + lookups, both spinner `OnItemSelectedListener`s, the
  spinner-feeding observers (`setRates`, `setSelection`, `setCurrentRate`, `setCurrentSum`) and
  `toggleEvent()`. Imports `AdapterView`, `Rate`, `SearchableSpinner` removed.
- `onCreate`: without `ARG_TAPPED_CURRENCY` (or an unparseable code) the screen now `finish()`es
  immediately — defensive, since the converter entry point is gone (no crash either way). With a
  tapped currency: title/subtitle as in Phase 4, then
  `viewModel.setConversionCurrencies(tapped, Currency.EUR)` — the pair is fixed per screen, there
  is nothing to pick. The Phase 4/5b home-row special case (`setBaseCurrency(home)`) is obsolete:
  the base is now always the tapped currency, which is the same value for the home row.
- Confirm (`confirmEditedAmount`, keypad ↵ and hardware ENTER unchanged): persists
  `getCurrentBaseValueAsNumber()` — the **entered amount in the tapped currency** — instead of the
  converted result. Persistence targets are untouched (Phase 4/5b preserved): home row →
  `Database.setBaseValue(amount)` (scales all list rows), any other row →
  `Database.setEditedAmount(currency, amount)`. This is semantics-preserving: the stored value was
  and still is "the amount in the row's currency" — only the input currency changed (typed
  directly, no conversion of the input).
- The result line observer prepends `≈ ` so the EUR line reads as an equivalent.

### 3. `MainViewModel.kt`
- Replaced `setBaseCurrency()`/`setDestinationCurrency()` with **`setConversionCurrencies(base,
  destination)`**: one atomic `Database.saveLastUsedRates(base, destination)` write. Calling the
  two old setters back-to-back was racy (the second read the not-yet-updated base LiveData and
  could overwrite the first write) — irrelevant with pickers, fatal with a fixed pair.
- All calculation/persistence internals (`currentBaseValue`, `result`, fee, historical dates,
  `ratesInformationFooter`) untouched. With base = tapped and destination = EUR the existing math
  yields exactly "amount in tapped currency → EUR equivalent" (+fee, as before), and the footer
  shows the unit rate ("1 TRY ≈ 0,0679 EUR"). `getResultAsNumber()` kept as API (now unused).

### 4. `RatesListActivity.kt`
- Only the dead converter menu handler removed (see above). Tapping a row still launches
  `MainActivity` with `ARG_TAPPED_CURRENCY`; `onResume` refresh still picks up edits.

## Not touched
Providers, data sources, `Database.kt`, fiat providers, build files, keypads (`main_keypad*.xml`
already carry AC/()/%/↵ from Phase 4), `SearchableSpinner`/dialog classes (still referenced by
their own dialog), timeline.

## Deviations
- The EUR-equivalent line keeps the big `TitleLarge` style in the layout (pre-existing style of
  `textTo`); it reads as a secondary line because it sits right under the amount and is prefixed
  with `≈`. Kept the pre-existing vertical geometry of the display (no redesign beyond removals).
- When editing the home currency itself (EUR) the equivalent line shows the same number as the
  input — harmless, no special-casing added.
- `ic_swap_vert` drawable is now unused (left on disk; `desc_toggle_currencies` is still used by
  `menu/timeline.xml` and was kept).

## Verification
- `JAVA_HOME=/opt/jdk-21.0.6+7 ANDROID_HOME=/opt/android-sdk ./gradlew :app:compilePlayDebugKotlin`
  → BUILD SUCCESSFUL (resources processed, Kotlin compiled, no errors).
