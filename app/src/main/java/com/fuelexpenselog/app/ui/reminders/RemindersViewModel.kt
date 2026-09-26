package com.fuelexpenselog.app.ui.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderEvaluator
import com.fuelexpenselog.domain.reminder.ReminderStatus
import com.fuelexpenselog.domain.time.CivilDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.ZoneId

data class RemindersUiState(
    val loading: Boolean = true,
    /** The vehicle is gone: deleted elsewhere, or replaced by a restore. */
    val missing: Boolean = false,
    val vehicle: Vehicle? = null,
    /** Most urgent first. */
    val rows: List<Pair<Reminder, ReminderStatus>> = emptyList(),
)

/**
 * One vehicle's reminders, evaluated on read against its current reading and today. Today is
 * read on every emission, and again whenever the screen comes back after a while, so a list
 * left open overnight does not keep yesterday's answer.
 */
class RemindersViewModel(
    handle: SavedStateHandle,
    repository: FuelLogRepository,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {

    val vehicleId: Long = checkNotNull(handle.get<Long>(Routes.ARG_ID))

    val state: StateFlow<RemindersUiState> = combine(
        repository.observeVehicle(vehicleId),
        repository.observeReminders(vehicleId),
        repository.observeCurrentOdometer(vehicleId),
    ) { vehicle, reminders, odometer ->
        if (vehicle == null) return@combine RemindersUiState(loading = false, missing = true)
        RemindersUiState(
            loading = false,
            vehicle = vehicle,
            rows = ReminderEvaluator.evaluate(reminders, mapOf(vehicleId to odometer), CivilDate.today(clock, zone())),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())
}
