package com.fuelexpenselog.domain.unit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnitsTest {

    @Test
    fun `distance round-trips through every unit`() {
        for (unit in DistanceUnit.entries) {
            for (display in listOf(0.0, 1.0, 48_210.0, 123_456.7)) {
                val back = unit.fromMetres(unit.toMetres(display))
                assertThat(back).isWithin(1e-3).of(display)
            }
        }
    }

    @Test
    fun `energy round-trips through every unit`() {
        for (unit in EnergyUnit.entries) {
            for (display in listOf(0.0, 1.0, 14.6, 55.25, 999.999)) {
                val back = unit.fromMicro(unit.toMicro(display))
                assertThat(back).isWithin(1e-5).of(display)
            }
        }
    }

    @Test
    fun `the defining constants are exactly what the definitions say`() {
        // The international mile is 1609.344 m by definition, not by measurement.
        assertThat(DistanceUnit.MILE.toMetres(100.0)).isEqualTo(160_934L)
        assertThat(DistanceUnit.MILE.metresPerUnit).isEqualTo(1609.344)

        // A US gallon is 231 cubic inches; an inch is 2.54 cm.
        // isWithin, not isEqualTo: recomputing 2.54^3 here accumulates float error that the
        // literal constant does not have. The constant is the exact decimal value.
        assertThat(Canonical.MICROLITRES_PER_US_GALLON)
            .isWithin(1e-6).of(231.0 * 2.54 * 2.54 * 2.54 * 1000.0)

        // The imperial gallon is 4.54609 L.
        assertThat(EnergyUnit.IMP_GALLON.toMicro(1.0)).isEqualTo(4_546_090L)

        // And therefore an imperial gallon is about 1.201 US gallons.
        val ratio = Canonical.MICROLITRES_PER_IMP_GALLON / Canonical.MICROLITRES_PER_US_GALLON
        assertThat(ratio).isWithin(1e-5).of(1.20095)
    }

    @Test
    fun `a long odometer round trip does not drift`() {
        // 48,210 miles stored as metres and read back. Sub-metre rounding is irrelevant at
        // this scale, but a systematic drift would not be.
        val metres = DistanceUnit.MILE.toMetres(48_210.0)
        assertThat(DistanceUnit.MILE.fromMetres(metres)).isWithin(1e-3).of(48_210.0)
    }

    @Test
    fun `energy refuses to mix litres with kilowatt hours`() {
        val petrol = Energy.of(EnergyUnit.LITRE, 40.0)
        val charge = Energy.of(EnergyUnit.KWH, 40.0)

        // Both are "40 units" and both are 40_000_000 micro. Only the kind stops this from
        // being a silently plausible addition.
        assertThat(petrol.micro).isEqualTo(charge.micro)
        assertThat(runCatching { petrol + charge }.isFailure).isTrue()

        assertThat((petrol + Energy.of(EnergyUnit.LITRE, 5.0)).micro).isEqualTo(45_000_000L)
    }

    @Test
    fun `energy cannot be negative`() {
        assertThat(runCatching { Energy(EnergyKind.LIQUID, -1L) }.isFailure).isTrue()
    }

    @Test
    fun `a unit cannot express an amount of the other kind`() {
        val charge = Energy.of(EnergyUnit.KWH, 60.0)
        assertThat(runCatching { charge.inUnit(EnergyUnit.LITRE) }.isFailure).isTrue()
        assertThat(charge.inUnit(EnergyUnit.KWH)).isWithin(1e-9).of(60.0)
    }
}
