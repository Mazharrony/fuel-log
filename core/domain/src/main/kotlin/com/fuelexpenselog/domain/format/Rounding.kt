package com.fuelexpenselog.domain.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Rounding happens once, at the point of display.
 *
 * Never round an intermediate. Summing a few hundred Doubles legitimately produces
 * 4549.999999999999, and rounding on the way through turns that into a drift users notice
 * when the list and its total disagree by a penny.
 */
object Rounding {

    fun to(value: Double, decimals: Int): Double {
        if (!value.isFinite()) return value
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * For CSV and any other machine-readable output.
     *
     * `String.format("%.2f")` under a comma-decimal locale emits "4550,00", which corrupts
     * the file for everyone who opens it afterwards - including the accountant it was
     * exported for. This is locale-independent by construction.
     */
    fun toPlainString(value: Double, decimals: Int): String {
        if (!value.isFinite()) return ""
        return BigDecimal.valueOf(value)
            .setScale(decimals, RoundingMode.HALF_UP)
            .toPlainString()
    }

    /** Display formatting, where the user's locale IS what should decide the separator. */
    fun toLocalisedString(value: Double, decimals: Int, locale: Locale): String {
        if (!value.isFinite()) return EM_DASH
        return String.format(locale, "%,.${decimals}f", to(value, decimals))
    }

    /** What the app shows when it does not know. A zero would be a lie. */
    const val EM_DASH = "\u2014"
}

fun Double.finiteOrNull(): Double? = takeIf { it.isFinite() }
