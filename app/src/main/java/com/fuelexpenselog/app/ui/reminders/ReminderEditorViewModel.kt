package com.fuelexpenselog.app.ui.reminders

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.format.InputText
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderCompletion
import com.fuelexpenselog.domain.reminder.ReminderEvaluator
import com.fuelexpenselog.domain.reminder.ReminderInput
import com.fuelexpenselog.domain.reminder.ReminderKind
import com.fuelexpenselog.domain.reminder.ReminderStatus
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.validate.EntryValidation
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.util.Locale

/**
 * A reminder as typed so far: every how long, and when it was last done. The same
 * stored-or-typed rule as the entry forms: a distance nobody touched saves back exactly as
 * stored, not as its rounded display.
 */
data class ReminderForm(
    val vehicleId: Long,
    val title: String,
    val titleEdited: Boolean,
    val category: ExpenseCategory?,
    val kind: ReminderKind,
    val everyMonthsText: String,
    val everyDistanceText: String,
    val everyDistanceEdited: Boolean,
    val lastDone: CivilDate,
    val lastDoneEdited: Boolean,
    val lastOdometerText: String,
    val lastOdometerEdited: Boolean,
) {
    fun toBundle() = Bundle().apply {
        putLong("vehicleId", vehicleId)
        putString("title", title)
        putBoolean("titleEdited", titleEdited)
        putString("category", category?.name)
        putString("kind", kind.name)
        putString("everyMonthsText", everyMonthsText)
        putString("everyDistanceText", everyDistanceText)
        putBoolean("everyDistanceEdited", everyDistanceEdited)
        putInt("lastDone", lastDone.value)
        putBoolean("lastDoneEdited", lastDoneEdited)
        putString("lastOdometerText", lastOdometerText)
        putBoolean("lastOdometerEdited", lastOdometerEdited)
    }

    companion object {
        fun fromBundle(b: Bundle) = ReminderForm(
            vehicleId = b.getLong("vehicleId"),
            title = b.getString("title").orEmpty(),
            titleEdited = b.getBoolean("titleEdited"),
            category = b.getString("category")?.let { decodeOr(it, ExpenseCategory.OTHER) },
            kind = decodeOr(b.getString("kind"), ReminderKind.BOTH),
            everyMonthsText = b.getString("everyMonthsText").orEmpty(),
            everyDistanceText = b.getString("everyDistanceText").orEmpty(),
            everyDistanceEdited = b.getBoolean("everyDistanceEdited"),
            lastDone = CivilDate(b.getInt("lastDone")),
            lastDoneEdited = b.getBoolean("lastDoneEdited"),
            lastOdometerText = b.getString("lastOdometerText").orEmpty(),
            lastOdometerEdited = b.getBoolean("lastOdometerEdited"),
        )

        /** Nothing chosen: the category chips come first and name it. */
        fun blank(vehicleId: Long, today: CivilDate) = ReminderForm(
            vehicleId = vehicleId,
            title = "",
            titleEdited = false,
            category = null,
            kind = ReminderKind.BOTH,
            everyMonthsText = "",
            everyDistanceText = "",
            everyDistanceEdited = false,
            lastDone = today,
            lastDoneEdited = false,
            lastOdometerText = "",
            lastOdometerEdited = false,
        )

        fun of(reminder: Reminder, unit: DistanceUnit, today: CivilDate, locale: Locale) = ReminderForm(
            vehicleId = reminder.vehicleId,
            title = reminder.title,
            titleEdited = true,
            category = reminder.category,
            kind = reminder.kind,
            everyMonthsText = reminder.repeatMonths?.let { InputText.of(it.toDouble(), 0, locale) }.orEmpty(),
            everyDistanceText = reminder.repeatDistanceM?.let { InputText.of(unit.fromMetres(it), 1, locale) }.orEmpty(),
            everyDistanceEdited = false,
            lastDone = reminder.anchorDate ?: today,
            lastDoneEdited = true,
            lastOdometerText = reminder.anchorOdometerM?.let { InputText.of(unit.fromMetres(it), 1, locale) }.orEmpty(),
            lastOdometerEdited = false,
        )
    }
}

data class ReminderEditorUiState(
    val isNew: Boolean,
    val form: ReminderForm? = null,
    val vehicle: Vehicle? = null,
    val today: CivilDate? = null,
    val currentOdometerM: Long? = null,
    /** What saving would store, and where that leaves it today. Null while it cannot save. */
    val preview: Pair<Reminder, ReminderStatus>? = null,
    /** Newest first. */
    val history: List<ReminderCompletion> = emptyList(),
    val canSave: Boolean = false,
    val isDirty: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    val categories: List<ExpenseCategory> get() = CATEGORIES

    companion object {
        /** The jobs that come round again. Tolls, parking and fines do not. */
        val CATEGORIES: List<ExpenseCategory> = ExpenseCategory.chipOrder -
            setOf(ExpenseCategory.TOLL, ExpenseCategory.PARKING, ExpenseCategory.FINE)
    }
}

/** Add or edit a reminder. It saves once it has a name and a repeat for what it counts. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderEditorViewModel(
    private val handle: SavedStateHandle,
    private val repository: FuelLogRepository,
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale = { Locale.getDefault() },
) : ViewModel() {

    private val editingId: Long = handle.get<Long>(Routes.ARG_ID) ?: 0L
    private val isNew = editingId == 0L
    private val requestedVehicleId: Long = handle.get<Long>(Routes.ARG_VEHICLE) ?: 0L

    private val original = MutableStateFlow<Reminder?>(null)
    private val form = MutableStateFlow(handle.get<Bundle>(KEY_FORM)?.let(ReminderForm::fromBundle))
    private val initial = MutableStateFlow(handle.get<Bundle>(KEY_INITIAL)?.let(ReminderForm::fromBundle))
    private val history = MutableStateFlow<List<ReminderCompletion>>(emptyList())
    private val error = MutableStateFlow<String?>(null)
    private val done = MutableStateFlow(false)

    private fun today() = CivilDate.today(clock, zone())

    init {
        viewModelScope.launch {
            if (!isNew) {
                val stored = repository.reminder(editingId)
                if (stored == null) {
                    done.value = true
                    return@launch
                }
                original.value = stored
                history.value = repository.completions(editingId)
            }
            if (form.value != null) return@launch

            val vehicle = repository.vehicle(original.value?.vehicleId ?: requestedVehicleId)
            if (vehicle == null) {
                done.value = true
                return@launch
            }
            val start = original.value?.let { ReminderForm.of(it, vehicle.distanceUnit, today(), locale()) }
                ?: ReminderForm.blank(vehicle.id, today())
            initial.value = start
            handle[KEY_INITIAL] = start.toBundle()
            setForm(start)
        }
    }

    private val vehicleData: Flow<Pair<Vehicle, Long?>?> = form
        .map { it?.vehicleId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(repository.observeVehicle(id), repository.observeCurrentOdometer(id)) { vehicle, odometer ->
                    vehicle?.let { it to odometer }
                }
            }
        }

    val state: StateFlow<ReminderEditorUiState> = combine(
        combine(form, initial, original, ::Triple),
        vehicleData,
        history,
        combine(error, done, ::Pair),
    ) { (f, start, stored), data, past, (err, isDone) ->
        val base = ReminderEditorUiState(isNew = isNew, history = past, error = err, done = isDone)
        if (f == null || data == null) return@combine base
        val (vehicle, odometer) = data
        val today = today()
        val built = build(f, vehicle, stored, odometer)
        base.copy(
            form = f,
            vehicle = vehicle,
            today = today,
            currentOdometerM = odometer,
            preview = built?.let { it to ReminderEvaluator.status(it, odometer, today) },
            canSave = built != null,
            isDirty = start != null && f != start,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderEditorUiState(isNew = isNew))

    fun onTitle(text: String) = edit { it.copy(title = text, titleEdited = true) }
    fun onKind(kind: ReminderKind) = edit { it.copy(kind = kind) }
    fun onEveryMonths(text: String) = edit { it.copy(everyMonthsText = text) }
    fun onEveryDistance(text: String) = edit { it.copy(everyDistanceText = text, everyDistanceEdited = true) }
    fun onLastDone(date: CivilDate) = edit { it.copy(lastDone = date, lastDoneEdited = true) }
    fun onLastOdometer(text: String) = edit { it.copy(lastOdometerText = text, lastOdometerEdited = true) }

    /**
     * A category names the reminder until the user types a name of their own. On a new one it
     * also looks up the last time that job was logged, since that is when it was last done.
     */
    fun onCategory(category: ExpenseCategory, label: String) {
        val before = form.value ?: return
        edit { it.copy(category = category, title = if (it.titleEdited && it.title.isNotBlank()) it.title else label) }
        if (!isNew || before.lastDoneEdited || before.lastOdometerEdited) return
        viewModelScope.launch {
            val last = repository.lastInCategory(before.vehicleId, category)
            val unit = repository.vehicle(before.vehicleId)?.distanceUnit ?: return@launch
            edit { now ->
                if (now.category != category || now.lastDoneEdited || now.lastOdometerEdited) {
                    now
                } else {
                    now.copy(
                        lastDone = last?.date ?: today(),
                        lastOdometerText = last?.odometerM?.let { InputText.of(unit.fromMetres(it), 1, locale()) }.orEmpty(),
                    )
                }
            }
        }
    }

    fun save() {
        val f = form.value ?: return
        viewModelScope.launch {
            runCatching {
                val vehicle = requireNotNull(repository.vehicle(f.vehicleId)) { "The vehicle no longer exists." }
                val reminder = build(f, vehicle, original.value, repository.currentOdometer(f.vehicleId)) ?: return@launch
                if (isNew) repository.addReminder(reminder) else repository.updateReminder(reminder)
            }
                .onSuccess { done.value = true }
                .onFailure { error.value = it.message }
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            runCatching { repository.deleteReminder(editingId) }
                .onSuccess { done.value = true }
                .onFailure { error.value = it.message }
        }
    }

    fun dismissError() {
        error.value = null
    }

    /**
     * The reminder this form saves, or null while it cannot. With no reading typed, the
     * current one anchors the distance, as the field's hint says. A changed due date or
     * reading is a new round, so the daily check may speak about it again.
     */
    private fun build(f: ReminderForm, vehicle: Vehicle, stored: Reminder?, currentOdometerM: Long?): Reminder? {
        val unit = vehicle.distanceUnit
        val months = ReminderInput.months(f.everyMonthsText)
        val distance = if (stored != null && !f.everyDistanceEdited) {
            stored.repeatDistanceM
        } else {
            ReminderInput.distanceM(f.everyDistanceText, unit)
        }
        if (!ReminderInput.canSave(f.title, f.kind, months, distance)) return null

        val typed = if (stored != null && !f.lastOdometerEdited) {
            stored.anchorOdometerM
        } else {
            EntryValidation.odometerMetres(f.lastOdometerText, unit)
        }
        val anchorOdometer = if (f.kind.usesDistance) typed ?: currentOdometerM else typed
        val next = ReminderEvaluator.schedule(f.kind, months, distance, f.lastDone, anchorOdometer)
        val rescheduled = stored == null || next.dueDate != stored.dueDate || next.dueOdometerM != stored.dueOdometerM

        return (stored ?: Reminder.new(f.vehicleId)).copy(
            title = f.title.trim(),
            kind = f.kind,
            category = f.category,
            repeatMonths = months,
            repeatDistanceM = distance,
            warnDaysBefore = Reminder.warnDaysFor(months),
            warnDistanceM = Reminder.warnDistanceFor(distance),
            dueDate = next.dueDate,
            dueOdometerM = next.dueOdometerM,
            anchorDate = f.lastDone,
            anchorOdometerM = anchorOdometer,
            isActive = true,
            lastNotified = if (rescheduled) null else stored?.lastNotified,
        )
    }

    private fun edit(change: (ReminderForm) -> ReminderForm) {
        form.value?.let { setForm(change(it)) }
    }

    private fun setForm(value: ReminderForm) {
        form.value = value
        handle[KEY_FORM] = value.toBundle()
    }

    private companion object {
        const val KEY_FORM = "reminder_form"
        const val KEY_INITIAL = "reminder_form_initial"
    }
}

/** An empty reminder for [vehicleId], for the editor to fill in. */
private fun Reminder.Companion.new(vehicleId: Long) = Reminder(
    id = 0,
    vehicleId = vehicleId,
    title = "",
    kind = ReminderKind.BOTH,
    category = null,
    dueDate = null,
    repeatMonths = null,
    dueOdometerM = null,
    repeatDistanceM = null,
    anchorDate = null,
    anchorOdometerM = null,
)
