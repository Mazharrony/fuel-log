package com.fuelexpenselog.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fuelexpenselog.app.FuelLogApp

/**
 * Alarms do not survive a reboot or an update of the app. Disabled in the manifest and
 * switched on only while reminders notify, so nobody who never opted in pays for boot work.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        (context.applicationContext as FuelLogApp).container.reminderNotifications.rearm()
    }
}
