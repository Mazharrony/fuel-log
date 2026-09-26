package com.fuelexpenselog.app.ui.entry

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.parse.DecimalParser
import com.fuelexpenselog.domain.parse.NumberKind
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
import java.util.Locale

data class ExpenseEditorUiState(
    val isNew: Boolean,
    val form: ExpenseForm? = null,
    val vehicle: Vehicle? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val noVehicle: Boolean = false,
    val today: CivilDate? = null,
    val previousOdometerM: Long? = null,
    val warnings: List<EntryWarning> = emptyList(),
    val canSave: Boolean = false,
    val isEditable: Boolean = true,
    val isDirty: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    /** Chips in how often people log them, not declaration order. */
    val categories: List<ExpenseCategory> get() = ExpenseCategory.chipOrder
}

/** Add or edit an expense. Only an empty amount blocks; the odometer is optional. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseEditorViewModel(
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

    private val original = MutableStateFlow<Expense?>(null)
    private val form = MutableStateFlow(handle.get<Bundle>(KEY_FORM)?.let(ExpenseForm::fromBundle))
    private val initial = MutableStateFlow(handle.get<Bundle>(KEY_INITIAL)?.let(ExpenseForm::fromBundle))
    private val noVehicle = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val done = MutableStateFlow(false)

    private fun today() = CivilDate.today(clock, zone())

    init {
        viewModelScope.launch {
            if (!isNew) {
                val stored = repository.expense(editingId)
                if (stored == null) {
                    done.value = true
                    return@launch
                }
                original.value = stored
            }
            if (form.value != null) return@launch

            val start = if (isNew) {
                val vehicle = resolveVehicleId(requestedVehicleId, repository, prefs)?.let { repository.vehicle(it) }
                if (vehicle == null) {
                    noVehicle.value = true
                    return@launch
                }
                ExpenseForm.blank(vehicle, today())
            } else {
                val stored = original.value!!
                val vehicle = repository.vehicle(stored.vehicleId) ?: return@launch
                ExpenseForm.of(stored, vehicle, locale())
            }
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
                combine(
                    repository.observeVehicle(id),
                    repository.observePreviousOdometer(id, excludingExpenseId = editingId),
                ) { vehicle, previous -> vehicle?.let { it to previous } }
            }
        }

    private val activeVehicles = repository.observeVehicles().map { list -> list.filter { !it.isArchived } }

    val state: StateFlow<ExpenseEditorUiState> = combine(
        combine(form, initial, original, ::Triple),
        vehicleData,
        activeVehicles,
        combine(noVehicle, error, done, ::Triple),
    ) { (f, start, stored), data, vehicles, (none, err, isDone) ->
        val base = ExpenseEditorUiState(isNew = isNew, vehicles = vehicles, noVehicle = none, error = err, done = isDone)
        if (f == null || data == null) return@combine base
        val (vehicle, previous) = data
        val today = today()
        val validation = EntryValidation.validate(
            ExpenseDraft(f.amountText, f.odometerText, f.date, vehicle.distanceUnit),
            EntryContext(today = today, previousOdometerM = previous),
        )
        val editable = (stored?.isEditable ?: true) && vehicle.isEditable
        base.copy(
            form = f,
            vehicle = vehicle,
            today = today,
            previousOdometerM = previous,
            warnings = validation.warnings,
            canSave = validation.canSave && editable,
            isEditable = editable,
            isDirty = start != null && f != start,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseEditorUiState(isNew = isNew))

    fun onAmount(text: String) = edit { it.copy(amountText = text, amountEdited = true) }
    fun onOdometer(text: String) = edit { it.copy(odometerText = text, odometerEdited = true) }
    fun onCategory(category: ExpenseCategory) = edit { it.copy(category = category) }
    fun onDate(date: CivilDate) = edit { it.copy(date = date) }
    fun onTag(tag: EntryTag) = edit { it.copy(tag = tag) }
    fun onVendor(text: String) = edit { it.copy(vendor = text) }
    fun onNote(text: String) = edit { it.copy(note = text) }

    fun onVehicle(vehicleId: Long) {
        if (!isNew) return
        viewModelScope.launch {
            val vehicle = repository.vehicle(vehicleId) ?: return@launch
            edit { it.copy(vehicleId = vehicleId, tag = vehicle.defaultTag) }
        }
    }

    fun save() {
        val f = form.value ?: return
        viewModelScope.launch {
            runCatching {
                val vehicle = requireNotNull(repository.vehicle(f.vehicleId)) { "The vehicle no longer exists." }
                val stored = original.value
                val amount = if (stored != null && !f.amountEdited) {
                    stored.amount
                } else {
                    val value = DecimalParser.parseOrNull(f.amountText, NumberKind.MONEY_TOTAL) ?: return@launch
                    Money.of(value, stored?.amount?.currency ?: vehicle.currencyCode)
                }
                val odometerM = if (stored != null && !f.odometerEdited) {
                    stored.odometerM
                } else {
                    EntryValidation.odometerMetres(f.odometerText, vehicle.distanceUnit)
                }
                val expense = Expense(
                    id = editingId,
                    vehicleId = f.vehicleId,
                    date = f.date,
                    instantMillis = entryInstant(f.date, today(), clock, zone(), stored?.let { it.date to it.instantMillis }),
                    odometerM = odometerM,
                    category = f.category,
                    amount = amount,
                    tag = f.tag,
                    vendor = f.vendor.trim().ifEmpty { null },
                    note = f.note.trim().ifEmpty { null },
                    reminderId = stored?.reminderId,
                )
                if (isNew) repository.addExpense(expense) else repository.updateExpense(expense)
                prefs.lastVehicleId = f.vehicleId
            }
                .onSuccess { done.value = true }
                .onFailure { error.value = it.message }
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            runCatching { repository.deleteExpense(editingId) }
                .onSuccess { done.value = true }
                .onFailure { error.value = it.message }
        }
    }

    fun dismissError() {
        error.value = null
    }

    private fun edit(change: (ExpenseForm) -> ExpenseForm) {
        form.value?.let { setForm(change(it)) }
    }

    private fun setForm(value: ExpenseForm) {
        form.value = value
        handle[KEY_FORM] = value.toBundle()
    }

    private companion object {
        const val KEY_FORM = "expense_form"
        const val KEY_INITIAL = "expense_form_initial"
    }
}
