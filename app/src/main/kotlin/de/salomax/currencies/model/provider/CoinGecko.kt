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

    /**
     * Gets the current price of every requested coin, denominated in the given vs currency.
     * The returned map holds "vs currency per 1 coin" (e.g. {BTC: 90000.0} when vs = EUR) -
     * NOT the app's internal "1 EUR = X units" convention (see [AssetRates]).
     *
     * @param ids CoinGecko coin id -> the [Currency] it stands for
     */
    suspend fun getPrices(
        ids: Map<String, Currency>,
        vs: Currency,
        @Suppress("UNUSED_PARAMETER") context: Context? = null
    ): Result<Map<Currency, Float>, FuelError> {
        return Fuel.get(
            BASE_URL +
                    "?ids=${ids.keys.joinToString(",")}" +
                    "&vs_currencies=${vs.iso4217Alpha().lowercase()}" +
                    "&include_last_updated_at=true"
        ).awaitResult(
            moshiDeserializerOf(
                Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .apply {
                        add(
                            CoinGeckoPricesAdapter(ids, vs)
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
