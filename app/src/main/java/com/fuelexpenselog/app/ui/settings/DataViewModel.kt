package com.fuelexpenselog.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.backup.BackupReader
import com.fuelexpenselog.app.backup.BackupWriter
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.data.repo.ImportBatch
import com.fuelexpenselog.app.transfer.SafGateway
import com.fuelexpenselog.domain.time.CivilDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

sealed interface DataStatus {
    data object Idle : DataStatus
    data object Working : DataStatus
    data class BackedUp(val fileName: String?) : DataStatus
    data class Refused(val why: BackupReader.Refusal) : DataStatus
    data class Failed(val message: String, val restoring: Boolean) : DataStatus
    data class Undone(val removed: Int) : DataStatus
}

/** Back up to a file the user picks, or replace everything from one; and take an import back. */
class DataViewModel(
    private val saf: SafGateway,
    private val writer: () -> BackupWriter,
    private val reader: () -> BackupReader,
    private val onRestored: () -> Unit,
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val repository: FuelLogRepository,
) : ViewModel() {

    private val _status = MutableStateFlow<DataStatus>(DataStatus.Idle)
    val status: StateFlow<DataStatus> = _status.asStateFlow()

    /** Newest first, each with the day it happened: an import can be undone as a whole. */
    val imports: StateFlow<List<Pair<ImportBatch, CivilDate>>> = repository.observeImportBatches()
        .map { list -> list.map { it to CivilDate.of(Instant.ofEpochMilli(it.importedAtMillis).atZone(zone()).toLocalDate()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun undoImport(batch: ImportBatch) {
        _status.value = DataStatus.Working
        viewModelScope.launch {
            runCatching { repository.undoImport(batch.id) }
                .onSuccess { _status.value = DataStatus.Undone(it) }
                .onFailure { _status.value = DataStatus.Failed(it.message.orEmpty(), restoring = false) }
        }
    }

    fun backupName(): String = "fuel-log-${CivilDate.today(clock, zone())}.fuellogbak"

    fun backup(uri: Uri) {
        _status.value = DataStatus.Working
        viewModelScope.launch {
            runCatching { saf.openOutput(uri).use { writer().write(it) } }
                .onSuccess { _status.value = DataStatus.BackedUp(saf.displayName(uri)) }
                .onFailure { _status.value = DataStatus.Failed(it.message.orEmpty(), restoring = false) }
        }
    }

    /**
     * Refusals leave everything as it was. A successful restore hands over to the app,
     * which rebuilds every screen on the restored database - the data reappearing is the
     * confirmation.
     */
    fun restore(uri: Uri) {
        _status.value = DataStatus.Working
        viewModelScope.launch {
            runCatching { saf.openInput(uri).use { reader().restore(it) } }
                .onSuccess { result ->
                    when (result) {
                        is BackupReader.Result.Restored -> {
                            _status.value = DataStatus.Idle
                            onRestored()
                        }
                        is BackupReader.Result.Refused -> _status.value = DataStatus.Refused(result.why)
                    }
                }
                .onFailure { _status.value = DataStatus.Failed(it.message.orEmpty(), restoring = true) }
        }
    }

    fun dismiss() {
        _status.value = DataStatus.Idle
    }
}
