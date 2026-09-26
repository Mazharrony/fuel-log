package com.fuelexpenselog.app.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.notify.NotificationGate
import com.fuelexpenselog.app.notify.ReminderNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class NotifyState(
    /** What the user chose. Never changed behind their back. */
    val on: Boolean = false,
    /** On, but the system will not show them: permission revoked, app or channel blocked. */
    val blocked: Boolean = false,
)

/**
 * Reminder notifications, for the Settings toggle and the Garage banner. The system can change
 * its answer while the app is in the background, so the screens ask again on every resume.
 */
class NotifyViewModel(
    private val prefs: AppPrefs,
    private val notifications: ReminderNotifications,
    private val gate: NotificationGate,
) : ViewModel() {

    private val tick = MutableStateFlow(0)

    val state: StateFlow<NotifyState> = combine(prefs.observeRemindersNotify(), tick) { on, _ -> NotifyState(on, gate.blocked(on)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotifyState(prefs.remindersNotify, gate.blocked(prefs.remindersNotify)))

    /** Turning on must ask first: API 33 and later, and not yet granted. */
    val needsPermission: Boolean get() = gate.needsRuntimePermission && !gate.hasPermission()

    fun refresh() = tick.update { it + 1 }

    fun turnOn() {
        notifications.enable()
        refresh()
    }

    fun turnOff() {
        notifications.disable()
        refresh()
    }

    fun settingsIntent(): Intent = gate.settingsIntent()
}
