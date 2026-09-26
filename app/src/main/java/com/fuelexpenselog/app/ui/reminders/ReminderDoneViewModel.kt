package com.fuelexpenselog.app.ui.reminders

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.ui.entry.entryInstant
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.parse.DecimalParser
import com.fuelexpenselog.domain.parse.NumberKind
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderEvaluator
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.validate.EntryContext
import com.fuelexpenselog.domain.validate.EntryValidation
import com.fuelexpenselog.domain.validate.EntryWarning
import com.fuelexpenselog.domain.validate.ExpenseDraft
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

/** Marking a reminder done: when, at what reading, and what it cost. Every field is optional. */
data class ReminderDoneForm(
    val date: CivilDate,
    val amountText: String,
    val odometerText: String,
    val note: String,
) {
    fun toBundle() = Bundle().apply {
        putInt("date", date.value)
        putString("amountText", amountText)
        putString("odometerText", odometerText)
        putString("note", note)
    }

    companion object {
        fun fromBundle(b: Bundle) = ReminderDoneForm(
            date = CivilDate(b.getInt("date")),
            amountText = b.getString("amountText").orEmpty(),
            odometerText = b.getString("odometerText").orEmpty(),
            note = b.getString("note").orEmpty(),
        )

        fun blank(today: CivilDate) = ReminderDoneForm(today, "", "", "")
    }
}

data class ReminderDoneUiState(
    val reminder: Reminder? = null,
    val vehicle: Vehicle? = null,
    val form: ReminderDoneForm? = null,
    val today: CivilDate? = null,
    /** The current reading: the hint under the odometer, and the anchor when it is left empty. */
    val previousOdometerM: Long? = null,
    val warnings: List<EntryWarning> = emptyList(),
    /** Where marking it done with this form leaves it. */
    val next: Reminder? = null,
    val isDirty: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

/**
 * Done: the completion, an expense if it cost something, and the next round - one
 * transaction in the repository. A reading lower than the last one warns and still saves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderDoneViewModel(
    private val handle: SavedStateHandle,
    private val repository: FuelLogRepository,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {

    private val reminderId: Long = checkNotNull(handle.get<Long>(Routes.ARG_ID))

    private val reminder = MutableStateFlow<Reminder?>(null)
    private val form = MutableStateFlow(handle.get<Bundle>(KEY_FORM)?.let(ReminderDoneForm::fromBundle))
    private val error = MutableStateFlow<String?>(null)
    private val done = MutableStateFlow(false)

    private fun today() = CivilDate.today(clock, zone())

    init {
        viewModelScope.launch {
            val stored = repository.reminder(reminderId)
            if (stored == null) {
                done.value = true
                return@launch
            }
            reminder.value = stored
            if (form.value == null) setForm(ReminderDoneForm.blank(today()))
        }
    }

    private val vehicleData: Flow<Pair<Vehicle, Long?>?> = reminder
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

    val state: StateFlow<ReminderDoneUiState> = combine(
        form,
        reminder,
        vehicleData,
        combine(error, done, ::Pair),
    ) { f, r, data, (err, isDone) ->
        val base = ReminderDoneUiState(error = err, done = isDone)
        if (f == null || r == null || data == null) return@combine base
        val (vehicle, current) = data
        val today = today()
        val unit = vehicle.distanceUnit
        // The expense rules, less the one block: here the amount is optional.
        val warnings = EntryValidation.validate(
            ExpenseDraft(f.amountText, f.odometerText, f.date, unit),
            EntryContext(today = today, previousOdometerM = current),
        ).warnings
        base.copy(
            reminder = r,
            vehicle = vehicle,
            form = f,
            today = today,
            previousOdometerM = current,
            warnings = warnings,
            next = ReminderEvaluator.advance(r, f.date, EntryValidation.odometerMetres(f.odometerText, unit) ?: current),
            isDirty = f != ReminderDoneForm.blank(today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderDoneUiState())

    fun onDate(date: CivilDate) = edit { it.copy(date = date) }
    fun onAmount(text: String) = edit { it.copy(amountText = text) }
    fun onOdometer(text: String) = edit { it.copy(odometerText = text) }
    fun onNote(text: String) = edit { it.copy(note = text) }

    fun save() {
        val f = form.value ?: return
        val r = reminder.value ?: return
        viewModelScope.launch {
            runCatching {
                val vehicle = requireNotNull(repository.vehicle(r.vehicleId)) { "The vehicle no longer exists." }
                val odometerM = EntryValidation.odometerMetres(f.odometerText, vehicle.distanceUnit)
                val note = f.note.trim().ifEmpty { null }
                val expense = DecimalParser.parseOrNull(f.amountText, NumberKind.MONEY_TOTAL)?.let { amount ->
                    Expense(
                        id = 0,
                        vehicleId = r.vehicleId,
                        date = f.date,
                        instantMillis = entryInstant(f.date, today(), clock, zone()),
                        odometerM = odometerM,
                        category = r.category ?: ExpenseCategory.OTHER,
                        amount = Money.of(amount, vehicle.currencyCode),
                        tag = vehicle.defaultTag,
                        note = note,
                        reminderId = r.id,
                    )
                }
                repository.completeReminder(r.id, f.date, odometerM, expense, note)
            }
                .onSuccess { done.value = true }
                .onFailure { error.value = it.message }
        }
    }

    fun dismissError() {
        error.value = null
    }

    private fun edit(change: (ReminderDoneForm) -> ReminderDoneForm) {
        form.value?.let { setForm(change(it)) }
    }

    private fun setForm(value: ReminderDoneForm) {
        form.value = value
        handle[KEY_FORM] = value.toBundle()
    }

    private companion object {
        const val KEY_FORM = "reminder_done_form"
    }
}
