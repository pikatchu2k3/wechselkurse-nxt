# PHASE 5D-BUGFIX: Investigate, don't guess — Diagnose first

Work ONLY inside this directory (cwd). Repo: `sal0max/currencies` (Kotlin, Views+XML).

## MANDATE: VERIFY, don't blindly fix
A previous build (Phase 5d) added a tab/chip add-currency dialog. The user reported on-device:
1. the category **chips are not shown**,
2. the **search does nothing**,
3. **every row shows value 1**.
The code compiles and all resource IDs/styles exist — so the failure is RUNTIME/DATA, not syntax.
Your job is to DIAGNOSE precisely first, then fix ONLY what the evidence supports. Add temporary
`android.util.Log` markers WITH a consistent tag (e.g. `Log.d("PHASE5DBUG", ...)`) — and REMOVE them
before finishing. Do not leave any `Log.`/debug statements in the final code.

## Step 1 — read the key files
- `app/src/main/kotlin/de/salomax/currencies/view/main/RatesListActivity.kt` — `showAddCurrencyDialog()` (around line 141): inflates `dialog_add_currency`, wires chips/search/FAB/list.
- `app/src/main/res/layout/dialog_add_currency.xml` — LinearLayout(vertical): HorizontalScrollView->ChipGroup(4 chips), FrameLayout(weight=1)->RecyclerView + SearchView(gone) + FAB.
- `app/src/main/kotlin/de/salomax/currencies/view/main/AddCurrencyDialogAdapter.kt` — `AddGroup` enum, `setCategory()/getCategories()`, `groupOf()`, `filter()`.
- `app/src/main/kotlin/de/salomax/currencies/view/model/main/RatesListViewModel.kt` — `getRows()`, `baseRateValue` fallback.
- `app/src/main/kotlin/de/salomax/currencies/view/main/spinner/SearchableSpinner.kt` (line ~43 instantiates `SearchableSpinnerDialog`) — POSSIBLE LEGACY PATH.

## Step 2 — figure out WHICH dialog actually opens on "+"
Confirm whether the "+" tap really routes to `RatesListActivity.showAddCurrencyDialog()`, or whether a
LEGACY `SearchableSpinnerDialog` (layout `searchable_spinner_dialog`) still opens instead. If the legacy
dialog is what shows (no chips, a plain search + list), THAT explains symptoms 1 & 2: the new tab layout
is never displayed. Trace the "+" button / menu handler. Do not assume — log it.

## Step 3 — figure out the layout height issue for the dialog
`AlertDialog.Builder(this).setView(view)` (RatesListActivity ~line 212) sets NO dialog height. The root
LinearLayout is `match_parent`; the inner FrameLayout uses `height=0dp` + `weight=1`. Verify whether that
collapses the list area to 0 height in a plain AlertDialog. If so, fix by giving the dialog a proper height
(e.g. set `dialog.window` height, or give the root a sensible height / `wrap_content` for chips + a fixed
min-height list). Confirm the chips at least render.

## Step 4 — the "all values = 1" issue
Likely the stale-cache / `baseRateValue`-fallback problem. Determine what `getRows()` returns and what
`getExchangeRates().value?.rates` contains. Add temporary logging around `baseRateValue` and `getEditedAmount`
to see if the snapshot has real rates or is empty (empty -> fallback 1f -> all 1). Distinguish "stale app cache
on device" (fix = tell user to clear data / fresh install) vs "real logic bug" (fix the fallback in code).

## Delivery
- Fix ONLY what the evidence supports. Prefer the smallest change.
- Material You: no hardcoded colors.
- Remove ALL debug logging before done.
- Verify compile: `export JAVA_HOME=/opt/jdk-21.0.6+7 ANDROID_HOME=/opt/android-sdk` THEN
  `./gradlew :app:compilePlayDebugKotlin` (must export JAVA_HOME or Gradle resolves JDK 25 and fails).
- `docs/phase5d_handoff.md` exists — update it with your findings + the concrete root cause for each symptom.

Respond in English. Summarize: root cause for each of the 3 symptoms + the exact fix + compile status.
