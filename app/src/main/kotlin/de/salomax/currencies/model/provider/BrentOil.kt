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

    // keyless default: Yahoo Finance, front-month Brent future BZ=F. Value = USD per barrel
    const val YAHOO_SYMBOL = "BZ=F"

    // keyed official alternative: EIA v2 "Europe Brent Spot Price FOB" (facet duoarea=RBRTE),
    // daily, latest period first. Value = USD per barrel
    private const val EIA_URL = "https://api.eia.gov/v2/petroleum/pri/spt/data/"

    /**
     * Gets the latest Brent spot price in USD per barrel.
     * Uses the official EIA endpoint if a Brent/EIA API key is configured
     * (see Database.getBrentApiKey), and falls back to the keyless Yahoo endpoint -
     * also whenever the keyed call fails for any reason.
     */
    suspend fun getUsdPerBarrel(context: Context?): Result<Float?, FuelError> {
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

    private suspend fun getFromYahoo(): Result<Float?, FuelError> {
        return YahooFinance.getLatestClose(YAHOO_SYMBOL)
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
