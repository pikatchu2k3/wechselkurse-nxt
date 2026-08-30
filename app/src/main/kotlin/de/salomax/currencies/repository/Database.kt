package de.salomax.currencies.repository

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.util.*
import java.time.LocalDate

class Database(context: Context) {

    companion object {
        /**
         * the currencies the list starts with on first launch, before the user adds their own
         * (also seeded into the starred set, see [seedDefaultStars])
         */
        val DEFAULT_CURRENCIES: Set<Currency> = setOf(Currency.EUR, Currency.USD)
    }

    /*
     * current exchange rates from api =============================================================
     */
    private val prefsRates: SharedPreferences = context.getSharedPreferences("rates", MODE_PRIVATE)

    private val keyDate = "_date"
    private val keyBaseRate = "_base"
    private val keyProvider = "_provider"

    fun insertExchangeRates(items: ExchangeRates) {
        // don't insert null-values. this would clear the cache
        if (items.date != null)
            prefsRates.apply {
                val editor = edit()
                // clear old values
                editor.clear()
                // apply new ones
                editor.putString(keyDate, items.date.toString())
                editor.putString(keyBaseRate, items.base?.iso4217Alpha())
                editor.putInt(keyProvider, items.provider?.id ?: -1)
                items.rates?.forEach { rate ->
                    editor.putFloat(rate.currency.iso4217Alpha(), rate.value)
                }
                // persist
                editor.apply()
            }
    }

    fun getExchangeRates(): LiveData<ExchangeRates?> {
        return SharedPreferenceExchangeRatesLiveData(prefsRates)
    }

    fun getDate(): LocalDate? {
        return prefsRates.getString(keyDate, null)?.let { LocalDate.parse(it) }
    }

    /**
     * the home currency of the rates list.
     * Always EUR: the rate-source base every stored rate value relates to
     * ("1 EUR = X units") - regardless of what the (possibly stale) cached
     * snapshot's "_base" string says
     */
    fun getHomeCurrency(): Currency? {
        return Currency.EUR
    }

    /*
     * last state ==================================================================================
     */
    private val prefsLastState: SharedPreferences = context.getSharedPreferences("last_state", MODE_PRIVATE)

    private val keyLastStateFrom = "_last_from"
    private val keyLastStateTo = "_last_to"
    private val keyIsUpdating = "_isUpdating"
    private val keyHistoricalDate = "_historical_date"

    fun saveLastUsedRates(from: Currency?, to: Currency?) {
        prefsLastState.apply {
            from?.let { edit().putString(keyLastStateFrom, it.iso4217Alpha()).apply() }
            to?.let { edit().putString(keyLastStateTo, it.iso4217Alpha()).apply() }
        }
    }

    fun getLastBaseCurrency(): LiveData<Currency?> {
        return SharedPreferenceStringLiveData(prefsLastState, keyLastStateFrom, "USD")
            .map { Currency.fromString(it!!) }
    }

    fun getLastDestinationCurrency(): LiveData<Currency?> {
        return SharedPreferenceStringLiveData(prefsLastState, keyLastStateTo, "EUR")
            .map { Currency.fromString(it!!) }
    }

    fun setUpdating(updating: Boolean) {
        prefsLastState.edit().putBoolean(keyIsUpdating, updating).apply()
    }

    fun isUpdating(): SharedPreferenceBooleanLiveData {
        return SharedPreferenceBooleanLiveData(prefsLastState, keyIsUpdating, false)
    }

    fun setHistoricalDate(date: LocalDate?) {
        prefsLastState.edit().putLong(keyHistoricalDate, date?.toMillis() ?: -1).apply()
    }

    fun getHistoricalLiveDate(): LiveData<LocalDate?> {
        return SharedPreferenceLongLiveData(prefsLastState, keyHistoricalDate, -1).map {
            if (it == -1L) null
            else it.toLocalDate()
        }
    }

    fun getHistoricalDate(): LocalDate? {
        return when (val date = prefsLastState.getLong(keyHistoricalDate, -1)) {
            -1L -> null
            else -> date.toLocalDate()
        }
    }

    /*
     * starred currencies ==========================================================================
     */
    private val prefsStarredCurrencies: SharedPreferences = context.getSharedPreferences("starred_currencies", MODE_PRIVATE)

    private val keyStars = "_stars"
    private val keyStarredEnabled = "_starredActive"
    private val keyStarsInitialized = "_starsInitialized"

    /**
     * first launch: pre-select the default currency set (EUR + USD), so the list starts small
     * and everything else can be added via the add-currency flow.
     * Runs only once: an empty starred set afterwards is the user's deliberate choice.
     */
    fun seedDefaultStars() {
        if (prefsStarredCurrencies.getBoolean(keyStarsInitialized, false)) return
        val editor = prefsStarredCurrencies.edit()
        editor.putBoolean(keyStarsInitialized, true)
        // only seed if the user has never chosen any stars
        if (prefsStarredCurrencies.getStringSet(keyStars, HashSet<String>())!!.isEmpty())
            editor.putStringSet(keyStars, DEFAULT_CURRENCIES.map { it.iso4217Alpha() }.toSet())
        editor.apply()
    }

    fun toggleCurrencyStar(currency: Currency) {
        prefsStarredCurrencies.apply {
            if (prefsStarredCurrencies.getStringSet(keyStars, HashSet<String>())!!.contains(currency.iso4217Alpha()))
                removeCurrencyStar(currency)
            else
                starCurrency(currency)
        }
    }

    fun getStarredCurrencies(): LiveData<Set<Currency>> {
        return SharedPreferenceStringSetLiveData(prefsStarredCurrencies, keyStars, HashSet())
            .map { set ->
                set.mapNotNull { code ->
                    Currency.fromString(code)
                }.toSet()
            }
    }

    private fun starCurrency(currency: Currency) {
        prefsStarredCurrencies.apply {
            edit().putStringSet(keyStars,
                prefsStarredCurrencies.getStringSet(keyStars, HashSet<String>())!!
                    .plus(currency.iso4217Alpha())
            ).apply()
        }
    }

    private fun removeCurrencyStar(currency: Currency) {
        prefsStarredCurrencies.apply {
            edit().putStringSet(keyStars,
                prefsStarredCurrencies.getStringSet(keyStars, HashSet<String>())!!
                    .minus(currency.iso4217Alpha())
            ).apply()
        }
    }

    fun isFilterStarredEnabled(): SharedPreferenceBooleanLiveData {
        return SharedPreferenceBooleanLiveData(prefsStarredCurrencies, keyStarredEnabled, false)
    }

    fun toggleStarredActive() {
        prefsStarredCurrencies.apply {
            edit().putBoolean(keyStarredEnabled,
                prefsStarredCurrencies.getBoolean(keyStarredEnabled, false).not()
            ).apply()
        }
    }

    /*
     * edited amounts ==============================================================================
     */
    private val prefsEditedAmounts: SharedPreferences = context.getSharedPreferences("edited_amounts", MODE_PRIVATE)

    /**
     * the amount for a currency, as manually edited via the "change amount" screen
     * (null removes the edited amount)
     */
    fun setEditedAmount(currency: Currency, amount: Double?) {
        val key = "edited_${currency.iso4217Alpha()}"
        if (amount == null)
            prefsEditedAmounts.edit().remove(key).apply()
        else
            prefsEditedAmounts.edit().putString(key, amount.toString()).apply()
    }

    fun getEditedAmount(currency: Currency): Double? {
        return prefsEditedAmounts.getString("edited_${currency.iso4217Alpha()}", null)?.toDoubleOrNull()
    }

    /*
     * base value ==================================================================================
     */
    private val keyBaseValue = "_base_value"

    /**
     * the amount of the home currency that all row amounts of the rates list are derived from.
     * Edited by confirming an amount on the home row (null removes it -> default 1.0)
     */
    fun setBaseValue(value: Double?) {
        if (value == null)
            prefsLastState.edit().remove(keyBaseValue).apply()
        else
            prefsLastState.edit().putString(keyBaseValue, value.toString()).apply()
    }

    fun getBaseValue(): Double? {
        return prefsLastState.getString(keyBaseValue, null)?.toDoubleOrNull()
    }

    fun getBaseValueAsLiveData(): LiveData<Double> {
        return SharedPreferenceStringLiveData(prefsLastState, keyBaseValue, "1.0").map {
            it?.toDoubleOrNull() ?: 1.0
        }
    }

    /*
     * preferences =================================================================================
     */
    private val prefs: SharedPreferences = context.getSharedPreferences("prefs", MODE_PRIVATE)

    private val keyApi = "_api"
    private val keyOpenExchangeratesApiKey = "_api_openExchangeratesApiKey"
    private val keyBrentApiKey = "_api_brentApiKey"
    private val keyTheme = "_theme"
    private val keyPureBlackEnabled = "_pureBlackEnabled"
    private val keyFeeEnabled = "_feeEnabled"
    private val keyFeeValue = "_fee"
    private val keyPreviewConversionEnabled = "_previewConversionEnabled"
    private val keyExtendedKeypadEnabled = "_extendedKeypadEnabled"

    /* api */

    fun setApiProvider(api: ApiProvider) {
        prefs.apply {
            edit().putInt(keyApi, api.id).apply()
        }
    }

    fun getApiProvider(): ApiProvider {
        return ApiProvider.fromId(prefs.getInt(keyApi, -1))
    }

    fun getApiProviderAsync(): LiveData<ApiProvider> {
        return SharedPreferenceIntLiveData(prefs, keyApi, -1).map {
            ApiProvider.fromId(it)
        }
    }

    fun setOpenExchangeRatesApiKey(id: String?) {
        prefs.apply {
            edit().putString(keyOpenExchangeratesApiKey, id).apply()
        }
    }

    fun getOpenExchangeRatesApiKey(): String? {
        return prefs.getString(keyOpenExchangeratesApiKey, null)
    }

    fun getOpenExchangeRatesApiKeyAsync(): LiveData<String?> {
        return SharedPreferenceStringLiveData(prefs, keyOpenExchangeratesApiKey, null)
    }

    fun setBrentApiKey(id: String?) {
        prefs.apply {
            edit().putString(keyBrentApiKey, id).apply()
        }
    }

    fun getBrentApiKey(): String? {
        return prefs.getString(keyBrentApiKey, null)
    }

    /* theme */

    fun setTheme(theme: Int) {
        prefs.apply {
            edit().putInt(keyTheme, theme).apply()
        }
    }

    /**
     * 0 = MODE_NIGHT_NO
     * 1 = MODE_NIGHT_YES
     * 2 = MODE_NIGHT_FOLLOW_SYSTEM
     */
    fun getTheme(): Int {
        return prefs.getInt("_theme", 2)
    }

    fun setPureBlackEnabled(enabled: Boolean) {
        prefs.apply {
            edit().putBoolean(keyPureBlackEnabled, enabled).apply()
        }
    }

    fun isPureBlackEnabled(): Boolean {
        return prefs.getBoolean(keyPureBlackEnabled, false)
    }

    /* fee */

    fun setFeeEnabled(enabled: Boolean) {
        prefs.apply {
            edit().putBoolean(keyFeeEnabled, enabled).apply()
        }
    }

    fun isFeeEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyFeeEnabled, false)
    }

    fun setFee(fee: Float) {
        prefs.apply {
            edit().putFloat(keyFeeValue, fee).apply()
        }
    }

    fun getFee(): LiveData<Float> {
        return SharedPreferenceFloatLiveData(prefs, keyFeeValue, 2.2f)
    }

    /* preview conversion */

    fun setPreviewConversionEnabled(enabled: Boolean) {
        prefs.apply {
            edit().putBoolean(keyPreviewConversionEnabled, enabled).apply()
        }
    }

    fun isPreviewConversionEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyPreviewConversionEnabled, false)
    }

    /* extended keypad */

    fun setExtendedKeypadEnabled(enabled: Boolean) {
        prefs.apply {
            edit().putBoolean(keyExtendedKeypadEnabled, enabled).apply()
        }
    }

    fun isExtendedKeypadEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, keyExtendedKeypadEnabled, false)
    }

}
