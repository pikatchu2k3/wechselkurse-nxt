package de.salomax.currencies.model

/**
 * The kind of tradable thing a [Currency] entry stands for. Drives grouping in the rates list,
 * the category tabs of the add-currency dialog and how a rate is described to the user.
 */
enum class AssetCategory {
    /** regular (fiat) currencies */
    FIAT,

    /** crypto currencies, e.g. bitcoin */
    CRYPTO,

    /** precious metals, e.g. gold - quoted per troy ounce */
    METAL,

    /** commodities, e.g. crude oil - quoted per barrel / bushel / pound / ... */
    COMMODITY
}
