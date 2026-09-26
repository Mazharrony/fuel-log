package com.fuelexpenselog.app.data

import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderEvaluator
import com.fuelexpenselog.domain.reminder.ReminderKind
import com.fuelexpenselog.domain.time.CivilDate
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderRepositoryTest {

    private val t = TestDb()
    private val repo = t.repo

    @After
    fun tearDown() = t.close()

    /** "Oil change every 6 months or 10,000 km", last done 26 March at 48,700 km. */
    private suspend fun oilChange(vehicleId: Long): Long {
        val anchor = CivilDate.of(2026, 3, 26)
        val next = ReminderEvaluator.schedule(ReminderKind.BOTH, 6, 10_000_000, anchor, 48_700_000)
        return repo.addReminder(
            Reminder(
                id = 0,
                vehicleId = vehicleId,
                title = "Oil change",
                kind = ReminderKind.BOTH,
                category = ExpenseCategory.OIL_CHANGE,
                dueDate = next.dueDate,
                repeatMonths = 6,
                dueOdometerM = next.dueOdometerM,
                repeatDistanceM = 10_000_000,
                anchorDate = anchor,
                anchorOdometerM = 48_700_000,
                lastNotified = CivilDate.of(2026, 9, 1),
            ),
        )
    }

    private suspend fun setUpCar(): Pair<Long, Long> {
        val car = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(car, day = 0, odometerKm = 58_000.0, litres = 40.0))
        return car to oilChange(car)
    }

    @Test
    fun `completing logs the expense, records the completion and moves the reminder on, together`() = runBlocking {
        val (car, oil) = setUpCar()
        val on = CivilDate.of(2026, 9, 26)

        val next = repo.completeReminder(
            oil,
            on,
            odometerM = 58_900_000,
            expense = DbFixtures.expense(car, day = 268, category = ExpenseCategory.OIL_CHANGE, amount = 89.0, odometerKm = 58_900.0),
            note = "5W-30",
        )!!

        val expense = repo.allExpenses().single()
        assertThat(expense.reminderId).isEqualTo(oil)
        assertThat(expense.category).isEqualTo(ExpenseCategory.OIL_CHANGE)

        val completion = repo.completions(oil).single()
        assertThat(completion.expenseId).isEqualTo(expense.id)
        assertThat(completion.date).isEqualTo(on)
        assertThat(completion.odometerM).isEqualTo(58_900_000)
        assertThat(completion.note).isEqualTo("5W-30")

        val stored = repo.reminder(oil)!!
        assertThat(stored).isEqualTo(next)
        assertThat(stored.anchorDate).isEqualTo(on)
        assertThat(stored.dueDate).isEqualTo(CivilDate.of(2027, 3, 26))
        assertThat(stored.dueOdometerM).isEqualTo(68_900_000)
        assertThat(stored.lastNotified).isNull()
    }

    @Test
    fun `a failure inside the transaction takes the completion and the expense back with it`() = runBlocking {
        val (car, oil) = setUpCar()
        val before = repo.reminder(oil)
        // A DAO that throws: the reminder's own update, the last step, after both inserts.
        t.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse BEFORE UPDATE ON reminder BEGIN SELECT RAISE(ABORT, 'refused'); END",
        )

        val result = runCatching {
            repo.completeReminder(
                oil,
                CivilDate.of(2026, 9, 26),
                odometerM = 58_900_000,
                expense = DbFixtures.expense(car, day = 268, category = ExpenseCategory.OIL_CHANGE),
            )
        }

        assertThat(result.isFailure).isTrue()
        assertThat(repo.completions(oil)).isEmpty()
        assertThat(repo.allExpenses()).isEmpty()
        assertThat(repo.reminder(oil)).isEqualTo(before)
    }

    @Test
    fun `with no reading typed, the current one anchors the distance and the completion says none`() = runBlocking {
        val (_, oil) = setUpCar()

        val next = repo.completeReminder(oil, CivilDate.of(2026, 9, 26), odometerM = null, expense = null)!!

        assertThat(next.anchorOdometerM).isEqualTo(58_000_000)
        assertThat(next.dueOdometerM).isEqualTo(68_000_000)
        assertThat(repo.completions(oil).single().odometerM).isNull()
        assertThat(repo.allExpenses()).isEmpty()
    }

    @Test
    fun `deleting the vehicle takes its reminders and their completions with it`() = runBlocking {
        val (car, oil) = setUpCar()
        repo.completeReminder(oil, CivilDate.of(2026, 9, 26), 58_900_000, expense = null)

        repo.deleteVehicle(repo.vehicle(car)!!)

        assertThat(t.db.reminderDao().all()).isEmpty()
        assertThat(t.db.reminderDao().allCompletions()).isEmpty()
    }

    @Test
    fun `deleting a reminder keeps the expenses it logged`() = runBlocking {
        val (car, oil) = setUpCar()
        repo.completeReminder(oil, CivilDate.of(2026, 9, 26), 58_900_000, DbFixtures.expense(car, day = 268))

        repo.deleteReminder(oil)

        assertThat(repo.reminder(oil)).isNull()
        assertThat(t.db.reminderDao().allCompletions()).isEmpty()
        assertThat(repo.allExpenses()).hasSize(1)
    }

    @Test
    fun `the daily scan sees only active reminders that may notify, and a notice records only its date`() = runBlocking {
        val (car, oil) = setUpCar()
        val quiet = oilChange(car).also { id -> repo.updateReminder(repo.reminder(id)!!.copy(notifyEnabled = false)) }
        val retired = oilChange(car).also { id -> repo.updateReminder(repo.reminder(id)!!.copy(isActive = false)) }

        assertThat(repo.notifiableReminders().map { it.id }).containsExactly(oil)
        assertThat(repo.notifiableReminders().map { it.id }).containsNoneOf(quiet, retired)

        val before = repo.reminder(oil)!!
        repo.markNotified(oil, CivilDate.of(2026, 9, 26))
        assertThat(repo.reminder(oil)).isEqualTo(before.copy(lastNotified = CivilDate.of(2026, 9, 26)))
    }
}
