package com.fuelexpenselog.app.notify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.fuelexpenselog.app.testing.TestDb
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.ZoneOffset

/** 26 and 34 straddle API 33, where POST_NOTIFICATIONS became a runtime permission. */
@RunWith(RobolectricTestRunner::class)
class NotificationGateTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val t = TestDb()
    private val gate = NotificationGate(context)
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @After
    fun tearDown() = t.close()

    @Test
    @Config(sdk = [26])
    fun `before API 33 there is nothing to ask for`() {
        assertThat(gate.needsRuntimePermission).isFalse()
        assertThat(gate.hasPermission()).isTrue()
        assertThat(gate.canPost()).isTrue()
    }

    @Test
    @Config(sdk = [34])
    fun `on API 34 posting waits for the permission, which starts denied`() {
        assertThat(gate.needsRuntimePermission).isTrue()
        assertThat(gate.hasPermission()).isFalse()
        assertThat(gate.blocked(remindersOn = true)).isTrue()

        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(gate.canPost()).isTrue()
    }

    @Test
    @Config(sdk = [34])
    fun `granted and then revoked shows the banner and leaves the setting as the user left it`() {
        val scheduler = ReminderScheduler(context, t.prefs, Clock.systemUTC()) { ZoneOffset.UTC }
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifications(t.prefs, gate, scheduler).enable()
        assertThat(gate.blocked(t.prefs.remindersNotify)).isFalse()

        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertThat(gate.blocked(t.prefs.remindersNotify)).isTrue()
        assertThat(t.prefs.remindersNotify).isTrue()
    }

    @Test
    fun `an app blocked in system settings is blocked, permission or not`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        gate.ensureChannel()
        assertThat(gate.canPost()).isTrue()

        shadowOf(manager).setNotificationsEnabled(false)

        assertThat(gate.canPost()).isFalse()
        assertThat(gate.blocked(remindersOn = true)).isTrue()
        assertThat(gate.blocked(remindersOn = false)).isFalse()
    }

    @Test
    fun `the channel is made when reminders are first turned on, and not before`() {
        assertThat(manager.getNotificationChannel(NotificationGate.CHANNEL_ID)).isNull()

        val scheduler = ReminderScheduler(context, t.prefs, Clock.systemUTC()) { ZoneOffset.UTC }
        ReminderNotifications(t.prefs, gate, scheduler).enable()

        assertThat(manager.getNotificationChannel(NotificationGate.CHANNEL_ID)).isNotNull()
    }
}
