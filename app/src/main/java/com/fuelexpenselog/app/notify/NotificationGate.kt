package com.fuelexpenselog.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fuelexpenselog.app.R

/**
 * Whether a reminder can reach the user as a notification, and the channel it arrives on.
 *
 * From API 33 posting needs the runtime POST_NOTIFICATIONS permission, asked for at the moment
 * reminders are turned on in Settings and never at launch. Below that it comes with the
 * install. Either way the user can block the app, or just this channel, in system settings -
 * so the answer is read fresh every time, never remembered.
 */
class NotificationGate(private val context: Context) {

    val needsRuntimePermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasPermission(): Boolean =
        !needsRuntimePermission ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Whether a reminder posted now would be shown at all. */
    fun canPost(): Boolean {
        if (!hasPermission() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * The banner: reminders are on in the app, but the system will not show them. The toggle
     * is left as the user set it; allowing notifications again brings everything back.
     */
    fun blocked(remindersOn: Boolean): Boolean = remindersOn && !canPost()

    /**
     * Created when reminders are first turned on, not at app start: a channel in system
     * settings for a feature nobody switched on would be clutter.
     */
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notify_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notify_channel_body) },
        )
    }

    /** This app's page in the system's notification settings. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val CHANNEL_ID = "reminders"
    }
}
