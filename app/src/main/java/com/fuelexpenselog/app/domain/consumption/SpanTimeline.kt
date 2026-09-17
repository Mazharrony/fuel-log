package com.fuelexpenselog.app.domain.consumption

import com.fuelexpenselog.app.domain.model.FillUp

enum class GapReason {
    /** The user flagged a skipped fill-up, so the chain is broken. */
    MISSED_ENTRY,

    /** The odometer did not advance between the two full tanks. */
    NO_DISTANCE,

    /** No fuel was recorded between the two full tanks. */
    NO_FUEL,
}

sealed interface SpanSlot {
    data class Filled(val span: Span) : SpanSlot
    data class Gap(val reason: GapReason, val endOdometer: Double, val endDate: Long) : SpanSlot
}

/**
 * The same walk as [computeSpans], but recording WHY a span was skipped instead
 * of dropping it silently.
 *
 * The statistics chart has to draw a dash exactly where the chain breaks - "a
 * dash where the chain breaks instead of a wrong number" is the product promise
 * - and [computeSpans] throws that information away.
 *
 * These two functions must never drift apart. SpanTimelineTest pins them
 * together over every fixture:
 *
 *     timeline.filterIsInstance<Filled>().map { it.span } == computeSpans(x)
 */
fun computeSpanTimeline(fillUps: List<FillUp>): List<SpanSlot> {
    val sorted = fillUps.sortedWith(compareBy({ it.odometer }, { it.date }))
    val slots = mutableListOf<SpanSlot>()
    var anchor: FillUp? = null
    var fuel = 0.0
    var cost = 0.0
    var broken = false
    for (f in sorted) {
        val a = anchor
        if (a == null) {
            if (f.isFullTank) anchor = f
            continue
        }
        fuel += f.volume
        cost += f.totalCost
        if (f.isMissedEntry) broken = true
        if (f.isFullTank) {
            if (!broken && f.odometer > a.odometer && fuel > 0.0) {
                slots += SpanSlot.Filled(Span(a.odometer, f.odometer, fuel, cost, f.date))
            } else {
                val reason = when {
                    broken -> GapReason.MISSED_ENTRY
                    f.odometer <= a.odometer -> GapReason.NO_DISTANCE
                    else -> GapReason.NO_FUEL
                }
                slots += SpanSlot.Gap(reason, f.odometer, f.date)
            }
            anchor = f
            fuel = 0.0
            cost = 0.0
            broken = false
        }
    }
    return slots
}

/** Segment-aware timeline, matching [computeAllSpans]. */
fun computeAllSpanTimeline(fillUps: List<FillUp>): List<SpanSlot> =
    segmentOnOdometerReset(fillUps).flatMap(::computeSpanTimeline)
