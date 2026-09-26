package com.fuelexpenselog.domain.validate

import com.fuelexpenselog.domain.consumption.ConsumptionEngine
import com.fuelexpenselog.domain.consumption.FuelEvent
import com.fuelexpenselog.domain.consumption.Gap
import com.fuelexpenselog.domain.consumption.GapReason
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyKind
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntryPreviewTest {

    private val base = CivilDate.of(2026, 9, 1)

    private fun event(id: Long, day: Int, km: Double?, litres: Double, full: Boolean = true, cost: Double = 60.0) =
        FuelEvent(
            id = id,
            date = base.plusDays(day.toLong()),
            instantMillis = day * 86_400_000L,
            odometerM = km?.let { DistanceUnit.KILOMETRE.toMetres(it) },
            energy = Energy.of(EnergyUnit.LITRE, litres),
            isFull = full,
            cost = Money.of(cost, "EUR"),
        )

    private fun preview(existing: List<FuelEvent>, draft: FuelEvent, replacing: Long = draft.id) =
        EntryPreview.fillUp(existing, draft, EnergyKind.LIQUID, DistanceUnit.KILOMETRE, replacingId = replacing)

    @Test
    fun `the smoke-test numbers - 500 km on 41,5 L is 8,3 L per 100 km`() {
        val first = event(1, day = 0, km = 48_200.0, litres = 40.0)
        val draft = event(EntryPreview.DRAFT_ID, day = 7, km = 48_700.0, litres = 41.5, cost = 62.0)

        val point = preview(listOf(first), draft) as Measured

        assertThat(point.distanceM).isEqualTo(500_000)
        assertThat(point.shownAs(ConsumptionFormat.L_PER_100KM)!!).isWithin(0.005).of(8.3)
        assertThat(point.shownAs(ConsumptionFormat.KM_PER_L)!!).isWithin(0.005).of(12.05)
        assertThat(point.shownAs(ConsumptionFormat.MPG_US)!!).isWithin(0.05).of(28.3)
    }

    @Test
    fun `the preview is what the engine says after the save`() {
        val saved = listOf(
            event(1, day = 0, km = 10_000.0, litres = 40.0),
            event(2, day = 5, km = 10_300.0, litres = 18.0, full = false),
        )
        val draft = event(EntryPreview.DRAFT_ID, day = 9, km = 10_720.0, litres = 25.0)

        val shown = (preview(saved, draft) as Measured).shownAs(ConsumptionFormat.L_PER_100KM)
        val afterSave = ConsumptionEngine.compute(saved + draft.copy(id = 3), EnergyKind.LIQUID)
            .measured.last().shownAs(ConsumptionFormat.L_PER_100KM)

        // The partial's 18 L count toward this tank: 43 L over 720 km, not 25 L.
        assertThat(shown).isEqualTo(afterSave)
        assertThat(shown!!).isWithin(0.01).of(43.0 / 7.2)
    }

    @Test
    fun `a first full tank is a starting line, and says so`() {
        val point = preview(emptyList(), event(EntryPreview.DRAFT_ID, day = 0, km = 100.0, litres = 40.0))
        assertThat((point as Gap).reason).isEqualTo(GapReason.FIRST_FILL_UP)
    }

    @Test
    fun `a partial fill has no figure of its own`() {
        val first = event(1, day = 0, km = 1_000.0, litres = 40.0)
        val point = preview(listOf(first), event(EntryPreview.DRAFT_ID, day = 3, km = 1_200.0, litres = 10.0, full = false))
        assertThat(point).isNotInstanceOf(Measured::class.java)
    }

    @Test
    fun `editing a row replaces it rather than counting it twice`() {
        val first = event(1, day = 0, km = 48_200.0, litres = 40.0)
        val second = event(2, day = 7, km = 48_700.0, litres = 41.5)
        // The user corrects the second reading to 48,800.
        val edited = second.copy(id = EntryPreview.DRAFT_ID, odometerM = 48_800_000)

        val point = preview(listOf(first, second), edited, replacing = 2) as Measured

        assertThat(point.distanceM).isEqualTo(600_000)
    }
}
