package com.fuelexpenselog.domain.consumption

import com.fuelexpenselog.domain.consumption.Fixtures.charge
import com.fuelexpenselog.domain.consumption.Fixtures.fill
import com.fuelexpenselog.domain.consumption.Fixtures.gapReasons
import com.fuelexpenselog.domain.consumption.Fixtures.run
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.EnergyKind
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * The cases that define correct behaviour. If one of these is wrong, every screen in the app
 * is confidently wrong in the same direction.
 */
class ConsumptionEngineTest {

    @Before fun reset() = Fixtures.resetIds()

    // -- the shape of a measurement -------------------------------------------------------

    @Test
    fun `no fill-ups produce nothing at all`() {
        val result = run(emptyList())

        assertThat(result.timeline).isEmpty()
        assertThat(result.summary).isNull()
    }

    @Test
    fun `one fill-up is a starting line, not a result`() {
        val result = run(listOf(fill(day = 0, odometerKm = 0.0, litres = 50.0)))

        assertThat(result.measured).isEmpty()
        assertThat(result.gapReasons()).containsExactly(GapReason.FIRST_FILL_UP)
        assertThat(result.summary).isNull()
    }

    @Test
    fun `two full tanks measure the distance between them, excluding the first tank's own fuel`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 10, odometerKm = 500.0, litres = 40.0),
            ),
        )

        assertThat(result.measured).hasSize(1)
        val span = result.measured.single()

        // 500 km on the 40 L that refilled it - NOT on 90 L, and NOT on the opening 50 L.
        // The opening tank paid for distance already driven.
        assertThat(span.distanceM).isEqualTo(500_000L)
        assertThat(span.energy.micro).isEqualTo(40_000_000L)
        assertThat(span.kmPerUnit).isWithin(1e-9).of(12.5)
        assertThat(span.shownAs(ConsumptionFormat.L_PER_100KM)!!).isWithin(1e-9).of(8.0)
        assertThat(span.confidence).isEqualTo(Confidence.EXACT)
        assertThat(span.fillUpsSpanned).isEqualTo(1)
    }

    @Test
    fun `a partial fill contributes its fuel to the next full span and no figure of its own`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 5, odometerKm = 250.0, litres = 20.0, full = false),
                fill(day = 10, odometerKm = 500.0, litres = 20.0),
            ),
        )

        // One figure, not two: the partial is not an endpoint. Same 12.5 km/L, because the
        // same 40 L covered the same 500 km however it was bought.
        assertThat(result.measured).hasSize(1)
        assertThat(result.measured.single().kmPerUnit).isWithin(1e-9).of(12.5)
        assertThat(result.measured.single().fillUpsSpanned).isEqualTo(2)
    }

    @Test
    fun `partials with no closing full tank produce no figure, and say so`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 5, odometerKm = 250.0, litres = 20.0, full = false),
                fill(day = 8, odometerKm = 400.0, litres = 15.0, full = false),
            ),
        )

        assertThat(result.measured).isEmpty()
        assertThat(result.gapReasons())
            .containsExactly(GapReason.FIRST_FILL_UP, GapReason.PARTIAL_ONLY).inOrder()
    }

    @Test
    fun `a leading partial cannot anchor, so the first full tank still has no baseline`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 20.0, full = false),
                fill(day = 10, odometerKm = 500.0, litres = 40.0),
            ),
        )

        assertThat(result.measured).isEmpty()
        assertThat(result.gapReasons()).containsExactly(GapReason.FIRST_FILL_UP)
    }

    // -- a missed fill-up -------------------------------------------------------------------

    @Test
    fun `a missed fill-up invalidates its span and the chain resumes immediately after`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 10, odometerKm = 500.0, litres = 40.0, missed = true),
                fill(day = 20, odometerKm = 1000.0, litres = 50.0),
            ),
        )

        assertThat(result.gapReasons())
            .containsExactly(GapReason.FIRST_FILL_UP, GapReason.MISSED_FILL_UP).inOrder()

        // The span AFTER the missed entry is fine: the tank is known full at both ends of it.
        assertThat(result.measured).hasSize(1)
        assertThat(result.measured.single().distanceM).isEqualTo(500_000L)
        assertThat(result.measured.single().kmPerUnit).isWithin(1e-9).of(10.0)
    }

    // -- readings that cannot produce a number -------------------------------------------------

    @Test
    fun `two identical readings give a dash, not a division by zero`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 500.0, litres = 40.0),
                fill(day = 1, odometerKm = 500.0, litres = 40.0),
            ),
        )

        assertThat(result.measured).isEmpty()
        assertThat(result.gapReasons())
            .containsExactly(GapReason.FIRST_FILL_UP, GapReason.ZERO_OR_NEGATIVE_DISTANCE)
        assertThat(result.timeline.filterIsInstance<Measured>().map { it.kmPerUnit })
            .doesNotContain(Double.POSITIVE_INFINITY)
    }

    @Test
    fun `a full tank with no reading is skipped as an anchor, and the span either side survives`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 7, odometerKm = null, litres = 30.0),
                fill(day = 14, odometerKm = 600.0, litres = 30.0),
            ),
        )

        // Breaking the chain here would discard a correct measurement. Full-to-full only
        // needs the tank full at both ends and every drop in between counted - both hold.
        assertThat(result.measured).hasSize(1)
        val span = result.measured.single()
        assertThat(span.distanceM).isEqualTo(600_000L)
        assertThat(span.energy.micro).isEqualTo(60_000_000L)
        assertThat(span.kmPerUnit).isWithin(1e-9).of(10.0)
        assertThat(span.confidence).isEqualTo(Confidence.AVERAGED)
        assertThat(span.fillUpsSpanned).isEqualTo(2)
    }

    @Test
    fun `a partial with no reading simply adds its fuel`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 5, odometerKm = null, litres = 10.0, full = false),
                fill(day = 10, odometerKm = 500.0, litres = 30.0),
            ),
        )

        assertThat(result.measured.single().energy.micro).isEqualTo(40_000_000L)
        assertThat(result.measured.single().kmPerUnit).isWithin(1e-9).of(12.5)
        assertThat(result.measured.single().confidence).isEqualTo(Confidence.EXACT)
    }

    @Test
    fun `fill-ups that never carry a reading produce a reason, not an empty chart`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = null, litres = 50.0),
                fill(day = 10, odometerKm = null, litres = 40.0),
            ),
        )

        assertThat(result.measured).isEmpty()
        assertThat(result.gapReasons()).containsExactly(GapReason.MISSING_ODOMETER)
    }

    // -- ordering ----------------------------------------------------------------------------

    @Test
    fun `two fill-ups on one day are ordered by the reading, not by insertion`() {
        // A CSV import carries a date with no clock time, so every entry that day shares a
        // timestamp and the odometer is the only monotone thing available.
        val result = run(
            listOf(
                fill(day = 5, odometerKm = 500.0, litres = 30.0, id = 3),
                fill(day = 0, odometerKm = 0.0, litres = 50.0, id = 1),
                fill(day = 5, odometerKm = 250.0, litres = 20.0, full = false, id = 2),
            ),
        )

        assertThat(result.measured).hasSize(1)
        assertThat(result.measured.single().energy.micro).isEqualTo(50_000_000L)
        assertThat(result.measured.single().kmPerUnit).isWithin(1e-9).of(10.0)
    }

    // -- money ---------------------------------------------------------------------------------

    @Test
    fun `a span that mixes currencies keeps its consumption and drops its cost`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0, cost = 70.0, currency = "EUR"),
                fill(day = 5, odometerKm = 250.0, litres = 20.0, full = false, cost = 30.0, currency = "EUR"),
                fill(day = 10, odometerKm = 500.0, litres = 20.0, cost = 25.0, currency = "GBP"),
            ),
        )

        val span = result.measured.single()

        // Litres do not care what you paid for them.
        assertThat(span.kmPerUnit).isWithin(1e-9).of(12.5)
        assertThat(span.cost).isNull()
        assertThat(span.costPerKm()).isNull()
    }

    @Test
    fun `a single-currency span reports cost per distance`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0, cost = 70.0),
                fill(day = 10, odometerKm = 500.0, litres = 40.0, cost = 60.0),
            ),
        )

        val span = result.measured.single()
        assertThat(span.cost!!.asDouble).isWithin(1e-9).of(60.0)
        assertThat(span.costPerKm()!!.asDouble).isWithin(1e-9).of(0.12)
    }

    // -- summaries -------------------------------------------------------------------------------

    @Test
    fun `the lifetime figure sums distance over energy, never averages the ratios`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 10, odometerKm = 100.0, litres = 20.0),    // 5 km/L over 100 km
                fill(day = 20, odometerKm = 1000.0, litres = 45.0),   // 20 km/L over 900 km
            ),
        )

        assertThat(result.kmPerLitreOfEachSpan()).containsExactly(5.0, 20.0).inOrder()

        // The mean of the ratios is 12.5, and it is wrong: it weights a 100 km span the same
        // as a 900 km one. The true figure is 1000 km on 65 L.
        val summary = result.summary!!
        assertThat(summary.lifetimeKmPerUnit!!).isWithin(1e-6).of(1000.0 / 65.0)
        assertThat(summary.lifetimeKmPerUnit!!).isNotWithin(0.1).of(12.5)

        assertThat(summary.best!!.kmPerUnit).isWithin(1e-9).of(20.0)
        assertThat(summary.worst!!.kmPerUnit).isWithin(1e-9).of(5.0)
        assertThat(summary.latest!!.kmPerUnit).isWithin(1e-9).of(20.0)
        assertThat(summary.measuredCount).isEqualTo(2)
    }

    @Test
    fun `the rolling recent figure is summed the same way`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 10, odometerKm = 100.0, litres = 20.0),
                fill(day = 20, odometerKm = 1000.0, litres = 45.0),
            ),
        )

        assertThat(ConsumptionSummary.recentKmPerUnit(result.measured, 1)!!)
            .isWithin(1e-9).of(20.0)
        assertThat(ConsumptionSummary.recentKmPerUnit(result.measured, 9)!!)
            .isWithin(1e-6).of(1000.0 / 65.0)
    }

    // -- an implausible figure is flagged, never hidden ----------------------------------------

    @Test
    fun `an absurd figure is still shown, and marked`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 10, odometerKm = 20.0, litres = 60.0),   // 0.33 km/L
            ),
        )

        val span = result.measured.single()
        assertThat(span.kmPerUnit).isWithin(1e-6).of(20.0 / 60.0)
        assertThat(span.isPlausible).isFalse()
        // Present in the timeline, not replaced by a gap.
        assertThat(result.timeline.filterIsInstance<Measured>()).hasSize(1)
    }

    // -- plug-in hybrid -------------------------------------------------------------------------

    @Test
    fun `petrol and charging form independent chains with no engine change`() {
        val events = listOf(
            fill(day = 0, odometerKm = 0.0, litres = 40.0),
            charge(day = 5, odometerKm = 300.0, kwh = 20.0),
            fill(day = 10, odometerKm = 600.0, litres = 30.0),
        )

        val petrol = run(events, EnergyKind.LIQUID)
        val span = petrol.measured.single()

        assertThat(span.distanceM).isEqualTo(600_000L)
        assertThat(span.energy.micro).isEqualTo(30_000_000L)
        assertThat(span.kmPerUnit).isWithin(1e-9).of(20.0)

        // The other source is recorded rather than ignored, so the UI can say that some of
        // this distance was not covered by petrol and the figure flatters it.
        assertThat(span.otherKindMicro).isEqualTo(20_000_000L)

        val electric = run(events, EnergyKind.ELECTRIC)
        assertThat(electric.measured).isEmpty()
        assertThat(electric.gapReasons()).containsExactly(GapReason.FIRST_FILL_UP)
    }

    @Test
    fun `an ordinary car never reports energy of the other kind`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 10, odometerKm = 500.0, litres = 40.0),
            ),
        )

        assertThat(result.measured.single().otherKindMicro).isEqualTo(0L)
    }

    // -- the timeline is dense --------------------------------------------------------------------

    @Test
    fun `gaps occupy slots so the chart cannot draw through a hole`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 10, odometerKm = 500.0, litres = 40.0, missed = true),
                fill(day = 20, odometerKm = 1000.0, litres = 50.0),
            ),
        )

        assertThat(result.timeline.map { it.index }).containsExactly(0, 1, 2).inOrder()
        assertThat(result.timeline[0]).isInstanceOf(Gap::class.java)
        assertThat(result.timeline[1]).isInstanceOf(Gap::class.java)
        assertThat(result.timeline[2]).isInstanceOf(Measured::class.java)
    }

    private fun ConsumptionResult.kmPerLitreOfEachSpan(): List<Double> =
        measured.map { it.kmPerUnit }
}
