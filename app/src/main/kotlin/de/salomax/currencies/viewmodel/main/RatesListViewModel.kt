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
        val rates: List<Rate>
    )

    private val mainViewModel = MainViewModel(app)

    private val rows = object : MediatorLiveData<Rows>() {
        var exchangeRates: ExchangeRates? = null
        var starred: Set<Currency>? = null

        init {
            addSource(mainViewModel.getExchangeRates()) { exchangeRates = it; update() }
            addSource(mainViewModel.getStarredCurrencies()) { starred = it; update() }
        }

        fun update() {
            val allRates = exchangeRates?.rates ?: return
            val starredCurrencies = starred
            value = Rows(
                base = exchangeRates?.base,
                rates = if (starredCurrencies.isNullOrEmpty())
                    allRates
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

    fun getCurrentBaseValueAsNumber(): LiveData<Double> {
        return mainViewModel.getCurrentBaseValueAsNumber()
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
