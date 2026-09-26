package com.fuelexpenselog.app.backup

import com.fuelexpenselog.app.data.db.FuelLogDatabase
import com.fuelexpenselog.app.data.db.SCHEMA_VERSION
import com.fuelexpenselog.app.data.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a `.fuellogbak`: a zip of `manifest.json` and the database file itself.
 *
 * Room runs in WAL mode, so the `.db` file on disk is stale until the write-ahead log is
 * folded back into it. The checkpoint runs first, with TRUNCATE, and a busy result stops the
 * backup rather than shipping one that is missing the user's most recent entries.
 */
class BackupWriter(
    private val database: () -> FuelLogDatabase,
    private val databaseFile: () -> File,
    private val prefs: AppPrefs,
    private val appVersion: String,
    private val clock: Clock,
) {

    data class Result(val counts: Map<String, Int>, val checkpointBusy: Int)

    suspend fun write(out: OutputStream): Result = withContext(Dispatchers.IO) {
        val sql = database().openHelper.writableDatabase
        val busy = sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        check(busy == 0) { "The database was busy and could not be checkpointed. Nothing was written." }

        val counts = TABLES.associateWith { table ->
            sql.query("SELECT COUNT(*) FROM $table").use { c ->
                c.moveToFirst()
                c.getInt(0)
            }
        }
        val manifest = BackupManifest(
            schemaVersion = SCHEMA_VERSION,
            appVersion = appVersion,
            createdAt = clock.instant().toString(),
            counts = counts,
            settings = BackupManifest.settingsOf(prefs),
            dismissedProposals = prefs.dismissedProposals,
        )

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BackupManifest.ENTRY_MANIFEST))
            zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupManifest.ENTRY_DATABASE))
            databaseFile().inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        Result(counts, busy)
    }

    companion object {
        val TABLES = listOf("vehicle", "fill_up", "expense", "odometer_segment", "reminder", "reminder_completion", "import_batch")
    }
}
