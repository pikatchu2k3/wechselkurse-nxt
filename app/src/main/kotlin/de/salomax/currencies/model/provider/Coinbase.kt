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
 * Coinbase public spot-price API — no API key required. Returns "EUR per 1 unit"
 * (e.g. EUR per 1 BTC, or EUR per 1 troy oz of gold via the PAXG token).
 *
 * Response shape (GET /v2/prices/{SYMBOL}-EUR/spot):
 *   { "data": { "amount": "68024.08", "base": "BTC", "currency": "EUR" } }
 *
 * Reliable and keyless, unlike CoinGecko (which rate-limits free/unauthenticated
 * calls) — used as the primary source for crypto + gold, with CoinGecko as fallback.
 */
object Coinbase {

    private const val BASE_URL = "https://api.coinbase.com/v2/prices/"

    /**
     * Gets the current spot price of [symbol] (a Coinbase pair base, e.g. "BTC", "PAXG")
     * denominated in EUR. Returns EUR per 1 unit, or 0/null on failure. Never throws.
     */
    suspend fun getEurSpot(symbol: String): Result<Float?, FuelError> {
        return Fuel.get(BASE_URL + symbol + "-EUR/spot")
            .header("User-Agent", "Mozilla/5.0")
            .awaitResult(moshiDeserializerOf(spotAmountAdapter))
    }

    /*
     * Extracts the "amount" string and converts it to a Float. Ignores everything else.
     */
    private val spotAmountAdapter = object : JsonAdapter<Float>() {

        override fun fromJson(reader: JsonReader): Float? {
            var amount: Float? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "data" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "amount" -> {
                                    if (reader.peek() == JsonReader.Token.NULL) {
                                        reader.skipValue()
                                    } else {
                                        amount = reader.nextString().toFloatOrNull()
                                    }
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
            return amount
        }

        override fun toJson(writer: JsonWriter, value: Float?) {
            writer.nullValue()
        }

    }

}
