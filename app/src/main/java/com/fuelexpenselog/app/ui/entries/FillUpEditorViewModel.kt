package com.fuelexpenselog.app.ui.entries

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import com.fuelexpenselog.app.data.FuelLogRepository
import com.fuelexpenselog.app.data.SettingsStore
import com.fuelexpenselog.app.domain.format.NumberKind
import com.fuelexpenselog.app.domain.format.parseNumber
import com.fuelexpenselog.app.domain.model.FillUp
import com.fuelexpenselog.app.domain.model.Vehicle
import com.fuelexpenselog.app.domain.units.Units
import com.fuelexpenselog.app.domain.validation.EntryWarning
import com.fuelexpenselog.app.domain.validation.FillUpDraft
import com.fuelexpenselog.app.domain.validation.ValidationResult
import com.fuelexpenselog.app.domain.validation.validateFillUp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FillUpUiState(
    val vehicle: Vehicle? = null,
    val previousOdometerKm: Double? = null,
    val isFullTank: Boolean = true,
    val isMissedEntry: Boolean = false,
    val dateMillis: Long = System.currentTimeMillis(),
    val validation: ValidationResult = ValidationResult(),
    val isEditing: Boolean = false,
    val showMore: Boolean = false,
    val saved: Boolean = false,
    /** This tank's figures, recomputed live as the fields change. */
    val thisTankKmPerLitre: Double? = null,
    val thisTankCostPerKm: Double? = null,
)

/**
 * The critical path.
 *
 * Text lives in [SavedStateHandle] via `saveable { TextFieldState() }`. That one
 * pattern covers rotation, process death and IME correctness together:
 * TextFieldState keeps text and selection, SavedStateHandle persists it through
 * onSaveInstanceState, and the field never round-trips through a String the
 * ViewModel might rewrite mid-edit.
 *
 * Nothing is filtered while typing. Whatever the user enters is accepted and
 * parsed at save, which kills the IME cursor-jump bug class and is what
 * "warn, never block" means at the keyboard.
 */
@OptIn(SavedStateHandleSaveableApi::class)
class FillUpEditorViewModel(
    private val handle: SavedStateHandle,
    private val repo: FuelLogRepository,
    private val settings: SettingsStore,
    private val vehicleId: Long,
    private val entryId: Long,
) : ViewModel() {

    val odometer by handle.saveable(stateSaver = TextFieldState.Saver) {
        mutableStateOfTextField()
    }
    val volume by handle.saveable(stateSaver = TextFieldState.Saver) {
        mutableStateOfTextField()
    }
    val totalPaid by handle.saveable(stateSaver = TextFieldState.Saver) {
        mutableStateOfTextField()
    }
    val note by handle.saveable(stateSaver = TextFieldState.Saver) {
        mutableStateOfTextField()
    }
    val fuelType by handle.saveable(stateSaver = TextFieldState.Saver) {
        mutableStateOfTextField()
    }

    private val _state = MutableStateFlow(FillUpUiState(isEditing = entryId != 0L))
    val state: StateFlow<FillUpUiState> = _state.asStateFlow()

    /** True once any field has been touched, so back can confirm before discarding. */
    var isDirty: Boolean
        get() = handle["dirty"] ?: false
        private set(value) { handle["dirty"] = value }

    init {
        viewModelScope.launch {
            val vehicle = repo.getVehicle(vehicleId)
            val previous = repo.latestOdometerKm(vehicleId)

            if (entryId != 0L) {
                repo.getFillUp(entryId)?.let { existing ->
                    if (odometer.text.isEmpty() && vehicle != null) {
                        odometer.setTextAndPlaceCursorAtEnd(
                            trimNumber(Units.kmToDisplay(existing.odometer, vehicle.distanceUnit))
                        )
                        volume.setTextAndPlaceCursorAtEnd(
                            trimNumber(Units.litresToDisplay(existing.volume, vehicle.volumeUnit))
                        )
                        totalPaid.setTextAndPlaceCursorAtEnd(trimNumber(existing.totalCost))
                        existing.note?.let { note.setTextAndPlaceCursorAtEnd(it) }
                        existing.fuelType?.let { fuelType.setTextAndPlaceCursorAtEnd(it) }
                    }
                    _state.update {
                        it.copy(
                            isFullTank = handle["full"] ?: existing.isFullTank,
                            isMissedEntry = handle["missed"] ?: existing.isMissedEntry,
                            dateMillis = handle["date"] ?: existing.date,
                        )
                    }
                }
            }

            _state.update {
                it.copy(
                    vehicle = vehicle,
                    previousOdometerKm = previous,
                    isFullTank = handle["full"] ?: it.isFullTank,
                    dateMillis = handle["date"] ?: it.dateMillis,
                )
            }
            revalidate()
        }
    }

    fun setFullTank(value: Boolean) {
        handle["full"] = value
        isDirty = true
        _state.update { it.copy(isFullTank = value) }
        revalidate()
    }

    fun setMissedEntry(value: Boolean) {
        handle["missed"] = value
        isDirty = true
        _state.update { it.copy(isMissedEntry = value) }
    }

    fun setDate(millis: Long) {
        handle["date"] = millis
        isDirty = true
        _state.update { it.copy(dateMillis = millis) }
        revalidate()
    }

    fun toggleMore() = _state.update { it.copy(showMore = !it.showMore) }

    fun onFieldChanged() {
        isDirty = true
        revalidate()
    }

    /**
     * Recomputes warnings and the live "this tank" figures. Cheap enough to run
     * on every keystroke, and the figures being live is what makes the screen
     * feel like it is paying attention.
     */
    fun revalidate() {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        val draft = currentDraft(vehicle)

        viewModelScope.launch {
            val duplicate = draft.odometerKm != null &&
                repo.looksLikeDuplicate(vehicleId, s.dateMillis, draft.odometerKm, entryId)

            val result = validateFillUp(
                draft = draft,
                previousOdometerKm = s.previousOdometerKm,
                tankCapacityLitres = vehicle.tankCapacityLitres,
                nowMillis = System.currentTimeMillis(),
                isDuplicate = duplicate,
            )

            // Distance since the last full tank, over the volume just added.
            val kmPerLitre = if (
                draft.odometerKm != null && draft.volumeLitres != null &&
                s.previousOdometerKm != null && draft.volumeLitres > 0.0 &&
                draft.odometerKm > s.previousOdometerKm && s.isFullTank
            ) {
                (draft.odometerKm - s.previousOdometerKm) / draft.volumeLitres
            } else null

            val costPerKm = if (
                kmPerLitre != null && draft.totalCost != null &&
                draft.odometerKm != null && s.previousOdometerKm != null
            ) {
                draft.totalCost / (draft.odometerKm - s.previousOdometerKm)
            } else null

            _state.update {
                it.copy(
                    validation = result,
                    thisTankKmPerLitre = kmPerLitre,
                    thisTankCostPerKm = costPerKm,
                )
            }
        }
    }

    fun save() {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        val draft = currentDraft(vehicle)
        if (draft.odometerKm == null || draft.volumeLitres == null || draft.totalCost == null) return

        viewModelScope.launch {
            val entry = FillUp(
                id = entryId,
                vehicleId = vehicleId,
                date = s.dateMillis,
                odometer = draft.odometerKm,
                volume = draft.volumeLitres,
                totalCost = draft.totalCost,
                isFullTank = s.isFullTank,
                isMissedEntry = s.isMissedEntry,
                fuelType = fuelType.text.toString().trim().ifBlank { null },
                note = note.text.toString().trim().ifBlank { null },
            )
            if (entryId == 0L) {
                repo.addFillUp(entry)
                // Drives the gentle export reminder - a lost phone is the one
                // real weakness of an app that never talks to a server.
                settings.fillUpsSinceExport = settings.fillUpsSinceExport + 1
            } else {
                repo.updateFillUp(entry)
            }
            isDirty = false
            // The baseline this screen measures against has just moved.
            _state.update {
                it.copy(saved = true, previousOdometerKm = repo.latestOdometerKm(vehicleId))
            }
        }
    }

    fun delete() {
        if (entryId == 0L) return
        viewModelScope.launch {
            repo.getFillUp(entryId)?.let { repo.deleteFillUp(it) }
            isDirty = false
            _state.update { it.copy(saved = true) }
        }
    }

    /** Resets for a fresh entry, keeping the refreshed odometer baseline. */
    fun clear() {
        odometer.clearText(); volume.clearText(); totalPaid.clearText()
        note.clearText(); fuelType.clearText()
        handle["full"] = true
        handle["missed"] = false
        isDirty = false
        _state.update {
            it.copy(
                saved = false, isFullTank = true, isMissedEntry = false,
                showMore = false, thisTankKmPerLitre = null, thisTankCostPerKm = null,
                validation = ValidationResult(),
            )
        }
        viewModelScope.launch {
            _state.update { it.copy(previousOdometerKm = repo.latestOdometerKm(vehicleId)) }
        }
    }

    private fun currentDraft(vehicle: Vehicle) = FillUpDraft(
        odometerKm = parseNumber(odometer.text.toString(), NumberKind.ODOMETER)
            ?.let { Units.displayToKm(it, vehicle.distanceUnit) },
        volumeLitres = parseNumber(volume.text.toString(), NumberKind.VOLUME)
            ?.let { Units.displayToLitres(it, vehicle.volumeUnit) },
        totalCost = parseNumber(totalPaid.text.toString(), NumberKind.MONEY),
        dateMillis = _state.value.dateMillis,
    )
}

/** Copy for a warning, in the app's own voice: never alarming, never blocking. */
fun EntryWarning.message(tankCapacity: String?): String = when (this) {
    EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS ->
        "That reading is lower than the last one. Save anyway if the odometer was replaced or reset."
    EntryWarning.VOLUME_OVER_TANK_CAPACITY ->
        "Volume is above this vehicle's tank capacity${tankCapacity?.let { " of $it" } ?: ""}. " +
            "You can still save - jerry cans and twin tanks are real."
    EntryWarning.FUTURE_DATE ->
        "That date is in the future. Saving it anyway is fine."
    EntryWarning.LOOKS_LIKE_DUPLICATE ->
        "Looks like a duplicate of an entry on the same day at the same reading."
    EntryWarning.IMPLAUSIBLE_CONSUMPTION ->
        "That gives an unusual consumption figure. It is shown as recorded, not hidden."
}

private fun mutableStateOfTextField() =
    androidx.compose.runtime.mutableStateOf(TextFieldState())

/** Drops a trailing .0 so an editor never opens showing "84210.0". */
private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else com.fuelexpenselog.app.domain.format.Rounding.toPlainString(value, 2).trimEnd('0').trimEnd('.')
