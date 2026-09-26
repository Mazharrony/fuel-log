package com.fuelexpenselog.domain.format

import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DisplayTest {

    @Test
    fun `every format has a precision`() {
        ConsumptionFormat.entries.forEach { assertThat(it.decimals()).isIn(1..2) }
        assertThat(ConsumptionFormat.L_PER_100KM.decimals()).isEqualTo(1)
        assertThat(ConsumptionFormat.KM_PER_L.decimals()).isEqualTo(2)
    }

    @Test
    fun `an odometer rounds to whole dashboard units`() {
        assertThat(DistanceUnit.KILOMETRE.wholeUnits(48_200_499)).isEqualTo(48_200)
        assertThat(DistanceUnit.KILOMETRE.wholeUnits(48_200_500)).isEqualTo(48_201)
        assertThat(DistanceUnit.MILE.wholeUnits(DistanceUnit.MILE.toMetres(84_210.0))).isEqualTo(84_210)
    }

    @Test
    fun `a cost per kilometre scales to a cost per mile`() {
        val perKm = Money.of(0.08, "EUR")
        assertThat(perKm.perDistanceUnit(DistanceUnit.KILOMETRE)).isEqualTo(perKm)
        assertThat(perKm.perDistanceUnit(DistanceUnit.MILE).asDouble).isWithin(1e-6).of(0.128748)
    }
}
