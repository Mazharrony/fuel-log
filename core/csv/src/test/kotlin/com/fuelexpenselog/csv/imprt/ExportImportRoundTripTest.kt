package com.fuelexpenselog.csv.imprt

import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.FillUp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The app's own export, read back by its own importer, loses nothing it wrote: exact
 * canonical values, which of total and unit price was typed, tags, and text a spreadsheet
 * would have run as a formula. Only the time of day is not in the file.
 */
class ExportImportRoundTripTest {

    private val source = Fixtures.vehicle(id = 1, name = "Golf")
    private val target = Fixtures.vehicle(id = 42, name = "Golf again")

    /** What a record means, less where it lives and the time of day the file does not carry. */
    private fun FillUp.meaning() = copy(id = 0, vehicleId = 0, instantMillis = 0)
    private fun Expense.meaning() = copy(id = 0, vehicleId = 0, instantMillis = 0)

    @Test
    fun `every fill-up and expense comes back exactly as exported`() {
        val (fillUps, expenses) = Fixtures.history(source.id)
        val plan = ImportPlanner.plan(Fixtures.ownExport(listOf(source), fillUps, expenses))

        val rows = Fixtures.stage(plan, target)

        assertThat(rows.none { it.isError }).isTrue()
        val back = rows.mapNotNull { it.entry }
        assertThat(back.filterIsInstance<StagedEntry.Fuel>().map { it.fillUp.meaning() })
            .containsExactlyElementsIn(fillUps.map { it.meaning() })
        assertThat(back.filterIsInstance<StagedEntry.Cost>().map { it.expense.meaning() })
            .containsExactlyElementsIn(expenses.map { it.meaning() })
        assertThat(back.all { entry ->
            when (entry) {
                is StagedEntry.Fuel -> entry.fillUp.vehicleId == target.id
                is StagedEntry.Cost -> entry.expense.vehicleId == target.id
            }
        }).isTrue()
    }

    @Test
    fun `importing the same export again finds every row already there`() {
        val (fillUps, expenses) = Fixtures.history(source.id)
        val plan = ImportPlanner.plan(Fixtures.ownExport(listOf(source), fillUps, expenses))
        val first = Fixtures.stage(plan, target).mapNotNull { it.entry }
        val existing = ExistingEntries(
            first.filterIsInstance<StagedEntry.Fuel>().map { it.fillUp },
            first.filterIsInstance<StagedEntry.Cost>().map { it.expense },
        )

        assertThat(Fixtures.stage(plan, target, existing).all { it.duplicate }).isTrue()
    }

    @Test
    fun `an export of two vehicles imports one of them at a time`() {
        val other = Fixtures.vehicle(id = 2, name = "Van")
        val (golfFills, golfCosts) = Fixtures.history(source.id)
        val vanFills = Fixtures.history(other.id).first.map { it.copy(id = it.id + 100) }
        val plan = ImportPlanner.plan(Fixtures.ownExport(listOf(source, other), golfFills + vanFills, golfCosts))

        assertThat(plan.vehicles).containsExactly("Golf", "Van").inOrder()
        val vanOnly = StagedImport.stage(
            plan,
            Fixtures.assumptions(plan, target).copy(sourceVehicle = "Van"),
            target,
            Fixtures.ZONE,
            Fixtures.TODAY,
        )
        assertThat(vanOnly.mapNotNull { it.entry }).hasSize(vanFills.size)
    }
}
