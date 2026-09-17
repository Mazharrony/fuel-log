package com.fuelexpenselog.app.domain.units

import com.fuelexpenselog.app.domain.consumption.Span

/**
 * How consumption is shown. This is the one app-level unit setting - everything
 * else is per vehicle - because users in different markets expect different
 * conventions for the same underlying number.
 *
 * [lowerIsBetter] is not cosmetic. L/100km falls as efficiency rises while the
 * other three climb, so the month-on-month delta arrow and its improved/worsened
 * colour invert on it. Getting this backwards is the classic bug in this
 * category of app.
 */
enum class ConsumptionConvention(val lowerIsBetter: Boolean) {
    L_PER_100KM(lowerIsBetter = true),
    KM_PER_L(lowerIsBetter = false),
    MPG_US(lowerIsBetter = false),
    MPG_UK(lowerIsBetter = false);

    /**
     * Derived from the conversion constants rather than the usual 235.21 / 282.48
     * magic numbers, so there is exactly one place a unit can be wrong.
     */
    fun fromKmPerLitre(kmPerLitre: Double): Double = when (this) {
        L_PER_100KM -> 100.0 / kmPerLitre
        KM_PER_L -> kmPerLitre
        MPG_US -> kmPerLitre * Units.LITRES_PER_US_GALLON / Units.KM_PER_MILE
        MPG_UK -> kmPerLitre * Units.LITRES_PER_UK_GALLON / Units.KM_PER_MILE
    }

    fun toKmPerLitre(value: Double): Double = when (this) {
        L_PER_100KM -> 100.0 / value
        KM_PER_L -> value
        MPG_US -> value * Units.KM_PER_MILE / Units.LITRES_PER_US_GALLON
        MPG_UK -> value * Units.KM_PER_MILE / Units.LITRES_PER_UK_GALLON
    }

    /** Null when the figure is not meaningful, which the UI renders as a dash. */
    fun of(span: Span): Double? {
        val kmPerLitre = span.kmPerLitre
        if (!kmPerLitre.isFinite() || kmPerLitre <= 0.0) return null
        return fromKmPerLitre(kmPerLitre).takeIf { it.isFinite() }
    }
}

/**
 * A figure outside this range is almost certainly a typo. It is still shown -
 * suppressing a number reads as a bug, whereas flagging one reads as the app
 * paying attention - just with a marker beside it.
 */
object Plausibility {
    const val MIN_KM_PER_LITRE = 1.0
    const val MAX_KM_PER_LITRE = 100.0

    fun isImplausible(kmPerLitre: Double): Boolean =
        !kmPerLitre.isFinite() || kmPerLitre < MIN_KM_PER_LITRE || kmPerLitre > MAX_KM_PER_LITRE
}
