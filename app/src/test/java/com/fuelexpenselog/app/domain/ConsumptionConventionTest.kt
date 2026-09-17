package com.fuelexpenselog.app.domain

import com.fuelexpenselog.app.domain.consumption.Span
import com.fuelexpenselog.app.domain.units.ConsumptionConvention
import com.fuelexpenselog.app.domain.units.Plausibility
import com.fuelexpenselog.app.domain.units.Units
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConsumptionConventionTest {

    @Test
    fun `every convention round-trips through km per litre`() {
        for (convention in ConsumptionConvention.entries) {
            for (kmPerLitre in listOf(3.5, 10.0, 17.3, 42.0)) {
                val there = convention.fromKmPerLitre(kmPerLitre)
                assertThat(convention.toKmPerLitre(there)).isWithin(1e-9).of(kmPerLitre)
            }
        }
    }

    @Test
    fun `L per 100km and km per L really are inverses`() {
        val kmPerLitre = 12.5
        val lPer100 = ConsumptionConvention.L_PER_100KM.fromKmPerLitre(kmPerLitre)

        assertThat(lPer100).isWithin(1e-9).of(8.0)
        assertThat(lPer100 * kmPerLitre).isWithin(1e-9).of(100.0)
    }

    @Test
    fun `MPG conversions match the published constants without hard-coding them`() {
        // The familiar 235.215 and 282.481 magic numbers, derived rather than typed.
        assertThat(ConsumptionConvention.MPG_US.fromKmPerLitre(1.0))
            .isWithin(1e-6).of(Units.LITRES_PER_US_GALLON / Units.KM_PER_MILE)
        assertThat(ConsumptionConvention.MPG_US.fromKmPerLitre(1.0)).isWithin(1e-3).of(2.352146)
        assertThat(ConsumptionConvention.MPG_UK.fromKmPerLitre(1.0)).isWithin(1e-3).of(2.824810)
        // A UK gallon is larger, so the same car scores higher in MPG UK.
        assertThat(ConsumptionConvention.MPG_UK.fromKmPerLitre(10.0))
            .isGreaterThan(ConsumptionConvention.MPG_US.fromKmPerLitre(10.0))
    }

    /**
     * Orientation drives the delta arrow and its improved/worsened colour. L/100km
     * falls as efficiency rises while the other three climb, and inverting this
     * tells every metric user their economy got worse when it got better.
     */
    @Test
    fun `only L per 100km is lower-is-better`() {
        assertThat(ConsumptionConvention.L_PER_100KM.lowerIsBetter).isTrue()
        assertThat(ConsumptionConvention.KM_PER_L.lowerIsBetter).isFalse()
        assertThat(ConsumptionConvention.MPG_US.lowerIsBetter).isFalse()
        assertThat(ConsumptionConvention.MPG_UK.lowerIsBetter).isFalse()
    }

    @Test
    fun `a more efficient car moves every convention in its stated direction`() {
        val thirsty = 8.0
        val frugal = 20.0
        for (convention in ConsumptionConvention.entries) {
            val a = convention.fromKmPerLitre(thirsty)
            val b = convention.fromKmPerLitre(frugal)
            if (convention.lowerIsBetter) assertThat(b).isLessThan(a)
            else assertThat(b).isGreaterThan(a)
        }
    }

    @Test
    fun `a zero-distance span yields no figure rather than infinity`() {
        val degenerate = Span(100.0, 100.0, 40.0, 80.0, day(1))

        for (convention in ConsumptionConvention.entries) {
            assertThat(convention.of(degenerate)).isNull()
        }
    }

    @Test
    fun `a zero-fuel span yields no figure rather than infinity`() {
        val degenerate = Span(100.0, 600.0, 0.0, 0.0, day(1))

        for (convention in ConsumptionConvention.entries) {
            assertThat(convention.of(degenerate)).isNull()
        }
    }

    @Test
    fun `implausible figures are flagged at the stated bounds`() {
        assertThat(Plausibility.isImplausible(0.5)).isTrue()
        assertThat(Plausibility.isImplausible(150.0)).isTrue()
        assertThat(Plausibility.isImplausible(Double.NaN)).isTrue()
        assertThat(Plausibility.isImplausible(Double.POSITIVE_INFINITY)).isTrue()
        assertThat(Plausibility.isImplausible(14.5)).isFalse()
    }
}
