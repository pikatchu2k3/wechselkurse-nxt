# PHASE 6: Simplify the calculator screen — no currency picker, only the tapped currency — IMPLEMENT

Work ONLY inside this directory (cwd). Do NOT read, glob, search, or edit anything outside it.
Repo: `sal0max/currencies` (Kotlin, Views+XML). The calculator screen is
`view/main/MainActivity.kt` with `res/layout/activity_main.xml` → includes `res/layout/main_display.xml`
(the display: two `SearchableSpinner` = `spinnerFrom`/`spinnerTo`, `textFrom`/`textTo` amount texts,
`btn_toggle` swap FAB, fee/info texts) + `res/layout/main_keypad*.xml`. The list start screen
(`RatesListActivity`) launches this screen with `ARG_TAPPED_CURRENCY` (ISO code) and it is also
reachable via the list overflow menu "converter" (which opens MainActivity WITHOUT that extra).

Read `MainActivity.kt`, `main_display.xml`, `RatesListActivity.kt`, and `MainViewModel.kt` first.

## Goal
In the calculator ("Betrag ändern") screen, REMOVE the currency-picker/converter elements. The
screen should let you type an amount and compute it ONLY in the single, specific currency that was
tapped (the destination). Do not offer a From/To currency choice in this screen.

Specifically:
1. **Remove the From currency picker entirely.** The base is always EUR (the app's base). There
   should be no `spinnerFrom`. The From/`textFrom` display (and `btn_toggle` swap FAB) should be
   removed or repurposed so there is no "convert from X to Y" concept.
2. **Keep only the tapped/destination currency.** Display it as a fixed label (the full name + ISO,
   consistent with the action-bar subtitle already set in Phase 4). The big amount field shows the
   value in that currency; the calculator computes in it. Introduce input in EUR is NOT wanted here
   — the keypad inputs the amount in the tapped currency, and the screen shows its EUR value (the
   conversion) — OR the simpler/truer reading: the keypad inputs the amount, and the result line /
   info line shows the EUR equivalent. Pick the interpretation that best matches the reference
   (reference_calc.png = "Betrag ändern" with one currency and a keypad) and keep it clean.
3. **Remove or hide `spinnerTo`, `btn_toggle`.** Keep the number display, keypad, AC/()/%/↵, the
   confirmation (confirm stays and still persists the base value for the home row / edited amount
   for non-home rows, as in Phase 4/5b — DO NOT break that).
4. The screen is still reachable from the list overflow "converter" WITHOUT `ARG_TAPPED_CURRENCY`.
   Decide: either (a) remove the "converter" overflow item (since a standalone converter no longer
   exists), OR (b) keep it but default to EUR↔EUR / the last destination currency. Prefer (a) — the
   screen is now "change amount for the tapped currency", so a standalone converter entry is
   redundant. If you remove it, also remove/neutralize the corresponding menu item and any now-dead
   handler; ensure no crash. Document the choice.

## Constraints
- Do NOT touch providers, data sources, or the fiat providers. Do NOT break the Phase 4/5b behavior:
  confirming on the home row still sets the base value (scales the list), and confirming a non-home
  row still stores the edited amount.
- The action bar "Betrag ändern" title + subtitle (full name + ISO) from Phase 4 MUST remain.
- Material You: colors via theme attrs; no hardcoded colors.
- Verify compile with `./gradlew :app:compilePlayDebugKotlin` ONCE at the end using
  `JAVA_HOME=/opt/jdk-21.0.6+7` and `ANDROID_HOME=/opt/android-sdk` (the shell must export these, or
  grading resolves JDK 25 and fails at configuration with "25.0.4.1"). Fix any errors; stop.
- `docs/phase6_notes.md`: what you removed/changed, the chosen interpretation (a/b), deviations.

Respond in English. Summarize changes + compile status.
