package com.fuelexpenselog.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fuelexpenselog.app.FuelLogApp
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.domain.reminder.ReminderEvaluator
import com.fuelexpenselog.domain.time.CivilDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

/**
 * The daily check: which reminders newly need saying, each said once, and tomorrow's check
 * armed. It never turns reminders off by itself - blocked notifications leave the setting as
 * the user chose it and nothing marked as said, so it is said once they are allowed again.
 */
class DailyCheck(
    private val repository: FuelLogRepository,
    private val prefs: AppPrefs,
    private val gate: NotificationGate,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) {

    /** How many reminders were posted. */
    suspend fun run(): Int {
        if (!prefs.remindersNotify) {
            scheduler.cancel()
            return 0
        }
        try {
            if (!gate.canPost()) return 0
            val today = CivilDate.today(clock, zone())
            val reminders = repository.notifiableReminders()
            val vehicles = reminders.map { it.vehicleId }.distinct().associateWith { repository.vehicle(it) }
            val readings = vehicles.keys.associateWith { repository.currentOdometer(it) }
            var posted = 0
            for ((reminder, status) in ReminderEvaluator.evaluate(reminders, readings, today)) {
                if (!ReminderEvaluator.shouldNotify(status, reminder.lastNotified, today)) continue
                // A sold car's oil change is nobody's business any more.
                val vehicle = vehicles[reminder.vehicleId]?.takeIf { !it.isArchived } ?: continue
                notifier.post(reminder, status, vehicle)
                repository.markNotified(reminder.id, today)
                posted++
            }
            return posted
        } finally {
            // Always tomorrow's, even if today's failed half way: a chain that breaks once
            // would stay broken until the next reboot.
            scheduler.scheduleNext()
        }
    }
}

/** Woken by the one alarm. Not exported: nothing outside the app can make it run. */
class DailyCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val container = (context.applicationContext as FuelLogApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                container.dailyCheck().run()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.fuelexpenselog.app.action.DAILY_CHECK"
    }
}
