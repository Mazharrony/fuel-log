package com.fuelexpenselog.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.app.ui.vehicle.VehicleSnapshot
import com.fuelexpenselog.app.ui.vehicle.observeSnapshot
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.HistoryEntry
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.time.MonthKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val snapshot: VehicleSnapshot? = null,
    val rows: List<HistoryRow> = emptyList(),
    /** Null shows both. */
    val tag: EntryTag? = null,
    /** Set when opened from a month: only that month's entries. */
    val month: MonthKey? = null,
    val missing: Boolean = false,
)

/**
 * The combined timeline, fill-ups and expenses interleaved, newest first. The business
 * filter is the one an accountant asks for.
 */
class HistoryViewModel(
    private val handle: SavedStateHandle,
    repository: FuelLogRepository,
    prefs: AppPrefs,
) : ViewModel() {

    private val vehicleId: Long = checkNotNull(handle.get<Long>(Routes.ARG_ID))
    private val month: MonthKey? = handle.get<Int>(Routes.ARG_MONTH)?.takeIf { it > 0 }?.let(::MonthKey)
    private val tag = handle.getStateFlow(KEY_TAG, "")

    val state: StateFlow<HistoryUiState> = combine(repository.observeSnapshot(vehicleId, prefs), tag) { snapshot, rawTag ->
        if (snapshot == null) return@combine HistoryUiState(missing = true)
        val filterTag = rawTag.takeIf { it.isNotEmpty() }?.let { decodeOr(it, EntryTag.PERSONAL) }
        // Rows are paired with the engine over the WHOLE history, then filtered: a span's
        // figure does not change because its neighbours are hidden.
        val rows = historyRows(snapshot.history, snapshot.consumption).filter { row ->
            (month == null || row.entry.date.monthKey == month) &&
                (filterTag == null || row.entry.tag() == filterTag)
        }
        HistoryUiState(snapshot, rows, filterTag, month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(month = month))

    fun onTag(tag: EntryTag?) {
        handle[KEY_TAG] = tag?.name.orEmpty()
    }

    private fun HistoryEntry.tag(): EntryTag = when (this) {
        is HistoryEntry.Fuel -> fillUp.tag
        is HistoryEntry.Cost -> expense.tag
    }

    private companion object {
        const val KEY_TAG = "history_tag"
    }
}
