package com.fuelexpenselog.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fuelexpenselog.app.data.db.FuelLogDatabase
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.domain.consumption.ConsumptionEngine
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyKind
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class FuelLogRepositoryTest {

    private lateinit var db: FuelLogDatabase
    private lateinit var repo: FuelLogRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FuelLogDatabase::class.java,
        ).allowMainThreadQueries().build()

        repo = FuelLogRepository(db) { DbFixtures.NOW }
    }

    @After
    fun tearDown() = db.close()

    // -- round trips ---------------------------------------------------------------------

    @Test
    fun `a vehicle comes back exactly as it went in`() = runTest {
        val id = repo.addVehicle(DbFixtures.vehicle(currency = "GBP"))
        val back = repo.vehicle(id)!!

        assertThat(back.name).isEqualTo("Honda Civic")
        assertThat(back.currencyCode).isEqualTo("GBP")
        assertThat(back.volumeUnit).isEqualTo(EnergyUnit.LITRE)
        assertThat(back.tankCapacity!!.inUnit(EnergyUnit.LITRE)).isWithin(1e-6).of(47.0)
        assertThat(back.isEditable).isTrue()
    }

    @Test
    fun `an amount survives storage exactly, to the micro`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        // A three-decimal unit price is the ordinary case, and the reason money is micros.
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 100.0, litres = 40.0, total = 58.396))

        val back = repo.observeFillUps(v).first().single()
        assertThat(back.total!!.micros).isEqualTo(58_396_000L)
        assertThat(back.unitPriceOrDerived()!!.asDouble).isWithin(1e-6).of(58.396 / 40.0)
    }

    @Test
    fun `fill-ups come back in the order the engine wants, whatever order they went in`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(v, day = 20, odometerKm = 600.0, litres = 30.0))
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 100.0, litres = 50.0))
        repo.addFillUp(DbFixtures.fillUp(v, day = 10, odometerKm = 300.0, litres = 20.0, full = false))

        val ordered = repo.observeFillUps(v).first()
        assertThat(ordered.map { DistanceUnit.KILOMETRE.fromMetres(it.odometerM!!) })
            .containsExactly(100.0, 300.0, 600.0).inOrder()
    }

    // -- cascade ---------------------------------------------------------------------------

    @Test
    fun `deleting a vehicle takes its entries with it`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 100.0, litres = 40.0))
        repo.addExpense(DbFixtures.expense(v, day = 1))

        repo.deleteVehicle(repo.vehicle(v)!!)

        assertThat(repo.allFillUps()).isEmpty()
        assertThat(repo.allExpenses()).isEmpty()
        assertThat(repo.allVehicles()).isEmpty()
    }

    // -- the current odometer ----------------------------------------------------------------

    @Test
    fun `the current reading comes from expenses too, because a service records one`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 48_000.0, litres = 40.0))
        repo.addExpense(DbFixtures.expense(v, day = 5, odometerKm = 48_500.0))

        assertThat(DistanceUnit.KILOMETRE.fromMetres(repo.currentOdometer(v)!!))
            .isWithin(1e-3).of(48_500.0)
    }

    @Test
    fun `an expense with no reading does not break the current reading`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 48_000.0, litres = 40.0))
        repo.addExpense(DbFixtures.expense(v, day = 5, category = ExpenseCategory.PARKING))

        assertThat(DistanceUnit.KILOMETRE.fromMetres(repo.currentOdometer(v)!!))
            .isWithin(1e-3).of(48_000.0)
    }

    @Test
    fun `a vehicle with nothing recorded has no reading, rather than a zero`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        assertThat(repo.currentOdometer(v)).isNull()
    }

    // -- duplicate detection is a warning, and has to be neither deaf nor paranoid -----------

    @Test
    fun `a same-day repeat of the same reading is flagged`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        val entry = DbFixtures.fillUp(v, day = 0, odometerKm = 48_000.0, litres = 40.0)
        repo.addFillUp(entry)

        assertThat(repo.looksLikeDuplicate(v, entry.instantMillis, entry.odometerM!!)).isTrue()
    }

    @Test
    fun `two genuine stops on one day are not flagged`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 48_000.0, litres = 40.0))

        val later = DbFixtures.fillUp(v, day = 0, odometerKm = 48_300.0, litres = 20.0)
        assertThat(repo.looksLikeDuplicate(v, later.instantMillis, later.odometerM!!)).isFalse()
    }

    @Test
    fun `the same reading a week later is not flagged`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 48_000.0, litres = 40.0))

        val later = DbFixtures.fillUp(v, day = 7, odometerKm = 48_000.0, litres = 40.0)
        assertThat(repo.looksLikeDuplicate(v, later.instantMillis, later.odometerM!!)).isFalse()
    }

    // -- values from a newer version ---------------------------------------------------------

    @Test
    fun `an unknown category degrades to OTHER so the row still opens`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        val e = repo.addExpense(DbFixtures.expense(v, day = 0, category = ExpenseCategory.TOLL))

        db.openHelper.writableDatabase.execSQL(
            "UPDATE expense SET category = 'CONGESTION_CHARGE' WHERE id = $e",
        )

        val back = repo.observeExpenses(v).first().single()
        assertThat(back.category).isEqualTo(ExpenseCategory.OTHER)
        // A category has an honest catch-all, so the row stays editable and its cost counts.
        assertThat(back.isEditable).isTrue()
    }

    @Test
    fun `a row with an unrecognised unit opens but refuses to be saved over`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 100.0, litres = 40.0))

        // As if a newer version had written this and the backup were restored here.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE fill_up SET energyUnitEntered = 'HYDROGEN_KG'",
        )

        val back = repo.observeFillUps(v).first().single()

        // Visible - hiding somebody's data is worse than showing it.
        assertThat(back.energy.micro).isEqualTo(40_000_000L)
        // But not writable: saving would put LITRE over the real value, permanently.
        assertThat(back.isEditable).isFalse()
        assertThat(back.unreadable).contains("energyUnitEntered=HYDROGEN_KG")

        val refused = runCatching { repo.updateFillUp(back.copy(note = "just editing the note")) }
        assertThat(refused.isFailure).isTrue()
    }

    // -- derived figures are never stale --------------------------------------------------------

    @Test
    fun `correcting a volume changes the figure, because nothing derived was stored`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 0.0, litres = 50.0))
        repo.addFillUp(DbFixtures.fillUp(v, day = 10, odometerKm = 500.0, litres = 50.0))

        val vehicle = repo.vehicle(v)!!
        assertThat(repo.observeConsumption(vehicle).first().measured.single().kmPerUnit)
            .isWithin(1e-9).of(10.0)

        // The 50 was a typo for 40.
        val latest = repo.observeFillUps(v).first().last()
        repo.updateFillUp(latest.copy(energy = com.fuelexpenselog.domain.unit.Energy.of(EnergyUnit.LITRE, 40.0)))

        assertThat(repo.observeConsumption(vehicle).first().measured.single().kmPerUnit)
            .isWithin(1e-9).of(12.5)
    }

    @Test
    fun `the engine gets the same answer through the database as it does in memory`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        val entries = listOf(
            DbFixtures.fillUp(v, day = 0, odometerKm = 10_000.0, litres = 45.0),
            DbFixtures.fillUp(v, day = 14, odometerKm = 10_520.0, litres = 40.0),
            DbFixtures.fillUp(v, day = 28, odometerKm = 10_800.0, litres = 20.0, full = false),
            DbFixtures.fillUp(v, day = 42, odometerKm = 11_100.0, litres = 25.0),
            DbFixtures.fillUp(v, day = 56, odometerKm = 11_650.0, litres = 44.0),
            DbFixtures.fillUp(v, day = 70, odometerKm = 12_200.0, litres = 50.0, missed = true),
            DbFixtures.fillUp(v, day = 84, odometerKm = 12_700.0, litres = 40.0),
        )
        entries.forEach { repo.addFillUp(it) }

        val vehicle = repo.vehicle(v)!!
        val throughDb = repo.observeConsumption(vehicle).first()
        val inMemory = ConsumptionEngine.compute(
            entries.mapIndexed { i, f -> f.copy(id = (i + 1).toLong()) }.map { it.toFuelEvent() },
            EnergyKind.LIQUID,
            emptyList(),
            DistanceUnit.KILOMETRE,
        )

        // Same figures the golden dataset asserts by hand, arrived at through storage.
        assertThat(throughDb.measured.map { it.kmPerUnit })
            .isEqualTo(inMemory.measured.map { it.kmPerUnit })
        assertThat(throughDb.timeline.map { it::class }).isEqualTo(inMemory.timeline.map { it::class })
        assertThat(throughDb.summary!!.lifetimeKmPerUnit!!).isWithin(1e-6).of(2150.0 / 169.0)
    }

    @Test
    fun `combined history interleaves fuel and costs, newest first`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 100.0, litres = 40.0))
        repo.addExpense(DbFixtures.expense(v, day = 5, category = ExpenseCategory.TOLL, amount = 12.0))
        repo.addFillUp(DbFixtures.fillUp(v, day = 10, odometerKm = 600.0, litres = 35.0))

        val history = repo.observeHistory(v).first()

        assertThat(history).hasSize(3)
        assertThat(history.map { it.date.value }).isInOrder(Comparator<Int> { a, b -> b.compareTo(a) })
        assertThat(history.first().amount!!.asDouble).isWithin(1e-6).of(35.0 * 1.5)
    }

    @Test
    fun `the last entry in a category is what powers a service reminder`() = runTest {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addExpense(DbFixtures.expense(v, day = 0, category = ExpenseCategory.OIL_CHANGE, odometerKm = 40_000.0))
        repo.addExpense(DbFixtures.expense(v, day = 100, category = ExpenseCategory.OIL_CHANGE, odometerKm = 48_200.0))
        repo.addExpense(DbFixtures.expense(v, day = 120, category = ExpenseCategory.PARKING))

        val last = repo.lastInCategory(v, ExpenseCategory.OIL_CHANGE)!!
        assertThat(DistanceUnit.KILOMETRE.fromMetres(last.odometerM!!)).isWithin(1e-3).of(48_200.0)
    }
}
