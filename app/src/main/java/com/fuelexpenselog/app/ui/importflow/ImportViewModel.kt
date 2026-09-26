package com.fuelexpenselog.app.ui.importflow

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.transfer.SafGateway
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.csv.imprt.DateFormatResolver
import com.fuelexpenselog.csv.imprt.DateOrder
import com.fuelexpenselog.csv.imprt.DialectId
import com.fuelexpenselog.csv.imprt.Field
import com.fuelexpenselog.csv.imprt.ImportAssumptions
import com.fuelexpenselog.csv.imprt.ImportPlan
import com.fuelexpenselog.csv.imprt.ImportPlanner
import com.fuelexpenselog.csv.imprt.StagedEntry
import com.fuelexpenselog.csv.imprt.StagedImport
import com.fuelexpenselog.csv.imprt.StagedRow
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Clock
import java.time.ZoneId

enum class RowFilter { ALL, WARNINGS, DUPLICATES, ERRORS }

enum class ImportFailure { UNREADABLE, TOO_LARGE, NO_ROWS }

data class ImportDone(val count: Int, val vehicleName: String)

data class ImportUiState(
    val loading: Boolean = true,
    val failure: ImportFailure? = null,
    val fileName: String = "",
    val plan: ImportPlan? = null,
    val assumptions: ImportAssumptions? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val target: Vehicle? = null,
    val rows: List<StagedRow> = emptyList(),
    /** Rows the user switched on or off, by line, over what each row does by default. */
    val toggles: Map<Int, Boolean> = emptyMap(),
    val filter: RowFilter = RowFilter.ALL,
    /** A choice was just made and the rows below are being read again. */
    val staging: Boolean = false,
    val importing: Boolean = false,
    val done: ImportDone? = null,
    val error: String? = null,
) {
    /** The file's dates read two ways and nobody has said which yet: nothing can be imported. */
    val needsDateAnswer: Boolean
        get() = plan?.dates is DateFormatResolver.Result.Ambiguous && assumptions?.dateOrder == null

    fun isIncluded(row: StagedRow): Boolean = row.entry != null && (toggles[row.line] ?: row.includedByDefault)

    val importCount: Int get() = rows.count(::isIncluded)

    val canImport: Boolean get() = !importing && !staging && target != null && importCount > 0

    fun matching(filter: RowFilter): List<StagedRow> = when (filter) {
        RowFilter.ALL -> rows
        RowFilter.WARNINGS -> rows.filter { it.hasWarnings }
        RowFilter.DUPLICATES -> rows.filter { it.duplicate }
        RowFilter.ERRORS -> rows.filter { it.isError }
    }

    val visible: List<StagedRow> get() = matching(filter)
}

/**
 * The import preview. The file is read once; after that every choice - the vehicle, the date
 * order, the units, a generic file's columns - re-runs only the pure staging step, so the rows
 * answer at once. Nothing is written until "Import", and then all of it in one transaction
 * that "Undo import" can take back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModel(
    private val handle: SavedStateHandle,
    private val repository: FuelLogRepository,
    private val prefs: AppPrefs,
    private val saf: SafGateway,
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val uri: String = checkNotNull(handle.get<String>(Routes.ARG_URI))
    private val requestedVehicle: Long = handle.get<Long>(Routes.ARG_VEHICLE) ?: 0L

    private val loaded = MutableStateFlow<Loaded?>(null)
    private val choices = MutableStateFlow(Choices.restore(handle))
    private val toggles = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    private val filter = MutableStateFlow(RowFilter.ALL)
    private val importing = MutableStateFlow(false)
    private val done = MutableStateFlow<ImportDone?>(null)
    private val error = MutableStateFlow<String?>(null)

    private sealed interface Loaded {
        data class Read(val fileName: String, val plan: ImportPlan) : Loaded
        data class Failed(val why: ImportFailure) : Loaded
    }

    /** What the user chose. Units and dates left null follow the file, then the vehicle. */
    private data class Choices(
        val targetId: Long = 0,
        val dateOrder: DateOrder? = null,
        val distance: DistanceUnit? = null,
        val volume: EnergyUnit? = null,
        val sourceVehicle: String? = null,
        val mapping: Map<Field, Int>? = null,
    ) {
        fun save(handle: SavedStateHandle) {
            handle[KEY_TARGET] = targetId
            handle[KEY_DATES] = dateOrder?.name
            handle[KEY_DISTANCE] = distance?.name
            handle[KEY_VOLUME] = volume?.name
            handle[KEY_SOURCE] = sourceVehicle
        }

        companion object {
            fun restore(handle: SavedStateHandle) = Choices(
                targetId = handle.get<Long>(KEY_TARGET) ?: 0L,
                dateOrder = handle.get<String>(KEY_DATES)?.let { name -> DateOrder.entries.firstOrNull { it.name == name } },
                distance = handle.get<String>(KEY_DISTANCE)?.let { name -> DistanceUnit.entries.firstOrNull { it.name == name } },
                volume = handle.get<String>(KEY_VOLUME)?.let { name -> EnergyUnit.entries.firstOrNull { it.name == name } },
                sourceVehicle = handle.get<String>(KEY_SOURCE),
            )
        }
    }

    init {
        viewModelScope.launch {
            loaded.value = withContext(io) { read() }
        }
    }

    private fun read(): Loaded = try {
        val parsed = Uri.parse(uri)
        val bytes = saf.openInput(parsed).use(::readCapped)
        if (bytes == null) {
            Loaded.Failed(ImportFailure.TOO_LARGE)
        } else {
            val plan = ImportPlanner.plan(bytes)
            if (plan.rowCount == 0) {
                Loaded.Failed(ImportFailure.NO_ROWS)
            } else {
                Loaded.Read(saf.displayName(parsed) ?: parsed.lastPathSegment.orEmpty(), plan)
            }
        }
    } catch (unreadable: Exception) {
        Loaded.Failed(ImportFailure.UNREADABLE)
    }

    /** Null past [MAX_BYTES]: no fuel log is that big, and reading it all in would not end well. */
    private fun readCapped(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) return null
        }
    }

    private val vehicles: Flow<List<Vehicle>> = repository.observeVehicles().map { list -> list.filter { !it.isArchived } }

    private data class Staged(
        val choices: Choices,
        val plan: ImportPlan,
        val target: Vehicle,
        val assumptions: ImportAssumptions,
        val rows: List<StagedRow>,
    )

    /** Re-staged on every choice; the file itself is never read again. */
    private val staged: Flow<Staged?> = combine(loaded, vehicles, choices, ::Triple)
        .mapLatest { (l, list, c) ->
            val read = l as? Loaded.Read ?: return@mapLatest null
            val target = list.firstOrNull { it.id == c.targetId }
                ?: list.firstOrNull { it.id == requestedVehicle }
                ?: list.firstOrNull { it.id == prefs.lastVehicleId }
                ?: list.firstOrNull()
                ?: return@mapLatest null
            val plan = c.mapping?.let { ImportPlanner.remap(read.plan, it) } ?: read.plan
            val assumptions = ImportAssumptions(
                dateOrder = c.dateOrder ?: (plan.dates as? DateFormatResolver.Result.Resolved)?.order,
                distanceUnit = c.distance ?: plan.distanceUnit ?: target.distanceUnit,
                volumeUnit = c.volume ?: plan.volumeUnit ?: target.volumeUnit,
                sourceVehicle = c.sourceVehicle ?: plan.vehicles.firstOrNull(),
            )
            val existing = repository.existingEntries(target.id)
            val rows = withContext(work) {
                StagedImport.stage(plan, assumptions, target, zone(), CivilDate.today(clock, zone()), existing)
            }
            Staged(c, plan, target, assumptions, rows)
        }
        .onStart { emit(null) }

    val state: StateFlow<ImportUiState> = combine(
        combine(loaded, staged, vehicles, ::Triple),
        combine(choices, toggles, filter, ::Triple),
        combine(importing, done, error, ::Triple),
    ) { (l, s, list), (c, t, f), (busy, finished, err) ->
        val base = ImportUiState(
            loading = l == null,
            failure = (l as? Loaded.Failed)?.why,
            fileName = (l as? Loaded.Read)?.fileName.orEmpty(),
            vehicles = list,
            toggles = t,
            filter = f,
            importing = busy,
            done = finished,
            error = err,
        )
        when {
            l !is Loaded.Read -> base
            s == null -> base.copy(plan = l.plan, staging = true)
            else -> base.copy(
                plan = s.plan,
                assumptions = s.assumptions,
                target = s.target,
                rows = s.rows,
                staging = s.choices != c,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportUiState())

    fun onTarget(id: Long) = choose(resetRows = true) { it.copy(targetId = id) }
    fun onDateOrder(order: DateOrder) = choose { it.copy(dateOrder = order) }
    fun onDistance(unit: DistanceUnit) = choose { it.copy(distance = unit) }
    fun onVolume(unit: EnergyUnit) = choose { it.copy(volume = unit) }
    fun onSourceVehicle(name: String) = choose(resetRows = true) { it.copy(sourceVehicle = name) }

    fun onFilter(value: RowFilter) {
        filter.value = value
    }

    /** A column means one thing: choosing it for one field takes it from any other. */
    fun onColumn(field: Field, column: Int?) {
        val plan = (loaded.value as? Loaded.Read)?.plan ?: return
        if (plan.dialect != DialectId.GENERIC) return
        choose(resetRows = true) { c ->
            val current = c.mapping ?: plan.tables.firstOrNull()?.mapping.orEmpty()
            val cleared = current.filterValues { it != column } - field
            c.copy(mapping = if (column == null) cleared else cleared + (field to column))
        }
    }

    fun onToggle(row: StagedRow) {
        if (row.entry == null) return
        val included = state.value.isIncluded(row)
        toggles.value = toggles.value + (row.line to !included)
    }

    fun import() {
        val s = state.value
        val target = s.target ?: return
        val plan = s.plan ?: return
        if (!s.canImport) return
        val chosen = s.rows.filter(s::isIncluded)
        importing.value = true
        viewModelScope.launch {
            runCatching {
                repository.commitImport(
                    source = plan.dialect.name,
                    fileName = s.fileName,
                    vehicleId = target.id,
                    fuel = chosen.mapNotNull { row -> (row.entry as? StagedEntry.Fuel)?.let { it.fillUp to it.hash } },
                    costs = chosen.mapNotNull { row -> (row.entry as? StagedEntry.Cost)?.let { it.expense to it.hash } },
                )
            }
                .onSuccess {
                    prefs.lastVehicleId = target.id
                    done.value = ImportDone(chosen.size, target.name)
                }
                .onFailure { error.value = it.message }
            importing.value = false
        }
    }

    fun dismissError() {
        error.value = null
    }

    /** A new vehicle or new columns make new rows, so per-row choices start over. */
    private fun choose(resetRows: Boolean = false, change: (Choices) -> Choices) {
        val next = change(choices.value)
        choices.value = next
        next.save(handle)
        if (resetRows) toggles.value = emptyMap()
    }

    private companion object {
        const val MAX_BYTES = 20 * 1024 * 1024
        const val KEY_TARGET = "import_target"
        const val KEY_DATES = "import_dates"
        const val KEY_DISTANCE = "import_distance"
        const val KEY_VOLUME = "import_volume"
        const val KEY_SOURCE = "import_source"
    }
}
