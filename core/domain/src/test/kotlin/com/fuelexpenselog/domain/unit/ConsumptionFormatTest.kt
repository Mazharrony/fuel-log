package com.fuelexpenselog.domain.unit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConsumptionFormatTest {

    @Test
    fun `every format round-trips through the pivot`() {
        for (format in ConsumptionFormat.entries) {
            for (kmPerUnit in listOf(0.8, 1.0, 5.0, 14.5, 99.0)) {
                val back = format.toKmPerUnit(format.fromKmPerUnit(kmPerUnit))
                assertThat(back).isWithin(1e-9).of(kmPerUnit)
            }
        }
    }

    @Test
    fun `L per 100km and km per L are exact inverses`() {
        // 12.5 km/L is 8.0 L/100km, and the product is 100 by construction.
        assertThat(ConsumptionFormat.L_PER_100KM.fromKmPerUnit(12.5)).isWithin(1e-9).of(8.0)
        assertThat(ConsumptionFormat.KM_PER_L.fromKmPerUnit(12.5)).isWithin(1e-9).of(12.5)
        assertThat(12.5 * 8.0).isWithin(1e-9).of(100.0)
    }

    @Test
    fun `the MPG factors match the known constants without being hard-coded`() {
        // 2.352146 and 2.824810 are the numbers every conversion table prints. They are
        // never written down in the source; they fall out of the gallon and mile
        // definitions, so there is exactly one place a unit can be wrong.
        assertThat(ConsumptionFormat.MPG_US.fromKmPerUnit(1.0)).isWithin(1e-6).of(2.352146)
        assertThat(ConsumptionFormat.MPG_UK.fromKmPerUnit(1.0)).isWithin(1e-6).of(2.824810)
    }

    @Test
    fun `only the per-100km formats are lower-is-better`() {
        // Not cosmetic: the month-on-month delta arrow and its colour invert on these two.
        val inverted = ConsumptionFormat.entries.filter { it.lowerIsBetter }.toSet()

        assertThat(inverted).containsExactly(
            ConsumptionFormat.L_PER_100KM,
            ConsumptionFormat.KWH_PER_100KM,
        )
    }

    @Test
    fun `a more efficient car moves every format in its stated direction`() {
        val thirsty = 10.0
        val frugal = 20.0

        for (format in ConsumptionFormat.entries) {
            val a = format.fromKmPerUnit(thirsty)
            val b = format.fromKmPerUnit(frugal)

            if (format.lowerIsBetter) {
                assertThat(b).isLessThan(a)
            } else {
                assertThat(b).isGreaterThan(a)
            }
        }
    }

    @Test
    fun `a real fill-up produces the figure a driver would compute by hand`() {
        // 500 miles on 14.6 US gallons is 34.25 mpg by long division. Everything in the
        // chain - metres, microlitres, the pivot - has to survive that.
        val distanceM = DistanceUnit.MILE.toMetres(500.0)
        val energy = Energy.of(EnergyUnit.US_GALLON, 14.6)

        assertThat(ConsumptionFormat.MPG_US.of(distanceM, energy)!!).isWithin(0.01).of(34.25)
        assertThat(ConsumptionFormat.L_PER_100KM.of(distanceM, energy)!!).isWithin(0.01).of(6.87)
        assertThat(ConsumptionFormat.KM_PER_L.of(distanceM, energy)!!).isWithin(0.01).of(14.56)

        // The same car in imperial gallons reads higher, because the gallon is bigger.
        assertThat(ConsumptionFormat.MPG_UK.of(distanceM, energy)!!).isWithin(0.01).of(41.13)
    }

    @Test
    fun `an unknowable figure is null, never zero and never infinity`() {
        val energy = Energy.of(EnergyUnit.LITRE, 40.0)
        val distance = DistanceUnit.KILOMETRE.toMetres(500.0)

        assertThat(ConsumptionFormat.MPG_US.of(0L, energy)).isNull()          // no distance
        assertThat(ConsumptionFormat.MPG_US.of(-100L, energy)).isNull()       // odometer went back
        assertThat(ConsumptionFormat.MPG_US.of(distance, Energy.of(EnergyUnit.LITRE, 0.0)))
            .isNull()                                                         // no fuel
    }

    @Test
    fun `a liquid format refuses an electric amount`() {
        val charge = Energy.of(EnergyUnit.KWH, 60.0)
        val distance = DistanceUnit.KILOMETRE.toMetres(300.0)

        assertThat(runCatching { ConsumptionFormat.MPG_US.of(distance, charge) }.isFailure).isTrue()

        // The electric formats handle it, and the engine needs no other change for a PHEV.
        assertThat(ConsumptionFormat.KWH_PER_100KM.of(distance, charge)!!).isWithin(0.01).of(20.0)
        assertThat(ConsumptionFormat.KM_PER_KWH.of(distance, charge)!!).isWithin(0.01).of(5.0)
        assertThat(ConsumptionFormat.MI_PER_KWH.of(distance, charge)!!).isWithin(0.01).of(3.107)
    }

    @Test
    fun `plausibility flags the absurd and passes the ordinary`() {
        assertThat(Plausibility.isPlausible(14.5, EnergyKind.LIQUID)).isTrue()
        assertThat(Plausibility.isPlausible(0.5, EnergyKind.LIQUID)).isFalse()
        assertThat(Plausibility.isPlausible(150.0, EnergyKind.LIQUID)).isFalse()
        assertThat(Plausibility.isPlausible(Double.NaN, EnergyKind.LIQUID)).isFalse()
        assertThat(Plausibility.isPlausible(Double.POSITIVE_INFINITY, EnergyKind.LIQUID)).isFalse()

        assertThat(Plausibility.isPlausible(5.0, EnergyKind.ELECTRIC)).isTrue()
        assertThat(Plausibility.isPlausible(0.1, EnergyKind.ELECTRIC)).isFalse()
    }
}
