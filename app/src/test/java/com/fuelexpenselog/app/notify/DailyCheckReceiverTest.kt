package com.fuelexpenselog.app.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderEvaluator
import com.fuelexpenselog.domain.reminder.ReminderKind
import com.fuelexpenselog.domain.time.CivilDate
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The daily check: said once, never nagging, never turning itself off. */
@RunWith(RobolectricTestRunner::class)
class DailyCheckReceiverTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val t = TestDb()
    private val clock = Clock.fixed(Instant.parse("2026-09-26T09:00:00Z"), ZoneOffset.UTC)
    private val gate = NotificationGate(context)
    private val scheduler = ReminderScheduler(context, t.prefs, clock) { ZoneOffset.UTC }
    private val manager get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    private fun check() = DailyCheck(t.repo, t.prefs, gate, ReminderNotifier(context), scheduler, clock) { ZoneOffset.UTC }

    @Before
    fun setUp() {
        // What turning reminders on in Settings leaves behind: the permission, the setting, the channel.
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        t.prefs.remindersNotify = true
        gate.ensureChannel()
    }

    @After
    fun tearDown() = t.close()

    /** "Oil change every 6 months or 10,000 km", due at 58,700 km, on a car now at 59,000. */
    private suspend fun overdueOilChange(name: String = "Civic"): Pair<Long, Long> {
        val car = t.repo.addVehicle(DbFixtures.vehicle(name = name))
        t.repo.addFillUp(DbFixtures.fillUp(car, day = 250, odometerKm = 59_000.0, litres = 40.0))
        val anchor = CivilDate.of(2026, 6, 1)
        val next = ReminderEvaluator.schedule(ReminderKind.BOTH, 6, 10_000_000, anchor, 48_700_000)
        val id = t.repo.addReminder(
            Reminder(
                id = 0,
                vehicleId = car,
                title = "Oil change",
                kind = ReminderKind.BOTH,
                category = ExpenseCategory.OIL_CHANGE,
                dueDate = next.dueDate,
                repeatMonths = 6,
                dueOdometerM = next.dueOdometerM,
                repeatDistanceM = 10_000_000,
                anchorDate = anchor,
                anchorOdometerM = 48_700_000,
            ),
        )
        return car to id
    }

    @Test
    fun `firing twice in one day posts once`() = runBlocking {
        val (_, oil) = overdueOilChange()

        assertThat(check().run()).isEqualTo(1)
        assertThat(check().run()).isEqualTo(0)

        val posted = manager.allNotifications.single()
        assertThat(posted.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Oil change")
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("Civic · 300 km overdue")
        assertThat(t.repo.reminder(oil)!!.lastNotified).isEqualTo(CivilDate.of(2026, 9, 26))
    }

    @Test
    fun `blocked notifications post nothing and mark nothing, so the reminder is said once allowed`() = runBlocking {
        val (_, oil) = overdueOilChange()
        manager.setNotificationsEnabled(false)

        assertThat(check().run()).isEqualTo(0)
        assertThat(manager.allNotifications).isEmpty()
        assertThat(t.repo.reminder(oil)!!.lastNotified).isNull()
        // The user's choice stands; only the banner says anything.
        assertThat(t.prefs.remindersNotify).isTrue()

        manager.setNotificationsEnabled(true)
        assertThat(check().run()).isEqualTo(1)
    }

    @Test
    fun `an archived vehicle's reminders stay quiet`() = runBlocking {
        val (car, _) = overdueOilChange()
        t.repo.setVehicleArchived(car, true)

        assertThat(check().run()).isEqualTo(0)
        assertThat(manager.allNotifications).isEmpty()
    }

    @Test
    fun `with reminders off the check posts nothing and stops the alarm`() = runBlocking {
        overdueOilChange()
        scheduler.scheduleNext()
        t.prefs.remindersNotify = false

        assertThat(check().run()).isEqualTo(0)
        assertThat(manager.allNotifications).isEmpty()
        assertThat(shadowOf(context.getSystemService(android.app.AlarmManager::class.java)).scheduledAlarms).isEmpty()
    }
}
