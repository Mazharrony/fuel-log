package com.fuelexpenselog.app.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.data.db.FuelLogDatabase
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository

/** An in-memory database, a repository over it, and a fresh prefs file, for one test. */
class TestDb {
    val context: Context = ApplicationProvider.getApplicationContext()

    val db: FuelLogDatabase = Room.inMemoryDatabaseBuilder(context, FuelLogDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val repo = FuelLogRepository(db) { DbFixtures.NOW }

    val prefs: AppPrefs = AppPrefs(context).also {
        context.getSharedPreferences(AppPrefs.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    fun close() = db.close()
}
