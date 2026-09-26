package com.fuelexpenselog.app.format

import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocaleDefaultsTest {

    @Test
    fun `en-US is miles, US gallons, MPG and dollars`() {
        val us = LocaleDefaults.detect(Locale.US)
        assertThat(us.countryCode).isEqualTo("US")
        assertThat(us.distanceUnit).isEqualTo(DistanceUnit.MILE)
        assertThat(us.volumeUnit).isEqualTo(EnergyUnit.US_GALLON)
        assertThat(us.consumptionFormat).isEqualTo(ConsumptionFormat.MPG_US)
        assertThat(us.currencyCode).isEqualTo("USD")
    }

    @Test
    fun `en-GB is miles and litres with UK MPG, in pounds`() {
        val gb = LocaleDefaults.detect(Locale.UK)
        assertThat(gb.distanceUnit).isEqualTo(DistanceUnit.MILE)
        assertThat(gb.volumeUnit).isEqualTo(EnergyUnit.LITRE)
        assertThat(gb.consumptionFormat).isEqualTo(ConsumptionFormat.MPG_UK)
        assertThat(gb.currencyCode).isEqualTo("GBP")
    }

    @Test
    fun `de-DE is metric in L per 100 km and euros`() {
        val de = LocaleDefaults.detect(Locale.GERMANY)
        assertThat(de.distanceUnit).isEqualTo(DistanceUnit.KILOMETRE)
        assertThat(de.consumptionFormat).isEqualTo(ConsumptionFormat.L_PER_100KM)
        assertThat(de.currencyCode).isEqualTo("EUR")
    }

    @Test
    fun `en-IN is metric in km per litre and rupees`() {
        val india = LocaleDefaults.detect(Locale("en", "IN"))
        assertThat(india.distanceUnit).isEqualTo(DistanceUnit.KILOMETRE)
        assertThat(india.consumptionFormat).isEqualTo(ConsumptionFormat.KM_PER_L)
        assertThat(india.currencyCode).isEqualTo("INR")
    }

    @Test
    fun `a locale with no country falls back on the language's measurement system`() {
        assertThat(LocaleDefaults.detect(Locale("fr")).distanceUnit).isEqualTo(DistanceUnit.KILOMETRE)
    }
}
