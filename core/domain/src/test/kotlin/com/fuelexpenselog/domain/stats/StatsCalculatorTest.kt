package com.fuelexpenselog.domain.stats

import com.fuelexpenselog.domain.consumption.ConsumptionEngine
import com.fuelexpenselog.domain.consumption.FuelEvent
import com.fuelexpenselog.domain.consumption.Gap
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.model.HistoryEntry
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.time.MonthKey
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyKind
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StatsCalculatorTest {

    private var nextId = 1L

    private fun fuel(date: CivilDate, km: Double?, litres: Double, total: Double, currency: String = "EUR", full: Boolean = true) =
        HistoryEntry.Fuel(
            FillUp(
                id = nextId++,
                vehicleId = 1,
                date = date,
                instantMillis = date.value.toLong(),
                odometerM = km?.let { DistanceUnit.KILOMETRE.toMetres(it) },
                energy = Energy.of(EnergyUnit.LITRE, litres),
                energyUnitEntered = EnergyUnit.LITRE,
                isFull = full,
                missedPrevious = false,
                total = Money.of(total, currency),
                unitPrice = null,
                tag = EntryTag.PERSONAL,
            ),
        )

    private fun cost(date: CivilDate, amount: Double, category: ExpenseCategory = ExpenseCategory.SERVICE, km: Double? = null, currency: String = "EUR") =
        HistoryEntry.Cost(
            Expense(
                id = nextId++,
                vehicleId = 1,
                date = date,
                instantMillis = date.value.toLong(),
                odometerM = km?.let { DistanceUnit.KILOMETRE.toMetres(it) },
                category = category,
                amount = Money.of(amount, currency),
                tag = EntryTag.PERSONAL,
            ),
        )

    private fun d(y: Int, m: Int, day: Int) = CivilDate.of(y, m, day)

    @Test
    fun `mixed currencies come out as two subtotals and never as a sum`() {
        val entries = listOf(
            fuel(d(2026, 9, 3), 1_000.0, 40.0, 60.0, "EUR"),
            fuel(d(2026, 9, 17), 1_500.0, 40.0, 5_000.0, "INR"),
            cost(d(2026, 9, 20), 25.0, currency = "EUR"),
        )
        val september = StatsCalculator.months(entries).single()

        assertThat(september.total).containsExactly(Money.of(5_000.0, "INR"), Money.of(85.0, "EUR"))
        assertThat(september.total.map { it.currency }).containsNoDuplicates()
        assertThat(StatsCalculator.yearTotals(listOf(september), 2026)).hasSize(2)
        // Nothing to compare across currencies, so no delta either.
        assertThat(StatsCalculator.moneyDelta(september.total, listOf(Money.of(10.0, "EUR")))).isNull()
    }

    @Test
    fun `months with no entries are left out, not shown as zero`() {
        val entries = listOf(
            fuel(d(2026, 6, 10), 1_000.0, 40.0, 60.0),
            fuel(d(2026, 9, 10), 2_000.0, 40.0, 60.0),
        )
        assertThat(StatsCalculator.months(entries).map { it.month })
            .containsExactly(MonthKey.of(2026, 9), MonthKey.of(2026, 6)).inOrder()
    }

    @Test
    fun `a lower L per 100 km is an improvement, a lower MPG is not`() {
        val better = StatsCalculator.consumptionDelta(7.5, 8.0, ConsumptionFormat.L_PER_100KM)!!
        assertThat(better.percent).isWithin(1e-9).of(-6.25)
        assertThat(better.improved).isTrue()

        val worse = StatsCalculator.consumptionDelta(30.0, 32.0, ConsumptionFormat.MPG_US)!!
        assertThat(worse.improved).isFalse()
        assertThat(StatsCalculator.consumptionDelta(null, 8.0, ConsumptionFormat.L_PER_100KM)).isNull()
    }

    @Test
    fun `spending less than last month is the improvement`() {
        val delta = StatsCalculator.moneyDelta(listOf(Money.of(90.0, "USD")), listOf(Money.of(120.0, "USD")))!!
        assertThat(delta.percent).isWithin(1e-9).of(-25.0)
        assertThat(delta.improved).isTrue()
    }

    @Test
    fun `the same month last year is one MonthKey call away`() {
        val september = MonthKey.of(2026, 9)
        val entries = listOf(fuel(d(2025, 9, 14), 500.0, 40.0, 58.0), fuel(d(2026, 9, 14), 20_500.0, 40.0, 61.0))
        val lastYear = StatsCalculator.months(entries).single { it.month == september.minusYears(1) }
        assertThat(lastYear.total).containsExactly(Money.of(58.0, "EUR"))
    }

    @Test
    fun `distance driven needs readings and never claims a zero`() {
        val september = MonthKey.of(2026, 9)
        assertThat(StatsCalculator.distanceDriven(listOf(cost(d(2026, 9, 2), 20.0)), september)).isNull()

        // One reading, but an August reading to measure from.
        val entries = listOf(fuel(d(2026, 8, 28), 10_000.0, 40.0, 60.0), fuel(d(2026, 9, 25), 11_042.0, 40.0, 60.0))
        assertThat(StatsCalculator.distanceDriven(entries, september)).isEqualTo(1_042_000)

        // A lone reading with nothing before it says nothing about distance.
        assertThat(StatsCalculator.distanceDriven(listOf(fuel(d(2026, 9, 25), 11_042.0, 40.0, 60.0)), september)).isNull()
    }

    @Test
    fun `categories put fuel first, then the largest expense`() {
        val september = MonthKey.of(2026, 9)
        val entries = listOf(
            fuel(d(2026, 9, 1), 1_000.0, 40.0, 60.0),
            fuel(d(2026, 9, 15), 1_500.0, 40.0, 62.0),
            cost(d(2026, 9, 3), 12.0, ExpenseCategory.PARKING),
            cost(d(2026, 9, 9), 89.9, ExpenseCategory.OIL_CHANGE),
            cost(d(2026, 8, 9), 500.0, ExpenseCategory.REPAIR),
        )
        val rows = StatsCalculator.categories(entries, september)
        assertThat(rows.map { it.category }).containsExactly(null, ExpenseCategory.OIL_CHANGE, ExpenseCategory.PARKING).inOrder()
        assertThat(rows.first().count).isEqualTo(2)
        assertThat(rows.first().subtotal).containsExactly(Money.of(122.0, "EUR"))
    }

    @Test
    fun `last done at, and how far since`() {
        val entries = listOf(
            cost(d(2026, 3, 1), 80.0, ExpenseCategory.OIL_CHANGE, km = 80_000.0),
            cost(d(2026, 7, 1), 85.0, ExpenseCategory.OIL_CHANGE, km = 83_898.0),
            cost(d(2026, 8, 1), 300.0, ExpenseCategory.SERVICE),
        )
        val oil = StatsCalculator.lastDone(entries, ExpenseCategory.OIL_CHANGE, currentOdometerM = 84_210_000)!!
        assertThat(oil.date).isEqualTo(d(2026, 7, 1))
        assertThat(oil.distanceAgoM).isEqualTo(312_000)
        // A service with no reading is still "last done", just with no distance.
        assertThat(StatsCalculator.lastDone(entries, ExpenseCategory.SERVICE, 84_210_000)!!.distanceAgoM).isNull()
        assertThat(StatsCalculator.lastDone(entries, ExpenseCategory.TYRES, 84_210_000)).isNull()
    }

    @Test
    fun `recent keeps the gaps in their slots and sums the measured ones`() {
        fun ev(id: Long, day: Long, km: Double, litres: Double, missed: Boolean = false) = FuelEvent(
            id = id,
            date = d(2026, 1, 1).plusDays(day),
            instantMillis = day,
            odometerM = DistanceUnit.KILOMETRE.toMetres(km),
            energy = Energy.of(EnergyUnit.LITRE, litres),
            isFull = true,
            missedPrevious = missed,
        )
        val timeline = ConsumptionEngine.compute(
            listOf(
                ev(1, 0, 10_000.0, 40.0),
                ev(2, 10, 10_500.0, 40.0),
                ev(3, 20, 11_000.0, 50.0, missed = true),
                ev(4, 30, 11_600.0, 40.0),
            ),
            EnergyKind.LIQUID,
        ).timeline

        val recent = StatsCalculator.recent(timeline, 9)
        assertThat(recent.points).hasSize(timeline.size)
        assertThat(recent.points.filterIsInstance<Gap>()).isNotEmpty()
        val measured = recent.points.filterIsInstance<Measured>()
        val expected = measured.sumOf { it.distanceM } / 1000.0 / (measured.sumOf { it.energy.micro } / 1e6)
        assertThat(recent.kmPerUnit!!).isWithin(1e-9).of(expected)
    }
}
