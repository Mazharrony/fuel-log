package com.fuelexpenselog.app

import android.content.Context
import com.fuelexpenselog.app.data.FuelLogRepository
import com.fuelexpenselog.app.data.SettingsStore
import com.fuelexpenselog.app.data.db.FuelLogDatabase

/**
 * Manual dependency wiring. At this size a DI framework costs more in build
 * configuration and permission-audit surface than it returns.
 */
class AppContainer(context: Context) {
    private val database by lazy { FuelLogDatabase.build(context) }

    val repository by lazy {
        FuelLogRepository(database.vehicleDao(), database.fillUpDao(), database.expenseDao())
    }

    val settings by lazy { SettingsStore(context) }
}
