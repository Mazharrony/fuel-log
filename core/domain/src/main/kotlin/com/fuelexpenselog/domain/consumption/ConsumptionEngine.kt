package com.fuelexpenselog.domain.consumption

import com.fuelexpenselog.domain.money.CurrencySubtotals
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyKind

/**
 * Turns a vehicle's fill-ups into consumption figures.
 *
 * A pure function over its inputs: no Android, no database, no coroutines, no clock. That is
 * not architectural tidiness - it is so the forty-odd scenarios that define correct
 * behaviour can be written as plain data and run in milliseconds, because this is the code
 * where a wrong answer looks right.
 *
 * The one rule everything else follows: **consumption is only knowable between two full
 * tanks.** The naive `volume / (odometer - previousOdometer)` is wrong whenever either
 * fill-up was partial, and wrong in a way that produces a believable number.
 */
object ConsumptionEngine {

    fun compute(
        events: List<FuelEvent>,
        kind: EnergyKind,
        declaredSegments: List<DeclaredSegment> = emptyList(),
        displayUnit: DistanceUnit = DistanceUnit.KILOMETRE,
    ): ConsumptionResult {
        if (events.isEmpty()) return ConsumptionResult.Empty

        val ordered = events.sortedWith(ORDER).map { event ->
            Walked(event, event.odometerM?.plus(offsetAt(event.date, declaredSegments)))
        }

        val analysis = OdometerAnalysis.analyse(ordered, displayUnit)
        val breakIds = analysis.breakBeforeIds + declaredHardBreaks(ordered, declaredSegments)
        val segments = segment(ordered, breakIds)

        val timeline = buildTimeline(segments, kind, analysis.suspectIds)

        return ConsumptionResult(
            timeline = timeline,
            summary = ConsumptionSummary.of(kind, timeline.filterIsInstance<Measured>()),
            proposals = analysis.proposals,
        )
    }

    /**
     * Date first, then odometer.
     *
     * Odometer as the same-day tiebreak is deliberate and matters more than it looks: a CSV
     * import routinely carries a date with no clock time, so every entry on a day shares one
     * timestamp and the reading is the only thing that is actually monotone. Sorting by
     * odometer FIRST, on the other hand, is what lets a reset scramble history - which is
     * why that is the segmenter's job, on a date-ordered list, and not this comparator's.
     */
    private val ORDER: Comparator<FuelEvent> =
        compareBy<FuelEvent> { it.date.value }
            .thenBy(nullsLast()) { it.odometerM }
            .thenBy { it.instantMillis }
            .thenBy { it.id }

    private fun offsetAt(date: CivilDate, declared: List<DeclaredSegment>): Long =
        declared.filter { it.offsetM != null && it.startsAt <= date }.sumOf { it.offsetM!! }

    /** Declared breaks with no offset are hard: the first event on or after the date starts anew. */
    private fun declaredHardBreaks(
        ordered: List<Walked>,
        declared: List<DeclaredSegment>,
    ): Set<Long> = declared
        .filter { it.offsetM == null }
        .mapNotNull { seg -> ordered.firstOrNull { it.event.date >= seg.startsAt }?.event?.id }
        .toSet()

    private fun segment(ordered: List<Walked>, breakIds: Set<Long>): List<List<Walked>> {
        val out = mutableListOf<MutableList<Walked>>()
        var current = mutableListOf<Walked>()

        for (w in ordered) {
            if (w.event.id in breakIds && current.isNotEmpty()) {
                out += current
                current = mutableListOf()
            }
            current += w
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    private fun buildTimeline(
        segments: List<List<Walked>>,
        kind: EnergyKind,
        suspects: Set<Long>,
    ): List<TimelinePoint> {
        val points = mutableListOf<TimelinePoint>()

        for ((segmentIndex, segment) in segments.withIndex()) {
            if (segmentIndex > 0) {
                // The chart has to draw a dash exactly where the chain breaks. A gap that
                // occupies a slot is the only way the UI cannot accidentally draw a smooth
                // line straight through a replaced odometer.
                points += Gap(
                    index = points.size,
                    endDate = segment.first().event.date,
                    reason = GapReason.ODOMETER_RESET,
                    eventIds = listOf(segment.first().event.id),
                )
            }
            appendSegment(points, segment, kind, suspects, isFirstSegment = segmentIndex == 0)
        }

        return points
    }

    private fun appendSegment(
        points: MutableList<TimelinePoint>,
        segment: List<Walked>,
        kind: EnergyKind,
        suspects: Set<Long>,
        isFirstSegment: Boolean,
    ) {
        val ofKind = segment.filter { it.event.energy.kind == kind }
        if (ofKind.isEmpty()) return

        val anchors = segment.indices.filter { isAnchor(segment[it], kind, suspects) }

        if (anchors.isEmpty()) {
            val reason =
                if (ofKind.any { it.event.isFull }) GapReason.MISSING_ODOMETER
                else GapReason.PARTIAL_ONLY
            points += Gap(points.size, ofKind.last().event.date, reason, ofKind.map { it.event.id })
            return
        }

        if (isFirstSegment) {
            // The first full tank is a starting line, not a result: there is no earlier tank
            // to measure from. A bare dash here reads as a bug, so the UI needs the reason
            // ("needs two full tanks") rather than an empty chart.
            points += Gap(
                index = points.size,
                endDate = segment[anchors.first()].event.date,
                reason = GapReason.FIRST_FILL_UP,
                eventIds = listOf(segment[anchors.first()].event.id),
            )
        }

        for (pair in anchors.zipWithNext()) {
            points += spanBetween(segment, pair.first, pair.second, kind, suspects, points.size)
        }

        // Fills after the last full tank have contributed nothing yet. Saying so is more
        // useful than the chart silently ending one entry early.
        val trailing = segment.drop(anchors.last() + 1)
            .filter { it.event.energy.kind == kind && !it.event.energy.isZero }
        if (trailing.isNotEmpty()) {
            points += Gap(
                index = points.size,
                endDate = trailing.last().event.date,
                reason = GapReason.PARTIAL_ONLY,
                eventIds = trailing.map { it.event.id },
            )
        }
    }

    private fun isAnchor(w: Walked, kind: EnergyKind, suspects: Set<Long>): Boolean =
        w.event.isFull &&
            w.event.energy.kind == kind &&
            w.odometerM != null &&
            w.event.id !in suspects

    private fun spanBetween(
        segment: List<Walked>,
        from: Int,
        to: Int,
        kind: EnergyKind,
        suspects: Set<Long>,
        index: Int,
    ): TimelinePoint {
        val a = segment[from]
        val b = segment[to]

        // Strictly AFTER the opening anchor, up to and INCLUDING the closing one.
        //
        // The opening tank's own fuel paid for distance already driven, not distance about
        // to be driven, so it is excluded. The closing tank's fuel is what refills what this
        // stretch burned, so it is included. Getting this backwards is the classic error and
        // it shifts every figure in the app by one fill-up.
        val between = segment.subList(from + 1, to + 1)

        val energyMicro = between.filter { it.event.energy.kind == kind }.sumOf { it.event.energy.micro }
        val otherMicro = between.filter { it.event.energy.kind != kind }.sumOf { it.event.energy.micro }
        val distanceM = b.odometerM!! - a.odometerM!!
        val eventIds = between.map { it.event.id }

        // "A fill-up before this one is missing" invalidates the span that CONTAINS or ENDS
        // at that event, never the one that starts at it: the tank is known to be full at
        // the opening anchor whatever happened earlier, so the forward measurement survives
        // and the chain resumes immediately.
        if (between.any { it.event.missedPrevious }) {
            return Gap(index, b.event.date, GapReason.MISSED_FILL_UP, eventIds)
        }
        if (distanceM <= 0L) {
            return Gap(index, b.event.date, GapReason.ZERO_OR_NEGATIVE_DISTANCE, eventIds)
        }
        if (energyMicro <= 0L) {
            return Gap(index, b.event.date, GapReason.NO_ENERGY_RECORDED, eventIds)
        }

        val skippedAnchors = between.count {
            it.event.isFull && it.event.energy.kind == kind && !isAnchor(it, kind, suspects)
        }

        val costs = between.filter { it.event.energy.kind == kind }.mapNotNull { it.event.cost }

        return Measured(
            index = index,
            endDate = b.event.date,
            startEventId = a.event.id,
            endEventId = b.event.id,
            distanceM = distanceM,
            energy = Energy(kind, energyMicro),
            // Null when the span mixes currencies: this app holds no exchange rates. The
            // consumption figure is unaffected, because litres do not care what you paid.
            cost = CurrencySubtotals.singleCurrencyTotal(costs),
            fillUpsSpanned = between.count { it.event.energy.kind == kind },
            confidence = if (skippedAnchors > 0) Confidence.AVERAGED else Confidence.EXACT,
            otherKindMicro = otherMicro,
        )
    }
}
