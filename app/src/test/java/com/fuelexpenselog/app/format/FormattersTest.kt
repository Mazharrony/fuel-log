package com.fuelexpenselog.app.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class FormattersTest {

    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `German grouping and decimal marks`() {
        val de = Locale.GERMANY
        assertThat(DistanceFormatter(de, res).format(84_210_000, DistanceUnit.KILOMETRE)).isEqualTo("84.210 km")
        assertThat(CurrencyFormatter(de).format(Money.of(1234.5, "EUR"))).contains("1.234,50")
        assertThat(ConsumptionFormatter(de, res).value(8.2891, ConsumptionFormat.L_PER_100KM)).isEqualTo("8,3")
        assertThat(VolumeFormatter(de, res).number(Energy.of(EnergyUnit.LITRE, 41.5), EnergyUnit.LITRE)).isEqualTo("41,50")
    }

    @Test
    fun `the currency decides the symbol and decimals, the locale decides the layout`() {
        val us = CurrencyFormatter(Locale.US)
        assertThat(us.format(Money.of(1234.5, "EUR"))).isEqualTo("€1,234.50")
        assertThat(us.format(Money.of(1234.5, "USD"))).isEqualTo("$1,234.50")
        assertThat(us.format(Money.of(1234.5, "JPY"))).isEqualTo("¥1,235")
        // A rate keeps two decimals even in a currency that has none.
        assertThat(us.formatRate(Money.of(0.5, "JPY"))).isEqualTo("¥0.50")
    }

    @Test
    fun `a currency code the platform does not know is shown, not dropped`() {
        assertThat(CurrencyFormatter(Locale.US).format(Money.of(12.5, "ZZZ"))).isEqualTo("ZZZ 12.50")
        assertThat(CurrencyFormatter(Locale.US).symbol("ZZZ")).isEqualTo("ZZZ")
    }

    @Test
    fun `no figure is a dash, never a zero`() {
        val f = ConsumptionFormatter(Locale.US, res)
        assertThat(f.value(null, ConsumptionFormat.MPG_US)).isEqualTo("—")
        assertThat(f.value(Double.NaN, ConsumptionFormat.MPG_US)).isEqualTo("—")
        assertThat(f.value(12.049, ConsumptionFormat.KM_PER_L)).isEqualTo("12.05")
    }

    @Test
    fun `odometers are whole units with the unit label`() {
        val f = DistanceFormatter(Locale.US, res)
        assertThat(f.format(DistanceUnit.MILE.toMetres(84_210.0), DistanceUnit.MILE)).isEqualTo("84,210 mi")
        assertThat(f.number(83_898_400, DistanceUnit.KILOMETRE)).isEqualTo("83,898")
    }

    @Test
    fun `a civil date never shifts a day`() {
        val date = CivilDate.of(2026, 9, 17)
        assertThat(DateFormatter(Locale.US).medium(date)).isEqualTo("Sep 17, 2026")
    }
}
