package com.fuelexpenselog.app.ui.garage

import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.testing.MainDispatcherRule
import com.fuelexpenselog.app.testing.TestDb
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GarageViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t = TestDb()

    @After
    fun tearDown() = t.close()

    private fun ready(vm: GarageViewModel) = vm.state.filterIsInstance<GarageUiState.Ready>()

    @Test
    fun `an empty garage on first run asks for onboarding, and still emits`() = runTest {
        val vm = GarageViewModel(t.repo, t.prefs)
        val state = ready(vm).first()
        assertThat(state.active).isEmpty()
        assertThat(state.needsOnboarding).isTrue()
    }

    @Test
    fun `an empty garage after onboarding is just empty`() = runTest {
        t.prefs.onboardingDone = true
        val state = ready(GarageViewModel(t.repo, t.prefs)).first()
        assertThat(state.needsOnboarding).isFalse()
    }

    @Test
    fun `archived vehicles are kept out of the list until asked for`() = runTest {
        val civic = t.repo.addVehicle(DbFixtures.vehicle(name = "Civic"))
        val sold = t.repo.addVehicle(DbFixtures.vehicle(name = "Old van"))
        t.repo.setVehicleArchived(sold, true)

        val vm = GarageViewModel(t.repo, t.prefs)
        val state = ready(vm).first { it.active.isNotEmpty() }
        assertThat(state.active.map { it.vehicle.id }).containsExactly(civic)
        assertThat(state.archived.map { it.vehicle.id }).containsExactly(sold)
        assertThat(state.showArchived).isFalse()
        assertThat(state.needsOnboarding).isFalse()

        vm.toggleArchived()
        assertThat(ready(vm).first { it.showArchived }.archived).hasSize(1)
    }

    @Test
    fun `a vehicle's odometer comes from fill-ups and expenses alike`() = runTest {
        val id = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(id, day = 0, odometerKm = 48_200.0, litres = 40.0))
        t.repo.addExpense(DbFixtures.expense(id, day = 3, odometerKm = 48_350.0))

        val state = ready(GarageViewModel(t.repo, t.prefs)).first { it.active.isNotEmpty() }
        assertThat(state.active.single().odometerM).isEqualTo(48_350_000)
    }
}
