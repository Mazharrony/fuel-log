package com.fuelexpenselog.app.di

import android.content.Context
import com.fuelexpenselog.app.BuildConfig
import com.fuelexpenselog.app.backup.BackupReader
import com.fuelexpenselog.app.backup.BackupWriter
import com.fuelexpenselog.app.data.db.FuelLogDatabase
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.transfer.SafGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.Clock
import java.time.ZoneId

/**
 * The whole object graph, by hand. About a dozen nodes: Hilt would cost a Gradle plugin, a
 * KSP processor and generated code to audit, for nothing this file cannot say plainly.
 *
 * The clock and the zone live here and only here. Nothing in the domain reads ambient time;
 * a test replaces both, and "today" means the same thing on every screen at once.
 */
class AppContainer(
    private val context: Context,
    val clock: Clock = Clock.systemUTC(),
    /** Read on every call, not captured: the user can cross a border with the app open. */
    val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    @Volatile
    private var db: FuelLogDatabase? = null

    /**
     * Opened on first use, and rebuildable: restoring a backup closes this instance, swaps
     * the file underneath it and opens a new one. Everything reads it through the repository's
     * provider, so nothing keeps a reference to a closed database.
     */
    val database: FuelLogDatabase
        get() = db ?: synchronized(this) {
            db ?: FuelLogDatabase.build(context).also { db = it }
        }

    /** Closes the live database so its files can be replaced. The next access reopens. */
    fun closeDatabase() = synchronized(this) {
        db?.close()
        db = null
    }

    fun databaseFile(): File = context.getDatabasePath(FuelLogDatabase.NAME)

    val repository: FuelLogRepository = FuelLogRepository({ database }, clock::millis)

    val prefs: AppPrefs = AppPrefs(context)

    val saf: SafGateway = SafGateway(context.contentResolver)

    fun backupWriter() = BackupWriter({ database }, ::databaseFile, prefs, BuildConfig.VERSION_NAME, clock)

    fun backupReader() = BackupReader(context.cacheDir, ::databaseFile, prefs, ::closeDatabase)

    private val _restoreEpoch = MutableStateFlow(0)

    /**
     * Bumped after a restore. The UI re-keys its navigation graph on it, so every screen,
     * ViewModel and collected Flow is built again against the restored database.
     */
    val restoreEpoch: StateFlow<Int> = _restoreEpoch.asStateFlow()

    fun onRestored() = _restoreEpoch.update { it + 1 }
}
