package com.fuelexpenselog.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fuelexpenselog.app.data.db.FuelLogDatabase
import com.fuelexpenselog.app.domain.consumption.computeAllSpans
import com.fuelexpenselog.app.domain.model.DistanceUnit
import com.fuelexpenselog.app.domain.model.Expense
import com.fuelexpenselog.app.domain.model.ExpenseCategory
import com.fuelexpenselog.app.domain.model.FillUp
import com.fuelexpenselog.app.domain.model.Vehicle
import com.fuelexpenselog.app.domain.model.VolumeUnit
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
@Config(sdk = [34])
class FuelLogRepositoryTest {

    private lateinit var db: FuelLogDatabase
    private lateinit var repo: FuelLogRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FuelLogDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = FuelLogRepository(db.vehicleDao(), db.fillUpDao(), db.expenseDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedVehicle(): Long = repo.addVehicle(
        Vehicle(
            name = "Honda City",
            distanceUnit = DistanceUnit.KILOMETRE,
            volumeUnit = VolumeUnit.LITRE,
            currency = "INR",
            tankCapacityLitres = 40.0,
        )
    )

    private fun fillUp(vehicleId: Long, odometer: Double, volume: Double, day: Long, full: Boolean = true) =
        FillUp(
            vehicleId = vehicleId,
            date = day * 86_400_000L,
            odometer = odometer,
            volume = volume,
            totalCost = volume * 100.0,
            isFullTank = full,
        )

    @Test
    fun `a vehicle round-trips through the database unchanged`() = runTest {
        val id = seedVehicle()
        val loaded = repo.getVehicle(id)

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("Honda City")
        assertThat(loaded.distanceUnit).isEqualTo(DistanceUnit.KILOMETRE)
        assertThat(loaded.volumeUnit).isEqualTo(VolumeUnit.LITRE)
        assertThat(loaded.currency).isEqualTo("INR")
        assertThat(loaded.tankCapacityLitres).isEqualTo(40.0)
        assertThat(loaded.isActive).isTrue()
    }

    @Test
    fun `fill-ups come back ordered the way the engine wants them`() = runTest {
        val id = seedVehicle()
        // Inserted out of order on purpose.
        repo.addFillUp(fillUp(id, odometer = 600.0, volume = 30.0, day = 7))
        repo.addFillUp(fillUp(id, odometer = 100.0, volume = 40.0, day = 0))
        repo.addFillUp(fillUp(id, odometer = 300.0, volume = 20.0, day = 3, full = false))

        val fillUps = repo.fillUps(id).first()

        assertThat(fillUps.map { it.odometer }).containsExactly(100.0, 300.0, 600.0).inOrder()
        // And the engine gets the right answer straight off the query.
        val spans = computeAllSpans(fillUps)
        assertThat(spans).hasSize(1)
        assertThat(spans[0].fuel).isEqualTo(50.0)
    }

    @Test
    fun `deleting a vehicle cascades to its entries`() = runTest {
        val id = seedVehicle()
        repo.addFillUp(fillUp(id, odometer = 100.0, volume = 40.0, day = 0))
        repo.addExpense(
            Expense(vehicleId = id, date = 0, category = ExpenseCategory.SERVICE, totalCost = 200.0)
        )

        repo.deleteVehicle(repo.getVehicle(id)!!)

        assertThat(repo.allFillUps()).isEmpty()
        assertThat(repo.allExpenses()).isEmpty()
    }

    @Test
    fun `latest odometer considers expenses as well as fill-ups`() = runTest {
        val id = seedVehicle()
        repo.addFillUp(fillUp(id, odometer = 48_000.0, volume = 40.0, day = 0))
        repo.addExpense(
            Expense(
                vehicleId = id, date = 86_400_000L, odometer = 48_200.0,
                category = ExpenseCategory.OIL_CHANGE, totalCost = 60.0,
            )
        )

        assertThat(repo.latestOdometerKm(id)).isEqualTo(48_200.0)
    }

    @Test
    fun `an expense with no odometer does not break the latest reading`() = runTest {
        val id = seedVehicle()
        repo.addFillUp(fillUp(id, odometer = 48_000.0, volume = 40.0, day = 0))
        repo.addExpense(
            Expense(vehicleId = id, date = 0, category = ExpenseCategory.INSURANCE, totalCost = 500.0)
        )

        assertThat(repo.latestOdometerKm(id)).isEqualTo(48_000.0)
    }

    @Test
    fun `a same-day repeat reading is reported as a likely duplicate`() = runTest {
        val id = seedVehicle()
        val day = 5 * 86_400_000L
        repo.addFillUp(
            FillUp(
                vehicleId = id, date = day, odometer = 48_210.0, volume = 32.5,
                totalCost = 4550.0, isFullTank = true,
            )
        )

        assertThat(repo.looksLikeDuplicate(id, day, 48_210.0)).isTrue()
        // A different reading on the same day is a real second stop, not a repeat.
        assertThat(repo.looksLikeDuplicate(id, day, 48_400.0)).isFalse()
        // The same reading a week later is a stalled odometer, not a repeat.
        assertThat(repo.looksLikeDuplicate(id, day + 7 * 86_400_000L, 48_210.0)).isFalse()
    }

    @Test
    fun `an unrecognised category degrades to OTHER instead of throwing`() = runTest {
        val id = seedVehicle()
        repo.addExpense(
            Expense(vehicleId = id, date = 0, category = ExpenseCategory.TYRES, totalCost = 300.0)
        )
        // Simulates a row written by a future version that added a category.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE expense SET category = 'SOMETHING_FROM_THE_FUTURE'"
        )

        val loaded = repo.allExpenses()

        assertThat(loaded).hasSize(1)
        assertThat(loaded[0].category).isEqualTo(ExpenseCategory.OTHER)
    }

    @Test
    fun `editing a fill-up recomputes rather than leaving a stale figure`() = runTest {
        val id = seedVehicle()
        repo.addFillUp(fillUp(id, odometer = 100.0, volume = 40.0, day = 0))
        val second = repo.addFillUp(fillUp(id, odometer = 600.0, volume = 50.0, day = 7))

        assertThat(computeAllSpans(repo.fillUps(id).first())[0].kmPerLitre).isEqualTo(10.0)

        // Correct a mistyped volume; every downstream figure must follow.
        repo.updateFillUp(repo.getFillUp(second)!!.copy(volume = 25.0))

        assertThat(computeAllSpans(repo.fillUps(id).first())[0].kmPerLitre).isEqualTo(20.0)
    }
}
