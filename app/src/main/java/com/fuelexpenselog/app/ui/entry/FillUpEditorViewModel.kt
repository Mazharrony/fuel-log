package com.fuelexpenselog.app.ui.entry

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.consumption.DeclaredSegment
import com.fuelexpenselog.domain.consumption.FuelEvent
import com.fuelexpenselog.domain.consumption.TimelinePoint
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.parse.DecimalParser
import com.fuelexpenselog.domain.parse.NumberKind
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyKind
import com.fuelexpenselog.domain.validate.EntryContext
import com.fuelexpenselog.domain.validate.EntryPreview
import com.fuelexpenselog.domain.validate.EntryValidation
import com.fuelexpenselog.domain.validate.EntryWarning
import com.fuelexpenselog.domain.validate.FillUpDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.util.Locale

data class FillUpEditorUiState(
    val isNew: Boolean,
    /** Null while loading. */
    val form: FillUpForm? = null,
    val vehicle: Vehicle? = null,
    /** Active vehicles, for choosing which one a new fill-up belongs to. */
    val vehicles: List<Vehicle> = emptyList(),
    /** There is no active vehicle to log against at all. */
    val noVehicle: Boolean = false,
    val today: CivilDate? = null,
    /** The highest reading on record, shown as a hint under an empty odometer. */
    val previousOdometerM: Long? = null,
    val warnings: List<EntryWarning> = emptyList(),
    val canSave: Boolean = false,
    val isEditable: Boolean = true,
    val isDirty: Boolean = false,
    /** What this tank would produce, from the real engine: a figure, or a gap and its reason. */
    val preview: TimelinePoint? = null,
    val format: ConsumptionFormat = ConsumptionFormat.L_PER_100KM,
    val error: String? = null,
    val done: Boolean = false,
)

private data class VehicleData(
    val vehicle: Vehicle,
    val fillUps: List<FillUp>,
    val segments: List<DeclaredSegment>,
    val previousOdometerM: Long?,
    val appFormat: ConsumptionFormat,
)

/**
 * Add or edit a fill-up: the critical path. Nothing here blocks except an empty volume or
 * total - every other oddity is a warning, and the entry saves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FillUpEditorViewModel(
    private val handle: SavedStateHandle,
    private val repository: FuelLogRepository,
    private val prefs: AppPrefs,
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale = { Locale.getDefault() },
) : ViewModel() {

    private val editingId: Long = handle.get<Long>(Routes.ARG_ID) ?: 0L
    private val isNew = editingId == 0L
    private val requestedVehicleId: Long = handle.get<Long>(Routes.ARG_VEHICLE) ?: 0L

    /** Captured once, so the duplicate probe does not re-run on every keystroke. */
    private val openedAt = clock.millis()

    private val original = MutableStateFlow<FillUp?>(null)
    private val form = MutableStateFlow(handle.get<Bundle>(KEY_FORM)?.let(FillUpForm::fromBundle))
    private val initial = MutableStateFlow(handle.get<Bundle>(KEY_INITIAL)?.let(FillUpForm::fromBundle))
    private val noVehicle = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val done = MutableStateFlow(false)

    private fun today() = CivilDate.today(clock, zone())

    init {
        viewModelScope.launch {
            if (!isNew) {
                val stored = repository.fillUp(editingId)
                if (stored == null) {
                    done.value = true // deleted from under us
                    return@launch
                }
                original.value = stored
            }
            if (form.value != null) return@launch // restored after process death

            val start = if (isNew) {
                val id = resolveVehicleId(requestedVehicleId, repository, prefs)
                val vehicle = id?.let { repository.vehicle(it) }
                if (vehicle == null) {
                    noVehicle.value = true
                    return@launch
                }
                FillUpForm.blank(vehicle, today())
            } else {
                val stored = original.value!!
                val vehicle = repository.vehicle(stored.vehicleId) ?: return@launch
                FillUpForm.of(stored, vehicle, locale())
            }
            initial.value = start
            handle[KEY_INITIAL] = start.toBundle()
            setForm(start)
        }
    }

    private val vehicleData: Flow<VehicleData?> = form
        .map { it?.vehicleId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(
                    repository.observeVehicle(id),
                    repository.observeFillUps(id),
                    repository.observeSegments(id),
                    repository.observePreviousOdometer(id, excludingFillUpId = editingId),
                    prefs.observeConsumptionFormat(),
                ) { vehicle, fillUps, segments, previous, format ->
                    vehicle?.let { VehicleData(it, fillUps, segments, previous, format) }
                }
            }
        }

    /** A warning only, and it needs a parsed reading to compare. */
    private val duplicate: Flow<Boolean> = combine(form, vehicleData) { f, data ->
        if (f == null || data == null) return@combine null
        val odometer = odometerM(f, data.vehicle) ?: return@combine null
        Triple(f.vehicleId, instant(f, useOpenedAt = true), odometer)
    }
        .distinctUntilChanged()
        .mapLatest { key ->
            key != null && repository.looksLikeDuplicate(key.first, key.second, key.third, excludingId = editingId)
        }
        .onStart { emit(false) }

    private val activeVehicles = repository.observeVehicles().map { list -> list.filter { !it.isArchived } }

    val state: StateFlow<FillUpEditorUiState> = combine(
        combine(form, initial, original, ::Triple),
        vehicleData,
        duplicate,
        combine(activeVehicles, noVehicle, error, done) { v, none, e, d -> Meta(v, none, e, d) },
    ) { (f, start, stored), data, isDuplicate, meta ->
        build(f, start, stored, data, isDuplicate, meta)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FillUpEditorUiState(isNew = isNew))

    private data class Meta(val vehicles: List<Vehicle>, val noVehicle: Boolean, val error: String?, val done: Boolean)

    private fun build(
        f: FillUpForm?,
        start: FillUpForm?,
        stored: FillUp?,
        data: VehicleData?,
        isDuplicate: Boolean,
        meta: Meta,
    ): FillUpEditorUiState {
        val base = FillUpEditorUiState(
            isNew = isNew,
            vehicles = meta.vehicles,
            noVehicle = meta.noVehicle,
            error = meta.error,
            done = meta.done,
        )
        if (f == null || data == null) return base

        val vehicle = data.vehicle
        val today = today()
        val validation = EntryValidation.validate(
            FillUpDraft(f.odometerText, f.volumeText, f.totalText, f.isFull, f.date, vehicle.volumeUnit, vehicle.distanceUnit),
            EntryContext(
                today = today,
                previousOdometerM = data.previousOdometerM,
                tankCapacity = vehicle.tankCapacity,
                looksLikeDuplicate = isDuplicate,
            ),
        )
        val editable = (stored?.isEditable ?: true) && vehicle.isEditable

        // A blank form has no odometer either, and saying so before anything is typed is noise.
        // The warning waits until there is a volume or a total it would apply to.
        val nothingTyped = f.volumeText.isBlank() && f.totalText.isBlank()
        val warnings = validation.warnings.filterNot { it == EntryWarning.ODOMETER_MISSING && nothingTyped }

        return base.copy(
            form = f,
            vehicle = vehicle,
            today = today,
            previousOdometerM = data.previousOdometerM,
            warnings = warnings,
            canSave = validation.canSave && editable,
            isEditable = editable,
            isDirty = start != null && f != start,
            preview = preview(f, data),
            format = vehicle.formatFor(EnergyKind.LIQUID, data.appFormat),
        )
    }

    /** The real engine over the saved tanks plus this one, replacing the row being edited. */
    private fun preview(f: FillUpForm, data: VehicleData): TimelinePoint? {
        val energy = energy(f, data.vehicle) ?: return null
        val draft = FuelEvent(
            id = EntryPreview.DRAFT_ID,
            date = f.date,
            instantMillis = instant(f, useOpenedAt = true),
            odometerM = odometerM(f, data.vehicle),
            energy = energy,
            isFull = f.isFull,
            missedPrevious = f.missedPrevious,
            cost = cost(f, data.vehicle, energy),
        )
        return EntryPreview.fillUp(
            existing = data.fillUps.map { it.toFuelEvent() },
            draft = draft,
            kind = EnergyKind.LIQUID,
            displayUnit = data.vehicle.distanceUnit,
            declaredSegments = data.segments,
            replacingId = editingId,
        )
    }

    // -- input -----------------------------------------------------------------------------

    fun onOdometer(text: String) = edit { it.copy(odometerText = text, odometerEdited = true) }
    fun onVolume(text: String) = edit { it.copy(volumeText = text, volumeEdited = true) }
    fun onTotal(text: String) = edit { it.copy(totalText = text, totalEdited = true) }
    fun onFull(full: Boolean) = edit { it.copy(isFull = full) }
    fun onMissedPrevious(missed: Boolean) = edit { it.copy(missedPrevious = missed) }
    fun onDate(date: CivilDate) = edit { it.copy(date = date) }
    fun onTag(tag: EntryTag) = edit { it.copy(tag = tag) }
    fun onStation(text: String) = edit { it.copy(station = text) }
    fun onNote(text: String) = edit { it.copy(note = text) }

    /** New entries only: an edit stays with its vehicle. */
    fun onVehicle(vehicleId: Long) {
        if (!isNew) return
        viewModelScope.launch {
            val vehicle = repository.vehicle(vehicleId) ?: return@launch
            edit { it.copy(vehicleId = vehicleId, tag = vehicle.defaultTag) }
        }
    }

    // -- save --------------------------------------------------------------------------------

    fun save() {
        val f = form.value ?: return
        viewModelScope.launch {
            runCatching {
                val vehicle = requireNotNull(repository.vehicle(f.vehicleId)) { "The vehicle no longer exists." }
                val energy = energy(f, vehicle) ?: return@launch
                val (total, unitPrice) = total(f, vehicle) ?: return@launch
                val stored = original.value
                val fillUp = FillUp(
                    id = editingId,
                    vehicleId = f.vehicleId,
                    date = f.date,
                    instantMillis = instant(f, useOpenedAt = false),
                    odometerM = odometerM(f, vehicle),
                    energy = energy,
                    energyUnitEntered = if (stored != null && !f.volumeEdited) stored.energyUnitEntered else vehicle.volumeUnit,
                    isFull = f.isFull,
                    missedPrevious = f.missedPrevious,
                    total = total,
                    unitPrice = unitPrice,
                    tag = f.tag,
                    station = f.station.trim().ifEmpty { null },
                    fuelGrade = stored?.fuelGrade,
                    paymentMethod = stored?.paymentMethod,
                    note = f.note.trim().ifEmpty { null },
                )
                if (isNew) repository.addFillUp(fillUp) else repository.updateFillUp(fillUp)
                prefs.lastVehicleId = f.vehicleId
            }
                .onSuccess { done.value = true }
                .onFailure { error.value = it.message }
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            runCatching { repository.deleteFillUp(editingId) }
                .onSuccess { done.value = true }
                .onFailure { error.value = it.message }
        }
    }

    fun dismissError() {
        error.value = null
    }

    // -- the stored-or-typed rule ---------------------------------------------------------------

    private fun odometerM(f: FillUpForm, vehicle: Vehicle): Long? {
        val stored = original.value
        return if (stored != null && !f.odometerEdited) stored.odometerM
        else EntryValidation.odometerMetres(f.odometerText, vehicle.distanceUnit)
    }

    private fun energy(f: FillUpForm, vehicle: Vehicle): Energy? {
        val stored = original.value
        if (stored != null && !f.volumeEdited) return stored.energy
        return DecimalParser.parseOrNull(f.volumeText, NumberKind.VOLUME)?.let { Energy.of(vehicle.volumeUnit, it) }
    }

    /**
     * Total and unit price as they will be stored. An untouched field keeps the row's own
     * pair, so an imported unit-price-only row stays one. A typed total is in the row's own
     * currency on an edit, and the vehicle's on a new entry.
     */
    private fun total(f: FillUpForm, vehicle: Vehicle): Pair<Money?, Money?>? {
        val stored = original.value
        if (stored != null && !f.totalEdited) return stored.total to stored.unitPrice
        val amount = DecimalParser.parseOrNull(f.totalText, NumberKind.MONEY_TOTAL) ?: return null
        val currency = stored?.total?.currency ?: stored?.unitPrice?.currency ?: vehicle.currencyCode
        return Money.of(amount, currency) to null
    }

    /** What the tank cost, for the preview: a unit-price row derives its total from the volume. */
    private fun cost(f: FillUpForm, vehicle: Vehicle, energy: Energy): Money? {
        val stored = original.value
        if (stored != null && !f.totalEdited) return stored.copy(energy = energy).totalOrDerived()
        return total(f, vehicle)?.first
    }

    private fun instant(f: FillUpForm, useOpenedAt: Boolean): Long {
        val stored = original.value
        val clock = if (useOpenedAt) Clock.fixed(java.time.Instant.ofEpochMilli(openedAt), zone()) else clock
        return entryInstant(f.date, today(), clock, zone(), stored?.let { it.date to it.instantMillis })
    }

    private fun edit(change: (FillUpForm) -> FillUpForm) {
        form.value?.let { setForm(change(it)) }
    }

    private fun setForm(value: FillUpForm) {
        form.value = value
        handle[KEY_FORM] = value.toBundle()
    }

    private companion object {
        const val KEY_FORM = "fillup_form"
        const val KEY_INITIAL = "fillup_form_initial"
    }
}
