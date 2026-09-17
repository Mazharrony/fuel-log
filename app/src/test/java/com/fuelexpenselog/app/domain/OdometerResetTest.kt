package com.fuelexpenselog.app.domain

import com.fuelexpenselog.app.domain.consumption.computeAllSpans
import com.fuelexpenselog.app.domain.consumption.computeSpans
import com.fuelexpenselog.app.domain.consumption.segmentOnOdometerReset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The hole the spec's own required test does not catch.
 *
 * computeSpans sorts by odometer, so a reset re-sorts the post-reset readings to
 * the front of the history where they get paired with pre-reset ones. The
 * resulting figure is roughly 2x wrong and looks completely plausible - far more
 * dangerous than an obviously broken number.
 */
class OdometerResetTest {

    private val resetHistory = listOf(
        fill(odometer = 100.0, volume = 40.0, full = true, day = 0),
        fill(odometer = 600.0, volume = 50.0, full = true, day = 7),
        fill(odometer = 1100.0, volume = 50.0, full = true, day = 14),
        // Odometer replaced here - the readings restart.
        fill(odometer = 0.0, volume = 40.0, full = true, day = 21),
        fill(odometer = 500.0, volume = 50.0, full = true, day = 28),
    )

    @Test
    fun `the verbatim engine really is fooled by a reset`() {
        // Documents WHY segmentation exists. Sorted by odometer the history
        // becomes 0, 100, 500, 600, 1100, and 100 -> 500 pairs a post-reset
        // reading with a pre-reset one.
        val naive = computeSpans(resetHistory)

        assertThat(naive.any { it.fromOdometer == 100.0 && it.toOdometer == 500.0 }).isTrue()
    }

    @Test
    fun `segmentation produces spans on both sides of a reset and none across it`() {
        val spans = computeAllSpans(resetHistory)

        assertThat(spans).hasSize(3)
        // Pre-reset chain.
        assertThat(spans[0].fromOdometer).isEqualTo(100.0)
        assertThat(spans[0].toOdometer).isEqualTo(600.0)
        assertThat(spans[1].fromOdometer).isEqualTo(600.0)
        assertThat(spans[1].toOdometer).isEqualTo(1100.0)
        // Post-reset chain, kept entirely separate.
        assertThat(spans[2].fromOdometer).isEqualTo(0.0)
        assertThat(spans[2].toOdometer).isEqualTo(500.0)
        // Nothing bridges the two.
        assertThat(spans.none { it.fromOdometer < 600.0 && it.toOdometer > 1000.0 }).isTrue()
    }

    @Test
    fun `a single mistyped low reading isolates one segment instead of poisoning the series`() {
        val typo = listOf(
            fill(odometer = 48_000.0, volume = 40.0, full = true, day = 0),
            fill(odometer = 48_500.0, volume = 50.0, full = true, day = 7),
            // 4820 typed instead of 48200.
            fill(odometer = 4_820.0, volume = 45.0, full = true, day = 14),
            fill(odometer = 49_500.0, volume = 50.0, full = true, day = 21),
            fill(odometer = 50_000.0, volume = 40.0, full = true, day = 28),
        )

        val spans = computeAllSpans(typo)

        // No span may claim a distance larger than the real history.
        assertThat(spans.none { it.distance > 5_000.0 }).isTrue()
        // The healthy stretch after the typo still produces a figure.
        assertThat(spans.any { it.fromOdometer == 49_500.0 && it.toOdometer == 50_000.0 }).isTrue()
    }

    @Test
    fun `an unbroken history is left as a single segment`() {
        val clean = listOf(
            fill(odometer = 100.0, volume = 40.0, day = 0),
            fill(odometer = 600.0, volume = 50.0, day = 7),
            fill(odometer = 1100.0, volume = 50.0, day = 14),
        )

        assertThat(segmentOnOdometerReset(clean)).hasSize(1)
        assertThat(computeAllSpans(clean)).isEqualTo(computeSpans(clean))
    }

    @Test
    fun `same-day entries are not mistaken for a reset`() {
        // Two fill-ups on one day, logged out of odometer order.
        val sameDay = listOf(
            fill(odometer = 100.0, volume = 40.0, full = true, day = 0),
            fill(odometer = 600.0, volume = 30.0, full = true, day = 5),
            fill(odometer = 400.0, volume = 20.0, full = false, day = 5),
        )

        assertThat(segmentOnOdometerReset(sameDay)).hasSize(1)
    }
}
