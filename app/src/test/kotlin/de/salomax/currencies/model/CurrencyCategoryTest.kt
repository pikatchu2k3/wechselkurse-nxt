package de.salomax.currencies.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyCategoryTest {

    @Test
    fun fiatIsTheDefaultCategory() {
        assertEquals(AssetCategory.FIAT, Currency.EUR.category())
        assertEquals(AssetCategory.FIAT, Currency.USD.category())
        assertTrue(!Currency.CHF.isAsset())
    }

    @Test
    fun assetsAreCategorized() {
        assertEquals(AssetCategory.CRYPTO, Currency.BTC.category())
        assertEquals(AssetCategory.CRYPTO, Currency.ETH.category())
        assertEquals(AssetCategory.METAL, Currency.XAU.category())
        assertEquals(AssetCategory.METAL, Currency.XAG.category())
        assertEquals(AssetCategory.METAL, Currency.XPT.category())
        assertEquals(AssetCategory.METAL, Currency.XPD.category())
        assertEquals(AssetCategory.COMMODITY, Currency.XBZ.category())
        assertEquals(AssetCategory.COMMODITY, Currency.XWT.category())
        assertTrue(Currency.XNG.isAsset())
    }

    @Test
    fun platinumAndPalladiumAreNoLongerFilteredOut() {
        assertEquals(Currency.XPT, Currency.fromString("XPT"))
        assertEquals(Currency.XPD, Currency.fromString("XPD"))
    }

    @Test
    fun supersededAndSpecialCodesAreStillFilteredOut() {
        assertNull(Currency.fromString("XDR"))
        assertNull(Currency.fromString("MRO"))
        assertNotNull(Currency.fromString("BTC"))
    }

    @Test
    fun everyMetalAndCommodityHasAUnitLabel() {
        Currency.entries
            .filter { it.category() == AssetCategory.METAL || it.category() == AssetCategory.COMMODITY }
            .forEach { assertNotNull("${it.iso4217Alpha()} needs a unit", it.unitLabel()) }
    }

    @Test
    fun codesAreUnique() {
        val codes = Currency.entries.map { it.iso4217Alpha() }
        assertEquals(codes.size, codes.toSet().size)
    }
}
