package de.salomax.currencies.model.provider

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.moshi.moshiDeserializerOf
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import okio.Buffer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Yahoo Finance chart endpoint - keyless. Used for everything that has no dedicated free API:
 * precious metals and commodities (futures, quoted in USD) and, as a fallback, crypto.
 */
object YahooFinance {

    private const val BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/"

    /**
     * One daily bar: the trading day (UTC) and that day's close.
     * The timestamp has to be kept - see [lastCloseOnOrBefore].
     */
    internal data class Bar(val date: LocalDate, val close: Float)

    /**
     * Latest daily close of [symbol] (e.g. "SI=F" for silver), in the quote currency of that
     * symbol (USD for all symbols used here).
     */
    suspend fun getLatestClose(symbol: String): Result<Float?, FuelError> {
        return get("$BASE_URL$symbol?interval=1d&range=5d")
            .map { bars -> bars.lastOrNull()?.close }
    }

    /**
     * Last daily close of [symbol] on or before [date]. Looks back 5 days, so weekends and
     * holidays resolve to the previous trading day.
     */
    suspend fun getCloseOn(symbol: String, date: LocalDate): Result<Float?, FuelError> {
        val from = date.minusDays(5).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val to = date.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        return get("$BASE_URL$symbol?interval=1d&period1=$from&period2=$to")
            .map { bars -> lastCloseOnOrBefore(bars, date) }
    }

    /**
     * Close of the last bar whose trading day is on or before [date], or null if there is none.
     *
     * Taking the last bar of the response is NOT the same thing: the upper bound of the requested
     * window (`period2`, see [getCloseOn]) is inclusive, and a symbol that trades around midnight
     * UTC gets a bar exactly on that bound. For crypto that is a bar for the day AFTER the
     * requested one (Yahoo reports the exchange timezone of BTC-USD as UTC, its daily bars start
     * at 00:00 UTC), so every historical crypto rate was one day off. Futures do not trade at
     * midnight UTC - that is why metals and commodities never showed the off-by-one.
     */
    internal fun lastCloseOnOrBefore(bars: List<Bar>, date: LocalDate): Float? =
        bars.filter { !it.date.isAfter(date) }.maxByOrNull { it.date }?.close

    /** Parses a chart response. Used by the unit tests, which run against recorded responses. */
    internal fun parseBars(json: String): List<Bar> =
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            yahooBarsAdapter.fromJson(reader).orEmpty()
        }

    private suspend fun get(url: String): Result<List<Bar>, FuelError> {
        return Fuel.get(url)
            .header("User-Agent", "Mozilla/5.0")
            .awaitResult(moshiDeserializerOf(yahooBarsAdapter))
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
     * timestamp and close line up by index, so both are read and zipped. A null close (the open
     * day, or a day without trading) keeps its slot instead of being dropped - dropping it would
     * shift every later close onto the wrong day.
     */
    private val yahooBarsAdapter = object : JsonAdapter<List<Bar>>() {

        override fun fromJson(reader: JsonReader): List<Bar> {
            val bars = mutableListOf<Bar>()
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
                                        readResult(reader, bars)
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
            return bars
        }

        /** Reads one entry of chart.result - timestamp and indicators are siblings there. */
        private fun readResult(reader: JsonReader, bars: MutableList<Bar>) {
            val timestamps = mutableListOf<Long>()
            val closes = mutableListOf<Float?>()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "timestamp" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() == JsonReader.Token.NULL) {
                                reader.skipValue()
                            } else {
                                timestamps.add(reader.nextLong())
                            }
                        }
                        reader.endArray()
                    }
                    "indicators" -> readIndicators(reader, closes)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            for (i in timestamps.indices) {
                val close = closes.getOrNull(i) ?: continue
                bars.add(
                    Bar(
                        Instant.ofEpochSecond(timestamps[i]).atZone(ZoneOffset.UTC).toLocalDate(),
                        close
                    )
                )
            }
        }

        private fun readIndicators(reader: JsonReader, closes: MutableList<Float?>) {
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
                                                closes.add(null)
                                            } else {
                                                closes.add(reader.nextDouble().toFloat())
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

        override fun toJson(writer: JsonWriter, value: List<Bar>?) {
            writer.nullValue()
        }

    }

}
