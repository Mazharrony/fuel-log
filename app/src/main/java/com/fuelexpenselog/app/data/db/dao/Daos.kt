package com.fuelexpenselog.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fuelexpenselog.app.data.db.entity.ExpenseEntity
import com.fuelexpenselog.app.data.db.entity.FillUpEntity
import com.fuelexpenselog.app.data.db.entity.ImportBatchEntity
import com.fuelexpenselog.app.data.db.entity.OdometerSegmentEntity
import com.fuelexpenselog.app.data.db.entity.ReminderCompletionEntity
import com.fuelexpenselog.app.data.db.entity.ReminderEntity
import com.fuelexpenselog.app.data.db.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * No aggregation happens in SQL.
 *
 * SQLite's `strftime(..., 'localtime')` reads the process timezone at query time and
 * mishandles historical offsets, so month totals computed in SQL would shift when the user
 * travels. Everything aggregates in Kotlin over rows these DAOs return, which also means
 * there is one code path for money - and therefore no way for the vehicle screen and the
 * month screen to disagree with each other by a penny.
 */

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicle ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicle WHERE id = :id")
    fun observe(id: Long): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicle WHERE id = :id")
    suspend fun byId(id: Long): VehicleEntity?

    @Query("SELECT * FROM vehicle ORDER BY id ASC")
    suspend fun all(): List<VehicleEntity>

    @Query("SELECT COUNT(*) FROM vehicle")
    suspend fun count(): Int

    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Query("UPDATE vehicle SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    /** Cascades to every fill-up, expense, reminder and segment. There is no undo. */
    @Delete
    suspend fun delete(vehicle: VehicleEntity)
}

@Dao
interface FillUpDao {

    /**
     * Ordered the way the engine wants it - date first, reading as the same-day tiebreak -
     * so the sort is a plain index walk rather than a temporary b-tree.
     */
    @Query(
        """
        SELECT * FROM fill_up
        WHERE vehicleId = :vehicleId
        ORDER BY occurredLocalDate ASC, odometerM ASC
        """,
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<FillUpEntity>>

    @Query(
        """
        SELECT * FROM fill_up
        WHERE vehicleId = :vehicleId
          AND occurredLocalDate BETWEEN :fromDate AND :toDate
        ORDER BY occurredLocalDate ASC, odometerM ASC
        """,
    )
    suspend fun inRange(vehicleId: Long, fromDate: Int, toDate: Int): List<FillUpEntity>

    /** The tax export. Uses the (vehicleId, tag, occurredLocalDate) index. */
    @Query(
        """
        SELECT * FROM fill_up
        WHERE vehicleId = :vehicleId AND tag = :tag
          AND occurredLocalDate BETWEEN :fromDate AND :toDate
        ORDER BY occurredLocalDate ASC
        """,
    )
    suspend fun taggedInRange(vehicleId: Long, tag: String, fromDate: Int, toDate: Int): List<FillUpEntity>

    /** Where the vehicle is now. Every distance reminder needs this on every screen open. */
    @Query("SELECT MAX(odometerM) FROM fill_up WHERE vehicleId = :vehicleId")
    fun observeMaxOdometer(vehicleId: Long): Flow<Long?>

    @Query("SELECT MAX(odometerM) FROM fill_up WHERE vehicleId = :vehicleId")
    suspend fun maxOdometer(vehicleId: Long): Long?

    /**
     * The reading an entry is compared against, leaving out the row being edited - otherwise
     * lowering the latest fill-up's own mistyped reading would warn against itself. Still a
     * search on the (vehicleId, odometerM) index.
     */
    @Query("SELECT MAX(odometerM) FROM fill_up WHERE vehicleId = :vehicleId AND id != :excludingId")
    fun observeMaxOdometerExcluding(vehicleId: Long, excludingId: Long): Flow<Long?>

    /**
     * A half-kilometre and half-day window catches a genuine double-entry without flagging
     * two real stops on one day. The result is a WARNING; it never blocks a save.
     */
    @Query(
        """
        SELECT COUNT(*) FROM fill_up
        WHERE vehicleId = :vehicleId
          AND id != :excludingId
          AND ABS(occurredAtMillis - :atMillis) < 43200000
          AND odometerM IS NOT NULL
          AND ABS(odometerM - :odometerM) < 500
        """,
    )
    suspend fun countSimilar(vehicleId: Long, atMillis: Long, odometerM: Long, excludingId: Long): Int

    @Query("SELECT COUNT(*) FROM fill_up WHERE importRowHash = :hash")
    suspend fun countByRowHash(hash: String): Int

    @Query("SELECT * FROM fill_up ORDER BY id ASC")
    suspend fun all(): List<FillUpEntity>

    @Query("SELECT * FROM fill_up WHERE id = :id")
    suspend fun byId(id: Long): FillUpEntity?

    @Insert
    suspend fun insert(fillUp: FillUpEntity): Long

    @Insert
    suspend fun insertAll(fillUps: List<FillUpEntity>): List<Long>

    @Update
    suspend fun update(fillUp: FillUpEntity)

    @Delete
    suspend fun delete(fillUp: FillUpEntity)

    @Query("DELETE FROM fill_up WHERE importBatchId = :batchId")
    suspend fun deleteByBatch(batchId: Long): Int
}

@Dao
interface ExpenseDao {

    @Query(
        """
        SELECT * FROM expense
        WHERE vehicleId = :vehicleId
        ORDER BY occurredLocalDate DESC, id DESC
        """,
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expense
        WHERE vehicleId = :vehicleId
          AND occurredLocalDate BETWEEN :fromDate AND :toDate
        ORDER BY occurredLocalDate ASC
        """,
    )
    suspend fun inRange(vehicleId: Long, fromDate: Int, toDate: Int): List<ExpenseEntity>

    @Query(
        """
        SELECT * FROM expense
        WHERE vehicleId = :vehicleId AND tag = :tag
          AND occurredLocalDate BETWEEN :fromDate AND :toDate
        ORDER BY occurredLocalDate ASC
        """,
    )
    suspend fun taggedInRange(vehicleId: Long, tag: String, fromDate: Int, toDate: Int): List<ExpenseEntity>

    /**
     * "When was the last oil change." Uses the (vehicleId, category, occurredLocalDate)
     * index, and is what makes a service reminder possible with no background work at all.
     */
    @Query(
        """
        SELECT * FROM expense
        WHERE vehicleId = :vehicleId AND category = :category
        ORDER BY occurredLocalDate DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun lastInCategory(vehicleId: Long, category: String): ExpenseEntity?

    @Query("SELECT MAX(odometerM) FROM expense WHERE vehicleId = :vehicleId")
    fun observeMaxOdometer(vehicleId: Long): Flow<Long?>

    @Query("SELECT MAX(odometerM) FROM expense WHERE vehicleId = :vehicleId")
    suspend fun maxOdometer(vehicleId: Long): Long?

    @Query("SELECT MAX(odometerM) FROM expense WHERE vehicleId = :vehicleId AND id != :excludingId")
    fun observeMaxOdometerExcluding(vehicleId: Long, excludingId: Long): Flow<Long?>

    @Query("SELECT * FROM expense ORDER BY id ASC")
    suspend fun all(): List<ExpenseEntity>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun byId(id: Long): ExpenseEntity?

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Insert
    suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long>

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expense WHERE importBatchId = :batchId")
    suspend fun deleteByBatch(batchId: Long): Int
}

@Dao
interface OdometerSegmentDao {

    @Query("SELECT * FROM odometer_segment WHERE vehicleId = :vehicleId ORDER BY startsAtLocalDate ASC")
    fun observeForVehicle(vehicleId: Long): Flow<List<OdometerSegmentEntity>>

    @Query("SELECT * FROM odometer_segment ORDER BY id ASC")
    suspend fun all(): List<OdometerSegmentEntity>

    @Insert
    suspend fun insert(segment: OdometerSegmentEntity): Long

    @Delete
    suspend fun delete(segment: OdometerSegmentEntity)
}

@Dao
interface ReminderDao {

    @Query(
        """
        SELECT * FROM reminder
        WHERE vehicleId = :vehicleId AND isActive = 1
        ORDER BY dueLocalDate ASC
        """,
    )
    fun observeActiveForVehicle(vehicleId: Long): Flow<List<ReminderEntity>>

    /** The daily alarm's scan, across every vehicle at once. */
    @Query(
        """
        SELECT * FROM reminder
        WHERE isActive = 1 AND notifyEnabled = 1
        ORDER BY dueLocalDate ASC
        """,
    )
    suspend fun allNotifiable(): List<ReminderEntity>

    @Query("SELECT * FROM reminder ORDER BY id ASC")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun byId(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("SELECT * FROM reminder_completion WHERE reminderId = :reminderId ORDER BY completedLocalDate DESC")
    suspend fun completions(reminderId: Long): List<ReminderCompletionEntity>

    @Query("SELECT * FROM reminder_completion ORDER BY id ASC")
    suspend fun allCompletions(): List<ReminderCompletionEntity>

    @Insert
    suspend fun insertCompletion(completion: ReminderCompletionEntity): Long
}

@Dao
interface ImportBatchDao {

    @Query("SELECT * FROM import_batch ORDER BY importedAtMillis DESC")
    fun observeAll(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batch ORDER BY id ASC")
    suspend fun all(): List<ImportBatchEntity>

    @Insert
    suspend fun insert(batch: ImportBatchEntity): Long

    @Query("DELETE FROM import_batch WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Undo, as one unit. Import is the highest-regret operation in the app. */
    @Transaction
    suspend fun undo(batchId: Long, fillUps: FillUpDao, expenses: ExpenseDao): Int {
        val removed = fillUps.deleteByBatch(batchId) + expenses.deleteByBatch(batchId)
        deleteById(batchId)
        return removed
    }
}
