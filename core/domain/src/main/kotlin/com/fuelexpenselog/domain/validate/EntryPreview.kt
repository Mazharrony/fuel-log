package com.fuelexpenselog.domain.validate

import com.fuelexpenselog.domain.consumption.ConsumptionEngine
import com.fuelexpenselog.domain.consumption.DeclaredSegment
import com.fuelexpenselog.domain.consumption.FuelEvent
import com.fuelexpenselog.domain.consumption.Gap
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.consumption.TimelinePoint
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyKind

/**
 * What a fill-up being typed would produce, from the real engine.
 *
 * The preview runs [ConsumptionEngine.compute] over the saved events plus the draft on every
 * keystroke, so the figure shown while typing can never disagree with the figure that
 * appears after saving. The tempting shortcut - `(odometer - previous) / volume` - is the
 * naive formula the engine exists to replace: it ignores partial fills, missed fill-ups and
 * declared segments, and it would count an expense's reading as a tank.
 */
object EntryPreview {

    /** The id a draft carries while it has none of its own. */
    const val DRAFT_ID = -1L

    /**
     * The timeline slot the draft ends up in: a [Measured] span ending at it, a [Gap] that
     * explains why it has no figure, or null when there is nothing to say yet.
     *
     * [existing] may contain the row being edited; it is replaced by [draft], never counted
     * twice.
     */
    fun fillUp(
        existing: List<FuelEvent>,
        draft: FuelEvent,
        kind: EnergyKind,
        displayUnit: DistanceUnit,
        declaredSegments: List<DeclaredSegment> = emptyList(),
        replacingId: Long = draft.id,
    ): TimelinePoint? {
        val events = existing.filter { it.id != replacingId && it.id != draft.id } + draft
        val timeline = ConsumptionEngine.compute(events, kind, declaredSegments, displayUnit).timeline
        return timeline.lastOrNull { point ->
            when (point) {
                is Measured -> point.endEventId == draft.id
                is Gap -> draft.id in point.eventIds
            }
        }
    }
}
