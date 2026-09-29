package de.salomax.currencies.model.provider

import android.content.Context
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

/**
 * Everything that isn't a fiat currency: crypto, precious metals and commodities.
 *
 * None of the fiat providers offers these, so they are fetched from supplementary keyless
 * sources and merged into the provider's snapshot (see ExchangeRatesRepository). Every asset
 * lists the sources it can be read from; they are tried in this order until one delivers:
 *
 *  1. [Coinbase] exchange-rates: one request for all crypto, "units per 1 EUR" (latest only)
 *  2. [YahooFinance]: USD quote of a future / crypto pair - the only source for commodities
 *     and the only one that can serve a historical date
 *  3. [CoinGecko]: last resort for the few assets it knows (latest only)
 *
 * All results are converted into the app's internal convention "units per 1 base unit of the
 * provider's snapshot", so they can be displayed with the same formula as any fiat rate.
 */
object AssetRates {

    /**
     * @param coinbase code in Coinbase's exchange-rates response
     * @param yahoo Yahoo symbol, quoted in USD
     * @param yahooDivisor quote / divisor = USD per 1 unit (100 for futures quoted in US cents)
     * @param coinGecko CoinGecko coin id
     */
    private data class Source(
        val currency: Currency,
        val coinbase: String? = null,
        val yahoo: String? = null,
        val yahooDivisor: Float = 1f,
        val coinGecko: String? = null
    )

    private val SOURCES = listOf(
        // crypto
        Source(Currency.BTC, coinbase = "BTC", yahoo = "BTC-USD", coinGecko = "bitcoin"),
        Source(Currency.ETH, coinbase = "ETH", yahoo = "ETH-USD", coinGecko = "ethereum"),
        Source(Currency.XRP, coinbase = "XRP", yahoo = "XRP-USD", coinGecko = "ripple"),
        Source(Currency.BNB, coinbase = "BNB", yahoo = "BNB-USD", coinGecko = "binancecoin"),
        Source(Currency.SOL, coinbase = "SOL", yahoo = "SOL-USD", coinGecko = "solana"),
        Source(Currency.ADA, coinbase = "ADA", yahoo = "ADA-USD", coinGecko = "cardano"),
        Source(Currency.DOGE, coinbase = "DOGE", yahoo = "DOGE-USD", coinGecko = "dogecoin"),
        Source(Currency.DOT, coinbase = "DOT", yahoo = "DOT-USD", coinGecko = "polkadot"),
        Source(Currency.LTC, coinbase = "LTC", yahoo = "LTC-USD", coinGecko = "litecoin"),
        Source(Currency.BCH, coinbase = "BCH", yahoo = "BCH-USD", coinGecko = "bitcoin-cash"),
        Source(Currency.LINK, coinbase = "LINK", yahoo = "LINK-USD", coinGecko = "chainlink"),
        Source(Currency.AVAX, coinbase = "AVAX", yahoo = "AVAX-USD", coinGecko = "avalanche-2"),
        Source(Currency.XLM, coinbase = "XLM", yahoo = "XLM-USD", coinGecko = "stellar"),
        // precious metals, per troy ounce (Coinbase doesn't list them -> Yahoo futures).
        // CoinGecko: gold via the PAX Gold token (1 token = 1 oz t)
        Source(Currency.XAU, yahoo = "GC=F", coinGecko = "pax-gold"),
        Source(Currency.XAG, yahoo = "SI=F"),
        Source(Currency.XPT, yahoo = "PL=F"),
        Source(Currency.XPD, yahoo = "PA=F"),
        // commodities (front-month futures)
        Source(Currency.XBZ, yahoo = BrentOil.YAHOO_SYMBOL),                // USD / barrel
        Source(Currency.XWT, yahoo = "CL=F"),                               // USD / barrel
        Source(Currency.XNG, yahoo = "NG=F"),                               // USD / MMBtu
        Source(Currency.XCU, yahoo = "HG=F"),                               // USD / lb
        Source(Currency.XWH, yahoo = "ZW=F", yahooDivisor = 100f),          // US cents / bushel
        Source(Currency.XCN, yahoo = "ZC=F", yahooDivisor = 100f),          // US cents / bushel
        Source(Currency.XSY, yahoo = "ZS=F", yahooDivisor = 100f),          // US cents / bushel
        Source(Currency.XCF, yahoo = "KC=F", yahooDivisor = 100f),          // US cents / lb
        Source(Currency.XSG, yahoo = "SB=F", yahooDivisor = 100f),          // US cents / lb
        Source(Currency.XCT, yahoo = "CT=F", yahooDivisor = 100f),          // US cents / lb
        Source(Currency.XCC, yahoo = "CC=F"),                               // USD / metric ton
    )

    // Yahoo is asked for several symbols at once - but not for all at once
    private const val YAHOO_PARALLELISM = 4

    /**
     * Fetches every asset the snapshot doesn't already contain. Assets that no source could
     * deliver are simply missing from the result; this never throws for a failed source.
     *
     * @param fiat the provider's fiat snapshot; defines the base the results are scaled to
     * @param date historical date, or null for the latest rates
     * @return rates in the app's convention "units per 1 base unit of [fiat]"
     */
    suspend fun fetch(context: Context, fiat: ExchangeRates, date: LocalDate?): List<Rate> {
        val snapshot = fiat.rates.orEmpty()
        // what the provider delivers itself (e.g. OpenExchangerates has gold) wins
        val open = SOURCES.filter { source -> snapshot.none { it.currency == source.currency } }
        if (open.isEmpty()) return emptyList()

        // fiat values are "per 1 base unit": EUR/USD convert the sources' EUR/USD quotes to the snapshot's base
        val eurValue = snapshot.find { it.currency == Currency.EUR }?.value
            ?.takeIf { it > 0f } ?: 1f
        val usdValue = snapshot.find { it.currency == Currency.USD }?.value
            ?.takeIf { it > 0f }

        val result = LinkedHashMap<Currency, Float>()

        // 1. Coinbase: units per EUR, latest only
        if (date == null) {
            val perEur = attempt { Coinbase.getRatesPerEur().component1() }
            if (perEur != null) {
                open.forEach { source ->
                    source.coinbase?.let { perEur[it] }?.let { result[source.currency] = it * eurValue }
                }
            }
        }

        // 2. Yahoo: USD per unit -> units per base = USD per base / USD per unit
        if (usdValue != null) {
            val gate = Semaphore(YAHOO_PARALLELISM)
            coroutineScope {
                open
                    .filter { it.yahoo != null && it.currency !in result }
                    .map { source ->
                        async(Dispatchers.IO) {
                            gate.withPermit { source to getUsdPrice(context, source, date) }
                        }
                    }
                    .awaitAll()
            }.forEach { (source, usdPerUnit) ->
                if (usdPerUnit != null) result[source.currency] = usdValue / usdPerUnit
            }
        }

        // 3. CoinGecko: EUR per unit, latest only
        if (date == null) {
            val remaining = open
                .filter { it.coinGecko != null && it.currency !in result }
                .associate { it.coinGecko!! to it.currency }
            if (remaining.isNotEmpty()) {
                attempt { CoinGecko.getPrices(remaining, Currency.EUR, context).component1() }
                    ?.forEach { (currency, eurPerUnit) ->
                        if (eurPerUnit > 0f) result[currency] = eurValue / eurPerUnit
                    }
            }
        }

        return result.map { (currency, value) -> Rate(currency, value) }
    }

    /**
     * USD per 1 unit of the asset, or null. Brent uses the (optionally keyed) EIA endpoint for
     * the latest price, everything else goes to Yahoo.
     */
    private suspend fun getUsdPrice(context: Context, source: Source, date: LocalDate?): Float? {
        val close = attempt {
            when {
                source.currency == Currency.XBZ && date == null ->
                    BrentOil.getUsdPerBarrel(context).component1()
                date == null -> YahooFinance.getLatestClose(source.yahoo!!).component1()
                else -> YahooFinance.getCloseOn(source.yahoo!!, date).component1()
            }
        }
        return close?.takeIf { it > 0f && it.isFinite() }?.div(source.yahooDivisor)
    }

    // a failing source must never break the others (or the fiat rates that are already cached)
    private inline fun <T> attempt(block: () -> T?): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (ignored: Exception) {
            null
        }
    }

}
