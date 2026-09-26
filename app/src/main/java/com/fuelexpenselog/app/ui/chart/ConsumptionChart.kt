package com.fuelexpenselog.app.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.consumption.TimelinePoint
import com.fuelexpenselog.domain.format.Rounding
import com.fuelexpenselog.domain.unit.ConsumptionFormat

private const val GROW_MS = 420
private const val STAGGER_MS = 30

/**
 * The bars, their values above and their dates below. Geometry comes from [ChartLayout]; the
 * Canvas only draws. A gap is a stub labelled with a dash - never a zero, never skipped.
 *
 * The bars grow once per screen entry, staggered by 30ms, from one Animatable rather than a
 * delay() per bar, and a data change never replays it. With animations off it is a static frame.
 */
@Composable
fun ConsumptionChart(
    points: List<TimelinePoint>,
    format: ConsumptionFormat,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.chartHeight,
    gap: Dp = Dimens.chartBarGap,
    slots: Int = Dimens.CHART_BARS,
    showLabels: Boolean = true,
    inactive: Color = FuelTheme.colors.chartInactive,
    empty: Color = FuelTheme.colors.chartEmpty,
    latest: Color = FuelTheme.colors.primary,
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val total = (GROW_MS + STAGGER_MS * (slots - 1)).toFloat()
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) { clock.animateTo(total, tween(total.toInt(), easing = LinearEasing)) }
    val shown = points.takeLast(slots)
    // Matches ChartLayout: a short timeline sits at the right, newest at the edge.
    val offset = slots - shown.size

    Column(modifier) {
        if (showLabels) {
            SlotRow(slots, gap) { i ->
                shown.getOrNull(i - offset)?.let { point ->
                    val value = (point as? Measured)?.shownAs(format)
                    Text(
                        if (value == null) Rounding.EM_DASH else f.consumption.value(value, format),
                        style = FuelTheme.type.chartValue,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val geometry = ChartLayout.compute(points, format, size.width, size.height, gapPx, slots)
            geometry.bars.forEachIndexed { i, bar ->
                val t = ((clock.value - i * STAGGER_MS) / GROW_MS).coerceIn(0f, 1f)
                val grown = bar.height * FastOutSlowInEasing.transform(t)
                val color = when {
                    bar.isStub -> empty
                    bar.isLatest -> latest
                    else -> inactive
                }
                drawRect(color, topLeft = Offset(bar.left, geometry.baseline - grown), size = Size(bar.width, grown))
            }
        }
        if (showLabels) {
            SlotRow(slots, gap) { i ->
                shown.getOrNull(i - offset)?.let { point ->
                    Text(
                        f.date.numeric(point.endDate),
                        style = FuelTheme.type.chartLabel,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A row of [slots] equal cells with the same gap as the bars, so labels sit under their bar. */
@Composable
private fun SlotRow(slots: Int, gap: Dp, cell: @Composable (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(slots) { i ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { cell(i) }
        }
    }
}
