package de.salomax.currencies.util

fun calculateDifference(old: Float?, new: Float?): Float? {
    return if (old == null || new == null)
        null
    else {
        val percentage = (new - old) / old * 100
        if (percentage.isFinite())
            percentage
        else
            null
    }
}

/**
 * Decimal places needed to show [significantNumbers] significant digits of this value, e.g.
 * 0.0026 -> 4, 1.5 -> 2.
 *
 * Never throws: NaN/Infinity have no decimal places (and `toBigDecimal()` would throw for them),
 * so they fall back to [significantNumbers].
 */
fun Float.getSignificantDecimalPlaces(significantNumbers: Int = 2): Int {
    if (!this.isFinite() || this >= 0.01) {
        return significantNumbers
    }
    val decimalStr = this.toBigDecimal().stripTrailingZeros().toPlainString()
    val decimalPart = decimalStr.substringAfter('.', "")
    // find leading zeros
    val leadingZeros = decimalPart.takeWhile { it == '0' }.length
    // take x significant numbers after leading zeros
    val significantDigits = decimalPart.drop(leadingZeros).take(significantNumbers).length
    return leadingZeros + significantDigits
}
