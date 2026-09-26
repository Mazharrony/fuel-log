package com.fuelexpenselog.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fuelexpenselog.app.data.db.dao.ExpenseDao
import com.fuelexpenselog.app.data.db.dao.FillUpDao
import com.fuelexpenselog.app.data.db.dao.ImportBatchDao
import com.fuelexpenselog.app.data.db.dao.OdometerSegmentDao
import com.fuelexpenselog.app.data.db.dao.ReminderDao
import com.fuelexpenselog.app.data.db.dao.VehicleDao
import com.fuelexpenselog.app.data.db.entity.ExpenseEntity
import com.fuelexpenselog.app.data.db.entity.FillUpEntity
import com.fuelexpenselog.app.data.db.entity.ImportBatchEntity
import com.fuelexpenselog.app.data.db.entity.OdometerSegmentEntity
import com.fuelexpenselog.app.data.db.entity.ReminderCompletionEntity
import com.fuelexpenselog.app.data.db.entity.ReminderEntity
import com.fuelexpenselog.app.data.db.entity.VehicleEntity

/** The schema this build writes. A backup claiming a newer one is refused, not guessed at. */
const val SCHEMA_VERSION = 1

/**
 * No views, no triggers, no computed columns. "Nothing derived is persisted" is enforced by
 * the schema containing nothing derived, rather than by everyone remembering the rule.
 */
@Database(
    entities = [
        VehicleEntity::class,
        FillUpEntity::class,
        ExpenseEntity::class,
        OdometerSegmentEntity::class,
        ReminderEntity::class,
        ReminderCompletionEntity::class,
        ImportBatchEntity::class,
    ],
    version = SCHEMA_VERSION,
    exportSchema = true,
)
abstract class FuelLogDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    abstract fun fillUpDao(): FillUpDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun odometerSegmentDao(): OdometerSegmentDao
    abstract fun reminderDao(): ReminderDao
    abstract fun importBatchDao(): ImportBatchDao

    companion object {
        /**
         * Public because the backup rules in AndroidManifest have to name this file
         * explicitly, along with its -wal and -shm siblings.
         */
        const val NAME = "fuel-log.db"

        fun build(context: Context): FuelLogDatabase =
            Room.databaseBuilder(context, FuelLogDatabase::class.java, NAME)
                .addCallback(ForeignKeysOn)
                // Deliberately no fallbackToDestructiveMigration. Silently wiping somebody's
                // tax records because a migration was missing is not an acceptable failure
                // mode; failing to open is at least recoverable from a backup.
                .build()

        /** Room enables foreign keys by default, but "by default" is not a guarantee worth
         *  relying on for the constraint that keeps orphan rows out of a tax export. */
        private val ForeignKeysOn = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}
