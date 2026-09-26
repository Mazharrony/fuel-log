package com.fuelexpenselog.app.ui.entry

import androidx.lifecycle.SavedStateHandle
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.testing.MainDispatcherRule
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.validate.EntryWarning
import com.google.common.truth.Truth.assertThat
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
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ExpenseEditorViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t = TestDb()
    private val clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC)

    @After
    fun tearDown() = t.close()

    private fun editor(vehicleId: Long = 0, expenseId: Long = 0) = ExpenseEditorViewModel(
        handle = SavedStateHandle(
            buildMap {
                if (vehicleId != 0L) put(Routes.ARG_VEHICLE, vehicleId)
                if (expenseId != 0L) put(Routes.ARG_ID, expenseId)
            },
        ),
        repository = t.repo,
        prefs = t.prefs,
        clock = clock,
        zone = { ZoneOffset.UTC },
        locale = { Locale.US },
    )

    private suspend fun ExpenseEditorViewModel.ready() = state.first { it.form != null && it.vehicle != null }

    @Test
    fun `an empty amount is the one thing that blocks`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        val vm = editor(v)
        assertThat(vm.ready().canSave).isFalse()

        vm.onAmount("89.90")
        assertThat(vm.state.first { it.form?.amountText == "89.90" }.canSave).isTrue()
    }

    @Test
    fun `categories are offered in logging order, with Other preselected`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        val state = editor(v).ready()
        assertThat(state.categories).isEqualTo(ExpenseCategory.chipOrder)
        assertThat(state.form!!.category).isEqualTo(ExpenseCategory.OTHER)
    }

    @Test
    fun `the odometer is optional and its absence is not even a warning`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle(currency = "GBP"))
        val vm = editor(v)
        vm.ready()
        vm.onAmount("12.50")
        vm.onCategory(ExpenseCategory.PARKING)

        val state = vm.state.first { it.form?.category == ExpenseCategory.PARKING }
        assertThat(state.warnings).isEmpty()

        vm.save()
        vm.state.first { it.done }
        val saved = t.repo.allExpenses().single()
        assertThat(saved.odometerM).isNull()
        assertThat(saved.category).isEqualTo(ExpenseCategory.PARKING)
        assertThat(saved.amount.currency).isEqualTo("GBP")
        assertThat(saved.amount.micros).isEqualTo(12_500_000)
    }

    @Test
    fun `a service reading below the last one warns and saves`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 48_700.0, litres = 40.0))

        val vm = editor(v)
        vm.ready()
        vm.onAmount("120")
        vm.onOdometer("48000")
        val state = vm.state.first { it.form?.odometerText == "48000" }
        assertThat(state.warnings).containsExactly(EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS)
        assertThat(state.canSave).isTrue()
    }
}
