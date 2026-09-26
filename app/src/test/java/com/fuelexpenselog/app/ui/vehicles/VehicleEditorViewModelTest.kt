package com.fuelexpenselog.app.ui.vehicles

import androidx.lifecycle.SavedStateHandle
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.data.repo.toEntity
import com.fuelexpenselog.app.testing.MainDispatcherRule
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class VehicleEditorViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t = TestDb()

    @After
    fun tearDown() = t.close()

    private fun editor(id: Long = 0L) = VehicleEditorViewModel(
        handle = SavedStateHandle(if (id == 0L) emptyMap() else mapOf(Routes.ARG_ID to id)),
        repository = t.repo,
        prefs = t.prefs,
        locale = { Locale.US },
    )

    @Test
    fun `changing a vehicle's units leaves every stored reading and volume byte-identical`() = runTest {
        val id = t.repo.addVehicle(DbFixtures.vehicle(tankLitres = 47.0))
        t.repo.addFillUp(DbFixtures.fillUp(id, day = 0, odometerKm = 48_200.0, litres = 40.0))
        t.repo.addFillUp(DbFixtures.fillUp(id, day = 10, odometerKm = 48_700.0, litres = 41.5))
        t.repo.addExpense(DbFixtures.expense(id, day = 12, odometerKm = 48_750.0))
        val fillUpsBefore = t.db.fillUpDao().all()
        val expensesBefore = t.db.expenseDao().all()
        val tankBefore = t.repo.vehicle(id)!!.tankCapacity

        val vm = editor(id)
        vm.state.first { it.form != null }
        vm.onDistanceUnit(DistanceUnit.MILE)
        vm.onVolumeUnit(EnergyUnit.US_GALLON)
        vm.save()
        vm.state.first { it.done }

        assertThat(t.db.fillUpDao().all()).isEqualTo(fillUpsBefore)
        assertThat(t.db.expenseDao().all()).isEqualTo(expensesBefore)

        val after = t.repo.vehicle(id)!!
        assertThat(after.distanceUnit).isEqualTo(DistanceUnit.MILE)
        assertThat(after.volumeUnit).isEqualTo(EnergyUnit.US_GALLON)
        // An untouched tank field writes the stored capacity back, not a rounded conversion.
        assertThat(after.tankCapacity).isEqualTo(tankBefore)
    }

    @Test
    fun `the tank field re-expresses the same tank when the volume unit changes`() = runTest {
        val id = t.repo.addVehicle(DbFixtures.vehicle(tankLitres = 47.0))
        val vm = editor(id)
        assertThat(vm.state.first { it.form != null }.form!!.tankText).isEqualTo("47")

        vm.onVolumeUnit(EnergyUnit.US_GALLON)

        assertThat(vm.state.first { it.form?.volumeUnit == EnergyUnit.US_GALLON }.form!!.tankText).isEqualTo("12.42")
    }

    @Test
    fun `an unreadable vehicle cannot be saved but can still be hidden`() = runTest {
        val raw = DbFixtures.vehicle().toEntity().copy(volumeUnit = "HYDROGEN_KG")
        val id = t.db.vehicleDao().insert(raw)

        val vm = editor(id)
        val state = vm.state.first { it.form != null }
        assertThat(state.isEditable).isFalse()
        assertThat(state.canSave).isFalse()

        vm.onActive(false)

        assertThat(t.repo.observeVehicle(id).first { it?.isArchived == true }).isNotNull()
        // The value this build cannot read is still exactly what the newer version wrote.
        assertThat(t.db.vehicleDao().byId(id)!!.volumeUnit).isEqualTo("HYDROGEN_KG")
    }

    @Test
    fun `a name is the only thing a new vehicle needs`() = runTest {
        val vm = editor()
        assertThat(vm.state.first { it.form != null }.canSave).isFalse()

        vm.onName("  Honda Civic ")
        assertThat(vm.state.first { it.form?.name?.isNotBlank() == true }.canSave).isTrue()

        vm.save()
        vm.state.first { it.done }

        val saved = t.repo.allVehicles().single()
        assertThat(saved.name).isEqualTo("Honda Civic")
        assertThat(saved.volumeUnit.kind).isEqualTo(EnergyUnit.LITRE.kind)
        assertThat(saved.energyUnit).isEqualTo(EnergyUnit.KWH)
        assertThat(t.prefs.lastVehicleId).isEqualTo(saved.id)
    }

    @Test
    fun `deleting takes the vehicle's entries with it`() = runTest {
        val id = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(id, day = 0, odometerKm = 100.0, litres = 40.0))
        t.repo.addExpense(DbFixtures.expense(id, day = 1))

        val vm = editor(id)
        assertThat(vm.state.first { it.entryCount == 2 }.storedName).isEqualTo("Honda Civic")

        vm.delete()
        vm.state.first { it.done }

        assertThat(t.repo.allVehicles()).isEmpty()
        assertThat(t.repo.allFillUps()).isEmpty()
        assertThat(t.repo.allExpenses()).isEmpty()
    }
}
