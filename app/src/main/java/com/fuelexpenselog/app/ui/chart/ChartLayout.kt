package com.fuelexpenselog.app.ui.chart

import com.fuelexpenselog.domain.consumption.Gap
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.consumption.TimelinePoint
import com.fuelexpenselog.domain.unit.ConsumptionFormat

/** One slot of the consumption chart, in pixels, ready to draw. */
data class ChartBar(
    val point: TimelinePoint,
    val left: Float,
    val width: Float,
    /** Top edge; the bar runs from here down to the baseline. */
    val top: Float,
    val height: Float,
    /** The figure in the chart's format, or null for a gap - drawn as a dash, never a zero. */
    val value: Double?,
    val isStub: Boolean,
    val isLatest: Boolean,
)

data class ChartGeometry(val bars: List<ChartBar>, val baseline: Float, val slotWidth: Float)

/**
 * The chart's geometry, as plain arithmetic with no Compose in sight, so what the chart
 * claims can be unit-tested: nine slots, gaps as stubs, bars scaled against the tallest,
 * one highlighted. The Canvas that draws it holds draw calls and nothing else.
 */
object ChartLayout {

    /** A gap is a 3px stub: visibly a slot, visibly not a value. */
    const val STUB_PX = 3f

    fun compute(
        points: List<TimelinePoint>,
        format: ConsumptionFormat,
        widthPx: Float,
        heightPx: Float,
        gapPx: Float,
        slots: Int,
    ): ChartGeometry {
        val shown = points.takeLast(slots)
        // Fewer points than slots sit at the right, so the newest is always at the edge.
        val offset = slots - shown.size
        val slotWidth = ((widthPx - gapPx * (slots - 1)) / slots).coerceAtLeast(0f)
        val values = shown.map { (it as? Measured)?.shownAs(format) }
        val max = values.filterNotNull().maxOrNull()
        val latestIndex = shown.indexOfLast { it is Measured }

        val bars = shown.mapIndexed { i, point ->
            val value = values[i]
            val height = when {
                point is Gap || value == null || max == null || max <= 0.0 -> STUB_PX
                else -> (value / max * heightPx).toFloat().coerceAtLeast(STUB_PX)
            }
            ChartBar(
                point = point,
                left = (i + offset) * (slotWidth + gapPx),
                width = slotWidth,
                top = heightPx - height,
                height = height,
                value = value,
                isStub = value == null,
                isLatest = i == latestIndex,
            )
        }
        return ChartGeometry(bars, baseline = heightPx, slotWidth = slotWidth)
    }
}
