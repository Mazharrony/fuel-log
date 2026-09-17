package com.fuelexpenselog.app.data

import com.fuelexpenselog.app.data.db.ExpenseDao
import com.fuelexpenselog.app.data.db.FillUpDao
import com.fuelexpenselog.app.data.db.VehicleDao
import com.fuelexpenselog.app.domain.model.Expense
import com.fuelexpenselog.app.domain.model.FillUp
import com.fuelexpenselog.app.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The only data entry point the UI knows about.
 *
 * Nothing derived is ever stored. Deleting or editing a fill-up changes every
 * span after it, and recomputing is cheap at these volumes - a sort and a single
 * pass over a few hundred rows - whereas cached derived values are how a list
 * and its totals end up disagreeing with each other.
 */
class FuelLogRepository(
    private val vehicleDao: VehicleDao,
    private val fillUpDao: FillUpDao,
    private val expenseDao: ExpenseDao,
) {
    fun vehicles(): Flow<List<Vehicle>> =
        vehicleDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun vehicle(id: Long): Flow<Vehicle?> =
        vehicleDao.observeById(id).map { it?.toDomain() }

    fun fillUps(vehicleId: Long): Flow<List<FillUp>> =
        fillUpDao.observeForVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    fun expenses(vehicleId: Long): Flow<List<Expense>> =
        expenseDao.observeForVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    suspend fun getVehicle(id: Long): Vehicle? = vehicleDao.getById(id)?.toDomain()
    suspend fun getFillUp(id: Long): FillUp? = fillUpDao.getById(id)?.toDomain()
    suspend fun getExpense(id: Long): Expense? = expenseDao.getById(id)?.toDomain()

    suspend fun vehicleCount(): Int = vehicleDao.count()
    suspend fun fillUpCount(): Int = fillUpDao.count()

    suspend fun addVehicle(vehicle: Vehicle): Long = vehicleDao.insert(vehicle.toEntity())
    suspend fun updateVehicle(vehicle: Vehicle) = vehicleDao.update(vehicle.toEntity())

    /** Cascades to every fill-up and expense. There is no undo - confirm first. */
    suspend fun deleteVehicle(vehicle: Vehicle) = vehicleDao.delete(vehicle.toEntity())

    suspend fun addFillUp(fillUp: FillUp): Long = fillUpDao.insert(fillUp.toEntity())
    suspend fun updateFillUp(fillUp: FillUp) = fillUpDao.update(fillUp.toEntity())
    suspend fun deleteFillUp(fillUp: FillUp) = fillUpDao.delete(fillUp.toEntity())

    suspend fun addExpense(expense: Expense): Long = expenseDao.insert(expense.toEntity())
    suspend fun updateExpense(expense: Expense) = expenseDao.update(expense.toEntity())
    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense.toEntity())

    /**
     * Highest reading across both tables. Used to pre-fill the odometer field as
     * a HINT rather than a value, and to measure "last done at" distances.
     */
    suspend fun latestOdometerKm(vehicleId: Long): Double? {
        val fromFuel = fillUpDao.maxOdometer(vehicleId)
        val fromExpense = expenseDao.maxOdometer(vehicleId)
        return listOfNotNull(fromFuel, fromExpense).maxOrNull()
    }

    /** Same day, effectively the same reading. Warn, never block. */
    suspend fun looksLikeDuplicate(
        vehicleId: Long,
        dateMillis: Long,
        odometerKm: Double,
        excludingId: Long = 0,
    ): Boolean = fillUpDao.countSimilar(
        vehicleId = vehicleId,
        fromDate = dateMillis - DAY_MILLIS / 2,
        toDate = dateMillis + DAY_MILLIS / 2,
        odometer = odometerKm,
        excludingId = excludingId,
    ) > 0

    // Whole-database reads, for CSV export.
    suspend fun allVehicles(): List<Vehicle> = vehicleDao.getAll().map { it.toDomain() }
    suspend fun allFillUps(): List<FillUp> = fillUpDao.getAll().map { it.toDomain() }
    suspend fun allExpenses(): List<Expense> = expenseDao.getAll().map { it.toDomain() }

    suspend fun insertFillUps(fillUps: List<FillUp>) =
        fillUpDao.insertAll(fillUps.map { it.toEntity() })

    suspend fun insertExpenses(expenses: List<Expense>) =
        expenseDao.insertAll(expenses.map { it.toEntity() })

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
