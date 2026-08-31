package de.salomax.currencies.model.provider

import android.content.Context
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.moshi.moshiDeserializerOf
import com.github.kittinunf.result.Result
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.adapter.CoinGeckoPricesAdapter

object CoinGecko {

    private const val BASE_URL = "https://api.coingecko.com/api/v3/simple/price"

    // fixed crypto set: CoinGecko id -> Currency. extend here if more coins are wanted
    private val CRYPTO_IDS: Map<String, Currency> = mapOf(
        "bitcoin" to Currency.BTC
    )

    // precious metals: CoinGecko token-backed ids -> Currency (1 token == 1 troy oz)
    // Gold (XAU) via Pax Gold. Silver (XAG) has no reliable no-key CoinGecko id -> left out.
    private val METAL_IDS: Map<String, Currency> = mapOf(
        "pax-gold" to Currency.XAU
    )

    fun cryptoIds(): List<String> {
        return CRYPTO_IDS.keys.toList()
    }

    fun metalIds(): List<String> {
        return METAL_IDS.keys.toList()
    }

    /**
     * Gets the current price of every requested coin, denominated in the given vs currency.
     * The returned map holds "vs currency per 1 coin" (e.g. {BTC: 90000.0} when vs = EUR) -
     * NOT the app's internal "1 EUR = X units" convention (see ExchangeRatesRepository).
     */
    suspend fun getPrices(
        ids: List<String>,
        vs: Currency,
        @Suppress("UNUSED_PARAMETER") context: Context? = null
    ): Result<Map<Currency, Float>, FuelError> {
        return Fuel.get(
            BASE_URL +
                    "?ids=${ids.joinToString(",")}" +
                    "&vs_currencies=${vs.iso4217Alpha().lowercase()}" +
                    "&include_last_updated_at=true"
        ).awaitResult(
            moshiDeserializerOf(
                Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .apply {
                        add(
                            CoinGeckoPricesAdapter(
                                (CRYPTO_IDS + METAL_IDS).filterKeys { ids.contains(it) },
                                vs
                            )
                        )
                    }
                    .build()
                    .adapter<Map<Currency, Float>>(
                        Types.newParameterizedType(
                            Map::class.java,
                            Currency::class.java,
                            Float::class.javaObjectType
                        )
                    )
            )
        )
    }

}
