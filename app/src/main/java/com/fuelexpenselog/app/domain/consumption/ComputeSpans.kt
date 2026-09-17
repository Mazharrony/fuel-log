package com.fuelexpenselog.app.domain.consumption

import com.fuelexpenselog.app.domain.model.FillUp

/**
 * Reduces a vehicle's fill-ups to the list of valid full-to-full spans.
 * Everything the statistics screen shows derives from this list, so the rules
 * live here and nowhere else.
 *
 * The naive `volume / (odometer - previousOdometer)` is wrong whenever either
 * fill-up was partial: the fuel added does not correspond to the distance
 * travelled. Instead, consumption is measured between two consecutive FULL
 * fill-ups, summing the fuel from every fill-up after the first full one up to
 * and including the second.
 *
 * The anchor's own volume is excluded - it paid for distance already driven,
 * not distance about to be driven.
 *
 * VERBATIM from the build plan's reference implementation. Callers should use
 * [computeAllSpans] instead, which handles odometer resets around it.
 */
fun computeSpans(fillUps: List<FillUp>): List<Span> {
    val sorted = fillUps.sortedWith(compareBy({ it.odometer }, { it.date }))
    val spans = mutableListOf<Span>()
    var anchor: FillUp? = null
    var fuel = 0.0
    var cost = 0.0
    var broken = false
    for (f in sorted) {
        val a = anchor
        if (a == null) {
            // No baseline yet: the first full tank starts the first span.
            if (f.isFullTank) anchor = f
            continue
        }
        // The anchor's own volume is excluded - it paid for distance
        // already driven, not distance about to be driven.
        fuel += f.volume
        cost += f.totalCost
        if (f.isMissedEntry) broken = true
        if (f.isFullTank) {
            if (!broken && f.odometer > a.odometer && fuel > 0.0) {
                spans += Span(a.odometer, f.odometer, fuel, cost, f.date)
            }
            anchor = f
            fuel = 0.0
            cost = 0.0
            broken = false
        }
    }
    return spans
}
