package com.fuelexpenselog.app.domain

import com.fuelexpenselog.app.domain.model.DistanceUnit
import com.fuelexpenselog.app.domain.model.VolumeUnit
import com.fuelexpenselog.app.domain.units.Units
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnitsTest {

    @Test
    fun `distance round-trips through every unit`() {
        for (unit in DistanceUnit.entries) {
            val km = 48_210.0
            assertThat(Units.displayToKm(Units.kmToDisplay(km, unit), unit)).isWithin(1e-9).of(km)
        }
    }

    @Test
    fun `volume round-trips through every unit`() {
        for (unit in VolumeUnit.entries) {
            val litres = 32.5
            assertThat(Units.displayToLitres(Units.litresToDisplay(litres, unit), unit))
                .isWithin(1e-9).of(litres)
        }
    }

    @Test
    fun `known conversions are correct`() {
        assertThat(Units.kmToDisplay(160.9344, DistanceUnit.MILE)).isWithin(1e-6).of(100.0)
        assertThat(Units.litresToDisplay(Units.LITRES_PER_US_GALLON, VolumeUnit.US_GALLON))
            .isWithin(1e-9).of(1.0)
        assertThat(Units.litresToDisplay(Units.LITRES_PER_UK_GALLON, VolumeUnit.UK_GALLON))
            .isWithin(1e-9).of(1.0)
        // A UK gallon is about 20% larger than a US one.
        assertThat(Units.LITRES_PER_UK_GALLON / Units.LITRES_PER_US_GALLON).isWithin(1e-3).of(1.201)
    }

    @Test
    fun `a mile round-trip does not drift enough to show on screen`() {
        // 48210 mi -> km -> mi comes back 48209.999... The display layer rounds,
        // but the raw drift must stay far below what a user could ever see.
        val miles = 48_210.0
        val back = Units.kmToDisplay(Units.displayToKm(miles, DistanceUnit.MILE), DistanceUnit.MILE)
        assertThat(back).isWithin(1e-6).of(miles)
    }
}
