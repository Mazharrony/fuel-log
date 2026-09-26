package com.fuelexpenselog.app.notify

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.fuelexpenselog.app.testing.TestDb
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** One alarm at a time, the next daily check, and a chain that re-arms itself. */
@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val t = TestDb()

    /** 26 September 2026, 14:00 UTC: past today's nine o'clock. */
    private val clock = Clock.fixed(Instant.parse("2026-09-26T14:00:00Z"), ZoneOffset.UTC)
    private val scheduler = ReminderScheduler(context, t.prefs, clock) { ZoneOffset.UTC }
    private val gate = NotificationGate(context)
    private val notifications = ReminderNotifications(t.prefs, gate, scheduler)
    private val alarms: ShadowAlarmManager get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val tomorrowAtNine = Instant.parse("2026-09-27T09:00:00Z").toEpochMilli()

    @After
    fun tearDown() = t.close()

    private fun bootReceiverState() =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, BootReceiver::class.java))

    @Test
    fun `scheduling again replaces the alarm, so there is only ever one, at the next nine o'clock`() {
        scheduler.scheduleNext()
        scheduler.scheduleNext()
        scheduler.scheduleNext()

        assertThat(alarms.scheduledAlarms).hasSize(1)
        assertThat(alarms.peekNextScheduledAlarm()!!.triggerAtMs).isEqualTo(tomorrowAtNine)
        assertThat(alarms.peekNextScheduledAlarm()!!.type).isEqualTo(AlarmManager.RTC_WAKEUP)
    }

    @Test
    fun `turning on checks a minute later, and each check re-arms the next day's`() = runBlocking {
        notifications.enable()
        assertThat(alarms.scheduledAlarms).hasSize(1)
        assertThat(alarms.peekNextScheduledAlarm()!!.triggerAtMs).isEqualTo(clock.millis() + ReminderScheduler.SOON_MS)
        assertThat(bootReceiverState()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        // The alarm fires: the check runs, and leaves tomorrow's behind it.
        DailyCheck(t.repo, t.prefs, gate, ReminderNotifier(context), scheduler, clock) { ZoneOffset.UTC }.run()

        assertThat(alarms.scheduledAlarms).hasSize(1)
        assertThat(alarms.peekNextScheduledAlarm()!!.triggerAtMs).isEqualTo(tomorrowAtNine)
    }

    @Test
    fun `turning off cancels the alarm and the boot receiver`() {
        notifications.enable()
        notifications.disable()

        assertThat(alarms.scheduledAlarms).isEmpty()
        assertThat(bootReceiverState()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    @Test
    fun `re-arming keeps a check still to come rather than pushing it to tomorrow`() {
        notifications.enable()
        notifications.rearm()

        assertThat(alarms.scheduledAlarms).hasSize(1)
        assertThat(alarms.peekNextScheduledAlarm()!!.triggerAtMs).isEqualTo(clock.millis() + ReminderScheduler.SOON_MS)
    }

    @Test
    fun `after a reboot the boot receiver puts the check back, and only if reminders are on`() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertThat(alarms.scheduledAlarms).isEmpty()

        t.prefs.remindersNotify = true
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertThat(alarms.scheduledAlarms).hasSize(1)
    }
}
