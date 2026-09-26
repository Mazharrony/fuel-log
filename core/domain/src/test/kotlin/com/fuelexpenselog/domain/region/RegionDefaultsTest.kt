package com.fuelexpenselog.domain.region

import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RegionDefaultsTest {

    @Test
    fun `the six seed markets, exactly`() {
        assertThat(Regions.seed).containsExactly(
            RegionDefaults("US", DistanceUnit.MILE, EnergyUnit.US_GALLON, ConsumptionFormat.MPG_US, "USD"),
            RegionDefaults("GB", DistanceUnit.MILE, EnergyUnit.LITRE, ConsumptionFormat.MPG_UK, "GBP"),
            RegionDefaults("CA", DistanceUnit.KILOMETRE, EnergyUnit.LITRE, ConsumptionFormat.L_PER_100KM, "CAD"),
            RegionDefaults("AU", DistanceUnit.KILOMETRE, EnergyUnit.LITRE, ConsumptionFormat.L_PER_100KM, "AUD"),
            RegionDefaults("DE", DistanceUnit.KILOMETRE, EnergyUnit.LITRE, ConsumptionFormat.L_PER_100KM, "EUR"),
            RegionDefaults("IN", DistanceUnit.KILOMETRE, EnergyUnit.LITRE, ConsumptionFormat.KM_PER_L, "INR"),
        ).inOrder()
    }

    @Test
    fun `an unknown code is metric in L per 100 km, priced in dollars`() {
        val unknown = Regions.forCountry("ZZ")
        assertThat(unknown.distanceUnit).isEqualTo(DistanceUnit.KILOMETRE)
        assertThat(unknown.volumeUnit).isEqualTo(EnergyUnit.LITRE)
        assertThat(unknown.consumptionFormat).isEqualTo(ConsumptionFormat.L_PER_100KM)
        assertThat(unknown.currencyCode).isEqualTo("USD")
        assertThat(Regions.forCountry("").currencyCode).isEqualTo("USD")
    }

    @Test
    fun `Liberia and Myanmar measure in miles and US gallons too`() {
        listOf("LR", "MM").forEach { code ->
            assertThat(Regions.forCountry(code).distanceUnit).isEqualTo(DistanceUnit.MILE)
            assertThat(Regions.forCountry(code).volumeUnit).isEqualTo(EnergyUnit.US_GALLON)
        }
    }

    @Test
    fun `codes are case-insensitive and every listed country has a currency code`() {
        assertThat(Regions.forCountry("gb")).isEqualTo(Regions.forCountry("GB"))
        assertThat(Regions.all.size).isGreaterThan(200)
        Regions.all.forEach { assertThat(it.currencyCode).hasLength(3) }
    }
}
