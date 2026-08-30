# Analysis & Restyle Plan — "Wechselkurse"-style start screen

**Phase:** ANALYZE ONLY (no source/layout/build files were modified; no build was run).
**Input:** `ANALYZE_PROMPT.md`, `docs/design/reference_main.png`, `docs/design/reference_calc.png`, and the code under `app/src/main/`.

## DECISIONS (locked by user, 2026-08-30)

- **Theme = Material You / Dynamic Color.** NOT a pinned near-black palette. The app keeps the
  existing `Theme.Material3.DynamicColors.DayNight` (styles.xml:11) and respects the system/wallpaper
  theme on Android 12+ (`values-night-v31/colors.xml`). The reference screenshots are LAYOUT
  templates (title bar, list rows, keypad, "Betrag ändern"), not a color spec. Views must use theme
  attrs (`?attr/colorBackground`, `?android:textColorPrimary`) — no hardcoded colors. Keep the
  existing `AppTheme` / `AppTheme.PureBlack` switching (BaseActivity.kt:13-18) working.
- **Brent = dual-source.** Default = Yahoo Finance chart endpoint `BZ=F` (no key). OPTIONALLY, if
  an official API key is stored (EIA `api.eia.gov`, free), re-use the existing keyed-preference UI
  pattern (`preference_dialog_api_key.xml` + `Database.getOpenExchangeRatesApiKey()` +
  `OpenExchangerates.kt:40-78`); when a Brent/EIA key is present, the source switches to the official
  endpoint. Graceful degradation in both cases. User explicitly asked "both".

---

## 0. Executive summary

The app today is a **single-screen converter + calculator** (two spinners, two amount displays,
one keypad). The reference design is a **two-screen app**: an overview list (reference_main.png)
plus a per-currency amount/calculator screen (reference_calc.png). The good news: the existing
calculator screen is already ~90 % of reference_calc, and all the data plumbing (providers,
repository, persistence, LiveData) needed for the list already exists. The main work is:
one new list screen + one new row layout/adapter, re-parenting the existing converter into a
second activity, and opening up `Currency.fromString()` (Currency.kt:192-214) so BTC/metals/oil
can actually flow through the system.

Two **corrections to the prompt's assumptions** found during analysis:

1. **The Database is NOT Room-backed.** `repository/Database.kt` is plain `SharedPreferences`
   (`rates`, `last_state`, `starred_currencies`, `prefs` files) wrapped in `SharedPreference*LiveData`
   (`util/SharedPreferenceLiveData.kt` etc.). There is no `androidx.room` dependency in
   `app/build.gradle.kts:87-119`. Consequence: no schema migrations, but rates are stored
   key-per-currency (`Database.insertExchangeRates()` writes one float per ISO code) — merging
   extra sources (crypto/oil) into the same cache is straightforward.
2. **XAU/XAG do NOT come from Frankfurter/ECB today.** `FrankfurterAppRatesAdapter.kt:21-40` only
   maps whatever the ECB feed returns (~33 fiat codes); nothing injects XAU/XAG/XPD/XPT, and
   `Currency.fromString()` explicitly *filters them out* (Currency.kt:194-213). Metals need a new
   source or an extended InforEuro parser (see §5).

---

## 1. Current UI structure map (exact files)

### 1.1 Activities (AndroidManifest.xml)

| Component | File | Role |
|---|---|---|
| Launcher | `view/main/MainActivity.kt` | THE whole start screen: converter display + keypad |
| Secondary | `view/preference/PreferenceActivity.kt` + `PreferenceFragment.kt` | settings incl. fee (`preference_dialog_fee.xml`), API key (`preference_dialog_api_key.xml`), provider picker (`ProviderPickerPreference.kt`) |
| Secondary | `view/timeline/TimelineActivity.kt` | spark chart (`com.robinhood.spark`), stats; receives `ARG_FROM`/`ARG_TO` extras (TimelineActivity.kt:76-88) |

`BaseActivity.kt` applies `AppTheme` / `AppTheme_PureBlack` and night mode from `Database.getTheme()`.

### 1.2 Start-screen layout tree

`MainActivity.onCreate()` → `setContentView(R.layout.activity_main)` (MainActivity.kt:77), `title = null`.

- `res/layout/activity_main.xml` (+ `res/layout-land/activity_main.xml` for landscape/foldables):
  vertical (resp. horizontal) `LinearLayout @+id/main_root` with 3 includes:
  1. `main_display.xml` — `SwipeRefreshLayout @+id/swipeRefresh` → `ConstraintLayout` with
     `bg_display` background; contains:
     - `SearchableSpinner @+id/spinnerFrom` / `@+id/spinnerTo` (custom `AppCompatSpinner`,
       `view/main/spinner/SearchableSpinner.kt`)
     - `HorizontalScrollView @+id/scrollViewTextFrom/To` + `TextView @+id/textFrom` / `@+id/textTo`
       (TitleLarge amounts)
     - `TextView @+id/textCalculations` (calculation row), `@+id/textFee`, `@+id/textInfoConversion`,
       `@+id/textInfoDate`, `ImageView @+id/iconHistorical`, `FloatingActionButton @+id/btn_toggle`
       (swap), `LinearProgressIndicator @+id/refreshIndicator`, `CoordinatorLayout @+id/snackbar_top_position`
  2. `main_keypad.xml` — 4×4: `7 8 9 ÷ / 4 5 6 × / 1 2 3 − / 0 , ⌫ +`
     (`AppCompatButton`s with `android:onClick="numberEvent|decimalEvent|deleteEvent|calculationEvent"`)
  3. `main_keypad_extended.xml` — 4×5 variant (`÷ × − +` on top, `00`/`000` keys), toggled in
     `MainActivity.observe()` at MainActivity.kt:441-451 from `MainViewModel.isExtendedKeypadEnabled`.

- `res/menu/main.xml` — options menu: `timeline` (icon, `ic_timeline`), `date_picker` (icon,
  `ic_history`), `refresh` (overflow), `settings` (overflow). **No title bar text, no hamburger,
  no "+" today.** Handlers in `MainActivity.onOptionsItemSelected()` (MainActivity.kt:116-199):
  timeline opens `TimelineActivity` with ARG_FROM/ARG_TO; date_picker shows the historical-rates
  dialog (`main_dialog_historical_rates.xml` + `ScrollableDatePicker`).

### 1.3 Currency rows (all existing "row" layouts)

| Layout | Used by | Content |
|---|---|---|
| `res/layout/row_currency.xml` | `SearchableSpinnerAdapter.getView()` (SearchableSpinnerAdapter.kt:19-42) — collapsed spinner | flag 24×17dp (`ShapeableImageView @+id/image`) + ISO code (`@+id/text`) |
| `res/layout/row_currency_dropdown.xml` | `SearchableSpinnerDialogAdapter` (ViewHolder, line 186) | flag, ISO (`text2`), full name (`text`), star `btn_fav`, optional conversion preview `text3` |
| `res/layout/row_currency_dropdown_api_hint.xml` | same adapter, last row | "more providers" hint |

**There is no list-based start screen and no adapter that renders the reference-style row
(flag + full name + rate line + big amount).** The closest building block is
`row_currency_dropdown.xml` / `SearchableSpinnerDialogAdapter`.

### 1.4 Currency picker dialog

`SearchableSpinner.kt:66-79` `performClick()` → `SearchableSpinnerDialog` (`AppCompatDialogFragment`)
→ `searchable_spinner_dialog.xml` (SearchView + star filter `btn_toggle_fav` + RecyclerView),
backed by `SearchableSpinnerDialogAdapter` with filter/star/preview logic. This dialog is the
natural component to reuse for the reference's "+" (add currency) action.

### 1.5 Calculator / keypad wiring (today)

- Input handlers live on the Activity and are referenced from XML:
  `numberEvent()`, `decimalEvent()`, `deleteEvent()`, `calculationEvent()` (MainActivity.kt:465-493),
  `toggleEvent()` (line 534), hardware keyboard `onKeyDown()` (line 496).
  Long-press `btn_delete` = `viewModel.clear()` (MainActivity.kt:245-250).
- State & math live in `viewmodel/main/MainViewModel.kt`:
  `currentBaseValueText`, `currentCalculationValueText`, `addNumber/addDecimal/delete/clear`,
  `addition/subtraction/multiplication/division` (lines 456-572), mXparser evaluation in
  `currentBaseValue.evaluateMathExpression()` (lines 303-325), formatted outputs
  `getCurrentBaseValueFormatted()` / `getResultFormatted()` (lines 340-358, 432-450, locale-aware
  symbol placement via `util/TextUtils.hasAppendedCurrencySymbol()`), conversion math in `result`
  MediatorLiveData (lines 376-418, incl. fee).
- Footer strings: `ratesInformationFooter` (MainViewModel.kt:242-278) builds "1 EUR ≈ X SEK";
  date/provider line is assembled in `MainActivity.observe()` (lines 318-355) from
  `ExchangeRates.date` + `provider.getName()`.

### 1.6 Data layer (relevant for §5)

- `model/Currency.kt` — enum with `iso4217Alpha, iso4217Numeric, symbol, fullName (@StringRes), flag (@DrawableRes)`;
  `BTC/XAG/XAU/XPD/XPT` have `flag = null` → `flag_unknown` fallback in `flag()` (line 243-245).
  **`companion fromString()` (lines 192-214) blacklists BTC, XAG, XAU, XPD, XPT** plus superseded
  currencies — this is the single gate that keeps crypto/metals out of the app.
- `model/Rate.kt` (`currency` + `value: Float`), `model/ExchangeRates.kt` (base EUR + date + rates
  list + provider), `model/Timeline.kt`.
- `model/ApiProvider.kt` — enum of 7 providers, each wrapping an `ApiProvider.Api` abstract class
  (`getRates()`, `getTimeline()` returning Fuel `Result`); implementations in `model/provider/*`
  with Moshi/XML parsers in `model/adapter/*`. Keyed-provider precedent:
  `provider/OpenExchangerates.kt:40-78` reads `Database.getOpenExchangeRatesApiKey()` and maps
  401 → `error_invalid_api_key`; UI key entry exists (`preference_dialog_api_key.xml`,
  `Database.setOpenExchangeRatesApiKey()`).
- `repository/ExchangeRatesService.kt` (Fuel entry), `repository/ExchangeRatesRepository.kt`
  (coroutines → `Database.insertExchangeRates()`; errors via `postError()`), `repository/Database.kt`
  (SharedPreferences; also stars: `getStarredCurrencies()`, `toggleCurrencyStar()`,
  `isFilterStarredEnabled()` — Database.kt:106-159).

---

## 2. Target vs. current — gap analysis

reference_main.png requires: title bar ("Wechselkurse", hamburger, `+`, refresh, ⋮) · subtitle
"Aktualisiert: Gerade eben" · scrollable list of rows: flag, full name, ISO / "1 EUR = 1,1481 USD",
right-aligned large amount with symbol · rows Euro (home marker), US-Dollar, Britisches Pfund,
Schweizer Franken, Türkische Lira (pin marker, shows edited 150,00), Rohöl (Brent), Goldunze.

| Reference element | Exists today? | Where / what's missing |
|---|---|---|
| Title bar with hamburger/+ /refresh/⋮ | partially | `menu/main.xml` has refresh + settings; missing: visible title text, hamburger, "+" (add-currency) |
| "Aktualisiert: Gerade eben" | partially | data exists (`ExchangeRates.date`, `provider`) but is rendered bottom-right as absolute date (MainActivity.kt:318-355); no relative-time formatting (`util/DateTimeUtils.kt` has none) |
| Overview list of rows | **no** | start screen is converter+keypad; no `RecyclerView` on main; no row layout with name+rate+amount |
| Big right-aligned amount per row | no | formatting helper exists (`toHumanReadableNumber`) but no list rendering |
| Tap row → "Betrag ändern" calculator | partially | keypad screen exists as MainActivity itself; there is no navigation into it, no title/subtitle, no AC/`()`/`%`/↵ keys |
| Brent / Gold rows | **no** | `Currency.fromString()` filter (Currency.kt:192-214) blocks them; no data source for Brent; XAU not fetched |
| Crypto (BTC) | enum-only | `Currency.BTC` exists (Currency.kt:39, flag=null) but filtered out by `fromString()`; no provider |

---

## 3. Smallest change set for the START screen (→ reference_main.png)

**Recommended architecture (minimal blast radius):** keep `MainActivity` + its layouts exactly as
the "Betrag ändern" calculator screen, and add **one new launcher activity** with the list.
Nothing in the converter logic needs to move.

### 3.1 New files (implementation phase)

1. `view/main/RatesListActivity.kt` (new, becomes `LAUNCHER` in `AndroidManifest.xml`;
   `MainActivity` loses the launcher intent-filter and gains `parentActivityName`).
   - Reuses `MainViewModel` (or a thin `RatesListViewModel` wrapping the same repository) for
     `getExchangeRates()`, `isUpdating()`, `getError()`, `forceUpdateExchangeRate()`,
     `getCurrentBaseValueAsNumber()`.
2. `res/layout/activity_rates_list.xml` — `CoordinatorLayout`/`ConstraintLayout`:
   - Toolbar (title from a new string, e.g. reuse `app_name_alt` "Sorten" or add
     `strings.xml: rates_list_title`), subtitle `TextView` ("Aktualisiert: …"),
     `RecyclerView @+id/listRates` inside a `SwipeRefreshLayout` (pattern copied from
     `main_display.xml:2-7`), `LinearProgressIndicator` (copy from `main_display.xml:34-43`).
3. `res/layout/row_currency_main.xml` — new row: flag (`ShapeableImageView`,
   `AppTheme.FlagRoundedCorners`, larger e.g. 40×28dp), column with full name
   (`currency.fullName(context)`) + secondary line ("1 EUR = 1,1481 USD" — locale-decimals via
   `toHumanReadableNumber(decimalPlaces=4, trim)`), right-aligned large amount +
   optional marker slot (home/pin icons; new drawables or reuse `ic_favorite_on`).
4. `view/main/RatesListAdapter.kt` — `RecyclerView.Adapter`, modeled on
   `SearchableSpinnerDialogAdapter` (same ViewHolder idiom, flag via `Currency.flag(context)`).
   Per-row amount = `baseValue / baseRate.value * rowRate.value` (same math as
   `MainViewModel.result`, lines 399-417). Click → launch calculator (§4); long-press/context
   menu → timeline (`ARG_FROM`=base, `ARG_TO`=row currency, same extras as MainActivity.kt:126-140)
   and star toggle (existing `toggleCurrencyStar`).
5. `res/menu/rates_list.xml` — `+` (add: opens existing `SearchableSpinnerDialog`-style picker),
   `refresh` (calls `forceUpdateExchangeRate()`), overflow: historical date (reuse the
   `date_picker` dialog code, MainActivity.kt:141-196), settings, converter (open MainActivity
   directly).

### 3.2 Modified files

- `AndroidManifest.xml` — launcher swap (see above). Permissions stay INTERNET-only.
- `res/values/strings.xml` (+ translations as feasible; lint `MissingTranslation` is disabled,
  `app/build.gradle.kts:78-80`, so default-only is safe): title, "Aktualisiert: %s",
  "Gerade eben", "Betrag ändern".
- Row display list = starred currencies (`Database.getStarredCurrencies()`), seeded with
  EUR/USD/GBP/CHF/TRY/(XAU)/(XBR) on first run — this gives the "+" meaning with zero new
  persistence code. Base/home row = provider base (`ExchangeRates.base`, always EUR today).

### 3.3 Dark-theme tokens

Reference is near-black; current dark background is `blackOlive #333331`
(`values-night/colors.xml:4`, `values/colors.xml:5`). `BaseAppTheme` uses
`Theme.Material3.DynamicColors.DayNight` (styles.xml:11) — dynamic color will fight a fixed
brand look. Proposal: keep the existing `colorBackground` mechanism, optionally darken via the
existing `AppTheme.PureBlack` pathway (BaseActivity.kt:13-18) or a new branded overlay; all new
views must use theme attrs (`?attr/colorBackground`, `?android:textColorPrimary`) — no hardcoded
colors — so light/pure-black/dynamic variants keep working. `values-night-v31/colors.xml` must be
kept in sync.

---

## 4. Tap-a-row → calculator flow (→ reference_calc.png) using the EXISTING keypad

reference_calc.png = back arrow, title "Betrag ändern", subtitle "Türkische Lira (TRY)", large
right-aligned number, keypad `AC ( ) % ÷ / 7 8 9 × / 4 5 6 − / 1 2 3 + / 0 , ⌫ ↵`.

The existing MainActivity **is** this screen modulo chrome and three keys:

1. **Navigation chrome:** in `MainActivity` (or a renamed `CalculatorActivity`) enable the action
   bar: `title = getString(R.string.change_amount)`, `supportActionBar.subtitle = "<full name> (<ISO>)"`
   (both available via `Currency.fullName(context)` / `iso4217Alpha()`), `setDisplayHomeAsUpEnabled(true)`
   (pattern: TimelineActivity.kt:70-73, 113-116). The tapped currency arrives as
   `intent.getSerializableExtra("ARG_CURRENCY")` (same mechanism as TimelineActivity.kt:76-88) and
   is applied via `viewModel.setDestinationCurrency(currency)` (existing, MainViewModel.kt:585).
2. **Keypad mapping** (all buttons already call Activity methods from XML):
   - `7 8 9 4 5 6 1 2 3 0 ,` → existing `numberEvent()` / `decimalEvent()` (decimal label already
     locale-correct, MainActivity.kt:447-450).
   - `⌫` → existing `deleteEvent()`; long-press → `viewModel.clear()`.
   - `÷ × − +` → existing `calculationEvent()` (MainActivity.kt:486-493).
   - `AC` → new button, action = `viewModel.clear()` (MainViewModel.kt:526-529) — the long-press
     behavior promoted to a visible key.
   - `↵` (confirm) → new button: persist result and `finish()` back to the list (see below).
   - `( )` and `%` → **new ViewModel methods required** (`addParenthesis()`, `percent()`); mXparser
     can evaluate both, but `addOperator()`/`delete()` (MainViewModel.kt:547-572, 506-524) only
     know `+ − × ÷`. *Scope decision:* either implement these two or omit the two buttons in v1 —
     the reference shows them, so plan for implementing them.
3. **Keypad layout edits:** extend `main_keypad.xml` with `btn_ac` and `btn_confirm` (and the same
   in `main_keypad_extended.xml`); keep the outer `FrameLayout` (comment at
   main_keypad.xml:3 warns it's needed for RTL in landscape).
4. **Result flow back to the list:** simplest robust approach = persist, don't callback:
   store the edited amount + currency in `Database` (new keys beside
   `saveLastUsedRates()`, Database.kt:62-67); `RatesListActivity` already observes rates and can
   recompute rows on resume. Alternative: `Activity Result API`
   (`registerForActivityResult(StartActivityForResult())`) from the list — also fine, no new
   permission surface. The list marks the edited row with the pin icon (reference behavior:
   TRY row shows the raw 150,00 while other rows show the converted value).
5. **Feature reachability from the list:** charts/history stay reachable via per-row context
   action → `TimelineActivity` (existing extras) and the global historical-date dialog moved to
   the list's overflow; the fee calculator stays in Preferences (unchanged). Swipe-to-refresh and
   the `refreshIndicator` pattern carry over to the list screen.

---

## 5. Crypto + commodities data-source design

Constraint: keep `INTERNET` as the only permission; fit the existing `ApiProvider.Api` /
`ExchangeRates` adapter pattern; everything must remain cacheable by `Database.insertExchangeRates()`.

### 5.1 Content gating (prerequisite for everything)

Change `Currency.fromString()` (Currency.kt:192-214) to *allow* `BTC`, `XAU`, `XAG` (and a new
Brent pseudo-code, see below). Every rates adapter routes through this function, so this single
change turns filtered-out codes into real `Rate` entries wherever a source provides them. Keep the
superseded-currency filters (MRO/STD/VEF/CUC) intact.

### 5.2 Crypto — CoinGecko (free, no key)

- Endpoint from the prompt:
  `https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=eur`
  (+ `include_last_updated_at=true` for the "Aktualisiert" line).
- **Do not force it into the `ApiProvider` enum.** `ApiProvider` models *user-selectable fiat
  sources* with `getRates(all)`, `getTimeline(pair)` semantics; CoinGecko is a *supplementary
  merge source*. Recommended shape (mirrors existing classes):
  - `model/provider/CoinGecko.kt` with `suspend fun getPrices(ids: …, vs: Currency): Result<…>`
    using Fuel + a Moshi `FromJson` adapter in `model/adapter/CoinGeckoPricesAdapter.kt`
    (pattern: `FrankfurterApp.kt:41-66` + `FrankfurterAppRatesAdapter.kt`).
  - Called from `ExchangeRatesRepository.getExchangeRates()` (ExchangeRatesRepository.kt:29-64)
    after the fiat fetch; merged `Rate(Currency.BTC, eurValue)` appended to `rates` before
    `Database.insertExchangeRates()` — persistence then works unchanged (one float key "BTC").
  - Failure-tolerant: crypto merge failure should degrade gracefully (fiat-only list + optional
    snackbar), not trip the generic error path (`postError`).
- Timeline for BTC: CoinGecko `/coins/{id}/market_chart/vs_currency=eur&days=365` maps onto
  `Timeline` (Map<LocalDate, Rate>) — same merge-source pattern in
  `ExchangeRatesRepository.getTimeline()`; alternatively keep timeline fiat-only in v1 (risk §6).

### 5.3 Metals — XAU/XAG (and XPD/XPT)

- Prompt assumption is wrong for the *current* code (§0). Options, in order of fit:
  1. **InforEuro (EC)**: the Commission's InforEuro feed historically includes gold/silver
     reference prices alongside ~160 currencies — the existing XML/JSON parser
     (`InforEuroRatesAdapter.kt:24-51`) maps `isoA3Code`+`value` pairs generically, so XAU/XAG may
     start flowing *for free* once `fromString()` allows them. **Must be verified against the live
     feed** (field name/unit — possibly per-kg or per-gram, needs conversion to per-oz-troy).
  2. Fallback: same merge-source pattern as CoinGecko against a free metals endpoint
     (e.g. goldprice.org JSON) — unofficial, stability risk.
- XPD/XPT: no known free no-key source → recommend hiding these two from pickers/lists (they stay
  in the enum, still filtered by `fromString()`), documented in-app.

### 5.4 Crude oil (Brent)

No official free no-key JSON API. Candidate sources to research/prototype (both no key, both
unofficial):
- **Yahoo Finance chart endpoint**: `query1.finance.yahoo.com/v8/finance/chart/BZ=F?interval=1d&range=1mo`
  — JSON, gives current close + daily history (history would even power a Brent timeline).
- **stooq.com** CSV: `https://stooq.com/q/l/?s=cb.f&f=sd2t2ohlcv&h&e=csv` (Brent futures).
Either integrates as a third merge-source (`model/provider/BrentOil` returning a single
`Rate` in EUR/bbl after EUR-USD conversion using the freshly fetched fiat rates; note quote is
USD-denominated). Because these are unofficial, implement behind the same graceful-degradation
rule and add a documented **key/known-source prompt fallback**: the OpenExchangerates pattern
(`OpenExchangerates.kt:40-78` + `Database.getOpenExchangeRatesApiKey()` +
`preference_dialog_api_key.xml`) shows exactly how a keyed alternative (e.g. EIA's
api.eia.gov, free key) can be added later.

### 5.5 Modeling units ("bbl", "oz t", "XBZ")

Reference rows display *unit + amount* ("bbl 0,04", "oz t 0,000698") and even a pseudo-code
"XBZ". The current `Currency` model has no unit concept (`symbol` is `null` for XAU/XAG/BTC).
Proposal: extend the enum with a `unitLabel: String?` (e.g. `XAU → "oz t"`, `Brent → "bbl"`,
`BTC → "₿"`) used by the new row layout, plus non-flag icon drawables
(`Currency.flag = null` currently renders `flag_unknown` — unacceptable in the new list; add
e.g. `img_commodity_gold`, `img_commodity_oil`, `ic_crypto_btc` and branch in
`Currency.flag(context)` or a new `icon(context)` method).

---

## 6. Risks / unknowns

1. **Data-source reliability (highest risk).** Brent (Yahoo/stooq) and metals fallbacks are
   unofficial endpoints — no SLA, possible ToS/anti-bot issues, TLS/UA quirks with Fuel.
   InforEuro metals coverage must be verified against the live feed (unit + availability).
   Mitigation: merge-source pattern with graceful degradation; keyed official fallback (EIA)
   reusing the existing API-key preference UI.
2. **CoinGecko rate limits** (free tier, roughly 5–15 calls/min depending on endpoint load;
   `simple/price` is cheap but bursts during list refresh could throttle). Mitigation: single
   batched request per refresh, cache via existing SharedPreferences rates store, honor
   `Retry-After`, refresh crypto at most as often as fiat.
3. **`Currency.fromString()` change ripples everywhere.** All 7 providers' adapters call it; once
   BTC/XAU pass, *any* provider response containing such codes creates rows. Also
   `Database.getLastBaseCurrency()` (`Currency.fromString` on restore) and
   `SearchableSpinnerDialogAdapter` filters are affected. Needs a regression pass over all
   providers.
4. **Missing visuals for BTC/metals/oil.** `flag = null` → `flag_unknown` gray globe would look
   broken in the reference-style list. New drawables required (§5.5).
5. **Dark-theme tokens.** Reference near-black vs `blackOlive #333331`; Material3 *dynamic*
   colors (`Theme.Material3.DynamicColors.DayNight`, styles.xml:11) can override the intended
   palette on Android 12+ (`values-night-v31/colors.xml` exists). Decide: pinned brand palette vs
   dynamic; must respect existing `AppTheme` / `AppTheme.PureBlack` switching (BaseActivity.kt:13-18).
6. **Keypad feature gap.** `AC`, `()`, `%`, `↵` don't exist in `main_keypad*.xml` or
   `MainViewModel` (no parenthesis/percent handling in `addOperator()`/`delete()`); `↵` implies a
   new "confirm" contract. mXparser can evaluate `()`/`%`, but ViewModel string handling needs
   care (e.g. `isInCalculationMode()` heuristics, MainViewModel.kt:627-629).
7. **Number formatting for rows.** Reference shows 4–6 significant decimals ("1,1481",
   "0,000698"); existing helpers default to 2 (`getResultFormatted()`) — use
   `getSignificantDecimalPlaces()` (MainViewModel.kt:269) / `toHumanReadableNumber(decimalPlaces)`.
8. **Launcher-activity swap.** Moving `LAUNCHER` from `MainActivity` affects app shortcuts,
   back-stack/parent navigation, and any widget/tests referencing it; keep `MainActivity`'s class
   name to minimize breakage, or plan a careful rename.
9. **`android:onClick` XML binding.** Keypad buttons bind to Activity methods by name
   (`numberEvent` etc.); whichever activity inflates `main_keypad*.xml` must expose them (relevant
   if MainActivity is renamed or the keypad is reused elsewhere).
10. **Foldables/landscape.** `MainActivity.prepareFoldableLayoutChanges()` (MainActivity.kt:541-568)
    and `layout-land/activity_main.xml` assume display+keypad; the new list screen needs its own
    (trivial) land handling; calculator screen keeps the existing behavior.
11. **Localization.** New strings (title, "Aktualisiert: %s", "Betrag ändern", unit labels) across
    ~30 `values-*/strings.xml` trees; `MissingTranslation` lint is disabled so shipping
    default-English only is buildable, but user-visible German should be prioritized
    (`values-de/strings.xml`).
12. **Historical rates / timeline for new assets.** The date-picker dialog + providers'
    `getTimeline()` are fiat-oriented; BTC/oil timelines need new endpoints (CoinGecko
    market_chart, Yahoo history) or must be explicitly fiat-only in v1.
13. **Prompt-vs-code discrepancies** (documented in §0): no Room; no XAU/XAG from ECB today.
    Any plan that assumed either would need rework.

---

## 7. Suggested implementation order (for the later IMPLEMENT phase)

1. Flip `Currency.fromString()` gate + add unit/icon fields + new drawables.
2. CoinGecko merge-source (+ optional Brent merge-source behind the same pattern) → rows appear
   with real data.
3. New `RatesListActivity` + `row_currency_main.xml` + adapter + menu; make it launcher; wire
   "+" to the starred-currencies picker.
4. Re-chrome MainActivity as "Betrag ändern" (title/subtitle/up-nav/`↵`/`AC`), add `( )` `%`
   support to `MainViewModel`, persist edited amount.
5. Theme polish (dark tokens, pure-black variant), localization, foldable pass.
6. Timeline/historical coverage decision for non-fiat assets.
