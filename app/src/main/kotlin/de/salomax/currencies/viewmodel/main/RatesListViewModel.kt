package de.salomax.currencies.viewmodel.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import de.salomax.currencies.repository.Database
import java.time.LocalDate

class RatesListViewModel(app: Application) : AndroidViewModel(app) {

    class Factory(val app: Application) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RatesListViewModel(app) as T
        }
    }

    data class Rows(
        val base: Currency?,
        /**
         * the actual stored rate value of [base], taken from the full (unfiltered) rate list.
         * Basis for the row-amount conversion - a fallback of 1f is only correct for an
         * EUR-based snapshot (where EUR's value is 1.0 by definition)
         */
        val baseRateValue: Float,
        val rates: List<Rate>
    )

    private val mainViewModel: MainViewModel

    init {
        // first launch: the list starts with the default set (EUR + USD only),
        // everything else is added via the add-currency flow
        Database(app).seedDefaultStars()
        mainViewModel = MainViewModel(app)
    }

    private val rows = object : MediatorLiveData<Rows>() {
        var exchangeRates: ExchangeRates? = null
        var starred: Set<Currency>? = null
        var homeCurrency: Currency = Currency.EUR
        private val database = Database(app)

        init {
            addSource(mainViewModel.getExchangeRates()) { exchangeRates = it; update() }
            addSource(mainViewModel.getStarredCurrencies()) { starred = it; update() }
            // the list is drawn relative to the currency last calculated in the change-amount screen
            addSource(database.getHomeCurrencyLiveData()) { homeCurrency = it ?: Currency.EUR; update() }
        }

        fun update() {
            val allRates = exchangeRates?.rates ?: return
            val starredCurrencies = starred
            val baseCurrency = homeCurrency
            value = Rows(
                // the home row is always the currency the user last calculated a value for
                // (persisted via setHomeCurrency) - the whole list scales relative to it
                base = baseCurrency,
                baseRateValue = allRates.find { it.currency == baseCurrency }
                    ?.value?.takeIf { it != 0f } ?: 1f,
                // nothing starred (yet): show only the default set, never all rates
                rates = if (starredCurrencies.isNullOrEmpty())
                    allRates.filter { it.currency in Database.DEFAULT_CURRENCIES }
                else
                    allRates.filter { it.currency in starredCurrencies }
            )
        }
    }

    fun getRows(): LiveData<Rows> {
        return rows
    }

    fun getExchangeRates(): LiveData<ExchangeRates?> {
        return mainViewModel.getExchangeRates()
    }

    fun isUpdating(): LiveData<Boolean> {
        return mainViewModel.isUpdating()
    }

    fun getError(): LiveData<String?> {
        return mainViewModel.getError()
    }

    fun forceUpdateExchangeRate() {
        mainViewModel.forceUpdateExchangeRate()
    }

    /**
     * the persisted base value that all row amounts are derived from (default 1.0).
     * Edited by confirming an amount on the home row
     */
    fun getCurrentBaseValueAsNumber(): LiveData<Double> {
        return Database(getApplication()).getBaseValueAsLiveData()
    }

    fun getStarredCurrencies(): LiveData<Set<Currency>> {
        return mainViewModel.getStarredCurrencies()
    }

    fun toggleCurrencyStar(currency: Currency) {
        mainViewModel.toggleCurrencyStar(currency)
    }

    fun getHistoricalDate(): LocalDate? {
        return mainViewModel.getHistoricalDate()
    }

    fun setHistoricalDate(date: LocalDate?) {
        mainViewModel.setHistoricalDate(date)
    }

}
