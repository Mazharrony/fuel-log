package com.fuelexpenselog.domain.model

import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The scenario both policies are designed around: a backup written by a newer version of the
 * app, restored onto an older build. The database opens either way - the question is what
 * happens to a value the older build has never heard of.
 */
class EnumDecodeTest {

    @Test
    fun `an unknown category degrades to OTHER so the row still opens`() {
        val decoded = decodeOr("CONGESTION_CHARGE", ExpenseCategory.OTHER)

        assertThat(decoded).isEqualTo(ExpenseCategory.OTHER)
        assertThat(decodeOr("TOLL", ExpenseCategory.OTHER)).isEqualTo(ExpenseCategory.TOLL)
        assertThat(decodeOr(null, ExpenseCategory.OTHER)).isEqualTo(ExpenseCategory.OTHER)
    }

    @Test
    fun `an unknown unit is kept verbatim rather than guessed`() {
        // There is no honest fallback for a unit. Guessing litres would silently change what
        // every figure computed from the row means.
        val decoded = decodeStrict<EnergyUnit>("HYDROGEN_KG")

        assertThat(decoded).isInstanceOf(Decoded.Unknown::class.java)
        assertThat((decoded as Decoded.Unknown).raw).isEqualTo("HYDROGEN_KG")
    }

    @Test
    fun `a known unit decodes normally`() {
        val decoded = decodeStrict<EnergyUnit>("US_GALLON")

        assertThat(decoded).isInstanceOf(Decoded.Known::class.java)
        assertThat((decoded as Decoded.Known).value).isEqualTo(EnergyUnit.US_GALLON)
    }

    @Test
    fun `enums are stored by name, so reordering a declaration is safe`() {
        // If any of these were persisted as ordinals, inserting a category would silently
        // rewrite every row after it in somebody's history.
        assertThat(ExpenseCategory.TOLL.name).isEqualTo("TOLL")
        assertThat(EntryTag.BUSINESS.name).isEqualTo("BUSINESS")
        assertThat(EnergyUnit.IMP_GALLON.name).isEqualTo("IMP_GALLON")
    }

    @Test
    fun `the category list is exactly the twelve, and maintenance is marked`() {
        assertThat(ExpenseCategory.entries).hasSize(12)
        assertThat(ExpenseCategory.chipOrder).containsExactlyElementsIn(ExpenseCategory.entries)

        // "Last done at" lines are computed from maintenance categories only.
        assertThat(ExpenseCategory.entries.filter { it.isMaintenance }).containsExactly(
            ExpenseCategory.OIL_CHANGE,
            ExpenseCategory.SERVICE,
            ExpenseCategory.REPAIR,
            ExpenseCategory.TYRES,
            ExpenseCategory.PARTS,
        )
    }
}
