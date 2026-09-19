package com.fuelexpenselog.domain.consumption

import com.fuelexpenselog.domain.consumption.Fixtures.fill
import com.fuelexpenselog.domain.consumption.Fixtures.gapReasons
import com.fuelexpenselog.domain.consumption.Fixtures.run
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * A fixed history with every figure worked out by hand, so a regression anywhere in the
 * engine shows up as a specific number changing rather than as "some test broke".
 */
class GoldenDatasetTest {

    @Before fun reset() = Fixtures.resetIds()

    /**
     * Seven entries over twelve weeks. Distances and volumes are chosen so every expected
     * figure is a fraction that can be checked mentally.
     *
     *   day  0  odo 10,000  45 L  full     opening anchor - its own fuel is excluded
     *   day 14  odo 10,520  40 L  full       520 km / 40 L = 13.0    km/L
     *   day 28  odo 10,800  20 L  partial    carries forward
     *   day 42  odo 11,100  25 L  full       580 km / 45 L = 12.888. km/L
     *   day 56  odo 11,650  44 L  full       550 km / 44 L = 12.5    km/L
     *   day 70  odo 12,200  50 L  full  MISSED - this span is not knowable
     *   day 84  odo 12,700  40 L  full       500 km / 40 L = 12.5    km/L
     */
    private fun history() = listOf(
        fill(day = 0, odometerKm = 10_000.0, litres = 45.0, cost = 67.50),
        fill(day = 14, odometerKm = 10_520.0, litres = 40.0, cost = 60.00),
        fill(day = 28, odometerKm = 10_800.0, litres = 20.0, full = false, cost = 30.00),
        fill(day = 42, odometerKm = 11_100.0, litres = 25.0, cost = 37.50),
        fill(day = 56, odometerKm = 11_650.0, litres = 44.0, cost = 66.00),
        fill(day = 70, odometerKm = 12_200.0, litres = 50.0, missed = true, cost = 75.00),
        fill(day = 84, odometerKm = 12_700.0, litres = 40.0, cost = 60.00),
    )

    @Test
    fun `every span matches the figure computed by hand`() {
        val result = run(history())

        assertThat(result.measured.map { it.kmPerUnit }).hasSize(4)
        assertThat(result.measured[0].kmPerUnit).isWithin(1e-6).of(520.0 / 40.0)
        assertThat(result.measured[1].kmPerUnit).isWithin(1e-6).of(580.0 / 45.0)
        assertThat(result.measured[2].kmPerUnit).isWithin(1e-6).of(550.0 / 44.0)
        assertThat(result.measured[3].kmPerUnit).isWithin(1e-6).of(500.0 / 40.0)
    }

    @Test
    fun `the timeline is dense, ordered, and says why each hole is there`() {
        val result = run(history())

        assertThat(result.timeline.map { it.index }).isInOrder()
        assertThat(result.timeline.map { it.index }).isEqualTo((0 until result.timeline.size).toList())

        assertThat(result.gapReasons())
            .containsExactly(GapReason.FIRST_FILL_UP, GapReason.MISSED_FILL_UP).inOrder()
        assertThat(result.timeline).hasSize(6)
    }

    @Test
    fun `the lifetime figure is the sum over the sum`() {
        val summary = run(history()).summary!!

        // 520 + 580 + 550 + 500 = 2150 km on 40 + 45 + 44 + 40 = 169 L.
        // The mean of the four ratios is 12.722..., which happens to be close here - it
        // diverges as span lengths diverge, and it is wrong either way.
        assertThat(summary.totalDistanceM).isEqualTo(2_150_000L)
        assertThat(summary.totalEnergy.micro).isEqualTo(169_000_000L)
        assertThat(summary.lifetimeKmPerUnit!!).isWithin(1e-6).of(2150.0 / 169.0)

        assertThat(summary.lifetimeAs(ConsumptionFormat.L_PER_100KM)!!)
            .isWithin(1e-6).of(16_900.0 / 2150.0)

        // The missed span's fuel and distance are excluded from the lifetime figure too -
        // they are not knowable, so they are not counted.
        assertThat(summary.measuredCount).isEqualTo(4)
    }

    @Test
    fun `costs follow the same span boundaries as the fuel`() {
        val result = run(history())

        // First span: only the closing fill's cost. The opening tank paid for earlier distance.
        assertThat(result.measured[0].cost!!.asDouble).isWithin(1e-9).of(60.00)
        // Second span: the partial plus the closing full tank.
        assertThat(result.measured[1].cost!!.asDouble).isWithin(1e-9).of(30.00 + 37.50)

        val summary = result.summary!!
        assertThat(summary.totalCost!!.asDouble).isWithin(1e-9).of(60.0 + 67.5 + 66.0 + 60.0)
        assertThat(summary.costPerKm()!!.asDouble).isWithin(1e-6).of(253.5 / 2150.0)
    }

    // -- properties that must hold whatever the input looks like -----------------------------

    @Test
    fun `the result does not depend on the order rows arrive in`() {
        // Rows come back from a database, a CSV import, or an edit, in whatever order. The
        // engine sorts; nothing downstream should ever see a difference.
        val expected = run(history())
        val random = Random(19)

        repeat(25) {
            val shuffled = history().shuffled(random)
            val actual = run(shuffled)

            assertThat(actual.measured.map { it.kmPerUnit })
                .isEqualTo(expected.measured.map { it.kmPerUnit })
            assertThat(actual.gapReasons()).isEqualTo(expected.gapReasons())
            assertThat(actual.summary!!.lifetimeKmPerUnit)
                .isEqualTo(expected.summary!!.lifetimeKmPerUnit)
        }
    }

    @Test
    fun `every measured span accounts for real distance and real fuel`() {
        // A span with no distance or no fuel must never reach the timeline as a Measured -
        // that is the invariant that keeps infinity and divide-by-zero out of the UI.
        val random = Random(7)
        var odometer = 5_000.0

        val messy = (0..120).map { i ->
            odometer += random.nextInt(0, 800)          // includes zero-distance stretches
            fill(
                day = i * 5,
                odometerKm = if (random.nextInt(10) == 0) null else odometer,
                litres = random.nextInt(0, 60).toDouble(),   // includes zero-volume entries
                full = random.nextInt(3) != 0,
                missed = random.nextInt(20) == 0,
            )
        }

        val result = run(messy)

        for (span in result.measured) {
            assertThat(span.distanceM).isGreaterThan(0L)
            assertThat(span.energy.micro).isGreaterThan(0L)
            assertThat(span.kmPerUnit).isFinite()
            assertThat(span.kmPerUnit).isGreaterThan(0.0)
        }
        assertThat(result.timeline.map { it.index })
            .isEqualTo((0 until result.timeline.size).toList())
    }
}
