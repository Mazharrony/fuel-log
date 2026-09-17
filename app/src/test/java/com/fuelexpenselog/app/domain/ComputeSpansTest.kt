package com.fuelexpenselog.app.domain

import com.fuelexpenselog.app.domain.consumption.computeSpans
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The eight cases the build plan requires before any UI exists. Competitors
 * visibly fail several of these, and getting them wrong produces "this app's
 * numbers are wrong" reviews that never wash out.
 */
class ComputeSpansTest {

    @Test
    fun `single fill-up produces no spans`() {
        assertThat(computeSpans(listOf(fill(odometer = 100.0, volume = 40.0)))).isEmpty()
    }

    @Test
    fun `two full tanks produce one span with the anchor volume excluded`() {
        val spans = computeSpans(
            listOf(
                fill(odometer = 100.0, volume = 40.0, day = 0),
                fill(odometer = 600.0, volume = 50.0, day = 7),
            )
        )

        assertThat(spans).hasSize(1)
        assertThat(spans[0].distance).isEqualTo(500.0)
        // 50, not 90: the anchor's fuel paid for distance already driven.
        assertThat(spans[0].fuel).isEqualTo(50.0)
        assertThat(spans[0].kmPerLitre).isEqualTo(10.0)
    }

    @Test
    fun `full partial full sums both volumes after the anchor`() {
        val spans = computeSpans(
            listOf(
                fill(odometer = 100.0, volume = 40.0, full = true, day = 0),
                fill(odometer = 300.0, volume = 20.0, full = false, day = 3),
                fill(odometer = 600.0, volume = 30.0, full = true, day = 7),
            )
        )

        assertThat(spans).hasSize(1)
        assertThat(spans[0].distance).isEqualTo(500.0)
        assertThat(spans[0].fuel).isEqualTo(50.0)
    }

    @Test
    fun `full partial partial produces no span until the next full tank`() {
        val spans = computeSpans(
            listOf(
                fill(odometer = 100.0, volume = 40.0, full = true, day = 0),
                fill(odometer = 300.0, volume = 20.0, full = false, day = 3),
                fill(odometer = 500.0, volume = 20.0, full = false, day = 6),
            )
        )

        assertThat(spans).isEmpty()
    }

    @Test
    fun `a missed entry drops its span and the chain resumes after it`() {
        val spans = computeSpans(
            listOf(
                fill(odometer = 100.0, volume = 40.0, full = true, day = 0),
                fill(odometer = 300.0, volume = 20.0, full = false, day = 3, missed = true),
                fill(odometer = 600.0, volume = 30.0, full = true, day = 7),
                fill(odometer = 900.0, volume = 45.0, full = true, day = 14),
            )
        )

        assertThat(spans).hasSize(1)
        assertThat(spans[0].fromOdometer).isEqualTo(600.0)
        assertThat(spans[0].toOdometer).isEqualTo(900.0)
        assertThat(spans[0].fuel).isEqualTo(45.0)
    }

    @Test
    fun `duplicate odometer readings produce no span and no divide by zero`() {
        val spans = computeSpans(
            listOf(
                fill(odometer = 100.0, volume = 40.0, day = 0),
                fill(odometer = 100.0, volume = 30.0, day = 1),
            )
        )

        assertThat(spans).isEmpty()
    }

    @Test
    fun `an odometer reset produces no negative-distance span`() {
        val spans = computeSpans(
            listOf(
                fill(odometer = 100.0, volume = 40.0, day = 0),
                fill(odometer = 600.0, volume = 50.0, day = 7),
                fill(odometer = 10.0, volume = 45.0, day = 14),
            )
        )

        assertThat(spans.none { it.distance <= 0.0 }).isTrue()
    }

    @Test
    fun `two fill-ups on the same day are ordered by odometer and both counted`() {
        val spans = computeSpans(
            listOf(
                fill(odometer = 600.0, volume = 30.0, full = true, day = 5),
                fill(odometer = 300.0, volume = 20.0, full = false, day = 5),
                fill(odometer = 100.0, volume = 40.0, full = true, day = 5),
            )
        )

        assertThat(spans).hasSize(1)
        assertThat(spans[0].fromOdometer).isEqualTo(100.0)
        assertThat(spans[0].toOdometer).isEqualTo(600.0)
        assertThat(spans[0].fuel).isEqualTo(50.0)
    }

    @Test
    fun `the very first fill-up ever has no baseline and yields nothing`() {
        val spans = computeSpans(
            listOf(
                fill(odometer = 100.0, volume = 40.0, full = false, day = 0),
                fill(odometer = 400.0, volume = 30.0, full = true, day = 5),
            )
        )

        // The first full tank only becomes the anchor; it cannot close a span.
        assertThat(spans).isEmpty()
    }
}
