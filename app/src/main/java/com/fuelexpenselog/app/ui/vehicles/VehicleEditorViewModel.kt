package com.fuelexpenselog.app.ui.vehicles

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.format.LocaleDefaults
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.FuelType
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.VehicleType
import com.fuelexpenselog.domain.parse.DecimalParser
import com.fuelexpenselog.domain.parse.NumberKind
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class VehicleEditorUiState(
    val isNew: Boolean,
    /** Null until the stored vehicle has been read. */
    val form: VehicleForm?,
    /** The stored name, for the delete confirmation - not whatever is half-typed. */
    val storedName: String,
    val entryCount: Int,
    /** False for a vehicle holding a value this build cannot read. */
    val isEditable: Boolean,
    val canSave: Boolean,
    val isDirty: Boolean,
    /** The tank field has text DecimalParser cannot read; it will be saved as no capacity. */
    val tankUnreadable: Boolean,
    val error: String?,
    /** Saved or deleted: the screen should close. */
    val done: Boolean,
)

/**
 * Add and edit a vehicle.
 *
 * Units here are display settings. Changing a vehicle from kilometres to miles rewrites one
 * column on the vehicle row and nothing else: every odometer and volume stays in metres and
 * micro-litres, byte for byte, and only the numbers on screen change.
 */
class VehicleEditorViewModel(
    private val handle: SavedStateHandle,
    private val repository: FuelLogRepository,
    private val prefs: AppPrefs,
    private val locale: () -> Locale = { Locale.getDefault() },
) : ViewModel() {

    private val vehicleId: Long = handle.get<Long>(Routes.ARG_ID) ?: 0L
    private val isNew = vehicleId == 0L

    private val original = MutableStateFlow<Vehicle?>(null)
    private val form = MutableStateFlow(handle.get<Bundle>(KEY_FORM)?.let(VehicleForm::fromBundle))
    private val initial = MutableStateFlow(handle.get<Bundle>(KEY_INITIAL)?.let(VehicleForm::fromBundle))
    private val error = MutableStateFlow<String?>(null)
    private val done = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val stored = if (isNew) null else repository.vehicle(vehicleId)
            original.value = stored
            // After process death the handle already holds what the user had typed; the
            // stored row is re-read only for what the form does not carry.
            if (form.value == null) {
                val start = stored?.let { VehicleForm.of(it, locale()) } ?: newVehicleForm()
                initial.value = start
                handle[KEY_INITIAL] = start.toBundle()
                setForm(start)
            }
        }
    }

    val state: StateFlow<VehicleEditorUiState> = combine(
        combine(form, initial, original, ::Triple),
        if (isNew) flowOf(0) else repository.observeEntryCount(vehicleId),
        error,
        done,
    ) { (f, start, stored), count, err, isDone ->
        val editable = stored?.isEditable ?: true
        VehicleEditorUiState(
            isNew = isNew,
            form = f,
            storedName = stored?.name.orEmpty(),
            entryCount = count,
            isEditable = editable,
            // The single blocking rule: a vehicle needs a name. Everything else has a default.
            canSave = f != null && editable && f.name.isNotBlank(),
            isDirty = f != null && start != null && f != start,
            tankUnreadable = f != null && f.tankText.isNotBlank() &&
                DecimalParser.parseOrNull(f.tankText, NumberKind.VOLUME) == null,
            error = err,
            done = isDone,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        VehicleEditorUiState(isNew, null, "", 0, true, false, false, false, null, false),
    )

    fun onName(value: String) = edit { it.copy(name = value) }
    fun onDistanceUnit(unit: DistanceUnit) = edit { it.copy(distanceUnit = unit) }
    fun onCurrency(code: String) = edit { it.copy(currency = code) }
    fun onTank(text: String) = edit { it.copy(tankText = text, tankEdited = true) }
    fun onType(type: VehicleType) = edit { it.copy(type = type) }
    fun onFuelType(fuel: FuelType) = edit { it.copy(fuelType = fuel) }
    fun onDefaultTag(tag: EntryTag) = edit { it.copy(defaultTag = tag) }
    fun onFormat(format: ConsumptionFormat?) = edit { it.copy(format = format) }

    /**
     * Re-expresses the tank capacity in the new unit so it still means the same tank. An
     * untouched field re-renders from the stored value; a typed one converts what was typed.
     */
    fun onVolumeUnit(unit: EnergyUnit) = edit { f ->
        val capacity = if (!f.tankEdited) {
            original.value?.tankCapacity
        } else {
            DecimalParser.parseOrNull(f.tankText, NumberKind.VOLUME)?.let { Energy.of(f.volumeUnit, it) }
        }
        f.copy(
            volumeUnit = unit,
            tankText = capacity?.let { VehicleForm.tankText(it.inUnit(unit), locale()) } ?: f.tankText,
        )
    }

    /**
     * For an unreadable vehicle this applies at once, through a one-column update, because
     * Save is disabled for it and hiding a vehicle must always be possible.
     */
    fun onActive(active: Boolean) {
        val stored = original.value
        if (stored != null && !stored.isEditable) {
            viewModelScope.launch {
                runCatching { repository.setVehicleArchived(stored.id, !active) }
                    .onSuccess {
                        original.value = stored.copy(isArchived = !active)
                        edit { it.copy(active = active) }
                        initial.value = initial.value?.copy(active = active)
                    }
                    .onFailure { error.value = it.message }
            }
        } else {
            edit { it.copy(active = active) }
        }
    }

    fun save() {
        val f = form.value ?: return
        // Checked from the sources, not from `state`, which only runs while someone watches.
        if (f.name.isBlank() || original.value?.isEditable == false) return
        viewModelScope.launch {
            runCatching {
                val vehicle = buildVehicle(f)
                if (isNew) {
                    prefs.lastVehicleId = repository.addVehicle(vehicle)
                } else {
                    repository.updateVehicle(vehicle)
                }
            }
                .onSuccess { done.value = true }
                .onFailure { error.value = it.message }
        }
    }

    /** Cascades to every entry, reminder and segment. The screen confirms first. */
    fun delete() {
        val stored = original.value ?: return
        viewModelScope.launch {
            runCatching { repository.deleteVehicle(stored) }
                .onSuccess { done.value = true }
                .onFailure { error.value = it.message }
        }
    }

    fun dismissError() {
        error.value = null
    }

    private fun buildVehicle(f: VehicleForm): Vehicle {
        val tank = if (!f.tankEdited) {
            original.value?.tankCapacity
        } else {
            DecimalParser.parseOrNull(f.tankText, NumberKind.VOLUME)
                ?.takeIf { it > 0.0 }
                ?.let { Energy.of(f.volumeUnit, it) }
        }
        val base = original.value ?: Vehicle(
            id = 0,
            name = "",
            type = f.type,
            fuelType = f.fuelType,
            distanceUnit = f.distanceUnit,
            volumeUnit = f.volumeUnit,
            // The electric unit is always kWh; the volume unit is never allowed to be.
            energyUnit = EnergyUnit.KWH,
            consumptionFormat = f.format,
            currencyCode = f.currency,
            tankCapacity = null,
            batteryCapacity = null,
            defaultTag = f.defaultTag,
        )
        return base.copy(
            name = f.name.trim(),
            type = f.type,
            fuelType = f.fuelType,
            distanceUnit = f.distanceUnit,
            volumeUnit = f.volumeUnit,
            consumptionFormat = f.format,
            currencyCode = f.currency,
            tankCapacity = tank,
            defaultTag = f.defaultTag,
            isArchived = !f.active,
        )
    }

    /** Units and currency default from the app settings, then what the phone's locale implies. */
    private fun newVehicleForm(): VehicleForm {
        val region = LocaleDefaults.detect(locale())
        return VehicleForm(
            name = "",
            distanceUnit = prefs.defaultDistanceUnit ?: region.distanceUnit,
            volumeUnit = prefs.defaultVolumeUnit ?: region.volumeUnit,
            currency = prefs.defaultCurrency ?: region.currencyCode,
            tankText = "",
            tankEdited = false,
            type = VehicleType.CAR,
            fuelType = FuelType.PETROL,
            defaultTag = EntryTag.PERSONAL,
            format = null,
            active = true,
        )
    }

    private fun edit(change: (VehicleForm) -> VehicleForm) {
        form.value?.let { setForm(change(it)) }
    }

    private fun setForm(value: VehicleForm) {
        form.value = value
        handle[KEY_FORM] = value.toBundle()
    }

    private companion object {
        const val KEY_FORM = "vehicle_form"
        const val KEY_INITIAL = "vehicle_form_initial"
    }
}
