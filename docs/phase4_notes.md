# Phase 4 notes — "Betrag ändern" calculator screen

## What changed

### 1. `MainActivity.kt` — re-chromed as the change-amount screen
- Reads `RatesListActivity.ARG_TAPPED_CURRENCY` from the intent; if present (and resolvable via
  `Currency.fromString`):
    - `title = getString(R.string.change_amount)` ("Betrag ändern" via `values-de`)
    - `supportActionBar?.subtitle = "<fullName> (<ISO>)"`, e.g. "Türkische Lira (TRY)"
    - `viewModel.setDestinationCurrency(currency)` — destination only, base untouched (per prompt
      preference; conversion reads "from base to tapped").
- If the extra is absent, the previous default behavior is kept (`title = null`).
- `supportActionBar?.setDisplayHomeAsUpEnabled(true)` + `onSupportNavigateUp()` → `finish()`
  back to the list.
- New XML click handlers: `acEvent` → `viewModel.addClear()`, `parenEvent` →
  `viewModel.addParenthesis('(' or ')')` (reads the button caption), `percentEvent` →
  `viewModel.percent()`, `confirmEvent` → `confirmEditedAmount()`: persists
  `viewModel.getResultAsNumber()` for the tapped currency via
  `Database(this).setEditedAmount(...)` and `finish()`es.
- Hardware keyboard: added `(`, `)`, `%` and ENTER (also numpad enter) → confirm.

### 2. Keypad layouts (both `main_keypad.xml` + `main_keypad_extended.xml`)
- Outer `FrameLayout` (RTL workaround) and the inner `ConstraintLayout` kept; all existing button
  ids and `android:onClick` wiring preserved (`btn_0..9`, `btn_decimal`, `btn_delete`,
  `btn_add/subtract/multiply/divide`, plus `btn_00`/`btn_000` in the extended layout).
- Regular keypad restructured to the reference 5-row grid:
  `AC ( ) % ÷` / `7 8 9 ×` / `4 5 6 −` / `1 2 3 +` / `0 , ⌫ ↵`.
  Row 1 therefore has five columns (AC, `(`, `)` are separate buttons per spec), the other rows
  four.
- Extended keypad restructured to 6 rows × 4 cols (22 buttons needed because of `00`/`000`):
  `AC ( ) %` / `7 8 9 ÷` / `4 5 6 ×` / `1 2 3 −` / `0 00 000 +` / `. ⌫ ↵(double width)`.
- New ids: `btn_ac`, `btn_paren_open`, `btn_paren_close`, `btn_percent`, `btn_confirm`.
- Styling: `AppTheme.TextAppearance.Keypad`, operators/AC/%/↵ use `@color/color_keypad_operators`
  (theme color, no hardcoded colors).
- Vertical constraints: number buttons form the top-down chain (as before), operators align to
  their row anchor — avoids circular dependencies.

### 3. `MainViewModel.kt`
- `addParenthesis(char: Char)`: mirrors `addOperator` (switches into calculation mode using the
  base value). `(` gets an inserted `×` if appended directly after a number/`)` (mXparser has no
  implied multiplication); `)` is only appended when an unmatched, non-empty `(` exists.
- `addNumber`: added an additive-only branch: a number typed directly after `)` gets `×` inserted.
- `percent()`: mXparser treats `%` as **modulo**, so appending `%` would evaluate wrong. Hence
  percent is implemented as "÷100 of the current value", consistent in both modes: calculation
  mode appends `÷ 100` after a number; number-input mode divides the entered value by 100 via
  `BigDecimal` (exact, `toPlainString`, no scientific notation).
- `evaluateMathExpression()` verified: its replace chain only rewrites spaces/`−×÷`; `(`, `)` pass
  through unchanged, `%` is never appended.
- `addClear()` delegates to the existing `clear()`.

### 4. `Database.kt`
- New `edited_amounts` prefs section: `setEditedAmount(currency, Double?)` /
  `getEditedAmount(currency): Double?` under stable keys `edited_<ISO>` (stored as string to keep
  `Double` precision; `null` removes the entry). No other repository/provider logic touched.

### 5. `RatesListActivity.kt` + `RatesListAdapter.kt`
- `onBindViewHolder` shows `Database(context).getEditedAmount(currency)` as the row amount when
  non-null (existing `row_amount` formatting reused), else the converted amount as before.
- `RatesListActivity.onResume` calls the new `adapter.refresh()` so an edit made in the change
  amount screen is visible immediately when navigating back.

### 6. Strings
- No new strings needed: `change_amount` already exists in `values/` and `values-de/` (Phase 3).
  The subtitle is built in code from `Currency.fullName` + `iso4217Alpha()`.

## Deviations
- Parenthesis is two separate buttons (`btn_paren_open`, `btn_paren_close`) in row 1 → the
  reference image merges them into one `()` key; the prompt spec requires both ids, so row 1 has
  five narrower keys.
- Extended keypad needs 6 rows (5-row reference grid can't host the extra `00`/`000` keys);
  ↵ spans two columns on the last row.
- Percent is `÷100` instead of a literal `%` token (mXparser `%` = modulo) — as anticipated by
  the prompt.
- `RatesListAdapter.kt` was minimally touched (not in the prompt's touched-files list) because the
  row amount formatting lives there; required to fulfill deliverable #5.
