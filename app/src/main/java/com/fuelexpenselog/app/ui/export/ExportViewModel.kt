package com.fuelexpenselog.app.ui.export

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.transfer.SafGateway
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.csv.export.FuelLogCsvExporter
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.HistoryEntry
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.time.MonthKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.time.Clock
import java.time.ZoneId

enum class ExportPeriod { ALL, YEAR, MONTH }

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Working : ExportStatus
    data class Done(val rows: Int, val fileName: String?) : ExportStatus
    data class Failed(val message: String) : ExportStatus
}

data class ExportUiState(
    val vehicles: List<Vehicle> = emptyList(),
    /** 0 exports every vehicle. */
    val vehicleId: Long = 0,
    val period: ExportPeriod = ExportPeriod.ALL,
    val year: Int = 0,
    /** Offered only when opened from a month. */
    val month: MonthKey? = null,
    val tag: EntryTag? = null,
    val count: Int = 0,
    val status: ExportStatus = ExportStatus.Idle,
) {
    /** "fuel-log-civic-2026-09-business.csv": what the file is, in its name. */
    val suggestedName: String
        get() = buildList {
            add("fuel-log")
            vehicles.firstOrNull { it.id == vehicleId }?.name?.let { name ->
                name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').takeIf { it.isNotEmpty() }?.let(::add)
            }
            when (period) {
                ExportPeriod.ALL -> Unit
                ExportPeriod.YEAR -> add(year.toString())
                ExportPeriod.MONTH -> month?.let { add(it.toString()) }
            }
            tag?.let { add(it.name.lowercase()) }
        }.joinToString("-") + ".csv"
}

/**
 * A CSV of fill-ups and expenses for a vehicle or all of them, a month, a year or all time,
 * and optionally only the business ones - the file an accountant asks for.
 */
class ExportViewModel(
    private val handle: SavedStateHandle,
    private val repository: FuelLogRepository,
    private val saf: SafGateway,
    clock: Clock,
    zone: () -> ZoneId,
) : ViewModel() {

    private val today = CivilDate.today(clock, zone())
    private val month: MonthKey? = handle.get<Int>(Routes.ARG_MONTH)?.takeIf { it > 0 }?.let(::MonthKey)
    private val vehicleId = handle.getStateFlow(KEY_VEHICLE, handle.get<Long>(Routes.ARG_VEHICLE) ?: 0L)
    private val period = handle.getStateFlow(KEY_PERIOD, if (month != null) ExportPeriod.MONTH.name else ExportPeriod.ALL.name)
    private val tag = handle.getStateFlow(KEY_TAG, "")
    private val status = MutableStateFlow<ExportStatus>(ExportStatus.Idle)

    val state: StateFlow<ExportUiState> = combine(
        repository.observeVehicles(),
        repository.observeAllHistory(),
        combine(vehicleId, period, tag, ::Triple),
        status,
    ) { vehicles, history, (vid, rawPeriod, rawTag), st ->
        val selection = selection(vid, rawPeriod, rawTag)
        ExportUiState(
            vehicles = vehicles,
            vehicleId = vid,
            period = selection.period,
            year = today.year,
            month = month,
            tag = selection.tag,
            count = history.count { (v, entry) -> selection.matches(v, entry) },
            status = st,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportUiState(year = today.year, month = month))

    fun onVehicle(id: Long) {
        handle[KEY_VEHICLE] = id
    }

    fun onPeriod(value: ExportPeriod) {
        handle[KEY_PERIOD] = value.name
    }

    fun onTag(value: EntryTag?) {
        handle[KEY_TAG] = value?.name.orEmpty()
    }

    fun dismissStatus() {
        status.value = ExportStatus.Idle
    }

    fun export(uri: Uri) {
        status.value = ExportStatus.Working
        viewModelScope.launch {
            runCatching {
                val selection = selection(vehicleId.value, period.value, tag.value)
                val vehicles = repository.observeVehicles().first()
                val rows = repository.observeAllHistory().first().filter { (v, e) -> selection.matches(v, e) }
                withContext(Dispatchers.IO) {
                    saf.openOutput(uri).use { out ->
                        OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                            FuelLogCsvExporter.write(
                                vehicles = vehicles,
                                fillUps = rows.mapNotNull { (it.second as? HistoryEntry.Fuel)?.fillUp },
                                expenses = rows.mapNotNull { (it.second as? HistoryEntry.Cost)?.expense },
                                out = writer,
                            )
                        }
                    }
                }
                ExportStatus.Done(rows.size, saf.displayName(uri))
            }
                .onSuccess { status.value = it }
                .onFailure { status.value = ExportStatus.Failed(it.message.orEmpty()) }
        }
    }

    private class Selection(val vehicleId: Long, val period: ExportPeriod, val year: Int, val month: MonthKey?, val tag: EntryTag?) {
        fun matches(vehicle: Vehicle, entry: HistoryEntry): Boolean {
            if (vehicleId != 0L && vehicle.id != vehicleId) return false
            val inPeriod = when (period) {
                ExportPeriod.ALL -> true
                ExportPeriod.YEAR -> entry.date.year == year
                ExportPeriod.MONTH -> entry.date.monthKey == month
            }
            val entryTag = when (entry) {
                is HistoryEntry.Fuel -> entry.fillUp.tag
                is HistoryEntry.Cost -> entry.expense.tag
            }
            return inPeriod && (tag == null || entryTag == tag)
        }
    }

    private fun selection(vid: Long, rawPeriod: String, rawTag: String): Selection {
        val p = decodeOr(rawPeriod, ExportPeriod.ALL).let { if (it == ExportPeriod.MONTH && month == null) ExportPeriod.ALL else it }
        return Selection(vid, p, today.year, month, rawTag.takeIf { it.isNotEmpty() }?.let { decodeOr(it, EntryTag.PERSONAL) })
    }

    private companion object {
        const val KEY_VEHICLE = "export_vehicle"
        const val KEY_PERIOD = "export_period"
        const val KEY_TAG = "export_tag"
    }
}
