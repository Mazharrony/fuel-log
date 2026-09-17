package com.fuelexpenselog.app.domain

import com.fuelexpenselog.app.domain.model.ExpenseCategory
import com.fuelexpenselog.app.domain.stats.CurrencySubtotal
import com.fuelexpenselog.app.domain.stats.currencySubtotals
import com.fuelexpenselog.app.domain.stats.lastDone
import com.fuelexpenselog.app.domain.validation.EntryBlock
import com.fuelexpenselog.app.domain.validation.EntryWarning
import com.fuelexpenselog.app.domain.validation.ExpenseDraft
import com.fuelexpenselog.app.domain.validation.FillUpDraft
import com.fuelexpenselog.app.domain.validation.validateExpense
import com.fuelexpenselog.app.domain.validation.validateFillUp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LastDoneTest {

    @Test
    fun `only maintenance categories appear`() {
        val result = lastDone(
            listOf(
                expense(ExpenseCategory.OIL_CHANGE, odometer = 48_200.0, day = 1),
                expense(ExpenseCategory.INSURANCE, odometer = 48_300.0, day = 2),
                expense(ExpenseCategory.PARKING, day = 3),
            ),
            currentOdometerKm = 52_500.0,
        )

        assertThat(result.map { it.category }).containsExactly(ExpenseCategory.OIL_CHANGE)
    }

    @Test
    fun `distance since is measured from the highest recorded odometer`() {
        val result = lastDone(
            listOf(
                expense(ExpenseCategory.OIL_CHANGE, odometer = 40_000.0, day = 1),
                expense(ExpenseCategory.OIL_CHANGE, odometer = 48_200.0, day = 2),
            ),
            currentOdometerKm = 52_500.0,
        )

        assertThat(result).hasSize(1)
        assertThat(result[0].atOdometerKm).isEqualTo(48_200.0)
        assertThat(result[0].sinceKm).isEqualTo(4_300.0)
    }

    @Test
    fun `an entry with no odometer reading yields no distance rather than a wrong one`() {
        val result = lastDone(
            listOf(expense(ExpenseCategory.SERVICE, odometer = null, day = 1)),
            currentOdometerKm = 52_500.0,
        )

        assertThat(result).hasSize(1)
        assertThat(result[0].atOdometerKm).isNull()
        assertThat(result[0].sinceKm).isNull()
    }

    @Test
    fun `an unknown current odometer yields no distance`() {
        val result = lastDone(
            listOf(expense(ExpenseCategory.TYRES, odometer = 30_000.0, day = 1)),
            currentOdometerKm = null,
        )

        assertThat(result[0].sinceKm).isNull()
    }
}

class CurrencySubtotalsTest {

    @Test
    fun `currencies are never summed together`() {
        // The app holds no exchange rates and has no network to fetch them with.
        val result = currencySubtotals(listOf("EUR" to 100.0, "INR" to 5000.0, "EUR" to 50.0))

        assertThat(result).containsExactly(
            CurrencySubtotal("INR", 5000.0),
            CurrencySubtotal("EUR", 150.0),
        ).inOrder()
    }
}

class EntryValidationTest {

    private val now = day(10)

    @Test
    fun `a missing required field is the only thing that blocks a save`() {
        val result = validateFillUp(
            FillUpDraft(odometerKm = null, volumeLitres = 32.5, totalCost = 40.0, dateMillis = now),
            previousOdometerKm = null, tankCapacityLitres = null, nowMillis = now,
        )

        assertThat(result.blocks).containsExactly(EntryBlock.REQUIRED_FIELD_MISSING)
        assertThat(result.canSave).isFalse()
    }

    @Test
    fun `every suspicious entry warns but still saves`() {
        val result = validateFillUp(
            FillUpDraft(odometerKm = 100.0, volumeLitres = 90.0, totalCost = 40.0, dateMillis = day(30)),
            previousOdometerKm = 48_000.0,
            tankCapacityLitres = 50.0,
            nowMillis = now,
            isDuplicate = true,
        )

        assertThat(result.warnings).containsExactly(
            EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS,
            EntryWarning.VOLUME_OVER_TANK_CAPACITY,
            EntryWarning.FUTURE_DATE,
            EntryWarning.LOOKS_LIKE_DUPLICATE,
        )
        // The whole point: warn, never block. A replaced odometer, a jerry can
        // and a twin-tank truck are all real situations.
        assertThat(result.canSave).isTrue()
    }

    @Test
    fun `a zero cost is accepted silently`() {
        // Warranty work and free top-ups exist.
        val result = validateFillUp(
            FillUpDraft(odometerKm = 48_500.0, volumeLitres = 32.0, totalCost = 0.0, dateMillis = now),
            previousOdometerKm = 48_000.0, tankCapacityLitres = 50.0, nowMillis = now,
        )

        assertThat(result.warnings).isEmpty()
        assertThat(result.canSave).isTrue()
    }

    @Test
    fun `an expense needs no odometer reading`() {
        // Insurance has no reading; a service does.
        val result = validateExpense(
            ExpenseDraft(odometerKm = null, totalCost = 200.0, dateMillis = now),
            previousOdometerKm = 48_000.0, nowMillis = now,
        )

        assertThat(result.canSave).isTrue()
        assertThat(result.warnings).isEmpty()
    }
}
