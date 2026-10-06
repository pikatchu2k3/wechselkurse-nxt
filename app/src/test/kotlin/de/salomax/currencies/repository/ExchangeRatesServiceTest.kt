package de.salomax.currencies.repository

import com.github.kittinunf.fuel.core.FuelError
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Timeline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.AssumptionViolatedException
import org.junit.Test
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.ZoneId
import javax.net.ssl.SSLException

class ExchangeRatesServiceTest {

    @Test
    fun testFrankfurterApp() = runBlocking {
        // latest
        testWebservice(
            live { ExchangeRatesService.getRates(ApiProvider.FRANKFURTER_APP).get() }, 4
        )
        // timeline
        testTimeline(
            live {
                ExchangeRatesService.getTimeline(
                    ApiProvider.FRANKFURTER_APP,
                    Currency.EUR, Currency.ISK
                ).get()
            }
        )
    }

//    @Test
//    fun testFerEe() = runBlocking {
//        // latest
//        testWebservice(
//            ExchangeRatesService.getRates(ApiProvider.FER_EE).get(), 4
//        )
//        // timeline
//        testTimeline(
//            ExchangeRatesService.getTimeline(
//                ApiProvider.FER_EE,
//                Currency.EUR, Currency.ISK
//            ).get()
//        )
//    }

    @Test
    fun testInforEuro() = runBlocking {
        // latest
        testWebservice(
            live { ExchangeRatesService.getRates(ApiProvider.INFOR_EURO).get() }, 31
        )
        // timeline
        testTimeline(
            live {
                ExchangeRatesService.getTimeline(
                    ApiProvider.INFOR_EURO,
                    Currency.EUR, Currency.ISK
                ).get()
            }
        )
    }

    /*
     * Can't unit test providers where XmlPullParserFactory is used. It's an Android component!
     */

//    @Test
//    fun testBankOfCanada() = runBlocking {
//        // latest
//        testWebservice(
//            ExchangeRatesService.getRates(ApiProvider.BANK_OF_CANADA).get(), 4
//        )
//        // timeline
//        testTimeline(
//            ExchangeRatesService.getTimeline(
//                ApiProvider.BANK_OF_CANADA,
//                Currency.EUR, Currency.CAD
//            ).get()
//        )
//    }
//
//    @Test
//    fun testBankRossii() = runBlocking {
//        // latest
//        testWebservice(
//            ExchangeRatesService.getRates(ApiProvider.BANK_ROSSII).get(), 4
//        )
//        // timeline
//        testTimeline(
//            ExchangeRatesService.getTimeline(
//                ApiProvider.BANK_ROSSII,
//                Currency.EUR, Currency.RUB
//            ).get()
//        )
//    }
//
//    @Test
//    fun testNorgesBank() = runBlocking {
//        // latest
//        testWebservice(
//            ExchangeRatesService.getRates(ApiProvider.NORGES_BANK).get(), 4
//        )
//        // timeline
//        testTimeline(
//            ExchangeRatesService.getTimeline(
//                ApiProvider.NORGES_BANK,
//                Currency.EUR, Currency.NOK
//            ).get()
//        )
//    }

    /**
     * Runs a live call to a keyless provider.
     *
     * A connection failure (no route, DNS, TLS, timeout) skips the test: a CI runner that cannot
     * reach the host says nothing about our code. Deliberately NOT covered are truncated or
     * unparsable answers (EOFException, JsonDataException) - a moved or changed endpoint shows up
     * exactly like that. Skipping those would hide a real outage, which is how the Frankfurter
     * host change was found.
     */
    private suspend fun <T> live(block: suspend () -> T): T = try {
        block()
    } catch (e: FuelError) {
        if (e.isConnectionFailure()) throw AssumptionViolatedException("provider not reachable: ${e.message}")
        throw e
    }

    private fun Throwable.isConnectionFailure(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            when (cause) {
                is UnknownHostException,
                is ConnectException,
                is SocketTimeoutException,
                is NoRouteToHostException,
                is SSLException -> return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun testWebservice(rates: ExchangeRates?, maxAge: Long) {
        // see there is some valid data
        assertNotNull(rates)

        // see that there is a list of exchange rates
        assertNotNull(rates!!.rates)

        // check for some currencies
        val eur = rates.rates!!.find { rate -> rate.currency == Currency.EUR }
        assertTrue(eur != null)
        println(eur)

        val usd = rates.rates!!.find { rate -> rate.currency == Currency.USD }
        assertTrue(usd != null)
        println(usd)

        val jpy = rates.rates!!.find { rate -> rate.currency == Currency.JPY }
        assertTrue(jpy != null)
        println(jpy)

        val krw = rates.rates!!.find { rate -> rate.currency == Currency.KRW }
        assertTrue(krw != null)
        println(krw)

        val chf = rates.rates!!.find { rate -> rate.currency == Currency.CHF }
        assertTrue(chf != null)
        println(chf)

        // check if the date is current
        rates.date?.let {
            assertTrue(it >= LocalDate.now(ZoneId.of("UTC")).minusDays(maxAge))
            println(rates.date)
        }
    }

    private fun testTimeline(data: Timeline) {
        assertTrue(data.rates != null)
        assertTrue(data.rates!!.isNotEmpty())
    }

}
