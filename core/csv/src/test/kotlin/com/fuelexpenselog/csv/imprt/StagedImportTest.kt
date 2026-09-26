package com.fuelexpenselog.csv.imprt

import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneOffset

class StagedImportTest {

    private val golf = Fixtures.vehicle()

    private fun fuel(rows: List<StagedRow>) = rows.mapNotNull { (it.entry as? StagedEntry.Fuel)?.fillUp }

    private fun inline(csv: String) = ImportPlanner.plan(csv.toByteArray(Charsets.UTF_8))

    @Test
    fun `aCar's "Partial Fill-up - Yes" is a tank that was not filled`() {
        val truck = Fixtures.vehicle(distance = DistanceUnit.MILE, volume = EnergyUnit.US_GALLON, currency = "USD")
        val rows = Fixtures.stage(Fixtures.plan("acar.csv"), truck)

        val fillUps = fuel(rows)
        assertThat(fillUps.map { it.isFull }).containsExactly(true, true, false, true).inOrder()
        val first = fillUps.first()
        assertThat(first.odometerM).isEqualTo(DistanceUnit.MILE.toMetres(30_120.0))
        assertThat(first.energy).isEqualTo(Energy.of(EnergyUnit.US_GALLON, 11.2))
        assertThat(first.total).isEqualTo(Money.of(38.74, "USD"))
        // The file's own time of day orders same-day entries.
        assertThat(first.instantMillis).isEqualTo(CivilDate.of(2026, 1, 4).toLocalDate().atTime(LocalTime.of(8, 15)).toInstant(ZoneOffset.UTC).toEpochMilli())
    }

    @Test
    fun `Fuelio's Missed flag means a fill-up before this one is missing, the app's own direction`() {
        val rows = Fixtures.stage(Fixtures.plan("fuelio.csv"), golf)

        val fillUps = fuel(rows)
        assertThat(fillUps.map { it.missedPrevious }).containsExactly(false, false, false, true).inOrder()
        assertThat(fillUps.map { it.isFull }).containsExactly(true, true, false, true).inOrder()
        assertThat(fillUps[2].note).isEqualTo("half, then home")
        assertThat(fillUps[2].station).isEqualTo("Potsdam")
    }

    @Test
    fun `Fuelio's costs take their category from its own category list, and a zero reading is none`() {
        val costs = Fixtures.stage(Fixtures.plan("fuelio.csv"), golf).mapNotNull { (it.entry as? StagedEntry.Cost)?.expense }

        assertThat(costs.map { it.category }).containsExactly(ExpenseCategory.SERVICE, ExpenseCategory.INSURANCE).inOrder()
        assertThat(costs[0].note).isEqualTo("Oil and filter · 5W-30")
        assertThat(costs[0].amount).isEqualTo(Money.of(89.90, "EUR"))
        assertThat(costs[0].odometerM).isEqualTo(48_750_000)
        assertThat(costs[1].odometerM).isNull()
    }

    @Test
    fun `a semicolon file reads 32,5 as thirty-two and a half, and a grouped reading as thousands`() {
        val rows = Fixtures.stage(Fixtures.plan("german-semicolon.csv"), golf)

        val second = fuel(rows)[1]
        assertThat(second.energy).isEqualTo(Energy.of(EnergyUnit.LITRE, 32.5))
        assertThat(second.odometerM).isEqualTo(48_700_000)
        assertThat(second.total).isEqualTo(Money.of(48.90, "EUR"))
        val third = fuel(rows)[2]
        assertThat(third.isFull).isFalse()
        assertThat(third.note).isEqualTo("halb voll, Wäsche danach")
    }

    @Test
    fun `Drivvo's Portuguese file reads its own words and times`() {
        val rows = Fixtures.stage(Fixtures.plan("drivvo.csv"), Fixtures.vehicle(currency = "BRL"))

        val fillUps = fuel(rows)
        assertThat(fillUps.map { it.isFull }).containsExactly(true, true, false, true).inOrder()
        assertThat(fillUps[0].energy).isEqualTo(Energy.of(EnergyUnit.LITRE, 35.2))
        assertThat(fillUps[0].total).isEqualTo(Money.of(207.33, "BRL"))
        assertThat(fillUps[2].note).isEqualTo("meio tanque")
    }

    @Test
    fun `a row with only a unit price keeps it, and the total is left to be derived`() {
        val rows = Fixtures.stage(inline("date,odometer,liters,price\n2026-01-04,100,40,\"1,459\"\n"), golf)

        val only = fuel(rows).single()
        assertThat(only.total).isNull()
        assertThat(only.unitPrice).isEqualTo(Money.of(1.459, "EUR"))
    }

    @Test
    fun `rows already in the app are duplicates, excluded by default, hand-typed ones found by their window`() {
        val plan = Fixtures.plan("fuelio.csv")
        val first = fuel(Fixtures.stage(plan, golf)).first()
        // Typed by hand three hours earlier, 300 m apart, a slightly different volume: the same fill-up.
        val handTyped: FillUp = fuel(Fixtures.stage(plan, golf))[1].let {
            it.copy(id = 99, instantMillis = it.instantMillis - 3 * 3_600_000L, odometerM = it.odometerM!! + 300, energy = Energy.of(EnergyUnit.LITRE, 41.4))
        }

        val rows = Fixtures.stage(plan, golf, ExistingEntries(listOf(first.copy(id = 1), handTyped), emptyList()))

        val flagged = rows.filter { it.duplicate }.map { it.line }
        assertThat(flagged).containsExactly(rows[0].line, rows[1].line)
        assertThat(rows.filter { it.includedByDefault }.size).isEqualTo(rows.size - 2)
    }

    @Test
    fun `the same row twice in one file is a duplicate of the first`() {
        val rows = Fixtures.stage(inline("date,odometer,liters,total\n2026-01-04,100,40,60\n2026-01-04,100,40,60\n"), golf)
        assertThat(rows.map { it.duplicate }).containsExactly(false, true).inOrder()
    }

    @Test
    fun `unreadable rows are errors that say why, and a future date only warns`() {
        val rows = Fixtures.stage(
            inline("date,odometer,liters,total\n2026-01-04,100,40,60\nsoon,200,40,60\n2026-01-20,300,,60\n2026-02-01,400,40,\n2027-01-01,500,40,60\n"),
            golf,
        )

        assertThat(rows.map { it.issues }).containsExactly(
            emptyList<RowIssue>(),
            listOf(RowIssue.DATE_UNREADABLE),
            listOf(RowIssue.VOLUME_UNREADABLE),
            listOf(RowIssue.AMOUNT_UNREADABLE),
            listOf(RowIssue.FUTURE_DATE),
        ).inOrder()
        assertThat(rows.map { it.isError }).containsExactly(false, true, true, true, false).inOrder()
        assertThat(rows.last().hasWarnings).isTrue()
    }

    @Test
    fun `nothing is read while the file's dates are unanswered`() {
        val plan = inline("date,odometer,liters,total\n01/02/2026,100,40,60\n03/04/2026,200,40,60\n")
        assertThat(plan.dates).isInstanceOf(DateFormatResolver.Result.Ambiguous::class.java)

        val rows = Fixtures.stage(plan, golf)
        assertThat(rows.all { it.issues == listOf(RowIssue.DATE_UNREADABLE) }).isTrue()

        val answered = StagedImport.stage(plan, Fixtures.assumptions(plan, golf).copy(dateOrder = DateOrder.DMY), golf, Fixtures.ZONE, Fixtures.TODAY)
        assertThat(fuel(answered).map { it.date }).containsExactly(CivilDate.of(2026, 2, 1), CivilDate.of(2026, 4, 3)).inOrder()
    }

    @Test
    fun `categories from other apps are guessed by word, and an unknown one says so`() {
        assertThat(CategoryGuess.of("Ölwechsel")).isEqualTo(ExpenseCategory.OIL_CHANGE)
        assertThat(CategoryGuess.of("Pedágio")).isEqualTo(ExpenseCategory.TOLL)
        assertThat(CategoryGuess.of("Toilet paper")).isNull()
        assertThat(CategoryGuess.of("New tires")).isEqualTo(ExpenseCategory.TYRES)
    }
}
