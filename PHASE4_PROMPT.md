# PHASE 4: "Betrag ändern" calculator screen (→ reference_calc.png) — IMPLEMENT

Work ONLY inside this directory (cwd). Do NOT read, glob, search, or edit anything outside it.
Repo: `sal0max/currencies` (Kotlin, Views+XML). Reference image: `docs/design/reference_calc.png`
— OPEN it with vision and mirror its LAYOUT (colors from Material You, NOT the image).

## What the reference shows (layout template)
A screen titled "Betrag ändern" with a back arrow (up navigation), a subtitle with the currency,
e.g. "Türkische Lira (TRY)". A large right-aligned number display. A calculator keypad:
`AC ( ) % ÷` / `7 8 9 ×` / `4 5 6 −` / `1 2 3 +` / `0 , ⌫ ↵` (5 rows).

## Existing state (read before touching)
- `view/main/MainActivity.kt` — the converter+keypad screen. It will become the "change amount"
  screen. It already has: input handlers `numberEvent`/`decimalEvent`/`deleteEvent`/
  `calculationEvent` (lines ~465-493), hardware keyboard `onKeyDown`, state in
  `viewmodel/main/MainViewModel.kt` (`addNumber/addDecimal/delete/clear/addition/subtraction/
  multiplication/division`, `setBaseCurrency/setDestinationCurrency`, mXparser via
  `evaluateMathExpression`). It observes `isExtendedKeypadEnabled` and switches between
  `main_keypad.xml` and `main_keypad_extended.xml` (lines ~441-451).
- `view/main/RatesListActivity.kt` (Phase 3) launches the calculator with an extra
  `ARG_TAPPED_CURRENCY` (ISO code) — MainActivity does NOT yet read it.
- `viewmodel/main/MainViewModel.kt` — has `setDestinationCurrency(currency)` (line ~585).

## Deliverables

### 1. MainActivity becomes "Betrag ändern"
- In `onCreate`, set `title = getString(R.string.change_amount)` and the action bar subtitle to the
  tapped currency's full name + ISO, e.g. "Türkische Lira (TRY)" —
  `supportActionBar?.subtitle = "${currency.fullName(this)} (${currency.iso4217Alpha()})"`.
  Enable `supportActionBar?.setDisplayHomeAsUpEnabled(true)`; handle home/up to `finish()` back to
  the list. (The up arrow replaces the default; use `onSupportNavigateUp()`.)
- Read the tapped currency from the intent: `intent.getStringExtra(RatesListActivity.ARG_TAPPED_CURRENCY)`
  → `Currency.fromString(iso)` → `viewModel.setDestinationCurrency(currency)`. If absent, keep the
  current default behavior. Also set the base currency to EUR (the app's base) so the conversion
  reads "from EUR to tapped". Use `viewModel.setBaseCurrency(Currency.EUR)` if the base is not
  already EUR — but do NOT break the existing preference (only set destination; leave base as the
  repository default). Prefer: only set the destination; keep base unchanged.

### 2. Keypad layout → add AC, ( ), %, ↵
- In BOTH `main_keypad.xml` and `main_keypad_extended.xml`, add the missing buttons so the layout
  matches the reference 5-row grid while PRESERVING the existing 4×4 wiring and the outer
  `FrameLayout` (the comment warns it is needed for RTL in landscape — do NOT remove it).
- Buttons to ADD:
  - `btn_ac` (text "AC") → new `acEvent`, action = `viewModel.clear()`.
  - `btn_paren_open` (text "(") → new `parenEvent` (see #3).
  - `btn_paren_close` (text ")") → same `parenEvent` handler (or two handlers).
  - `btn_percent` (text "%") → new `percentEvent` (see #3).
  - `btn_confirm` (text "↵" or a check icon) → new `confirmEvent`, action = persist the current
    result as the edited amount for the tapped currency and `finish()` (see #4).
- Keep existing button ids (`btn_0..btn_9`, `btn_decimal`, `btn_delete`, `btn_add`/`btn_subtract`/
  `btn_multiply`/`btn_divide`) and their `android:onClick` names. Arrange the grid so the new keys
  fit (e.g. the reference uses 5 columns on the bottom-right; you may restructure the rows as long
  as all existing buttons remain reachable). Constrain within the existing `ConstraintLayout`.
- In `MainActivity`, add the matching handler methods (public, referenced by XML):
  `acEvent(view)`, `parenEvent(view)`, `percentEvent(view)`, `confirmEvent(view)`.

### 3. MainViewModel — parenthesis + percent (mXparser already supports both)
- Add `addParenthesis(char: Char)` that appends `(` or `)` to the calculation string
  (`currentCalculationValueText`), switching into calculation mode if needed (mirror
  `addOperator`). Guard against unbalanced/empty expressions minimally.
- Add `percent()` that appends `%` to the calculation string in calculation mode (append token),
  then let `evaluateMathExpression()` handle it — verify mXparser evaluates `%` correctly; if not,
  implement percent as "÷100 of the current value" consistently across both modes.
- Ensure `evaluateMathExpression()` (the `.replace()` chain) passes `(` `)` `%` through unchanged
  (it only swaps `− × ÷` for `- * /`; it already does, so verify nothing strips or breaks them).
- Add `addClear()` using the existing `clear()`.

### 4. Persist the edited amount (list marks edited row)
- Add accessors in `repository/Database.kt` for a per-currency edited amount, e.g.
  `setEditedAmount(currency: Currency, amount: Double?)` / `getEditedAmount(currency): Double?`
  storing under a stable key (like `edited_<ISO>`). Keep it minimal. (The list can later read it.)

### 5. Consume it in RatesListActivity (small)
- On the list, if `getEditedAmount(rowCurrency)` returns non-null, show that value as the row amount
  (the pin/edited marker) instead of the converted amount. Keep this optional and minimal — the
  amount formatting already exists in the adapter.

## Constraints
- Touched files: `MainActivity.kt`, `MainViewModel.kt`, `main_keypad.xml`,
  `main_keypad_extended.xml`, `Database.kt`, `RatesListActivity.kt`, `strings.xml` (+ `values-de`).
  Do NOT modify providers/repository logic beyond the new Database accessors, do NOT touch Phase 3
  list layout unless strictly needed for #5.
- All colors from theme attrs (no hardcoded colors). Reuse existing styles
  (`AppTheme.TextAppearance.Keypad`, `color_keypad_operators`).
- Verify compile with `./gradlew :app:compilePlayDebugKotlin` ONCE at the end; fix errors; stop.
- `docs/phase4_notes.md`: what you changed + any deviations.

Respond in English. Summarize changes + compile status.
