package com.fuelexpenselog.csv.export

import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.model.FuelType
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.VehicleType
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.util.Locale

class FuelLogCsvExporterTest {

    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    private fun golden(name: String): String =
        String(checkNotNull(javaClass.getResourceAsStream("/golden/$name")).readBytes(), Charsets.UTF_8)

    private fun vehicle(id: Long, name: String, distance: DistanceUnit, volume: EnergyUnit, currency: String) = Vehicle(
        id = id, name = name, type = VehicleType.CAR, fuelType = FuelType.PETROL,
        distanceUnit = distance, volumeUnit = volume, energyUnit = EnergyUnit.KWH,
        consumptionFormat = null, currencyCode = currency, tankCapacity = null, batteryCapacity = null,
        defaultTag = EntryTag.PERSONAL,
    )

    private val base = CivilDate.of(2026, 1, 1)

    private fun fill(
        id: Long, vehicle: Long, day: Long, odometerM: Long?, energy: Energy, unit: EnergyUnit,
        total: Money?, unitPrice: Money? = null, full: Boolean = true, station: String? = null, note: String? = null,
    ) = FillUp(
        id = id, vehicleId = vehicle, date = base.plusDays(day), instantMillis = day * 86_400_000L,
        odometerM = odometerM, energy = energy, energyUnitEntered = unit, isFull = full, missedPrevious = false,
        total = total, unitPrice = unitPrice, tag = EntryTag.PERSONAL, station = station, note = note,
    )

    private fun data(): Triple<List<Vehicle>, List<FillUp>, List<Expense>> {
        val civic = vehicle(1, "Civic", DistanceUnit.KILOMETRE, EnergyUnit.LITRE, "EUR")
        val truck = vehicle(2, "Silverado", DistanceUnit.MILE, EnergyUnit.US_GALLON, "USD")
        val l = EnergyUnit.LITRE
        val fillUps = listOf(
            // Deliberately out of order: the exporter sorts.
            fill(4, 1, 42, 11_100_000, Energy.of(l, 25.0), l, total = null, unitPrice = Money.of(1.459, "EUR")),
            fill(1, 1, 0, 10_000_000, Energy.of(l, 45.0), l, Money.of(67.5, "EUR")),
            fill(2, 1, 14, 10_520_000, Energy.of(l, 40.0), l, Money.of(60.0, "EUR"), note = "Shell, A1"),
            fill(3, 1, 28, 10_800_000, Energy.of(l, 20.0), l, Money.of(30.0, "EUR"), full = false, station = "=cmd"),
            fill(
                5, 2, 9, DistanceUnit.MILE.toMetres(84_210.0),
                Energy.of(EnergyUnit.US_GALLON, 10.5), EnergyUnit.US_GALLON, Money.of(36.8, "USD"),
            ),
        )
        val expenses = listOf(
            Expense(
                id = 1, vehicleId = 1, date = base.plusDays(20), instantMillis = 20 * 86_400_000L,
                odometerM = 10_700_000, category = ExpenseCategory.OIL_CHANGE, amount = Money.of(89.9, "EUR"),
                tag = EntryTag.BUSINESS, vendor = "Kwik Fit",
            ),
        )
        return Triple(listOf(truck, civic), fillUps, expenses)
    }

    private fun export(): String {
        val (vehicles, fillUps, expenses) = data()
        return StringBuilder().also { FuelLogCsvExporter.write(vehicles, fillUps, expenses, it) }.toString()
    }

    @Test
    fun `the golden dataset exports to the checked-in file`() {
        assertThat(export()).isEqualTo(golden("export.csv"))
    }

    @Test
    fun `an Arabic or German phone writes exactly the same bytes`() {
        val expected = golden("export.csv")
        for (locale in listOf(Locale.forLanguageTag("ar-EG"), Locale.GERMANY, Locale.forLanguageTag("hi-IN"))) {
            Locale.setDefault(locale)
            assertThat(export()).isEqualTo(expected)
        }
    }

    @Test
    fun `money is exact to the micro and never fewer than two decimals`() {
        assertThat(FuelLogCsvExporter.money(58_396_000)).isEqualTo("58.396")
        assertThat(FuelLogCsvExporter.money(60_000_000)).isEqualTo("60.00")
        assertThat(FuelLogCsvExporter.money(0)).isEqualTo("0.00")
    }

    @Test
    fun `free text that a spreadsheet would run as a formula is made inert`() {
        assertThat(FuelLogCsvExporter.text("=HYPERLINK(\"x\")")).isEqualTo("'=HYPERLINK(\"x\")")
        assertThat(FuelLogCsvExporter.text("+44 station")).isEqualTo("'+44 station")
        assertThat(FuelLogCsvExporter.text("Shell")).isEqualTo("Shell")
    }
}
