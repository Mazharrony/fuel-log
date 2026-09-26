package com.fuelexpenselog.app.data.repo

import com.fuelexpenselog.app.data.db.FuelLogDatabase
import com.fuelexpenselog.app.data.db.entity.ImportBatchEntity
import com.fuelexpenselog.domain.consumption.ConsumptionEngine
import com.fuelexpenselog.domain.consumption.ConsumptionResult
import com.fuelexpenselog.domain.consumption.DeclaredSegment
import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.model.HistoryEntry
import com.fuelexpenselog.domain.model.MaybeUnreadable
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.buildHistory
import com.fuelexpenselog.domain.unit.EnergyKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The only data entry point the UI knows about.
 *
 * **Nothing derived is ever stored.** Editing or deleting one fill-up changes every span
 * after it, and recomputing is cheap at these volumes - a sort and a single pass over a few
 * hundred rows. Cached derived values are how a list and its total end up disagreeing with
 * each other, which users read as the app being broken.
 */
class FuelLogRepository(
    /**
     * A provider, not an instance: restoring a backup closes the live database and opens the
     * restored file, and every DAO below must follow it there without anyone holding a
     * reference to the old one.
     */
    private val database: () -> FuelLogDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {

    constructor(db: FuelLogDatabase, now: () -> Long = System::currentTimeMillis) : this({ db }, now)

    private val vehicles get() = database().vehicleDao()
    private val fillUps get() = database().fillUpDao()
    private val expenses get() = database().expenseDao()
    private val segments get() = database().odometerSegmentDao()
    private val batches get() = database().importBatchDao()

    // -- vehicles ------------------------------------------------------------------------

    fun observeVehicles(): Flow<List<Vehicle>> =
        vehicles.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeVehicle(id: Long): Flow<Vehicle?> =
        vehicles.observe(id).map { it?.toDomain() }

    suspend fun vehicle(id: Long): Vehicle? = vehicles.byId(id)?.toDomain()

    suspend fun vehicleCount(): Int = vehicles.count()

    suspend fun addVehicle(vehicle: Vehicle): Long =
        vehicles.insert(vehicle.copy(createdAtMillis = now()).toEntity())

    suspend fun updateVehicle(vehicle: Vehicle) {
        requireEditable(vehicle)
        vehicles.update(vehicle.toEntity())
    }

    /**
     * A targeted UPDATE of one column, deliberately not [updateVehicle]: a vehicle this build
     * cannot fully read must still be hideable, and a full-row update would write this
     * build's guess over the unit it does not understand.
     */
    suspend fun setVehicleArchived(id: Long, archived: Boolean) {
        vehicles.setArchived(id, archived)
    }

    /** Cascades to every fill-up, expense, reminder and segment. There is no undo. */
    suspend fun deleteVehicle(vehicle: Vehicle) {
        vehicles.byId(vehicle.id)?.let { vehicles.delete(it) }
    }

    /** Fill-ups plus expenses: the number the delete confirmation has to name. */
    fun observeEntryCount(vehicleId: Long): Flow<Int> = observeHistory(vehicleId).map { it.size }

    // -- entries -------------------------------------------------------------------------

    fun observeFillUps(vehicleId: Long): Flow<List<FillUp>> =
        fillUps.observeForVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    fun observeExpenses(vehicleId: Long): Flow<List<Expense>> =
        expenses.observeForVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    fun observeHistory(vehicleId: Long): Flow<List<HistoryEntry>> =
        combine(observeFillUps(vehicleId), observeExpenses(vehicleId), ::buildHistory)

    suspend fun addFillUp(fillUp: FillUp): Long = fillUps.insert(fillUp.toEntity(now()))

    suspend fun updateFillUp(fillUp: FillUp) {
        requireEditable(fillUp)
        val existing = fillUps.byId(fillUp.id) ?: return
        fillUps.update(fillUp.toEntity(now(), createdAtMillis = existing.createdAtMillis))
    }

    suspend fun deleteFillUp(id: Long) {
        fillUps.byId(id)?.let { fillUps.delete(it) }
    }

    suspend fun addExpense(expense: Expense): Long = expenses.insert(expense.toEntity(now()))

    suspend fun updateExpense(expense: Expense) {
        requireEditable(expense)
        val existing = expenses.byId(expense.id) ?: return
        expenses.update(expense.toEntity(now(), createdAtMillis = existing.createdAtMillis))
    }

    suspend fun deleteExpense(id: Long) {
        expenses.byId(id)?.let { expenses.delete(it) }
    }

    // -- derived, always recomputed ---------------------------------------------------------

    /**
     * Consumption for one vehicle, recomputed on every change to its rows.
     *
     * Ten years of weekly fill-ups is about 520 rows; a big CSV import is a few thousand.
     * Sorting and walking that is sub-millisecond, so there is nothing here worth caching
     * and a great deal worth not getting stale.
     */
    fun observeConsumption(vehicle: Vehicle, kind: EnergyKind = EnergyKind.LIQUID): Flow<ConsumptionResult> =
        combine(
            observeFillUps(vehicle.id),
            segments.observeForVehicle(vehicle.id),
        ) { entries, declared ->
            ConsumptionEngine.compute(
                events = entries.map { it.toFuelEvent() },
                kind = kind,
                declaredSegments = declared.map { it.toDomain() },
                displayUnit = vehicle.distanceUnit,
            )
        }

    /**
     * Where the vehicle is now, from fill-ups AND expenses - a service records a reading too.
     *
     * Used to show the last reading as a HINT rather than to pre-fill the field: pre-filling
     * invites a save that silently repeats the previous reading, which produces a
     * zero-distance span and a dash where a figure should be.
     */
    fun observeCurrentOdometer(vehicleId: Long): Flow<Long?> =
        combine(
            fillUps.observeMaxOdometer(vehicleId),
            expenses.observeMaxOdometer(vehicleId),
        ) { fromFuel, fromCost -> maxOfNullable(fromFuel, fromCost) }

    suspend fun currentOdometer(vehicleId: Long): Long? =
        maxOfNullable(fillUps.maxOdometer(vehicleId), expenses.maxOdometer(vehicleId))

    /**
     * Whether an entry looks like a double-save. A warning only - it never blocks.
     * Half a kilometre and half a day catches a genuine duplicate without flagging two real
     * stops on the same day.
     */
    suspend fun looksLikeDuplicate(
        vehicleId: Long,
        atMillis: Long,
        odometerM: Long,
        excludingId: Long = 0,
    ): Boolean = fillUps.countSimilar(vehicleId, atMillis, odometerM, excludingId) > 0

    suspend fun lastInCategory(vehicleId: Long, category: ExpenseCategory): Expense? =
        expenses.lastInCategory(vehicleId, category.name)?.toDomain()

    suspend fun addSegment(vehicleId: Long, segment: DeclaredSegment): Long =
        segments.insert(
            com.fuelexpenselog.app.data.db.entity.OdometerSegmentEntity(
                vehicleId = vehicleId,
                startsAtLocalDate = segment.startsAt.value,
                startsAtMillis = now(),
                reason = segment.reason.name,
                offsetM = segment.offsetM,
                note = null,
                createdAtMillis = now(),
            ),
        )

    // -- bulk, for CSV and backup --------------------------------------------------------------

    suspend fun allVehicles() = vehicles.all().map { it.toDomain() }
    suspend fun allFillUps() = fillUps.all().map { it.toDomain() }
    suspend fun allExpenses() = expenses.all().map { it.toDomain() }

    suspend fun recordImport(source: String, fileName: String, rowCount: Int, vehicleId: Long?): Long =
        batches.insert(
            ImportBatchEntity(
                source = source,
                fileName = fileName,
                importedAtMillis = now(),
                rowCount = rowCount,
                vehicleId = vehicleId,
            ),
        )

    /** Undo an entire import in one go. */
    suspend fun undoImport(batchId: Long): Int = batches.undo(batchId, fillUps, expenses)

    private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    /**
     * Refuses to overwrite a row this build cannot fully read.
     *
     * Without this, restoring a backup written by a newer version and then editing an
     * unrelated field - a note, a station name - would write this build's fallback over the
     * real unit or currency, permanently. The row stays visible and stays correct; it just
     * cannot be saved until a version that understands it is installed.
     */
    private fun requireEditable(record: MaybeUnreadable) {
        require(record.isEditable) {
            "This entry was created by a newer version of the app and cannot be edited here: " +
                record.unreadable.joinToString()
        }
    }
}
