# PHASE 1: Data-model foundation — IMPLEMENT

Work ONLY inside this directory (cwd). Do NOT read, glob, search, or edit anything outside it.
Repo: `sal0max/currencies` (Kotlin, classic Views + XML, NOT Compose). Base component: the `Currency`
enum at `app/src/main/kotlin/de/salomax/currencies/model/Currency.kt`.

Read `docs/analysis_plan.md` §5.1 and §5.5 first for full context. Then implement ONLY the
data-model foundation described below. Do NOT touch layouts, activities, adapters, build files,
or any provider/repository code yet. Do NOT start a build. Make the change minimal and compile-clean.

## Background
`Currency.fromString()` (Currency.kt:192-214) currently BLACKLISTS BTC, XAG, XAU, XPD, XPT (so
crypto/metals can never flow into a `Rate`), plus superseded/special codes. Every rates adapter
routes through this function. We only want to UNBLOCK the ones we actually have data for, and we
need a pseudo-code for crude oil (Brent). We also need a unit label and an icon for the new
"non-flag" assets, so the new list row can render them nicely (the default `flag_unknown` gray
globe is not acceptable).

## Changes (Currency.kt only)

1. **Open the gate for the assets we support.** In `fromString()`, REMOVE the explicit blacklist
   entries for `BTC`, `XAU`, `XAG` so they resolve. KEEP the blacklist for `XPD`, `XPT` (no free
   data source — they stay hidden), and KEEP the superseded (MRO/STD/VEF/CUC) and special
   (XDR/CLF/CNH) exclusions exactly as-is.
2. **Add a Brent pseudo-code enum entry.** Add a new `Currency` enum value for crude oil (Brent)
   with:
   - `iso4217Alpha = "XBZ"` (matches the reference pseudo-code; internal, not a real ISO code)
   - `iso4217Numeric = null`, `symbol = null`
   - a `fullName` string-resource (add `name_xbz` to `res/values/strings.xml` → "Rohöl (Brent)"),
     English fallback "Crude oil (Brent)"
   - `flag = null`
   - Add `"XBZ"` to the allowed set in `fromString()` (it must NOT be re-blacklisted).
3. **Add a `unitLabel` field.** Extend the enum constructor to take `unitLabel: String? = null`
   (after `flag`). Populate for the assets that need it, for example:
   - `XAU -> "oz t"` (troy ounce), `XAG -> "oz t"`, `XPD/XPT -> "oz t"`
   - `XBZ -> "bbl"` (barrel), `BTC -> "₿"`
   Provide an accessor `fun unitLabel(): String?`. Default `null` for all fiat currencies (no unit).
4. **Add an `icon(@DrawableRes)` concept.** `flag()` currently falls back to `flag_unknown` when
   `flag == null`. Add a new `icon(context)` (or extend `flag()`) that returns a themed, meaningful
   drawable for non-flag assets instead of the gray globe:
   - Add new vector drawables under `res/drawable/`: `img_asset_bitcoin.xml` (a ₿ mark),
     `img_asset_gold.xml` (a bar/coin), `img_asset_silver.xml`, `img_asset_oil.xml` (a barrel),
     where practical. Provide a simple, clean vector path — keep them minimal (a few paths).
   - Wire the mapping so BTC→bitcoin, XAU→gold, XAG→silver, XBZ→oil; fiat currencies keep the flag;
     fall back to `flag_unknown` only for genuinely unknown codes.
   Do NOT change the existing `flag(context)` signature used by callers — add the new method and
   (later) point the new list adapter at it. For now just add it.

## Verification before you finish
- `git status --porcelain` must show ONLY `Currency.kt`, `res/values/strings.xml`, and the new
  `res/drawable/*.xml` files as modified/added. No other source files touched.
- Confirm `fromString("BTC")`, `fromString("XAU")`, `fromString("XBZ")` now return non-null, and
  `fromString("XPD")` still returns null, in your analysis (you may write a tiny scratch note in
  `docs/phase1_notes.md`, but do NOT modify any test or source beyond the listed files).
- Do NOT start a Gradle build.

Summarize in English what you changed and any compile risks you see.
