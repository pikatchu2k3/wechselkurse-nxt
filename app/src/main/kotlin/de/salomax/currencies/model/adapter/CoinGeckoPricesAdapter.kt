package de.salomax.currencies.model.adapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import de.salomax.currencies.model.Currency
import java.io.IOException

/*
 * Parses the CoinGecko simple/price response into a Map<Currency, Float>.
 * Response shape (include_last_updated_at=true):
 * {
 *   "bitcoin": {
 *     "eur": 90123.45,
 *     "last_updated_at": 1725012345
 *   }
 * }
 * Only the vs-currency price field is read, everything else is skipped.
 */
@Suppress("unused", "UNUSED_PARAMETER")
internal class CoinGeckoPricesAdapter(
    private val ids: Map<String, Currency>,
    private val vs: Currency
) {

    @Synchronized
    @FromJson
    @Throws(IOException::class)
    fun fromJson(reader: JsonReader): Map<Currency, Float> {
        val prices = mutableMapOf<Currency, Float>()
        val vsCode = vs.iso4217Alpha().lowercase()
        reader.beginObject()
        // convert
        while (reader.hasNext()) {
            val id: String = reader.nextName()
            val currency: Currency? = ids[id]
            reader.beginObject()
            while (reader.hasNext()) {
                val name: String = reader.nextName()
                if (currency != null && name == vsCode) {
                    prices[currency] = reader.nextDouble().toFloat()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endObject()
        return prices
    }

    @Synchronized
    @ToJson
    @Throws(IOException::class)
    fun toJson(writer: JsonWriter, value: Map<Currency, Float>?) {
        writer.nullValue()
    }

}
