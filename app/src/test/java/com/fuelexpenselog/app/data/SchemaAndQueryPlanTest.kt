package com.fuelexpenselog.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fuelexpenselog.app.data.db.FuelLogDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Index justifications are easy to write in a comment and easy to invalidate later by
 * editing a query. `EXPLAIN QUERY PLAN` turns each one into something that actually fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchemaAndQueryPlanTest {

    private lateinit var db: FuelLogDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FuelLogDatabase::class.java,
        ).allowMainThreadQueries().build()
        // Force the database open so the schema exists.
        db.openHelper.writableDatabase
    }

    @After
    fun tearDown() = db.close()

    private fun plan(sql: String): String {
        val out = StringBuilder()
        db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql").use { c ->
            while (c.moveToNext()) {
                for (i in 0 until c.columnCount) out.append(c.getString(i)).append(' ')
                out.append('\n')
            }
        }
        return out.toString()
    }

    private fun assertUsesIndex(label: String, sql: String) {
        val plan = plan(sql)
        // "USING INDEX" or "USING COVERING INDEX" - the latter is strictly better, since it
        // answers from the index alone and never touches the table.
        assertThat(plan).contains("USING")
        assertThat(plan).contains("INDEX")
        assertThat(plan.uppercase()).doesNotContain("SCAN FILL_UP")
        assertThat(plan.uppercase()).doesNotContain("SCAN EXPENSE")
        assertThat(plan.uppercase()).doesNotContain("USE TEMP B-TREE")
        println("$label -> $plan")
    }

    @Test
    fun `the engine's read walks an index and needs no temporary sort`() {
        // If this starts using a temp b-tree, every vehicle screen pays a sort on every
        // emission, for every row, forever.
        assertUsesIndex(
            "fill-ups for a vehicle",
            "SELECT * FROM fill_up WHERE vehicleId = 1 ORDER BY occurredLocalDate ASC, odometerM ASC",
        )
    }

    @Test
    fun `the current odometer is an index lookup, not a table scan`() {
        // Runs on every screen open for every distance-based reminder.
        val plan = plan("SELECT MAX(odometerM) FROM fill_up WHERE vehicleId = 1")

        // A COVERING index: the answer comes out of the index without reading a single table
        // row, which is exactly what you want for something every screen runs on open.
        assertThat(plan).contains("COVERING INDEX index_fill_up_vehicleId_odometerM")
        assertThat(plan.uppercase()).doesNotContain("SCAN FILL_UP")
    }

    @Test
    fun `the previous reading, leaving out the row being edited, is still an index search`() {
        // Runs on every keystroke-driven re-validation of an entry being edited.
        val fuel = plan("SELECT MAX(odometerM) FROM fill_up WHERE vehicleId = 1 AND id != 7")
        assertThat(fuel).contains("index_fill_up_vehicleId_odometerM")
        assertThat(fuel.uppercase()).doesNotContain("SCAN FILL_UP")

        // Expenses have no odometer index (schema stays at v1): a vehicle's own rows through
        // the vehicle index is enough at a few hundred rows, a full table scan is not.
        val cost = plan("SELECT MAX(odometerM) FROM expense WHERE vehicleId = 1 AND id != 7")
        assertThat(cost).contains("USING INDEX")
        assertThat(cost.uppercase()).doesNotContain("SCAN EXPENSE")
    }

    @Test
    fun `the business-personal tax export does not scan the table`() {
        assertUsesIndex(
            "tagged range",
            "SELECT * FROM fill_up WHERE vehicleId = 1 AND tag = 'BUSINESS' " +
                "AND occurredLocalDate BETWEEN 20260101 AND 20261231 ORDER BY occurredLocalDate ASC",
        )
        assertUsesIndex(
            "tagged expenses",
            "SELECT * FROM expense WHERE vehicleId = 1 AND tag = 'BUSINESS' " +
                "AND occurredLocalDate BETWEEN 20260101 AND 20261231 ORDER BY occurredLocalDate ASC",
        )
    }

    @Test
    fun `the monthly range scan uses the date index`() {
        assertUsesIndex(
            "month range",
            "SELECT * FROM expense WHERE vehicleId = 1 " +
                "AND occurredLocalDate BETWEEN 20260901 AND 20260930 ORDER BY occurredLocalDate ASC",
        )
    }

    @Test
    fun `when was the last oil change is a single indexed lookup`() {
        assertUsesIndex(
            "last in category",
            "SELECT * FROM expense WHERE vehicleId = 1 AND category = 'OIL_CHANGE' " +
                "ORDER BY occurredLocalDate DESC, id DESC LIMIT 1",
        )
    }

    @Test
    fun `the duplicate probe on re-import uses the hash index`() {
        val plan = plan("SELECT COUNT(*) FROM fill_up WHERE importRowHash = 'abc'")
        assertThat(plan).contains("USING")
        assertThat(plan.uppercase()).doesNotContain("SCAN FILL_UP")
    }

    // -- the committed schema is the shipped schema ----------------------------------------

    @Test
    fun `the exported schema is committed and matches what the database actually creates`() {
        val json = schemaFile().readText()

        assertThat(json).contains("\"version\": 1")

        val liveIndices = mutableSetOf<String>()
        db.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%'")
            .use { c -> while (c.moveToNext()) liveIndices += c.getString(0) }

        // Every index the running database creates must appear in the committed file, so a
        // schema change cannot land without a reviewable diff.
        for (index in liveIndices) {
            assertThat(json).contains(index)
        }

        assertThat(liveIndices).containsAtLeast(
            "index_fill_up_vehicleId_occurredLocalDate_odometerM",
            "index_fill_up_vehicleId_odometerM",
            "index_fill_up_vehicleId_tag_occurredLocalDate",
            "index_expense_vehicleId_category_occurredLocalDate",
            "index_reminder_isActive_notifyEnabled_dueLocalDate",
        )
    }

    @Test
    fun `no column in the whole schema has REAL affinity`() {
        val tables = mutableListOf<String>()
        db.openHelper.readableDatabase
            .query(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    // android_metadata is created by the platform, room_master_table by Room.
                    "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name != 'android_metadata'",
            )
            .use { c -> while (c.moveToNext()) tables += c.getString(0) }

        assertThat(tables).hasSize(7)

        val real = mutableListOf<String>()
        for (table in tables) {
            db.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { c ->
                while (c.moveToNext()) {
                    val column = c.getString(1)
                    val type = c.getString(2)
                    if (type.equals("REAL", ignoreCase = true)) real += "$table.$column"
                }
            }
        }

        // Distance is Long metres, energy Long micro-units, money Long micros. A float in an
        // odometer chain makes comparisons approximate and CSV round-trips lossy.
        assertThat(real).isEmpty()
    }

    @Test
    fun `foreign keys are on, so a delete cannot leave orphans in a tax export`() {
        db.openHelper.readableDatabase.query("PRAGMA foreign_keys").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }

    private fun schemaFile(): File {
        val relative = "schemas/com.fuelexpenselog.app.data.db.FuelLogDatabase/1.json"
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File(System.getProperty("user.dir"), relative),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Committed schema not found. Looked in: ${candidates.map { it.absolutePath }}")
    }
}
