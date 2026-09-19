package com.fuelexpenselog.domain.validate

import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntryValidationTest {

    private val today = CivilDate.of(2026, 9, 19)

    private fun draft(
        odometer: String = "48500",
        volume: String = "40",
        total: String = "60",
        full: Boolean = true,
        date: CivilDate = today,
        volumeUnit: EnergyUnit = EnergyUnit.LITRE,
    ) = FillUpDraft(odometer, volume, total, full, date, volumeUnit, DistanceUnit.KILOMETRE)

    private fun context(
        previousKm: Double? = 48_000.0,
        tankLitres: Double? = 47.0,
        duplicate: Boolean = false,
    ) = EntryContext(
        today = today,
        previousOdometerM = previousKm?.let { DistanceUnit.KILOMETRE.toMetres(it) },
        tankCapacity = tankLitres?.let { Energy.of(EnergyUnit.LITRE, it) },
        looksLikeDuplicate = duplicate,
    )

    @Test
    fun `an ordinary entry produces nothing at all`() {
        val result = EntryValidation.validate(draft(), context())

        assertThat(result.warnings).isEmpty()
        assertThat(result.canSave).isTrue()
    }

    @Test
    fun `an empty required field is the only thing that blocks a save`() {
        assertThat(EntryValidation.validate(draft(volume = ""), context()).canSave).isFalse()
        assertThat(EntryValidation.validate(draft(total = ""), context()).canSave).isFalse()
        assertThat(EntryValidation.validate(draft(volume = "abc"), context()).canSave).isFalse()

        // A missing reading does NOT block. It costs a consumption figure, which is the
        // user's business, not the app's.
        val noReading = EntryValidation.validate(draft(odometer = ""), context())
        assertThat(noReading.canSave).isTrue()
        assertThat(noReading.warnings).contains(EntryWarning.ODOMETER_MISSING)
    }

    @Test
    fun `every warning can fire at once and the entry is still saveable`() {
        val result = EntryValidation.validate(
            draft(odometer = "47000", volume = "60", date = CivilDate.of(2026, 12, 25)),
            context(duplicate = true),
        )

        assertThat(result.warnings).containsAtLeast(
            EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS,
            EntryWarning.VOLUME_OVER_TANK_CAPACITY,
            EntryWarning.FUTURE_DATE,
            EntryWarning.LOOKS_LIKE_DUPLICATE,
        )
        assertThat(result.canSave).isTrue()
    }

    @Test
    fun `a jerry can is not an error`() {
        // 60 L into a 47 L tank. Twin tanks and cans in the boot are real, so this warns.
        val result = EntryValidation.validate(draft(volume = "60"), context())

        assertThat(result.warnings).contains(EntryWarning.VOLUME_OVER_TANK_CAPACITY)
        assertThat(result.canSave).isTrue()
    }

    @Test
    fun `a replaced odometer is not an error either`() {
        val result = EntryValidation.validate(draft(odometer = "12"), context())

        assertThat(result.warnings).contains(EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS)
        assertThat(result.canSave).isTrue()
    }

    @Test
    fun `a cost of zero is accepted in silence`() {
        // Warranty work, a company fuel card, a friend filling the tank.
        val result = EntryValidation.validate(draft(total = "0"), context())

        assertThat(result.warnings).isEmpty()
        assertThat(result.canSave).isTrue()
    }

    @Test
    fun `an implausible figure warns, and the entry still saves`() {
        // 500 km on 60 L is fine; 5 km on 60 L is not.
        val result = EntryValidation.validate(draft(odometer = "48005", volume = "60"), context())

        assertThat(result.warnings).contains(EntryWarning.IMPLAUSIBLE_CONSUMPTION)
        assertThat(result.canSave).isTrue()
    }

    @Test
    fun `a partial fill is never judged on its consumption, because it has none`() {
        val result = EntryValidation.validate(
            draft(odometer = "48005", volume = "60", full = false),
            context(),
        )

        assertThat(result.warnings).doesNotContain(EntryWarning.IMPLAUSIBLE_CONSUMPTION)
    }

    @Test
    fun `the first ever entry has nothing to compare against and says nothing`() {
        val result = EntryValidation.validate(draft(), context(previousKm = null))

        assertThat(result.warnings).isEmpty()
        assertThat(result.canSave).isTrue()
    }

    @Test
    fun `the volume warning respects the vehicle's own unit`() {
        // 14 US gallons is 53 L, which is over a 47 L tank. Comparing 14 against 47 would
        // silently miss it - the capacity is canonical but the entry is in gallons.
        val overInGallons = EntryValidation.validate(
            draft(volume = "14", volumeUnit = EnergyUnit.US_GALLON),
            context(tankLitres = 47.0),
        )
        assertThat(overInGallons.warnings).contains(EntryWarning.VOLUME_OVER_TANK_CAPACITY)

        val underInGallons = EntryValidation.validate(
            draft(volume = "11", volumeUnit = EnergyUnit.US_GALLON),
            context(tankLitres = 47.0),
        )
        assertThat(underInGallons.warnings).doesNotContain(EntryWarning.VOLUME_OVER_TANK_CAPACITY)
    }

    @Test
    fun `a comma decimal is accepted here too, whatever the phone is set to`() {
        val result = EntryValidation.validate(draft(volume = "32,5", total = "45,90"), context())

        assertThat(result.canSave).isTrue()
        assertThat(result.warnings).isEmpty()
    }

    // -- expenses ---------------------------------------------------------------------------

    @Test
    fun `an expense needs an amount and nothing else`() {
        val noReading = ExpenseDraft("45.00", "", today, DistanceUnit.KILOMETRE)
        val result = EntryValidation.validate(noReading, context())

        assertThat(result.canSave).isTrue()
        // Not even a warning: a parking ticket has no odometer reading and never will.
        assertThat(result.warnings).isEmpty()

        val noAmount = ExpenseDraft("", "48500", today, DistanceUnit.KILOMETRE)
        assertThat(EntryValidation.validate(noAmount, context()).canSave).isFalse()
    }

    @Test
    fun `an expense with a backwards reading warns like a fill-up does`() {
        val draft = ExpenseDraft("45.00", "12", today, DistanceUnit.KILOMETRE)
        val result = EntryValidation.validate(draft, context())

        assertThat(result.warnings).contains(EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS)
        assertThat(result.canSave).isTrue()
    }
}
