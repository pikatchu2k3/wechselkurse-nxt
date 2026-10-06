package de.salomax.currencies.model.provider

import android.content.Context
import android.content.SharedPreferences
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDate

/**
 * Live test against the real crypto / metal / commodity sources - like [ExchangeRatesServiceTest]
 * it needs network access. If the sources can't be reached at all, the tests are skipped instead
 * of failing.
 */
class AssetRatesTest {

    private val context: Context = mock(Context::class.java).also {
        `when`(it.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
    }

    // EUR based snapshot with a fixed USD rate
    private fun snapshot(eur: Float = 1f, usd: Float = 1.1f, extra: List<Rate> = emptyList()) = ExchangeRates(
        success = true,
        error = null,
        base = Currency.EUR,
        date = LocalDate.now(),
        rates = listOf(Rate(Currency.EUR, eur), Rate(Currency.USD, usd)) + extra
    )

    private fun fetchOrSkip(fiat: ExchangeRates, date: LocalDate? = null): Map<Currency, Float> {
        val rates = runBlocking { AssetRates.fetch(context, fiat, date) }.associate { it.currency to it.value }
        assumeTrue("asset sources not reachable", rates.isNotEmpty())
        return rates
    }

    /**
     * Live fetch of every configured asset, retried once.
     *
     * AssetRates swallows a failing source by design, so an incomplete answer cannot be told apart
     * from an unreachable source - and this runs against keyless third-party APIs from a CI runner.
     * If the second attempt is still incomplete the test is skipped instead of failed; that the
     * source table itself covers every asset is guarded offline by [everyAssetCurrencyHasASource].
     */
    private fun fetchAllAssetsOrSkip(date: LocalDate? = null): Map<Currency, Float> {
        val expected = AssetRates.configuredAssets()
        var rates = fetchOrSkip(snapshot(), date)
        if (rates.keys.size < expected.size) {
            rates = fetchOrSkip(snapshot(), date)
            assumeTrue(
                "asset sources answered incompletely: ${expected - rates.keys}",
                rates.keys.size >= expected.size
            )
        }
        return rates
    }

    @Test
    fun everyAssetCurrencyHasASource() {
        // offline guard: the live coverage tests above may skip when a source is unreachable
        assertEquals(
            Currency.entries.filter { it.isAsset() }.toSet(),
            AssetRates.configuredAssets()
        )
    }

    @Test
    fun latestCoversEveryAsset() {
        val rates = fetchAllAssetsOrSkip()
        assertEquals(AssetRates.configuredAssets().size, rates.size)
        // sanity: everything positive and finite
        rates.forEach { (currency, value) -> assertTrue("$currency = $value", value > 0f && value.isFinite()) }
        // sanity: per 1 EUR - a coin is worth far more than 1 EUR, an ounce of gold as well
        assertTrue(rates.getValue(Currency.BTC) < 0.001f)
        assertTrue(rates.getValue(Currency.XAU) < 0.01f)
        // cheap things: more than one unit per EUR
        assertTrue(rates.getValue(Currency.XRP) > 0.1f)
    }

    @Test
    fun cents_quotedFuturesAreConvertedToDollars() {
        val rates = fetchOrSkip(snapshot(usd = 1f))
        // with USD as 1.0: value = units per USD. wheat costs 5 - 15 USD per bushel, not 500 - 1500
        assertTrue("wheat ${rates[Currency.XWH]}", rates.getValue(Currency.XWH) in 0.05f..0.5f)
        // coffee costs 1 - 8 USD per lb
        assertTrue("coffee ${rates[Currency.XCF]}", rates.getValue(Currency.XCF) in 0.1f..1.5f)
    }

    @Test
    fun resultIsScaledToTheSnapshotsBase() {
        val eurBased = fetchOrSkip(snapshot())
        // same market data, but on a base that's worth 0.01 EUR (like BankRossii's RUB scale):
        // there are 100x fewer units per base unit
        val scaled = fetchOrSkip(snapshot(eur = 0.01f, usd = 0.011f))
        listOf(Currency.BTC, Currency.ETH, Currency.XAU, Currency.XBZ).forEach {
            val ratio = scaled.getValue(it) / eurBased.getValue(it)
            assertEquals("$it", 0.01f, ratio, 0.002f)
        }
    }

    @Test
    fun providerSuppliedValuesWin() {
        val rates = fetchOrSkip(snapshot(extra = listOf(Rate(Currency.XAU, 123f))))
        assertTrue(Currency.XAU !in rates)
        assertTrue(Currency.XAG in rates)
    }

    @Test
    fun historicalDateUsesYahoo() {
        val rates = fetchAllAssetsOrSkip(LocalDate.now().minusDays(30))
        assertTrue(rates.getValue(Currency.BTC) < 0.001f)
        assertTrue(Currency.XAG in rates)
        // crypto + metals + commodities all resolved historically
        assertEquals(AssetRates.configuredAssets().size, rates.size)
    }
}
