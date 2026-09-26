package com.fuelexpenselog.app.ui.reminders

import androidx.lifecycle.SavedStateHandle
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.testing.MainDispatcherRule
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderEvaluator
import com.fuelexpenselog.domain.reminder.ReminderKind
import com.fuelexpenselog.domain.reminder.ReminderStatus
import com.fuelexpenselog.domain.time.CivilDate
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
class RemindersViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t = TestDb()
    private val today = CivilDate.of(2026, 9, 26)
    private val clock = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC)
    private val utc = { ZoneOffset.UTC }

    @After
    fun tearDown() = t.close()

    private suspend fun reminder(
        vehicleId: Long,
        title: String,
        kind: ReminderKind,
        anchor: CivilDate,
        anchorKm: Long?,
        months: Int? = 6,
        km: Long? = 10_000,
        category: ExpenseCategory = ExpenseCategory.OIL_CHANGE,
    ): Long {
        val next = ReminderEvaluator.schedule(kind, months, km?.times(1_000), anchor, anchorKm?.times(1_000))
        return t.repo.addReminder(
            Reminder(
                id = 0,
                vehicleId = vehicleId,
                title = title,
                kind = kind,
                category = category,
                dueDate = next.dueDate,
                repeatMonths = months,
                dueOdometerM = next.dueOdometerM,
                repeatDistanceM = km?.times(1_000),
                anchorDate = anchor,
                anchorOdometerM = anchorKm?.times(1_000),
            ),
        )
    }

    private fun list(vehicleId: Long) =
        RemindersViewModel(SavedStateHandle(mapOf(Routes.ARG_ID to vehicleId)), t.repo, clock, utc)

    @Test
    fun `a distance reminder on a vehicle with no reading is Unknown, which the screen shows as a dash`() = runTest {
        val bike = t.repo.addVehicle(DbFixtures.vehicle(name = "Bike"))
        reminder(bike, "Chain", ReminderKind.DISTANCE, anchor = today.plusDays(-400), anchorKm = 1_000, months = null, km = 500)

        val rows = list(bike).state.first { !it.loading }.rows

        // Due at 1,500 km long ago by any reckoning, but with no reading there is nothing to
        // count against: Unknown, never Overdue.
        assertThat(rows.single().second).isEqualTo(ReminderStatus.Unknown)
    }

    @Test
    fun `rows come most urgent first, counted against the vehicle's own reading`() = runTest {
        val car = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(car, day = 260, odometerKm = 59_000.0, litres = 40.0))
        reminder(car, "Insurance", ReminderKind.DATE, anchor = today.plusDays(-200), anchorKm = null, months = 12, km = null)
        reminder(car, "Oil change", ReminderKind.BOTH, anchor = today.plusDays(-100), anchorKm = 48_700)

        val rows = list(car).state.first { it.rows.size == 2 }.rows

        assertThat(rows.map { it.first.title }).containsExactly("Oil change", "Insurance").inOrder()
        assertThat(rows[0].second).isEqualTo(ReminderStatus.Overdue(daysOver = null, metresOver = 300_000))
        assertThat(rows[1].second).isEqualTo(ReminderStatus.Ok)
    }

    @Test
    fun `a new oil change reminder starts from the last oil change logged`() = runTest {
        val car = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addExpense(DbFixtures.expense(car, day = 100, category = ExpenseCategory.OIL_CHANGE, odometerKm = 41_200.0))
        val vm = ReminderEditorViewModel(
            SavedStateHandle(mapOf(Routes.ARG_VEHICLE to car)),
            t.repo,
            clock,
            utc,
        ) { Locale.US }
        vm.state.first { it.form != null }

        vm.onCategory(ExpenseCategory.OIL_CHANGE, "Oil change")
        vm.state.first { it.form?.lastOdometerText == "41200" }
        vm.onEveryMonths("6")
        vm.onEveryDistance("10000")
        assertThat(vm.state.first { it.canSave }.preview?.first?.dueOdometerM).isEqualTo(51_200_000)
        vm.save()
        vm.state.first { it.done }

        val saved = t.repo.observeReminders(car).first().single()
        val lastOil = DbFixtures.BASE.plusDays(100)
        assertThat(saved.title).isEqualTo("Oil change")
        assertThat(saved.anchorDate).isEqualTo(lastOil)
        assertThat(saved.anchorOdometerM).isEqualTo(41_200_000)
        assertThat(saved.dueDate).isEqualTo(lastOil.plusMonths(6))
        assertThat(saved.dueOdometerM).isEqualTo(51_200_000)
        assertThat(saved.notifyEnabled).isTrue()
    }

    @Test
    fun `marking it done logs the cost as an expense in its category and moves it on`() = runTest {
        val car = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(car, day = 260, odometerKm = 59_000.0, litres = 40.0))
        val oil = reminder(car, "Oil change", ReminderKind.BOTH, anchor = today.plusDays(-190), anchorKm = 48_700)
        val vm = ReminderDoneViewModel(SavedStateHandle(mapOf(Routes.ARG_ID to oil)), t.repo, clock, utc)
        vm.state.first { it.form != null && it.vehicle != null }

        vm.onAmount("89.90")
        vm.onOdometer("59100")
        vm.save()
        vm.state.first { it.done }

        val expense = t.repo.allExpenses().single()
        assertThat(expense.category).isEqualTo(ExpenseCategory.OIL_CHANGE)
        assertThat(expense.amount).isEqualTo(Money.of(89.90, "EUR"))
        assertThat(expense.odometerM).isEqualTo(59_100_000)
        assertThat(expense.tag).isEqualTo(EntryTag.PERSONAL)
        assertThat(expense.reminderId).isEqualTo(oil)
        assertThat(expense.date).isEqualTo(today)

        val next = t.repo.reminder(oil)!!
        assertThat(next.anchorDate).isEqualTo(today)
        assertThat(next.dueDate).isEqualTo(CivilDate.of(2027, 3, 26))
        assertThat(next.dueOdometerM).isEqualTo(69_100_000)
    }
}
