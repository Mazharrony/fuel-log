package com.fuelexpenselog.app.ui.garage

import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.testing.MainDispatcherRule
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class GarageViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t = TestDb()

    /** 15 February 2026; DbFixtures day 0 is 1 January. */
    private val clock = Clock.fixed(Instant.parse("2026-02-15T12:00:00Z"), ZoneOffset.UTC)

    @After
    fun tearDown() = t.close()

    private fun garage() = GarageViewModel(t.repo, t.prefs, clock) { ZoneOffset.UTC }

    private fun ready(vm: GarageViewModel) = vm.state.filterIsInstance<GarageUiState.Ready>()

    @Test
    fun `an empty garage on first run asks for onboarding, and still emits`() = runTest {
        val state = ready(garage()).first()
        assertThat(state.active).isEmpty()
        assertThat(state.needsOnboarding).isTrue()
        assertThat(state.yearTotal).isEmpty()
    }

    @Test
    fun `an empty garage after onboarding is just empty`() = runTest {
        t.prefs.onboardingDone = true
        assertThat(ready(garage()).first().needsOnboarding).isFalse()
    }

    @Test
    fun `archived vehicles are kept out of the list until asked for`() = runTest {
        val civic = t.repo.addVehicle(DbFixtures.vehicle(name = "Civic"))
        val sold = t.repo.addVehicle(DbFixtures.vehicle(name = "Old van"))
        t.repo.setVehicleArchived(sold, true)

        val vm = garage()
        val state = ready(vm).first { it.active.isNotEmpty() && it.archived.isNotEmpty() }
        assertThat(state.active.map { it.vehicle.id }).containsExactly(civic)
        assertThat(state.archived.map { it.vehicle.id }).containsExactly(sold)
        assertThat(state.showArchived).isFalse()

        vm.toggleArchived()
        assertThat(ready(vm).first { it.showArchived }.archived).hasSize(1)
    }

    @Test
    fun `a vehicle carries its last figure, sparkline, month totals and cost per distance`() = runTest {
        val id = t.repo.addVehicle(DbFixtures.vehicle(currency = "EUR"))
        t.repo.addFillUp(DbFixtures.fillUp(id, day = 20, odometerKm = 48_200.0, litres = 40.0, total = 60.0))
        t.repo.addFillUp(DbFixtures.fillUp(id, day = 40, odometerKm = 48_700.0, litres = 41.5, total = 62.0))
        t.repo.addExpense(DbFixtures.expense(id, day = 41, odometerKm = 48_750.0, amount = 25.0))

        val item = ready(garage()).first { it.active.firstOrNull()?.latest is Measured }.active.single()
        assertThat(item.odometerM).isEqualTo(48_750_000)
        assertThat((item.latest as Measured).shownAs(ConsumptionFormat.L_PER_100KM)!!).isWithin(0.005).of(8.3)
        assertThat(item.recent).hasSize(2)
        // January: the first fill-up. February: the second and the expense.
        assertThat(item.lastMonth).containsExactly(Money.of(60.0, "EUR"))
        assertThat(item.thisMonth).containsExactly(Money.of(87.0, "EUR"))
        assertThat(item.costPerDistance!!.asDouble).isWithin(1e-6).of(62.0 / 500.0)
    }

    @Test
    fun `two currencies are never added together`() = runTest {
        val eur = t.repo.addVehicle(DbFixtures.vehicle(name = "Golf", currency = "EUR"))
        val inr = t.repo.addVehicle(DbFixtures.vehicle(name = "Scooter", currency = "INR"))
        t.repo.addFillUp(DbFixtures.fillUp(eur, day = 40, odometerKm = 1_000.0, litres = 40.0, total = 60.0, currency = "EUR"))
        t.repo.addFillUp(DbFixtures.fillUp(inr, day = 41, odometerKm = 500.0, litres = 5.0, total = 500.0, currency = "INR"))

        val state = ready(garage()).first { it.yearTotal.size == 2 }
        assertThat(state.mixedCurrency).isTrue()
        assertThat(state.yearTotal).containsExactly(Money.of(500.0, "INR"), Money.of(60.0, "EUR"))
        assertThat(state.thisMonthByCurrency.map { it.currency }).containsExactly("INR", "EUR")
    }
}
