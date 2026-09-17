package com.fuelexpenselog.app.domain.consumption

import com.fuelexpenselog.app.domain.model.FillUp

/**
 * Odometer-reset handling, kept strictly outside [computeSpans] so that
 * function stays verbatim.
 *
 * [computeSpans] sorts by odometer rather than date. That is deliberate and
 * correct for two fill-ups on the same day - distance is the quantity being
 * measured, and a mistyped date should not reorder the chain.
 *
 * It does mean an odometer RESET corrupts the chain, though. Given readings
 * 100, 200, 300 and then a replaced odometer reading 0, 50, sorting by odometer
 * yields 0, 50, 100, 200, 300 - and the span 50 -> 100 pairs a post-reset
 * reading with a pre-reset one, charging 50 km of distance with fuel that
 * actually covered 100 km. The figure comes out roughly 2x wrong and entirely
 * plausible-looking.
 *
 * The spec's "odometer reset produces no negative-distance span" test passes
 * regardless, because `f.odometer > a.odometer` blocks only negative distance.
 *
 * So: split the history into segments wherever the odometer drops in DATE
 * order, and run the verbatim engine within each segment.
 */
fun segmentOnOdometerReset(fillUps: List<FillUp>): List<List<FillUp>> {
    if (fillUps.isEmpty()) return emptyList()

    val byDate = fillUps.sortedWith(compareBy({ it.date }, { it.odometer }))
    val segments = mutableListOf<List<FillUp>>()
    var current = mutableListOf<FillUp>()
    var highWater = Double.NEGATIVE_INFINITY

    var i = 0
    while (i < byDate.size) {
        val f = byDate[i]
        if (current.isNotEmpty() && f.odometer < highWater) {
            val next = byDate.getOrNull(i + 1)
            if (next != null && next.odometer >= highWater) {
                // The readings resume above where they already were, so this one
                // entry was mistyped rather than the odometer being replaced -
                // 4820 for 48200, say. Quarantine the bad reading in a segment of
                // its own (a single entry can never form a span) and let the real
                // chain continue uninterrupted.
                //
                // Without this, splitting at the dip alone would pair the typo
                // with the recovery and invent a 44,680 km span.
                segments += listOf(f)
                i++
                continue
            }
            // A genuine reset: replaced unit, rollover, or a vehicle bought
            // second-hand. The chain cannot cross this point.
            segments += current
            current = mutableListOf()
            highWater = Double.NEGATIVE_INFINITY
        }
        current += f
        if (f.odometer > highWater) highWater = f.odometer
        i++
    }
    if (current.isNotEmpty()) segments += current
    return segments
}

/**
 * The entry point every caller should use: segment on resets, then apply the
 * verbatim engine within each segment.
 */
fun computeAllSpans(fillUps: List<FillUp>): List<Span> =
    segmentOnOdometerReset(fillUps).flatMap(::computeSpans)
