package com.fuelexpenselog.app.ui.chart

import com.fuelexpenselog.domain.consumption.Confidence
import com.fuelexpenselog.domain.consumption.Gap
import com.fuelexpenselog.domain.consumption.GapReason
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.consumption.TimelinePoint
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChartLayoutTest {

    private val date = CivilDate.of(2026, 9, 1)

    /** A span of [km] on [litres]. */
    private fun measured(index: Int, km: Long, litres: Double) = Measured(
        index = index,
        endDate = date.plusDays(index.toLong()),
        startEventId = index.toLong(),
        endEventId = index + 1L,
        distanceM = km * 1000,
        energy = Energy.of(EnergyUnit.LITRE, litres),
        cost = null,
        fillUpsSpanned = 1,
        confidence = Confidence.EXACT,
        otherKindMicro = 0,
    )

    private fun gap(index: Int) = Gap(index, date.plusDays(index.toLong()), GapReason.MISSED_FILL_UP, listOf(index + 1L))

    private fun layout(points: List<TimelinePoint>) =
        ChartLayout.compute(points, ConsumptionFormat.KM_PER_L, widthPx = 900f, heightPx = 220f, gapPx = 7f, slots = 9)

    @Test
    fun `nine slots from the end of a longer timeline`() {
        val points = (0 until 12).map { measured(it, 500, 40.0) }
        val geometry = layout(points)
        assertThat(geometry.bars).hasSize(9)
        assertThat(geometry.bars.first().point.index).isEqualTo(3)
        assertThat(geometry.bars.last().left + geometry.bars.last().width).isWithin(0.01f).of(900f)
    }

    @Test
    fun `a gap is a stub with no value, not a zero bar`() {
        val geometry = layout(listOf(measured(0, 500, 40.0), gap(1), measured(2, 600, 40.0)))
        val stub = geometry.bars[1]
        assertThat(stub.isStub).isTrue()
        assertThat(stub.value).isNull()
        assertThat(stub.height).isEqualTo(ChartLayout.STUB_PX)
        assertThat(stub.top + stub.height).isEqualTo(geometry.baseline)
    }

    @Test
    fun `bars scale against the tallest`() {
        val geometry = layout(listOf(measured(0, 300, 40.0), measured(1, 600, 40.0)))
        assertThat(geometry.bars[1].height).isEqualTo(220f)
        assertThat(geometry.bars[0].height).isWithin(0.01f).of(110f)
    }

    @Test
    fun `only the latest measured bar is highlighted, even behind a trailing gap`() {
        val geometry = layout(listOf(measured(0, 500, 40.0), measured(1, 520, 40.0), gap(2)))
        assertThat(geometry.bars.count { it.isLatest }).isEqualTo(1)
        assertThat(geometry.bars[1].isLatest).isTrue()
    }

    @Test
    fun `an all-gap timeline is all stubs and highlights nothing`() {
        val geometry = layout(listOf(gap(0), gap(1)))
        assertThat(geometry.bars.all { it.isStub }).isTrue()
        assertThat(geometry.bars.none { it.isLatest }).isTrue()
    }
}
