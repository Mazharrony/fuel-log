package com.fuelexpenselog.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [VehicleEntity::class, FillUpEntity::class, ExpenseEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FuelLogDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun fillUpDao(): FillUpDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        /**
         * Named so the auto-backup rules can list it explicitly, along with its
         * -wal and -shm siblings. Room runs in WAL mode, and backing up the main
         * file alone can capture an inconsistent snapshot.
         */
        const val NAME = "fuel-log.db"

        fun build(context: Context): FuelLogDatabase =
            Room.databaseBuilder(context.applicationContext, FuelLogDatabase::class.java, NAME)
                .build()
    }
}
