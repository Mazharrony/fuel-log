package com.fuelexpenselog.app.domain

import com.fuelexpenselog.app.domain.consumption.GapReason
import com.fuelexpenselog.app.domain.consumption.SpanSlot
import com.fuelexpenselog.app.domain.consumption.computeSpanTimeline
import com.fuelexpenselog.app.domain.consumption.computeSpans
import com.fuelexpenselog.app.domain.model.FillUp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class SpanTimelineTest {

    private val fixtures: List<Pair<String, List<FillUp>>> = listOf(
        "empty" to emptyList(),
        "single" to listOf(fill(100.0, 40.0)),
        "two full" to listOf(fill(100.0, 40.0, day = 0), fill(600.0, 50.0, day = 7)),
        "full partial full" to listOf(
            fill(100.0, 40.0, full = true, day = 0),
            fill(300.0, 20.0, full = false, day = 3),
            fill(600.0, 30.0, full = true, day = 7),
        ),
        "missed entry" to listOf(
            fill(100.0, 40.0, full = true, day = 0),
            fill(300.0, 20.0, full = false, day = 3, missed = true),
            fill(600.0, 30.0, full = true, day = 7),
            fill(900.0, 45.0, full = true, day = 14),
        ),
        "duplicate odometer" to listOf(fill(100.0, 40.0, day = 0), fill(100.0, 30.0, day = 1)),
        "zero volume between fulls" to listOf(
            fill(100.0, 40.0, full = true, day = 0),
            fill(600.0, 0.0, full = true, day = 7),
        ),
    )

    /**
     * The timeline is a second implementation of the same walk, so it can drift
     * from the engine. This pins them together over every fixture: whatever the
     * timeline marks as Filled must be exactly what the engine returns.
     */
    @Test
    fun `timeline filled slots always match the engine exactly`() {
        for ((name, fillUps) in fixtures) {
            val fromTimeline = computeSpanTimeline(fillUps)
                .filterIsInstance<SpanSlot.Filled>()
                .map { it.span }
            assertWithMessage(name).that(fromTimeline).isEqualTo(computeSpans(fillUps))
        }
    }

    @Test
    fun `a missed entry is recorded as a gap rather than dropped`() {
        val slots = computeSpanTimeline(fixtures.first { it.first == "missed entry" }.second)

        assertThat(slots).hasSize(2)
        val gap = slots[0] as SpanSlot.Gap
        assertThat(gap.reason).isEqualTo(GapReason.MISSED_ENTRY)
        assertThat(gap.endOdometer).isEqualTo(600.0)
        assertThat(slots[1]).isInstanceOf(SpanSlot.Filled::class.java)
    }

    @Test
    fun `a repeated odometer reading is reported as no distance`() {
        val slots = computeSpanTimeline(fixtures.first { it.first == "duplicate odometer" }.second)

        assertThat(slots).hasSize(1)
        assertThat((slots[0] as SpanSlot.Gap).reason).isEqualTo(GapReason.NO_DISTANCE)
    }

    @Test
    fun `two full tanks with no fuel between them are reported as no fuel`() {
        val slots = computeSpanTimeline(fixtures.first { it.first == "zero volume between fulls" }.second)

        assertThat(slots).hasSize(1)
        assertThat((slots[0] as SpanSlot.Gap).reason).isEqualTo(GapReason.NO_FUEL)
    }
}
