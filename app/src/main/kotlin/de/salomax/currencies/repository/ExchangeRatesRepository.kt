package de.salomax.currencies.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.kittinunf.fuel.core.FuelError
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.provider.BrentOil
import de.salomax.currencies.model.provider.Coinbase
import de.salomax.currencies.model.provider.CoinGecko
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ExchangeRatesRepository(private val context: Context) {

    private val liveExchangeRates = Database(context).getExchangeRates()
    private val liveTimeline = MutableLiveData<Timeline?>()
    private var liveError = MutableLiveData<String?>()
    private var isUpdating = Database(context).isUpdating()

    /**
     * Gets and returns all latest exchange rates from the API.
     */
    fun getExchangeRates(): LiveData<ExchangeRates?> {
        val start = System.currentTimeMillis()
        Database(context).setUpdating(true)

        // run in background
        CoroutineScope(Dispatchers.IO).launch {
            // call api
            ExchangeRatesService.getRates(
                // use the right api
                apiProvider = Database(context).getApiProvider(),
                date = Database(context).getHistoricalDate(),
                context
            ).run  {
                val rates = component1()
                val fuelError = component2()
                // received some json
                if (rates != null && fuelError == null) {
                    // SUCCESS! update /store rates to preferences
                    if (rates.success == null || rates.success == true) {
                        postIsUpdating(start)
                        Database(context).insertExchangeRates(rates)
                        // reset error
                        liveError.postValue(null)

                        // enrich the fiat-only snapshot with the supplementary merge-sources
                        // (crypto + Brent). a merge failure is deliberately swallowed:
                        // the fiat rates above stay cached and usable, and merge problems
                        // must never trip the generic error path
                        try {
                            val mergeRates = fetchMergeRates(rates)
                            if (mergeRates.isNotEmpty()) {
                                val mergedRates = rates.rates.orEmpty()
                                    .filterNot { existing ->
                                        mergeRates.any { it.currency == existing.currency }
                                    }
                                    .plus(mergeRates)
                                Database(context).insertExchangeRates(rates.copy(rates = mergedRates))
                            }
                        } catch (ignored: Exception) {
                        }
                    }
                    // ERROR: got response from API, but just an error message
                    else {
                        postError(rates.error)
                    }
                }
                // generic error
                else handleGenericError(fuelError)
            }
        }

        return liveExchangeRates
    }

    /**
     * Gets and returns the timeline of the last year of the given base and target currency
     */
    fun getTimeline(base: Currency, symbol: Currency): LiveData<Timeline?> {
        val start = System.currentTimeMillis()
        Database(context).setUpdating(true)

        // run in background
        CoroutineScope(Dispatchers.IO).launch {
            // call api
            ExchangeRatesService.getTimeline(
                // use the right api
                apiProvider = Database(context).getApiProvider(),
                base = base,
                symbol = symbol,
                context = context
            ).run {
                val timeline = component1()
                val fuelError = component2()
                // received some json
                if (timeline != null && fuelError == null) {
                    // SUCCESS! update /store rates to preferences
                    if (timeline.success == null || timeline.success == true) {
                        postIsUpdating(start)
                        CoroutineScope(Dispatchers.Main).launch {
                            liveTimeline.setValue(timeline)
                        }
                        // reset error
                        liveError.postValue(null)
                    }
                    // ERROR! got response from API, but just an error message
                    else {
                        postError(timeline.error)
                    }
                }
                // generic error
                else handleGenericError(fuelError)
            }
        }

        return liveTimeline
    }

    /**
     * Fetches the supplementary merge-sources (crypto + Brent) and converts them into
     * [Rate]s following the app's internal "1 EUR = X units" convention:
     * CoinGecko returns "EUR per 1 coin" and Yahoo/EIA return "USD per barrel", so both
     * are inverted (Brent additionally converted to EUR via the freshly fetched fiat USD
     * rate) before merging. Never throws for a failed source - failures are ignored and
     * simply yield no Rate.
     */
    private suspend fun fetchMergeRates(rates: ExchangeRates): List<Rate> {
        val merged = mutableListOf<Rate>()

        // The fiat snapshot rows sit on the provider's own base scale (e.g. BankRossii: EUR=0.0099),
        // but the supplementary sources (Coinbase/CoinGecko) are EUR-denominated ("1 EUR = X units").
        // To display them consistently via the same formula (baseValue/baseRateValue * rate.value),
        // scale each merge value by the snapshot's EUR value.
        val eurScale = rates.rates?.find { it.currency == Currency.EUR }?.value
            ?.takeIf { it != 0f } ?: 1f

        // crypto + gold: primary source is Coinbase (reliable, no API key) — it returns
        // "EUR per 1 unit" (coin / troy oz), inverted to the app's "1 EUR = X units" convention.
        // Supplementary sources are merged for EVERY provider (the provider's own base does not gate them).
        try {
            listOf(Currency.BTC to "BTC", Currency.XAU to "PAXG").forEach { (currency, symbol) ->
                Coinbase.getEurSpot(symbol).component1()?.let { eurPerUnit ->
                    if (eurPerUnit > 0f) merged.add(Rate(currency, (1f / eurPerUnit) * eurScale))
                }
            }
        } catch (ignored: Exception) {
        }
        // fallback: CoinGecko (same "EUR per 1 unit" convention) for any currency Coinbase did not fill
        try {
            CoinGecko.getPrices(
                CoinGecko.cryptoIds() + CoinGecko.metalIds(),
                Currency.EUR,
                context
            ).component1()
                ?.forEach { (currency, eurPerUnit) ->
                    if (currency !in merged.map { it.currency } && eurPerUnit > 0f)
                        merged.add(Rate(currency, (1f / eurPerUnit) * eurScale))
                }
        } catch (ignored: Exception) {
        }
        // Brent: USD per barrel -> EUR per barrel via fiat USD rate -> invert to "1 EUR = X barrels"
        try {
            BrentOil.getUsdPerBarrel(context)
                .component1()
                ?.let { usdPerBarrel ->
                    // fiat Rate(USD).value is "USD per 1 EUR"
                    val usdPerEur = rates.rates?.find { it.currency == Currency.USD }?.value
                    if (usdPerEur != null && usdPerEur > 0f && usdPerBarrel > 0f)
                        merged.add(Rate(Currency.XBZ, usdPerEur / usdPerBarrel))
                }
        } catch (ignored: Exception) {
        }

        return merged
    }

    private fun handleGenericError(fuelError: FuelError?) {
        when {
            // shouldn't happen...
            fuelError == null ->
                postError(R.string.error_generic.text())
            // print http response code, if available
            fuelError.response.statusCode != -1 && fuelError.response.statusCode != 200 -> {
                postError(R.string.error_http.text(fuelError.response.statusCode))
            }
            // generic network error
            else -> {
                when (fuelError.exception) {
                    // timeout after 15s. likely server not reachable
                    is SocketTimeoutException ->
                        postError(R.string.error_timeout.text())
                    // happens e.g. when device is offline or there's a DNS error
                    is UnknownHostException ->
                        postError(R.string.error_no_data.text())
                    // received no data - happens e.g. with RUB @ Norges Bank
                    is NoSuchElementException ->
                        postError(R.string.error_empty_response.text())
                    // everything else
                    else ->
                        postError(fuelError.localizedMessage?.let { R.string.error.text(it) } ?: R.string.error_generic.text())
                }
            }
        }
    }

    fun getError(): LiveData<String?> {
        return liveError
    }

    fun isUpdating(): LiveData<Boolean> {
        return isUpdating
    }

    /*
     * "update" for at least 750ms
     */
    private suspend fun postIsUpdating(start: Long) {
        val now = System.currentTimeMillis()
        if (now - start < 750) {
            Database(context).setUpdating(true)

            withContext(Dispatchers.Main) {
                launch {
                    delay(750 - (now - start))
                    Database(context).setUpdating(false)
                }
            }
        } else
            Database(context).setUpdating(false)
    }

    private fun postError(message: String?) {
        // disable progress bar
        Database(context).setUpdating(false)

        // post error
        var errorMessage = "<b>" + (message ?: R.string.error_api_error.text()) + "\u00A0\uD83D\uDC40</b>"
        // tell the user the API can be changed
        if (message?.contains(R.string.error_no_data.text()) != true)
            errorMessage += "\n<br>${R.string.error_try_another_api.text()}\u00A0\uD83E\uDD13"
        liveError.postValue(errorMessage)

        // reset timeline
        liveTimeline.postValue(null)
    }

    private fun Int.text(vararg message: Any): String {
        return context.getString(this, *message)
    }

}
