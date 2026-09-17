package com.fuelexpenselog.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The DAOs stay deliberately dumb: they read and write rows.
 *
 * Every aggregation happens in Kotlin with java.time, never in SQL. SQLite's
 * strftime(..., 'localtime') uses the process timezone at query time and
 * mishandles historical offsets, so month totals would shift when the user
 * travels. One code path for money also means the vehicle, months and
 * month-detail screens cannot disagree with each other.
 */
@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicle ORDER BY isActive DESC, createdAt ASC, id ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicle WHERE id = :id")
    fun observeById(id: Long): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicle WHERE id = :id")
    suspend fun getById(id: Long): VehicleEntity?

    @Query("SELECT * FROM vehicle ORDER BY id ASC")
    suspend fun getAll(): List<VehicleEntity>

    @Query("SELECT COUNT(*) FROM vehicle")
    suspend fun count(): Int

    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Delete
    suspend fun delete(vehicle: VehicleEntity)
}

@Dao
interface FillUpDao {
    /** Ordered the way the engine wants it, so SQLite does the sorting. */
    @Query("SELECT * FROM fill_up WHERE vehicleId = :vehicleId ORDER BY odometer ASC, date ASC")
    fun observeForVehicle(vehicleId: Long): Flow<List<FillUpEntity>>

    @Query("SELECT * FROM fill_up WHERE id = :id")
    suspend fun getById(id: Long): FillUpEntity?

    @Query("SELECT * FROM fill_up ORDER BY vehicleId ASC, odometer ASC")
    suspend fun getAll(): List<FillUpEntity>

    @Query("SELECT MAX(odometer) FROM fill_up WHERE vehicleId = :vehicleId")
    suspend fun maxOdometer(vehicleId: Long): Double?

    @Query("SELECT COUNT(*) FROM fill_up")
    suspend fun count(): Int

    /**
     * Backs the "looks like a duplicate" warning. A half-kilometre window
     * catches a genuine double-entry without flagging two real stops on one day.
     */
    @Query(
        """
        SELECT COUNT(*) FROM fill_up
        WHERE vehicleId = :vehicleId
          AND date BETWEEN :fromDate AND :toDate
          AND ABS(odometer - :odometer) < 0.5
          AND id != :excludingId
        """
    )
    suspend fun countSimilar(
        vehicleId: Long,
        fromDate: Long,
        toDate: Long,
        odometer: Double,
        excludingId: Long = 0,
    ): Int

    @Insert
    suspend fun insert(fillUp: FillUpEntity): Long

    @Insert
    suspend fun insertAll(fillUps: List<FillUpEntity>)

    @Update
    suspend fun update(fillUp: FillUpEntity)

    @Delete
    suspend fun delete(fillUp: FillUpEntity)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expense ORDER BY vehicleId ASC, date ASC")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT MAX(odometer) FROM expense WHERE vehicleId = :vehicleId")
    suspend fun maxOdometer(vehicleId: Long): Double?

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Insert
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)
}
