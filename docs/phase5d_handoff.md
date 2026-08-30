# Phase 5d — Handoff / Ist-Zustand (2026-08-31)

## Was v1.4 enthalten sollte (kompiliert, Build grün)
Build: `app/build/outputs/apk/fdroid/debug/de.salomax.currencies-v12300-fdroid-debug.apk`
APK geliefert: `/tmp/wechselkurse-test-v1.4-tabs.apk` (12.790.454 Byte, MD5 `a570afc21eb8d5d4065c0a7794cab665`, AppId `de.salomax.currencies.debug`)
Commit: Phase 5d — tab/chip picker (Currencies/Crypto/Commodities/Metals + search FAB).

Die Absicht von Phase 5d war: Die „+"-Auswahl („Favorit hinzufügen") von der alten untereinandergestapelten
Gruppen-Liste in ein **Registerkarten-Layout** umbauen:
- 4 Chips oben (Währungen / Crypto / Rohstoffe / Edelmetalle) in einem `HorizontalScrollView` + `ChipGroup`.
- Nur die Liste der **ausgewählten Kategorie** wird angezeigt.
- Such-FAB unten rechts → blendet ein `SearchView` ein, das live die aktuelle Kategorie filtert.
- Stern-Toggle zum Hinzufügen/Entfernen.

## Vom Nutzer beobachtete Symptome (v1.4 auf Gerät)
1. **„Registerkarten keine da"** — die 4 Chips werden nicht / nicht sichtbar angezeigt.
2. **„Suche funktioniert nicht"** — die Suche filtert nicht / tut nichts.
3. **„Selber Fehler dass bei jeder Währung 1 steht"** — ALLE Zeilen zeigen den Wert **1**,
   nicht mehr nur TRY/Gold. Das ist das alte base-values-Problem, jetzt aber flächendeckend.

## Was im Code tatsächlich steht (verifiziert)
- `RatesListActivity.showAddCurrencyDialog()` (Zeile 141): inflatet `R.layout.dialog_add_currency`,
  findet `chipGroup`, `searchView`, `btnSearch`, `listView`; baut `AddCurrencyDialogAdapter`;
  versteckt Kategorien ohne Einträge; preselects erste Kategorie; `setOnCheckedStateChangeListener`
  → `adapter.setCategory(...)`; FAB toggelt SearchView; `setOnQueryTextListener` → `adapter.filter(...)`.
- `dialog_add_currency.xml`: `LinearLayout` (vertical) → `HorizontalScrollView` → `ChipGroup`
  (`singleSelection`, `selectionRequired`) mit 4 `Chip` (ids `chipCurrencies/Crypto/Commodities/Metals`) →
  `FrameLayout` (weight=1) → `RecyclerView` + `SearchView` (gone) + `FloatingActionButton` (`btnSearch`).
- `AddCurrencyDialogAdapter.kt`: `public enum AddGroup { CURRENCIES, CRYPTO, COMMODITIES, METALS }`,
  `setCategory()`/`getCategories()`, `groupOf()` unverändert (XAU/XAG→METALS, BTC→CRYPTO, XBZ→COMMODITIES,
  sonst CURRENCIES; XPD/XPT gefiltert), `filter()` wirkt nur auf ausgewählte Kategorie.
- `styles.xml` Zeile 126: `<style name="AppTheme.Chip.Category" parent="Widget.Material3.Chip.Filter">` existiert.
- Alle Ressourcen-IDs (`chipGroup`, `chipCurrencies`, `chipCrypto`, `chipCommodities`, `chipMetals`,
  `searchView`, `btnSearch`) sind im Layout vorhanden.
- KEIN Debug-Logging im Repo (Repo-weiter Scan mit `grep -rn "android.util.Log|TEMP DEBUG|printStackTrace"` leer).

⇒ Der Code **kompiliert** und die Ressourcen existieren. Das heißt: Der Code sieht auf dem Papier korrekt aus,
aber das Laufzeitverhalten auf dem Gerät weicht ab. Die Ursachen liegen also (sehr wahrscheinlich) NICHT in
fehlenden Ressourcen oder Syntax, sondern in Layout-/Runtime- oder Datenzustand-Details.

## Hypothesen zur Ursache (für opencode zu verifizieren — NICHT blind fixen)

### A) „Registerkarten keine da" + „Suche nicht" (Dialog-Layout)
Der Dialog wird mit `AlertDialog.Builder(this).setView(view)` aufgebaut (Zeile 212-216), **ohne** die
Dialog-Höhe zu setzen. Das Root-`LinearLayout` hat `layout_height="match_parent"`; das innere `FrameLayout`
nutzt `layout_height="0dp"` + `layout_weight="1"`. In einem `AlertDialog` ohne explizite Höhe wird der
Gewichts-Child ggf. auf 0 Höhe zusammengestaucht → Liste quasi unsichtbar. Die Chips (wrap_content) sollten
aber sichtbar sein — „keine Registerkarten" spricht eher für einen **falschen/alten Dialog**, den der Nutzer
sieht: Prüfen, ob beim „+"-Tap tatsächlich `showAddCurrencyDialog()` aufgerufen wird, oder ob noch ein
ALTER Pfad / der alte `SearchableSpinnerDialog` (in `SearchableSpinner.kt` Zeile 43 instanziiert, layout
`searchable_spinner_dialog`) aufgemacht wird. Da Phase 6 die Spinner aus `main_display.xml` entfernt hat,
könnte ein Alt-Pfad überleben. **Erst verifizieren, welcher Dialog real geöffnet wird (Logging/ADB), bevor gefixt wird.**

### B) „Alle Werte = 1" (base/rates-Datenzustand)
Das ist das bekannte Problem (stale `_base`/`edited amount` im `SharedPreferences`, `baseRateValue`-Fallback `1f`).
Phase 6b hat `base = Currency.EUR` fest gepinnt und `baseRateValue` aus dem **vollen ungefilterten Snapshot**
genommen. Wenn jetzt aber ALLE Zeilen = 1 sind, könnte (a) der Cache auf dem Gerät noch eine sehr alte
`_base` = USD / leere Rate-Menge enthält, oder (b) `getRows()` bei leerem/ungeladenem `rates`-Snapshot einen
`baseRateValue=1f`-Fallback greifen lässt. **Verdacht: Der Nutzer hat den alten App-Cache nicht gelöscht /
die App nicht frisch installiert — vor einem Fix unbedingt testen mit frischer Installation (Daten löschen).
Nur wenn es selbst nach frischer Installation überall 1 bleibt, ist es ein echter Logik-Bug.**

## Nächste Schritte (morgen, mit opencode)
1. **Erst verifizieren, nicht fixen:** Per ADB (OnePlus) bzw. Logcat feststellen, welcher Dialog beim „+"-Tap
   wirklich geöffnet wird, und was `getRows()`/`getExchangeRates()` tatsächlich liefert. → Eindeutig klären,
   warum Chips fehlen + alle = 1.
2. **Frischer Installations-Test** (App-Daten löschen) als Kontrollbedingung, um „stale Cache" vs. „echter Bug"
   voneinander zu trennen.
3. DANACH: Falls der falsche Dialog (SearchableSpinner) geöffnet wird → den Alt-Pfad entfernen bzw.
   `showAddCurrencyDialog()` korrekt verdrahten. Falls Layout-Höhe das Problem ist → Dialog-Höhe / Root-Gewicht fixen.
4. Falls alle = 1 (nach frischer Installation bestätigt) → `baseRateValue`-Logik in `RatesListViewModel.getRows()`
   nochmal prüfen (Fallback `1f` ist potenziell falsch, wenn Snapshot tatsächlich Raten enthält).
5. Build + v1.5 liefern, mit frischer Installation testen.

## Dateien
- `app/src/main/kotlin/de/salomax/currencies/view/main/RatesListActivity.kt` (showAddCurrencyDialog, Z.141-222)
- `app/src/main/kotlin/de/salomax/currencies/view/main/AddCurrencyDialogAdapter.kt`
- `app/src/main/res/layout/dialog_add_currency.xml`
- `app/src/main/res/values/styles.xml` (Z.126)
- `app/src/main/kotlin/de/salomax/currencies/view/main/spinner/SearchableSpinner.kt` (Z.43, möglicher Alt-Pfad)
- `app/src/main/kotlin/de/salomax/currencies/viewmodel/main/RatesListViewModel.kt` (getRows, baseRateValue-Fallback)
