package de.salomax.currencies.model.provider

import android.content.Context
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.moshi.moshiDeserializerOf
import com.github.kittinunf.result.Result
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import de.salomax.currencies.repository.Database

object BrentOil {

    // keyless default: Yahoo Finance chart endpoint, front-month Brent future BZ=F,
    // daily closes of the last 5 days. Value = USD per barrel
    private const val YAHOO_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/BZ=F?interval=1d&range=5d"

    // keyed official alternative: EIA v2 "Europe Brent Spot Price FOB" (facet duoarea=RBRTE),
    // daily, latest period first. Value = USD per barrel
    private const val EIA_URL = "https://api.eia.gov/v2/petroleum/pri/spt/data/"

    /**
     * Gets the latest Brent spot price in USD per barrel.
     * Uses the official EIA endpoint if a Brent/EIA API key is configured
     * (see Database.getBrentApiKey), and falls back to the keyless Yahoo endpoint -
     * also whenever the keyed call fails for any reason.
     */
    suspend fun getUsdPerBarrel(context: Context?): Result<Float, FuelError> {
        val apiKey = context?.let { Database(it).getBrentApiKey() }
        if (!apiKey.isNullOrBlank()) {
            val keyed = getFromEia(apiKey)
            if (keyed.component1() != null) return keyed
        }
        return getFromYahoo()
    }

    private suspend fun getFromEia(apiKey: String): Result<Float, FuelError> {
        return Fuel.get(
            EIA_URL +
                    "?api_key=$apiKey" +
                    "&frequency=daily" +
                    "&data[0]=value" +
                    "&facets[duoarea][]=RBRTE" +
                    "&sort[0][column]=period" +
                    "&sort[0][direction]=desc" +
                    "&length=1"
        ).awaitResult(moshiDeserializerOf(eiaLatestValueAdapter))
    }

    private suspend fun getFromYahoo(): Result<Float, FuelError> {
        return Fuel.get(YAHOO_URL)
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

    /*
     * EIA v2 response shape:
     * {
     *   "response": {
     *     "total": "123",
     *     "warnings": [ ],
     *     "data": [
     *       {
     *         "period": "2026-08-28",
     *         "duoarea": "RBRTE",
     *         "product-name": "Crude Oil Brent Europe",
     *         "value": 65.2,
     *         "units": "USD/bbl"
     *       }
     *     ]
     *   },
     *   "request": { ... }
     * }
     * Keeps the latest non-null value (query is sorted period-descending, length=1).
     */
    private val eiaLatestValueAdapter = object : JsonAdapter<Float>() {

        override fun fromJson(reader: JsonReader): Float? {
            var latestValue: Float? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "response" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "data" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "value" -> {
                                                    if (reader.peek() == JsonReader.Token.NULL) {
                                                        reader.skipValue()
                                                    } else {
                                                        latestValue = reader.nextDouble().toFloat()
                                                    }
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
            return latestValue
        }

        override fun toJson(writer: JsonWriter, value: Float?) {
            writer.nullValue()
        }

    }

}
