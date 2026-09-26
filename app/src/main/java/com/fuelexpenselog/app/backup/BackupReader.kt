package com.fuelexpenselog.app.backup

import android.database.sqlite.SQLiteDatabase
import com.fuelexpenselog.app.data.db.SCHEMA_VERSION
import com.fuelexpenselog.app.data.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Restores a `.fuellogbak`, replacing everything in the app.
 *
 * Every check happens on a copy before the live database is touched: the file must be a Fuel
 * Log backup, and neither its manifest nor the database inside may be newer than this build
 * understands - a newer schema is refused with a message, never opened with a guess. Only
 * then is the live database closed and its three files (.db, -wal, -shm) replaced. A stale
 * -wal left beside the restored file would be replayed over it.
 */
class BackupReader(
    private val scratchDir: File,
    private val databaseFile: () -> File,
    private val prefs: AppPrefs,
    private val closeDatabase: () -> Unit,
    private val appSchemaVersion: Int = SCHEMA_VERSION,
) {

    enum class Refusal { NOT_A_BACKUP, NEWER_SCHEMA }

    sealed interface Result {
        data class Restored(val manifest: BackupManifest) : Result
        data class Refused(val why: Refusal) : Result
    }

    suspend fun restore(input: InputStream): Result = withContext(Dispatchers.IO) {
        val dir = File(scratchDir, "restore").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val copy = File(dir, "restore.db")
            var manifestJson: String? = null
            ZipInputStream(input).use { zip ->
                // Entry names are compared, never used as paths, so a crafted archive cannot
                // write outside the scratch directory.
                generateSequence { zip.nextEntry }.forEach { entry ->
                    when (entry.name) {
                        BackupManifest.ENTRY_MANIFEST -> manifestJson = zip.readBounded(MAX_MANIFEST_BYTES)
                        BackupManifest.ENTRY_DATABASE -> copy.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val manifest = manifestJson?.let(BackupManifest::parse)
                ?: return@withContext Result.Refused(Refusal.NOT_A_BACKUP)
            if (manifest.schemaVersion > appSchemaVersion) return@withContext Result.Refused(Refusal.NEWER_SCHEMA)
            if (!copy.exists() || copy.length() == 0L) return@withContext Result.Refused(Refusal.NOT_A_BACKUP)

            // The manifest could lie; the database's own header cannot.
            val check = inspect(copy) ?: return@withContext Result.Refused(Refusal.NOT_A_BACKUP)
            if (check > appSchemaVersion) return@withContext Result.Refused(Refusal.NEWER_SCHEMA)

            closeDatabase()
            val target = databaseFile()
            target.parentFile?.mkdirs()
            listOf(target, File(target.path + "-wal"), File(target.path + "-shm")).forEach { it.delete() }
            copy.copyTo(target, overwrite = true)
            manifest.applySettings(prefs)
            Result.Restored(manifest)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** The database's user_version, or null when it is not a Fuel Log database. */
    private fun inspect(file: File): Int? = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val hasVehicles = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'vehicle'", null)
                .use { it.moveToFirst() }
            if (hasVehicles) db.version else null
        }
    }.getOrNull()

    private fun ZipInputStream.readBounded(limit: Int): String? {
        val bytes = readNBytesCompat(limit + 1)
        return if (bytes.size > limit) null else bytes.toString(Charsets.UTF_8)
    }

    private fun ZipInputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < max) {
            val n = read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 1_000_000
    }
}
