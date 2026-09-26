package com.fuelexpenselog.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.fuelexpenselog.app.BuildConfig
import com.fuelexpenselog.app.data.db.FuelLogDatabase
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The harness the first real migration will plug into.
 *
 * Schema v1 has no migrations yet, so this proves the plumbing rather than a step: the
 * committed `1.json` is readable, a database created from it opens, and validating that
 * database against the same schema passes. When v2 lands, the new test is one
 * `runMigrationsAndValidate(2, listOf(MIGRATION_1_2))` away - and nobody finds out during a
 * release week that the helper cannot see the schema.
 *
 * The driver-based constructor, because the name-based one compares the requested path
 * against the database name with a forward-slash split and fails on every Windows machine.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath(DB),
        driver = AndroidSQLiteDriver(),
        databaseClass = FuelLogDatabase::class,
    )

    /** The schemas are debug-only assets, so the release unit-test run has nothing to read. */
    @Before
    fun schemasArePackaged() = assumeTrue(BuildConfig.DEBUG)

    @Test
    fun `schema 1 creates and validates from the committed json`() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(1).close()
    }

    private companion object {
        const val DB = "migration-test"
    }
}
