package com.fuelexpenselog.app.domain.format

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rounding happens once, at the point of display. Never round intermediates:
 * summing a few hundred Doubles legitimately produces 4549.999999999999, and
 * rounding on the way through turns that into a drift users notice.
 */
object Rounding {
    fun to(value: Double, decimals: Int): Double =
        if (!value.isFinite()) value
        else BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toDouble()

    /**
     * Writes a number for a CSV file or any other machine-readable output.
     *
     * String.format("%.2f") under a comma-decimal locale emits "4550,00", which
     * corrupts the file for everyone who opens it afterwards. BigDecimal's
     * toPlainString is locale-independent by construction.
     */
    fun toPlainString(value: Double, decimals: Int): String =
        BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
}

/** Guards a computed figure before it reaches the UI. */
fun Double.finiteOrNull(): Double? = if (isFinite()) this else null
