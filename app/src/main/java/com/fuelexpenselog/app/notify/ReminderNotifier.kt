package com.fuelexpenselog.app.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fuelexpenselog.app.MainActivity
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.format.Formatters
import com.fuelexpenselog.app.ui.reminders.ReminderWording
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderStatus

/** Says one reminder, in the same words the app uses for it. Tapping it opens the app. */
class ReminderNotifier(private val context: Context) {

    /** Only ever called once [NotificationGate.canPost] has said yes. */
    @SuppressLint("MissingPermission")
    fun post(reminder: Reminder, status: ReminderStatus, vehicle: Vehicle) {
        val line = ReminderWording.status(context.resources, Formatters.from(context), reminder, status, vehicle.distanceUnit)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationGate.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gauge)
            .setContentTitle(reminder.title)
            .setContentText(listOfNotNull(vehicle.name, line).joinToString(" · "))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(TAG, reminder.id.toInt(), notification)
    }

    companion object {
        const val TAG = "reminder"
    }
}
