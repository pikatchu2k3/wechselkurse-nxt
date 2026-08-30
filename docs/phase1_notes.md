# Phase 1 notes (data-model foundation)

Scope: `Currency.kt`, `res/values/strings.xml`, 4 new `res/drawable/*.xml`. Nothing else touched.

## fromString() gating — static trace

`fromString()` is `entries.firstOrNull { it.iso4217Alpha == value }` guarded by a blacklist chain.
After the change:

- `fromString("BTC")` → not blacklisted → matches enum entry `BTC` → **non-null**
- `fromString("XAU")` → not blacklisted → matches `XAU` → **non-null**
- `fromString("XAG")` → not blacklisted → matches `XAG` → **non-null**
- `fromString("XBZ")` → never blacklisted (newly added to the allowed set by omission) → matches
  the new `XBZ` entry → **non-null**
- `fromString("XPD")` / `fromString("XPT")` → still explicitly blacklisted → **null**
- Superseded `MRO`/`STD`/`VEF`/`CUC` and special `XDR`/`CLF`/`CNH` → blacklists kept verbatim → **null**

## Strings

- `name_xbz` added to `res/values/strings.xml` as **"Crude oil (Brent)"** — the repo's default
  locale is English (`values/` English, `values-de/` German), so this is the fallback.
- German **"Rohöl (Brent)"** belongs in `values-de/`; deferred because Phase 1 restricts changes
  to `values/strings.xml` only. Follow-up in the translation phase (ideally also move/copy
  `name_xbz` into `strings_currencies.xml`, where all sibling `name_*` strings live, if the
  project owner prefers that convention).

## Icons

- `icon(context)` mapping: `BTC → img_asset_bitcoin`, `XAU → img_asset_gold`,
  `XAG → img_asset_silver`, `XBZ → img_asset_oil`; fiat → its flag; everything else with
  `flag == null` → `flag_unknown` (in practice only the hidden `XPD`/`XPT`, which never reach
  a list because `fromString()` filters them).
- Drawables are 21×15dp vectors (same slot as the flag vectors), hardcoded brand colors like the
  flags: bitcoin #F7931A, gold/silver ingot stacks, blue-gray oil barrel with drop.

## Compile risks

1. `unitLabel` param has a default (`= null`), so all ~160 untouched enum entries stay valid.
2. `fun unitLabel()` accessor + private `unitLabel` property coexist fine (separate namespaces).
3. New `R.drawable.img_asset_*` / `R.string.name_xbz` references resolve once the added resource
   files are picked up; no other code references them yet.
4. Moshi `@JsonClass(generateAdapter = false)` on the enum is unaffected by added constructor
   params (serializes by `name`).
5. Ripple risk (per analysis_plan §6.3, not part of Phase 1): providers may now emit rows for
   BTC/XAU/XAG wherever their feeds contain those codes — regression pass needed in a later phase.
