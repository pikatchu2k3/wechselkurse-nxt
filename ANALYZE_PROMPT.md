# TASK: Analyze + plan a currency app restyle (ANALYZE PHASE ONLY — no implementation yet)

Work ONLY inside this directory (cwd). Do NOT read, glob, or search anything outside it.
This is the `sal0max/currencies` Android app: Kotlin + classic Views + XML layouts (NOT Compose),
Material 1.12, minSdk 26, targetSdk 35. It already has: a Currency enum (all ISO fiat + BTC + XAU/XAG/XPD/XPT),
multiple fiat providers (Frankfurter/ECB, OpenExchangeRates, InforEuro, BankOfCanada, NorgesBank, BankRossii),
a repository + Room-backed Database, charts (spark), timeline/historical rates, a built-in calculator
(mXparser), and a keypad layout (`main_keypad.xml`, `main_keypad_extended.xml`). Manifest has ONLY INTERNET.

## Goal (user's product vision)

Restyle the app so its START screen matches the reference design, while KEEPING all existing
features (multi-provider, charts/history, calculator, fee calculator) accessible. The look should
mirror the Play Store "Wechselkurse" app:

- MAIN SCREEN (see docs/design/reference_main.png): dark theme, title bar "Wechselkurse" with
  menu/hamburger, "+", refresh, overflow ⋮; a subtitle line "Aktualisiert: Gerade eben"; then a
  scrollable list of entries, each row: flag icon, currency full name (e.g. "US-Dollar"), ISO code
  (e.g. "USD"), a secondary line "1 EUR = 1,1481 USD", and on the right a large amount with the
  currency symbol (e.g. "$3,11"). Rows shown: Euro, US-Dollar, Britisches Pfund, Schweizer Franken,
  Türkische Lira, Rohöl (Brent), Goldunze.
- CALCULATOR SCREEN (see docs/design/reference_calc.png): tapping a row opens "Betrag ändern"
  with the currency name subtitle, a large big-number display (e.g. "150,00"), and a calculator
  keypad: AC ( ), % ÷ / 7 8 9 × / 4 5 6 − / 1 2 3 + / 0 , backspace ↵. This is the classic
  Wechselkurse input keypad.
- CONTENT SCOPE: fiat currencies + crypto + commodities (precious metals AND crude oil).
  Existing fiat providers cover fiat. Crypto needs CoinGecko (free, no key:
  https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,...&vs_currencies=eur).
  Commodities: XAU/XAG come from Frankfurter/ECB already; crude oil (Brent) needs a free
  no-key source (research options) or a documented key prompt.

## What to deliver (ANALYZE ONLY — NO code changes)

1. Map the current UI structure: which XML layouts / activities / adapters render the start
   screen, the currency rows (`row_currency.xml`), and the keypad/calculator. Name the exact files.
2. Identify the smallest set of changes to make the START screen look like reference_main.png
   (a clean overview list) while keeping charts/history/calculator reachable.
3. Propose how the tap-a-row → calculator-screen flow should be wired using the EXISTING
   keypad/calculator, matching reference_calc.png.
4. Recommend a crypto + commodities data-source design that fits the existing
   `ApiProvider`/`ExchangeRates` adapter pattern (no unnecessary permissions; keep INTERNET only).
5. List risks/unknowns (e.g. ECB does not provide oil; CoinGecko rate limits; flag drawables for
   BTC/metals; dark-theme tokens).

Write the analysis as a file at docs/analysis_plan.md. Be concrete: file paths + functions.
Do NOT modify any source files, layouts, or build files. Do NOT start a build.
