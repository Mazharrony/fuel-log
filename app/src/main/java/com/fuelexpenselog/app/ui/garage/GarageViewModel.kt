package com.fuelexpenselog.app.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.domain.model.Vehicle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class GarageVehicle(
    val vehicle: Vehicle,
    /** From fill-ups and expenses both. Null until something records a reading. */
    val odometerM: Long?,
)

sealed interface GarageUiState {
    /** Before the first database read. Renders nothing, so the splash hands straight to data. */
    data object Loading : GarageUiState

    data class Ready(
        val active: List<GarageVehicle>,
        val archived: List<GarageVehicle>,
        val showArchived: Boolean,
        /** No vehicle at all and onboarding never finished: the first-run flow should show. */
        val needsOnboarding: Boolean,
    ) : GarageUiState
}

class GarageViewModel(
    private val repository: FuelLogRepository,
    prefs: AppPrefs,
) : ViewModel() {

    private val showArchived = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val vehicles = repository.observeVehicles().flatMapLatest { list ->
        // combine() over zero flows never emits, so an empty garage needs its own branch or
        // the screen would sit on Loading forever for a brand-new user.
        if (list.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(list.map { v -> repository.observeCurrentOdometer(v.id).map { GarageVehicle(v, it) } }) {
                it.toList()
            }
        }
    }

    val state: StateFlow<GarageUiState> = combine(
        vehicles,
        showArchived,
        prefs.observeOnboardingDone(),
    ) { all, show, onboarded ->
        // observeVehicles() includes archived rows; the garage is for the ones still driven.
        val (archived, active) = all.partition { it.vehicle.isArchived }
        GarageUiState.Ready(
            active = active,
            archived = archived,
            showArchived = show && archived.isNotEmpty(),
            needsOnboarding = all.isEmpty() && !onboarded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarageUiState.Loading)

    fun toggleArchived() = showArchived.update { !it }
}
