package com.fuelexpenselog.app.data

import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.csv.imprt.DateFormatResolver
import com.fuelexpenselog.csv.imprt.ImportAssumptions
import com.fuelexpenselog.csv.imprt.ImportPlanner
import com.fuelexpenselog.csv.imprt.StagedEntry
import com.fuelexpenselog.csv.imprt.StagedImport
import com.fuelexpenselog.csv.imprt.StagedRow
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime
import java.time.ZoneOffset

/** Import against a real database: one transaction, one batch, undone as one. */
@RunWith(RobolectricTestRunner::class)
class ImportCommitTest {

    private val t = TestDb()
    private val repo = t.repo

    @After
    fun tearDown() = t.close()

    private val january = "date,odometer,liters,total\n2026-01-04,48200,40,60\n2026-01-18,48700,41.5,62\n2026-02-01,49150,20,31\n"
    private val march = "date,odometer,liters,total\n2026-03-01,49600,38,58.9\n2026-03-15,50080,39,60.2\n"

    private suspend fun stage(vehicleId: Long, csv: String): List<StagedRow> {
        val vehicle = repo.vehicle(vehicleId)!!
        val plan = ImportPlanner.plan(csv.toByteArray(Charsets.UTF_8))
        val assumptions = ImportAssumptions(
            dateOrder = (plan.dates as DateFormatResolver.Result.Resolved).order,
            distanceUnit = DistanceUnit.KILOMETRE,
            volumeUnit = EnergyUnit.LITRE,
        )
        return StagedImport.stage(plan, assumptions, vehicle, ZoneOffset.UTC, CivilDate.of(2026, 9, 26), repo.existingEntries(vehicleId))
    }

    private suspend fun commit(vehicleId: Long, rows: List<StagedRow>, file: String): Long =
        repo.commitImport(
            source = "GENERIC",
            fileName = file,
            vehicleId = vehicleId,
            fuel = rows.filter { it.includedByDefault }.mapNotNull { r -> (r.entry as? StagedEntry.Fuel)?.let { it.fillUp to it.hash } },
            costs = rows.filter { it.includedByDefault }.mapNotNull { r -> (r.entry as? StagedEntry.Cost)?.let { it.expense to it.hash } },
        )

    @Test
    fun `an import is one batch, and undo takes away exactly that batch`() = runBlocking<Unit> {
        val golf = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(golf, day = 200, odometerKm = 60_000.0, litres = 40.0))
        val first = commit(golf, stage(golf, january), "january.csv")
        val second = commit(golf, stage(golf, march), "march.csv")
        assertThat(repo.allFillUps()).hasSize(6)

        val removed = repo.undoImport(first)

        assertThat(removed).isEqualTo(3)
        val left = repo.allFillUps().map { it.odometerM }
        assertThat(left).containsExactly(60_000_000L, 49_600_000L, 50_080_000L)
        assertThat(repo.observeImportBatches().first().map { it.id }).containsExactly(second)
    }

    @Test
    fun `a row that fails takes the whole import back, the batch row included`() = runBlocking {
        val golf = repo.addVehicle(DbFixtures.vehicle())
        val fuel = stage(golf, january).mapNotNull { (it.entry as? StagedEntry.Fuel)?.let { e -> e.fillUp to e.hash } }
        t.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse BEFORE INSERT ON expense BEGIN SELECT RAISE(ABORT, 'refused'); END",
        )

        val result = runCatching {
            repo.commitImport("GENERIC", "january.csv", golf, fuel, listOf(DbFixtures.expense(golf, day = 20, category = ExpenseCategory.SERVICE) to "hash"))
        }

        assertThat(result.isFailure).isTrue()
        assertThat(repo.allFillUps()).isEmpty()
        assertThat(repo.observeImportBatches().first()).isEmpty()
    }

    @Test
    fun `importing the same file again finds every row already there`() = runBlocking {
        val golf = repo.addVehicle(DbFixtures.vehicle())
        commit(golf, stage(golf, january), "january.csv")

        val again = stage(golf, january)

        assertThat(again.all { it.duplicate }).isTrue()
        assertThat(again.none { it.includedByDefault }).isTrue()
    }

    @Test
    fun `history typed in by hand is found by the same half-kilometre, half-day window`() = runBlocking {
        val golf = repo.addVehicle(DbFixtures.vehicle())
        // 18 January at 09:00, 200 m further on and a slightly different volume: no hash to match.
        val typed = DbFixtures.fillUp(golf, day = 17, odometerKm = 48_700.2, litres = 41.4)
        repo.addFillUp(
            typed.copy(
                instantMillis = CivilDate.of(2026, 1, 18).toLocalDate().atTime(LocalTime.of(9, 0)).toInstant(ZoneOffset.UTC).toEpochMilli(),
                energy = Energy.of(EnergyUnit.LITRE, 41.4),
            ),
        )

        val rows = stage(golf, january)

        assertThat(rows.map { it.duplicate }).containsExactly(false, true, false).inOrder()
    }

    @Test
    fun `an imported row keeps its stamp when edited, so undo and re-import still know it`() = runBlocking {
        val golf = repo.addVehicle(DbFixtures.vehicle())
        val batch = commit(golf, stage(golf, january), "january.csv")
        val edited = repo.allFillUps().first()
        repo.updateFillUp(edited.copy(note = "the receipt says 60.10", energy = Energy.of(EnergyUnit.LITRE, 40.1)))

        // Its content changed, but the file's row is still recognised by the stamp.
        assertThat(stage(golf, january).all { it.duplicate }).isTrue()
        assertThat(repo.undoImport(batch)).isEqualTo(3)
        assertThat(repo.allFillUps()).isEmpty()
    }
}
