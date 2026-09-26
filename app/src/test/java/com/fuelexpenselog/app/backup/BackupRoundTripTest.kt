package com.fuelexpenselog.app.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.data.db.FuelLogDatabase
import com.fuelexpenselog.app.data.db.SCHEMA_VERSION
import com.fuelexpenselog.app.data.db.entity.ReminderCompletionEntity
import com.fuelexpenselog.app.data.db.entity.ReminderEntity
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.domain.consumption.DeclaredSegment
import com.fuelexpenselog.domain.consumption.SegmentReason
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The master plan's mandated round trip, on a real file-backed database: WAL mode, a
 * checkpoint, a zip, every row deleted, and everything back - ids included.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file: File = context.getDatabasePath(NAME)
    private lateinit var db: FuelLogDatabase
    private lateinit var repo: FuelLogRepository
    private val prefs = AppPrefs(context)
    private val clock = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC)

    private fun open(): FuelLogDatabase = Room.databaseBuilder(context, FuelLogDatabase::class.java, NAME)
        .allowMainThreadQueries()
        .build()

    @Before
    fun setUp() {
        listOf(file, File(file.path + "-wal"), File(file.path + "-shm")).forEach { it.delete() }
        db = open()
        repo = FuelLogRepository({ db }) { DbFixtures.NOW }
    }

    @After
    fun tearDown() = db.close()

    private fun writer() = BackupWriter({ db }, { file }, prefs, "1.0.0", clock)

    private fun reader(schema: Int = SCHEMA_VERSION) = BackupReader(context.cacheDir, { file }, prefs, { db.close() }, schema)

    private suspend fun seed() {
        val v = repo.addVehicle(DbFixtures.vehicle())
        repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 10_000.0, litres = 45.0, total = 67.5))
        repo.addFillUp(DbFixtures.fillUp(v, day = 14, odometerKm = 10_520.0, litres = 40.0, total = 60.0))
        repo.addFillUp(DbFixtures.fillUp(v, day = 28, odometerKm = 10_800.0, litres = 20.0, full = false))
        val oil = repo.addExpense(DbFixtures.expense(v, day = 20, category = ExpenseCategory.OIL_CHANGE, odometerKm = 10_700.0))
        repo.addSegment(v, DeclaredSegment(DbFixtures.BASE.plusDays(30), SegmentReason.ROLLOVER, 100_000_000))
        val reminder = db.reminderDao().insert(
            ReminderEntity(
                vehicleId = v, title = "Oil", kind = "BOTH", category = ExpenseCategory.OIL_CHANGE.name,
                dueLocalDate = 20260701, repeatMonths = 6, dueOdometerM = 20_000_000, repeatDistanceM = 10_000_000,
                anchorLocalDate = 20260101, anchorOdometerM = 10_000_000, lastNotifiedLocalDate = null,
                createdAtMillis = DbFixtures.NOW, updatedAtMillis = DbFixtures.NOW,
            ),
        )
        db.reminderDao().insertCompletion(
            ReminderCompletionEntity(
                reminderId = reminder, vehicleId = v, completedLocalDate = 20260121, odometerM = 10_700_000,
                expenseId = oil, note = "done", createdAtMillis = DbFixtures.NOW,
            ),
        )
    }

    private data class Everything(val tables: List<List<Any>>)

    private suspend fun everything() = Everything(
        listOf(
            db.vehicleDao().all(), db.fillUpDao().all(), db.expenseDao().all(), db.odometerSegmentDao().all(),
            db.reminderDao().all(), db.reminderDao().allCompletions(), db.importBatchDao().all(),
        ),
    )

    @Test
    fun `back up, delete everything, restore, and every row returns with its id`() = runBlocking {
        seed()
        prefs.consumptionFormat = ConsumptionFormat.MPG_UK
        val before = everything()

        val bytes = ByteArrayOutputStream()
        val written = writer().write(bytes)
        assertThat(written.checkpointBusy).isEqualTo(0)
        assertThat(written.counts["fill_up"]).isEqualTo(3)

        db.clearAllTables()
        prefs.consumptionFormat = ConsumptionFormat.L_PER_100KM
        assertThat(db.vehicleDao().all()).isEmpty()

        val result = reader().restore(ByteArrayInputStream(bytes.toByteArray()))
        assertThat(result).isInstanceOf(BackupReader.Result.Restored::class.java)

        db = open()
        assertThat(everything()).isEqualTo(before)
        // The display setting came back too, so every figure reads as it did.
        assertThat(prefs.consumptionFormat).isEqualTo(ConsumptionFormat.MPG_UK)
    }

    @Test
    fun `the manifest says what the backup holds`() = runBlocking {
        seed()
        val bytes = ByteArrayOutputStream().also { writer().write(it) }.toByteArray()
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes() }
        }
        assertThat(entries.keys).containsExactly("manifest.json", "fuel-log.db")
        val manifest = BackupManifest.parse(entries.getValue("manifest.json").toString(Charsets.UTF_8))!!
        assertThat(manifest.schemaVersion).isEqualTo(SCHEMA_VERSION)
        assertThat(manifest.counts).containsEntry("vehicle", 1)
        assertThat(manifest.counts).containsEntry("reminder_completion", 1)
        assertThat(manifest.createdAt).isEqualTo("2026-09-26T10:00:00Z")
    }

    @Test
    fun `a backup from a newer schema is refused and nothing is touched`() = runBlocking {
        seed()
        val before = everything()
        val bytes = ByteArrayOutputStream().also { writer().write(it) }.toByteArray()

        // An app one schema behind the file it is handed.
        val result = reader(schema = SCHEMA_VERSION - 1).restore(ByteArrayInputStream(bytes))

        assertThat(result).isEqualTo(BackupReader.Result.Refused(BackupReader.Refusal.NEWER_SCHEMA))
        assertThat(everything()).isEqualTo(before)
    }

    @Test
    fun `a file that is not a backup is refused`() = runBlocking {
        val junk = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("""{"format":"something-else","schemaVersion":1}""".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        assertThat(reader().restore(ByteArrayInputStream(junk)))
            .isEqualTo(BackupReader.Result.Refused(BackupReader.Refusal.NOT_A_BACKUP))
        assertThat(reader().restore(ByteArrayInputStream("not even a zip".toByteArray())))
            .isEqualTo(BackupReader.Result.Refused(BackupReader.Refusal.NOT_A_BACKUP))
    }

    private companion object {
        const val NAME = "backup-roundtrip.db"
    }
}
