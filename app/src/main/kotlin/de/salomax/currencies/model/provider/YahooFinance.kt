package de.salomax.currencies.model.provider

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.moshi.moshiDeserializerOf
import com.github.kittinunf.result.Result
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Yahoo Finance chart endpoint - keyless. Used for everything that has no dedicated free API:
 * precious metals and commodities (futures, quoted in USD) and, as a fallback, crypto.
 */
object YahooFinance {

    private const val BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/"

    /**
     * Latest daily close of [symbol] (e.g. "SI=F" for silver), in the quote currency of that
     * symbol (USD for all symbols used here).
     */
    suspend fun getLatestClose(symbol: String): Result<Float, FuelError> {
        return get("$BASE_URL$symbol?interval=1d&range=5d")
    }

    /**
     * Last daily close of [symbol] on or before [date]. Looks back 5 days, so weekends and
     * holidays resolve to the previous trading day.
     */
    suspend fun getCloseOn(symbol: String, date: LocalDate): Result<Float, FuelError> {
        val from = date.minusDays(5).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val to = date.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        return get("$BASE_URL$symbol?interval=1d&period1=$from&period2=$to")
    }

    private suspend fun get(url: String): Result<Float, FuelError> {
        return Fuel.get(url)
            .header("User-Agent", "Mozilla/5.0")
            .awaitResult(moshiDeserializerOf(yahooLatestCloseAdapter))
    }

    /*
     * Yahoo response shape:
     * {
     *   "chart": {
     *     "result": [
     *       {
     *         "meta": { ... },
     *         "timestamp": [ 1724976000, ... ],
     *         "indicators": {
     *           "quote": [
     *             {
     *               "close": [ 78.12, 78.45, null, 77.98 ]
     *             }
     *           ]
     *         }
     *       }
     *     ],
     *     "error": null
     *   }
     * }
     * Keeps the latest non-null close (the last array element may be null for the open day).
     */
    private val yahooLatestCloseAdapter = object : JsonAdapter<Float>() {

        override fun fromJson(reader: JsonReader): Float? {
            var latestClose: Float? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "chart" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "result" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "indicators" -> {
                                                    reader.beginObject()
                                                    while (reader.hasNext()) {
                                                        when (reader.nextName()) {
                                                            "quote" -> {
                                                                reader.beginArray()
                                                                while (reader.hasNext()) {
                                                                    reader.beginObject()
                                                                    while (reader.hasNext()) {
                                                                        when (reader.nextName()) {
                                                                            "close" -> {
                                                                                reader.beginArray()
                                                                                while (reader.hasNext()) {
                                                                                    if (reader.peek() == JsonReader.Token.NULL) {
                                                                                        reader.skipValue()
                                                                                    } else {
                                                                                        latestClose = reader.nextDouble().toFloat()
                                                                                    }
                                                                                }
                                                                                reader.endArray()
                                                                            }
                                                                            else -> reader.skipValue()
                                                                        }
                                                                    }
                                                                    reader.endObject()
                                                                }
                                                                reader.endArray()
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
                                    }
                                    reader.endArray()
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
            return latestClose
        }

        override fun toJson(writer: JsonWriter, value: Float?) {
            writer.nullValue()
        }

    }

}
