package com.fuelexpenselog.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.fuelexpenselog.app.data.prefs.AppPrefs
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One alarm at a time: the next daily check, never one per reminder. Inexact on purpose - no
 * exact-alarm permission and no Play policy declaration, because "your insurance renews in 30
 * days" can wait a few minutes. No WorkManager either: it would add WAKE_LOCK,
 * RECEIVE_BOOT_COMPLETED and FOREGROUND_SERVICE to the manifest for work this small.
 */
class ReminderScheduler(
    private val context: Context,
    private val prefs: AppPrefs,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) {

    private val alarms: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    /** Tomorrow's check, or today's if 09:00 is still to come. */
    fun scheduleNext() = schedule(nextDailyCheck())

    /** A check a minute from now: turning reminders on answers for an overdue one today. */
    fun scheduleSoon() = schedule(clock.millis() + SOON_MS)

    /** Replaces whatever was set: the same PendingIntent means there is only ever one. */
    fun schedule(atMillis: Long) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent())
        prefs.reminderCheckAt = atMillis
    }

    fun cancel() {
        alarms.cancel(pendingIntent())
        prefs.reminderCheckAt = 0
    }

    /**
     * Picks the chain up again after something dropped it - a reboot, an update, a force stop.
     * A check still to come keeps its time, so reopening the app a minute after turning
     * reminders on does not push the first check to tomorrow.
     */
    fun rearm() {
        val planned = prefs.reminderCheckAt
        if (planned > clock.millis()) schedule(planned) else scheduleNext()
    }

    fun nextDailyCheck(): Long {
        val now = ZonedDateTime.now(clock.withZone(zone()))
        var next = now.toLocalDate().atTime(DAILY_CHECK).atZone(zone())
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    /** The boot receiver runs only while reminders notify: no boot-time work for anyone who never opted in. */
    fun setBootReceiverEnabled(enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, BootReceiver::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, DailyCheckReceiver::class.java).setAction(DailyCheckReceiver.ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        val DAILY_CHECK: LocalTime = LocalTime.of(9, 0)
        const val SOON_MS = 60_000L
        private const val REQUEST_CODE = 1
    }
}

/** Turning reminder notifications on and off: the preference, the channel, the alarm and the boot receiver together. */
class ReminderNotifications(
    private val prefs: AppPrefs,
    private val gate: NotificationGate,
    private val scheduler: ReminderScheduler,
) {
    val isOn: Boolean get() = prefs.remindersNotify

    fun enable() {
        prefs.remindersNotify = true
        gate.ensureChannel()
        scheduler.setBootReceiverEnabled(true)
        scheduler.scheduleSoon()
    }

    fun disable() {
        prefs.remindersNotify = false
        scheduler.cancel()
        scheduler.setBootReceiverEnabled(false)
    }

    /** After a reboot, an update or a force stop. Does nothing while reminders are off. */
    fun rearm() {
        if (prefs.remindersNotify) scheduler.rearm()
    }
}
