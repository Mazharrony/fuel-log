package com.fuelexpenselog.domain.consumption

import com.fuelexpenselog.domain.consumption.Fixtures.fill
import com.fuelexpenselog.domain.consumption.Fixtures.fillMiles
import com.fuelexpenselog.domain.consumption.Fixtures.gapReasons
import com.fuelexpenselog.domain.consumption.Fixtures.run
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * The failure this whole layer exists to prevent: an odometer that stops being a monotone
 * record of one vehicle's life, producing a span that charges the wrong distance against the
 * right fuel. The resulting figure is roughly twice wrong and completely believable, which
 * makes it the most dangerous bug the app can have.
 */
class OdometerAnomalyTest {

    @Before fun reset() = Fixtures.resetIds()

    private fun Measured.distanceKm() = DistanceUnit.KILOMETRE.fromMetres(distanceM)
    private fun Measured.distanceMiles() = DistanceUnit.MILE.fromMetres(distanceM)

    // -- a genuine reset ---------------------------------------------------------------------

    @Test
    fun `a replaced odometer splits the chain and nothing measures across it`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 100.0, litres = 40.0),
                fill(day = 10, odometerKm = 200.0, litres = 10.0),
                fill(day = 20, odometerKm = 300.0, litres = 10.0),
                // cluster replaced; the reading starts again from nothing
                fill(day = 30, odometerKm = 0.0, litres = 40.0),
                fill(day = 40, odometerKm = 50.0, litres = 5.0),
            ),
        )

        assertThat(result.gapReasons()).contains(GapReason.ODOMETER_RESET)

        // Three spans: two before the reset, one after. None crosses it - a crossing span
        // would report 200 km driven on fuel that covered 300, and look entirely normal.
        assertThat(result.measured).hasSize(3)
        assertThat(result.measured.map { it.distanceKm() })
            .containsExactly(100.0, 100.0, 50.0).inOrder()

        val proposal = result.proposals.single()
        assertThat(proposal.anomaly).isEqualTo(OdometerAnomaly.RESET)
        assertThat(proposal.offsetM).isNull()
    }

    @Test
    fun `a confirmed reset is the user's decision, never applied on its own`() {
        val events = listOf(
            fill(day = 0, odometerKm = 100.0, litres = 40.0),
            fill(day = 10, odometerKm = 200.0, litres = 10.0),
            fill(day = 20, odometerKm = 0.0, litres = 40.0),
        )

        // The engine proposes; it writes nothing. The caller decides what to persist.
        assertThat(run(events).proposals).hasSize(1)
        assertThat(run(events).proposals.single().correctedM).isNull()
    }

    // -- a single mistyped reading -------------------------------------------------------------

    @Test
    fun `a dropped digit is isolated without breaking the chain, and a correction is offered`() {
        // 4820 typed for 48200. The readings resume above where they already were, so the
        // history is fine and one entry is wrong.
        val result = run(
            listOf(
                fillMiles(day = 0, odometerMiles = 47_000.0, gallons = 12.0),
                fillMiles(day = 7, odometerMiles = 47_900.0, gallons = 12.0),
                fillMiles(day = 14, odometerMiles = 4_820.0, gallons = 12.0),
                fillMiles(day = 21, odometerMiles = 48_500.0, gallons = 12.0),
            ),
            unit = DistanceUnit.MILE,
        )

        // Nothing split. Splitting at the dip would pair the typo with the recovery and
        // invent a 43,680 mile span.
        assertThat(result.gapReasons()).containsExactly(GapReason.FIRST_FILL_UP)
        assertThat(result.measured).hasSize(2)

        assertThat(result.measured[0].distanceMiles()).isWithin(0.01).of(900.0)
        assertThat(result.measured[0].confidence).isEqualTo(Confidence.EXACT)

        // The second span swallows the bad anchor. That is lossless for full-to-full: the
        // tank is full at both ends and every gallon in between is still counted. Only the
        // granularity is lost, which is what AVERAGED tells the UI to say.
        assertThat(result.measured[1].distanceMiles()).isWithin(0.01).of(600.0)
        assertThat(result.measured[1].confidence).isEqualTo(Confidence.AVERAGED)
        assertThat(result.measured[1].fillUpsSpanned).isEqualTo(2)
        // Two separate 12-gallon purchases, so two rounded values summed - which is one
        // microlitre away from rounding 24 gallons once, and correctly so. That is a
        // millionth of a litre; the point is that the arithmetic is the sum of what was
        // actually recorded, not a re-derivation of it.
        assertThat(result.measured[1].energy.micro)
            .isEqualTo(2 * EnergyUnit.US_GALLON.toMicro(12.0))

        val proposal = result.proposals.single()
        assertThat(proposal.anomaly).isEqualTo(OdometerAnomaly.SUSPECTED_TYPO)
        assertThat(DistanceUnit.MILE.fromMetres(proposal.correctedM!!)).isWithin(0.01).of(48_200.0)
    }

    @Test
    fun `no span is absurd after a mistyped reading`() {
        val result = run(
            listOf(
                fillMiles(day = 0, odometerMiles = 47_000.0, gallons = 12.0),
                fillMiles(day = 7, odometerMiles = 47_900.0, gallons = 12.0),
                fillMiles(day = 14, odometerMiles = 4_820.0, gallons = 12.0),
                fillMiles(day = 21, odometerMiles = 48_500.0, gallons = 12.0),
            ),
            unit = DistanceUnit.MILE,
        )

        assertThat(result.measured.map { it.distanceMiles() }.max()).isLessThan(1_000.0)
    }

    @Test
    fun `an unexplainable dip that recovers still does not break the chain`() {
        // No single-digit slip explains 31,000 in this position, so no correction is offered
        // - but the readings recovered, so the odometer plainly did not reset.
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 47_000.0, litres = 40.0),
                fill(day = 7, odometerKm = 47_900.0, litres = 40.0),
                fill(day = 14, odometerKm = 31_111.0, litres = 40.0),
                fill(day = 21, odometerKm = 48_500.0, litres = 40.0),
            ),
        )

        assertThat(result.gapReasons()).containsExactly(GapReason.FIRST_FILL_UP)
        assertThat(result.measured).hasSize(2)
        assertThat(result.proposals.single().anomaly).isEqualTo(OdometerAnomaly.SUSPECTED_TYPO)
        assertThat(result.proposals.single().correctedM).isNull()
    }

    // -- rollover --------------------------------------------------------------------------------

    @Test
    fun `a six-digit odometer wrapping past its maximum is recognised as a rollover`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 999_500.0, litres = 50.0),
                fill(day = 7, odometerKm = 200.0, litres = 50.0),
                fill(day = 14, odometerKm = 700.0, litres = 50.0),
            ),
        )

        val proposal = result.proposals.single()
        assertThat(proposal.anomaly).isEqualTo(OdometerAnomaly.ROLLOVER)
        assertThat(DistanceUnit.KILOMETRE.fromMetres(proposal.offsetM!!)).isWithin(0.1).of(1_000_000.0)
    }

    @Test
    fun `a confirmed rollover offset bridges the wrap instead of breaking the chain`() {
        val events = listOf(
            fill(day = 0, odometerKm = 999_500.0, litres = 50.0),
            fill(day = 7, odometerKm = 200.0, litres = 50.0),
            fill(day = 14, odometerKm = 700.0, litres = 50.0),
        )

        val result = run(
            events,
            declared = listOf(
                DeclaredSegment(
                    startsAt = Fixtures.day(7),
                    reason = SegmentReason.ROLLOVER,
                    offsetM = DistanceUnit.KILOMETRE.toMetres(1_000_000.0),
                ),
            ),
        )

        assertThat(result.gapReasons()).containsExactly(GapReason.FIRST_FILL_UP)
        assertThat(result.measured.map { it.distanceKm() }).containsExactly(700.0, 500.0).inOrder()
    }

    // -- declared breaks ---------------------------------------------------------------------------

    @Test
    fun `a declared break splits an otherwise healthy chain`() {
        // Bought second-hand: the readings are continuous but the history before the purchase
        // is not this owner's.
        val events = listOf(
            fill(day = 0, odometerKm = 100.0, litres = 40.0),
            fill(day = 10, odometerKm = 600.0, litres = 40.0),
            fill(day = 20, odometerKm = 1_100.0, litres = 40.0),
        )

        val split = run(
            events,
            declared = listOf(
                DeclaredSegment(Fixtures.day(10), SegmentReason.PURCHASED_USED, offsetM = null),
            ),
        )

        assertThat(split.gapReasons()).contains(GapReason.ODOMETER_RESET)
        assertThat(split.measured).hasSize(1)
        assertThat(split.measured.single().distanceKm()).isWithin(1e-6).of(500.0)

        // Without the declaration it is one unbroken chain of two spans.
        assertThat(run(events).measured).hasSize(2)
    }

    // -- things that are NOT anomalies ----------------------------------------------------------------

    @Test
    fun `same-day entries recorded out of order are not mistaken for a reset`() {
        val result = run(
            listOf(
                fill(day = 0, odometerKm = 100.0, litres = 40.0),
                fill(day = 5, odometerKm = 600.0, litres = 20.0, id = 9),
                fill(day = 5, odometerKm = 350.0, litres = 20.0, full = false, id = 8),
            ),
        )

        assertThat(result.proposals).isEmpty()
        assertThat(result.gapReasons()).containsExactly(GapReason.FIRST_FILL_UP)
        assertThat(result.measured.single().distanceKm()).isWithin(1e-6).of(500.0)
    }

    @Test
    fun `an ordinary monotone history produces no proposals and no reset gaps`() {
        val random = Random(20_260_919)
        var odometer = 12_000.0

        val events = (0..80).map { i ->
            odometer += random.nextInt(250, 700)
            fill(day = i * 7, odometerKm = odometer, litres = random.nextInt(30, 60).toDouble())
        }

        val result = run(events)

        assertThat(result.proposals).isEmpty()
        assertThat(result.gapReasons()).doesNotContain(GapReason.ODOMETER_RESET)
        assertThat(result.measured).hasSize(80)
        assertThat(result.summary!!.lifetimeKmPerUnit).isNotNull()
    }

    @Test
    fun `injecting one dropped digit into a clean history costs exactly one anchor and no gap`() {
        val random = Random(4_820)
        var odometer = 12_000.0
        val clean = (0..40).map { i ->
            odometer += random.nextInt(400, 600)
            fill(day = i * 7, odometerKm = odometer, litres = 45.0)
        }

        val before = run(clean)
        assertThat(before.proposals).isEmpty()

        // Drop the last digit of one reading in the middle.
        val damaged = clean.toMutableList()
        val victim = damaged[20]
        damaged[20] = victim.copy(odometerM = victim.odometerM!! / 10)

        val after = run(damaged)

        assertThat(after.proposals).hasSize(1)
        assertThat(after.proposals.single().anomaly).isEqualTo(OdometerAnomaly.SUSPECTED_TYPO)
        assertThat(after.gapReasons()).doesNotContain(GapReason.ODOMETER_RESET)

        // One fewer span than before, because one anchor was skipped - and the total distance
        // measured is unchanged, which is what "lossless" means here.
        assertThat(after.measured).hasSize(before.measured.size - 1)
        assertThat(after.measured.sumOf { it.distanceM })
            .isEqualTo(before.measured.sumOf { it.distanceM })
        assertThat(after.measured.count { it.confidence == Confidence.AVERAGED }).isEqualTo(1)
    }
}
