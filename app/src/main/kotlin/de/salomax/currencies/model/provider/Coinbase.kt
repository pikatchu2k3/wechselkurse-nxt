package de.salomax.currencies.model.provider

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.moshi.moshiDeserializerOf
import com.github.kittinunf.result.Result
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

/**
 * Coinbase public exchange-rates API - no API key required. One single request returns the
 * value of "1 EUR" in every crypto currency Coinbase knows, which is
 * exactly the app's internal "1 EUR = X units" convention.
 *
 * Response shape (GET /v2/exchange-rates?currency=EUR):
 *   { "data": { "currency": "EUR", "rates": { "BTC": "0.0000147", "ETH": "0.00033", ... } } }
 *
 * Reliable and keyless, unlike CoinGecko (which rate-limits free/unauthenticated calls) -
 * used as the primary source for crypto, with Yahoo and CoinGecko as fallbacks.
 */
object Coinbase {

    private const val URL = "https://api.coinbase.com/v2/exchange-rates?currency=EUR"

    /**
     * Gets "units per 1 EUR" for every asset code Coinbase lists (e.g. "BTC" -> 0.0000147).
     * Codes with a missing / unparsable / non-positive value are left out.
     */
    suspend fun getRatesPerEur(): Result<Map<String, Float>, FuelError> {
        return Fuel.get(URL)
            .header("User-Agent", "Mozilla/5.0")
            .awaitResult(moshiDeserializerOf(ratesAdapter))
    }

    /*
     * Reads data.rates into a map. Everything else is skipped.
     */
    private val ratesAdapter = object : JsonAdapter<Map<String, Float>>() {

        override fun fromJson(reader: JsonReader): Map<String, Float> {
            val rates = mutableMapOf<String, Float>()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "data" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "rates" -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val code = reader.nextName()
                                        if (reader.peek() == JsonReader.Token.NULL) {
                                            reader.skipValue()
                                        } else {
                                            reader.nextString().toFloatOrNull()
                                                ?.takeIf { it > 0f && it.isFinite() }
                                                ?.let { rates[code] = it }
                                        }
                                    }
                                    reader.endObject()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return rates
        }

        override fun toJson(writer: JsonWriter, value: Map<String, Float>?) {
            writer.nullValue()
        }

    }

}
