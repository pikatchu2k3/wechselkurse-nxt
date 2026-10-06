package de.salomax.currencies.model.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Regression tests for the historical crypto off-by-one in [YahooFinance].
 *
 * The two fixtures are real chart API responses, recorded for the request "daily bars, five days
 * back, up to and including 2026-10-04" (a Sunday). Both contain a handful of real bars, so these
 * tests need no network - unlike [AssetRatesTest], which is a live test.
 */
class YahooFinanceTest {

    private val requestedDay = LocalDate.of(2026, 10, 4)

    private fun bars(fixture: String): List<YahooFinance.Bar> =
        checkNotNull(javaClass.getResourceAsStream("/$fixture")) { "missing fixture: $fixture" }
            .use { it.readBytes().decodeToString() }
            .let { YahooFinance.parseBars(it) }

    private fun List<YahooFinance.Bar>.closeOn(date: LocalDate): Float =
        single { it.date == date }.close

    @Test
    fun everyBarKeepsItsOwnTradingDay() {
        val bars = bars("yahoo-btc-usd-2026-10-04.json")
        assertEquals(7, bars.size)
        assertEquals(LocalDate.of(2026, 9, 29), bars.first().date)
        assertEquals(LocalDate.of(2026, 10, 5), bars.last().date)
    }

    @Test
    fun cryptoDoesNotUseTheBarAfterTheRequestedDay() {
        val bars = bars("yahoo-btc-usd-2026-10-04.json")
        // the trap: the response really does contain a bar beyond the requested day
        assertTrue(
            "fixture must contain a bar after the requested day",
            bars.any { it.date.isAfter(requestedDay) }
        )

        val chosen = YahooFinance.lastCloseOnOrBefore(bars, requestedDay)

        assertEquals(bars.closeOn(requestedDay), chosen!!, 0f)
        assertTrue("must not be the following day's close", chosen != bars.closeOn(LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun futuresAreUnchangedByTheFix() {
        val bars = bars("yahoo-si-futures-2026-10-04.json")
        assertTrue(
            "futures have no bar after the requested day",
            bars.none { it.date.isAfter(requestedDay) }
        )
        assertEquals(bars.last().close, YahooFinance.lastCloseOnOrBefore(bars, requestedDay)!!, 0f)
    }

    @Test
    fun aTradingDayEarlierInTheWindowIsUsed() {
        val bars = bars("yahoo-btc-usd-2026-10-04.json")
        assertEquals(
            bars.closeOn(LocalDate.of(2026, 10, 3)),
            YahooFinance.lastCloseOnOrBefore(bars, LocalDate.of(2026, 10, 3))!!,
            0f
        )
    }

    @Test
    fun aDateBeforeTheWindowHasNoCloseAtAll() {
        val bars = bars("yahoo-btc-usd-2026-10-04.json")
        assertNull(YahooFinance.lastCloseOnOrBefore(bars, LocalDate.of(2026, 9, 20)))
        assertNull(YahooFinance.lastCloseOnOrBefore(emptyList(), requestedDay))
    }

    @Test
    fun aDayWithoutACloseKeepsItsSlot() {
        // if the null close were dropped, 3.5 would be dated one day too early
        val json = """
            {"chart":{"result":[{"timestamp":[100,200,300],"indicators":{"quote":[{"close":[1.5,null,3.5]}]}}],"error":null}}
        """.trimIndent()
        val bars = YahooFinance.parseBars(json)
        assertEquals(2, bars.size)
        assertEquals(1.5f, bars[0].close, 0f)
        assertEquals(3.5f, bars[1].close, 0f)
        assertEquals(
            Instant.ofEpochSecond(300).atZone(ZoneOffset.UTC).toLocalDate(),
            bars[1].date
        )
    }

    @Test
    fun theOrderOfTheBarsDoesNotMatter() {
        val bars = bars("yahoo-btc-usd-2026-10-04.json")
        assertEquals(
            YahooFinance.lastCloseOnOrBefore(bars, requestedDay),
            YahooFinance.lastCloseOnOrBefore(bars.reversed(), requestedDay)
        )
    }
}
