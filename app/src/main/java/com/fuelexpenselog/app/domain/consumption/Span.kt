package com.fuelexpenselog.app.domain.consumption

/**
 * A stretch of driving between two consecutive full tanks, which is the only
 * interval over which consumption is actually knowable.
 *
 * VERBATIM from the build plan's reference implementation. Do not "improve"
 * this: it is the part of the app where a wrong answer looks right, and the
 * required unit tests are written against exactly this shape.
 *
 * All quantities are canonical - kilometres and litres.
 */
data class Span(
    val fromOdometer: Double,
    val toOdometer: Double,
    val fuel: Double,
    val cost: Double,
    val endDate: Long
) {
    val distance get() = toOdometer - fromOdometer
    val litresPer100Km get() = fuel / distance * 100
    val kmPerLitre get() = distance / fuel
    val costPerKm get() = cost / distance
}
