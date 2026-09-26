package com.fuelexpenselog.app.ui.vehicle

import androidx.lifecycle.SavedStateHandle
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.testing.MainDispatcherRule
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.app.ui.common.EntryBar
import com.fuelexpenselog.app.ui.common.labelRes
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.consumption.Confidence
import com.fuelexpenselog.domain.consumption.Gap
import com.fuelexpenselog.domain.consumption.GapReason
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.unit.ConsumptionFormat
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

@RunWith(RobolectricTestRunner::class)
class VehicleViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t = TestDb()

    /** 1 April 2026: the golden fixtures run from 1 January to 26 March. */
    private val clock = Clock.fixed(Instant.parse("2026-04-01T09:00:00Z"), ZoneOffset.UTC)

    @After
    fun tearDown() = t.close()

    private fun vm(id: Long) = VehicleViewModel(SavedStateHandle(mapOf(Routes.ARG_ID to id)), t.repo, t.prefs, clock) { ZoneOffset.UTC }

    private suspend fun VehicleViewModel.ready() = state.first { it.snapshot != null }

    /** The domain's golden dataset, through the real database this time. */
    private suspend fun golden(): Long {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        with(DbFixtures) {
            t.repo.addFillUp(fillUp(v, day = 0, odometerKm = 10_000.0, litres = 45.0, total = 67.5))
            t.repo.addFillUp(fillUp(v, day = 14, odometerKm = 10_520.0, litres = 40.0, total = 60.0))
            t.repo.addFillUp(fillUp(v, day = 28, odometerKm = 10_800.0, litres = 20.0, full = false, total = 30.0))
            t.repo.addFillUp(fillUp(v, day = 42, odometerKm = 11_100.0, litres = 25.0, total = 37.5))
            t.repo.addFillUp(fillUp(v, day = 56, odometerKm = 11_650.0, litres = 44.0, total = 66.0))
            t.repo.addFillUp(fillUp(v, day = 70, odometerKm = 12_200.0, litres = 50.0, missed = true, total = 75.0))
            t.repo.addFillUp(fillUp(v, day = 84, odometerKm = 12_700.0, litres = 40.0, total = 60.0))
        }
        return v
    }

    @Test
    fun `the headline is the last tank and the average is sum over sum`() = runTest {
        val state = vm(golden()).ready()

        val latest = state.latest as Measured
        assertThat(latest.shownAs(ConsumptionFormat.KM_PER_L)!!).isWithin(1e-9).of(500.0 / 40.0)
        // 2,150 km over 169 L across the four measured spans - not the mean of the four figures.
        val average = state.snapshot!!.consumption.summary!!.lifetimeAs(ConsumptionFormat.KM_PER_L)!!
        assertThat(average).isWithin(1e-9).of(2_150.0 / 169.0)
        assertThat(state.snapshot!!.format).isEqualTo(ConsumptionFormat.L_PER_100KM)
    }

    @Test
    fun `history bars follow the engine - yellow only where a span ends`() = runTest {
        val state = vm(golden()).ready()
        val bars = state.rows.map { it.bar }
        // Newest first: day 84 closes a span; day 70 closes the missed stretch, so no figure.
        assertThat(bars.take(3)).containsExactly(EntryBar.FUEL, EntryBar.PARTIAL, EntryBar.FUEL).inOrder()
        assertThat(state.entryCount).isEqualTo(7)
        assertThat(state.rows).hasSize(VehicleViewModel.HISTORY_PREVIEW)
    }

    @Test
    fun `a span that skipped an anchor says how many fill-ups it covers`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 10_000.0, litres = 40.0))
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 7, odometerKm = null, litres = 30.0))
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 14, odometerKm = 10_900.0, litres = 35.0))

        val latest = vm(v).ready().latest as Measured
        assertThat(latest.confidence).isEqualTo(Confidence.AVERAGED)
        assertThat(latest.fillUpsSpanned).isEqualTo(2)
        assertThat(latest.distanceM).isEqualTo(900_000)
    }

    @Test
    fun `no figure is a dash with its reason, in the mandated words`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 10_000.0, litres = 40.0))
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 7, odometerKm = 10_500.0, litres = 40.0, missed = true))

        val gap = vm(v).ready().latest as Gap
        assertThat(gap.reason).isEqualTo(GapReason.MISSED_FILL_UP)
        assertThat(t.context.getString(gap.reason.labelRes())).isEqualTo("A fill-up in this stretch was not recorded")
    }

    @Test
    fun `this month is compared with last, in one currency`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle(currency = "EUR"))
        // DbFixtures day 60 is 2 March, day 90 is 1 April - "now".
        t.repo.addExpense(DbFixtures.expense(v, day = 60, amount = 200.0))
        t.repo.addExpense(DbFixtures.expense(v, day = 90, amount = 150.0))

        val state = vm(v).ready()
        assertThat(state.monthTotal.single().asDouble).isWithin(1e-9).of(150.0)
        assertThat(state.monthDelta!!.percent).isWithin(1e-9).of(-25.0)
        assertThat(state.monthDelta!!.improved).isTrue()
    }

    @Test
    fun `last done at comes from the newest expense in each category`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 50, odometerKm = 84_210.0, litres = 40.0))
        t.repo.addExpense(DbFixtures.expense(v, day = 10, category = ExpenseCategory.OIL_CHANGE, odometerKm = 80_000.0))
        t.repo.addExpense(DbFixtures.expense(v, day = 40, category = ExpenseCategory.OIL_CHANGE, odometerKm = 83_898.0))

        val oil = vm(v).ready().lastDone.single()
        assertThat(oil.category).isEqualTo(ExpenseCategory.OIL_CHANGE)
        assertThat(oil.distanceAgoM).isEqualTo(312_000)
    }

    @Test
    fun `a deleted vehicle reports itself missing so the screen can close`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        val vm = vm(v)
        vm.ready()
        t.repo.deleteVehicle(t.repo.vehicle(v)!!)
        assertThat(vm.state.first { it.missing }.missing).isTrue()
    }
}
