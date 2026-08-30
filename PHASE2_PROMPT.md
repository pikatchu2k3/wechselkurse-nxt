# PHASE 2: Data sources — crypto + Brent merge-sources — IMPLEMENT

Work ONLY inside this directory (cwd). Do NOT read, glob, search, or edit anything outside it.
Repo: `sal0max/currencies` (Kotlin, Views+XML). Data layer uses Fuel + Moshi.
Read `docs/analysis_plan.md` §5.2–§5.4 and DECISIONS section first. Also read these existing files
to match the pattern exactly:
- `repository/ExchangeRatesRepository.kt` (where the fiat fetch + insert happens)
- `repository/ExchangeRatesService.kt`
- `model/provider/OpenExchangerates.kt` (the API-key pattern)
- `model/provider/FrankfurterApp.kt` + `model/adapter/FrankfurterAppRatesAdapter.kt` (adapter pattern)
- `model/ExchangeRates.kt`, `model/Rate.kt`, `model/Currency.kt` (BTC/XAU/XAG/XBZ now pass the gate)

## Goal
Add TWO supplementary merge-sources that enrich the fiat rates with crypto (BTC) and commodities
(XAU, XAG, XBZ/Brent). They must NOT be added to the `ApiProvider` enum (that is for user-selectable
fiat sources). They run AFTER the fiat fetch inside `ExchangeRatesRepository.getExchangeRates()`,
merge `Rate(...)` entries into the returned `ExchangeRates`, and MUST fail gracefully (if Crypto/commodity
fetch fails, keep fiat-only rates + an optional snapshot-level note; do NOT trip `postError`).

## Requirements

### 1. CoinGecko crypto merge-source
- New file `model/provider/CoinGecko.kt` with a function
  `suspend fun getPrices(ids: List<String>, vs: Currency, context: Context?): Result<Map<Currency, Float>, FuelError>`
  calling `https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=eur` (+ optionally
  `include_last_updated_at=true`). Map a small fixed crypto set: BTC (→ `BTC`, id `bitcoin`). Use Fuel
  + a Moshi `FromJson` adapter (new `model/adapter/CoinGeckoPricesAdapter.kt`) OR parse manually with
  a small Moshi `Map` adapter — keep it simple and robust. EUR base only for now.
- IMPORTANT: the returned value is "EUR per 1 coin" (e.g. BTC = ~90,000 EUR). That is exactly what a
  `Rate(currency=BTC, value=...)` expects when base is EUR (consistent with the app's "1 EUR = X BTC"
  semantics inverted — see note below). Keep it consistent with how the list row computes amounts.

### 2. Brent commodities merge-source (dual: default no-key Yahoo, keyed fallback)
- New file `model/provider/BrentOil.kt`:
  - Default (no key set): GET `https://query1.finance.yahoo.com/v8/finance/chart/BZ=F?interval=1d&range=5d`,
    parse the latest `close`. This is USD per barrel → convert to EUR using the freshly fetched fiat
    rates (EUR-USD) so the result is `Rate(currency=XBZ, value=eurPerBarrel)`.
  - If a Brent/EIA API key is configured (see keyed pattern below), switch to the official EIA endpoint
    (e.g. `https://api.eia.gov/v2/petroleum/pri/spt/data/?api_key=KEY&frequency=daily&data[0]=value`),
    parse the latest value. Graceful fallback to Yahoo if the keyed call fails.
- Add an API-key accessor to `Database.kt`: `getBrentApiKey()` / `setBrentApiKey(key)` mirroring the
  existing `getOpenExchangeRatesApiKey()`/`setOpenExchangeRatesApiKey()` (find those in Database.kt).

### 3. Wire into ExchangeRatesRepository.getExchangeRates()
After a successful fiat `insertExchangeRates(rates)`, run CoinGecko + BrentOil (on a background
dispatcher), merge any successful non-null `Rate`s into `rates.rates` (dedupe by currency), and
re-insert the merged `ExchangeRates` so the new assets are cached (one float key "BTC"/"XAU"/"XBZ").
If the merge source fails, catch and IGNORE (fall back to the fiat-only rates already stored) — never
call `postError`/`handleGenericError` for merge failures. Keep `ApiProvider`/`getTimeline` untouched.

### 4. Strings (optional, minimal)
- Add any user-facing strings you need (e.g. an error note) to `res/values/strings.xml` with sensible
  English defaults. Do NOT touch `values-de/` (later phase).

## Constraints
- Do NOT modify `ApiProvider.kt`, any fiat provider/adapter, layouts, activities, adapters, build files.
- Do NOT start a build. Do NOT run gradle.
- Verify with `git status --porcelain` at the end: expected modified files = `ExchangeRatesRepository.kt`,
  `Database.kt`, `res/values/strings.xml`; expected new files = `model/provider/CoinGecko.kt`,
  `model/provider/BrentOil.kt`, `model/adapter/CoinGeckoPricesAdapter.kt` (+ any helper). Explicitly
  list what you changed. If you think a fiat provider/adapter MUST change, stop and document why instead.
- `docs/phase2_notes.md` — brief notes on the source choices, the EUR↔USD conversion for Brent, and
  the exact JSON shapes you parse.

Respond in English. Summarize changes + any compile risks.
