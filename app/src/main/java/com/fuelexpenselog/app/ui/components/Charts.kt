package com.fuelexpenselog.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.motion.Durations
import com.fuelexpenselog.app.ui.motion.LocalMotionScale
import com.fuelexpenselog.app.ui.motion.StandardEasing
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import kotlin.math.max

private const val EM_DASH = "—"

/**
 * One value in the consumption chart. A null [value] is a span the engine could
 * not compute - a broken chain - and is drawn as a dash rather than as zero.
 *
 * "A dash where the chain breaks instead of a wrong number" is the whole
 * promise, so the gap is a first-class case here, not an absence.
 */
data class ChartBar(
    val value: Double?,
    val label: String,
    val caption: String? = null,
)

/**
 * The single chart in the app. Hand-drawn rather than pulled from a library:
 * every default a chart library brings - rounded corners, axes, a legend,
 * re-animation on data change - is wrong for this design, and a dependency is
 * something to audit on every release.
 */
@Composable
fun ConsumptionBarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    unitLabel: String = "",
) {
    if (bars.isEmpty()) return
    val colors = FuelTheme.colors
    val measurer = rememberTextMeasurer()
    val motionScale = LocalMotionScale.current

    val captionStyle = FuelTheme.type.meta.copy(color = colors.bodyGrey)
    val valueStyle = FuelTheme.type.meta.copy(color = colors.ink)

    val progress = remember { Animatable(0f) }

    // Keyed on Unit, never on the data: bars grow once per screen entry and
    // never re-animate when a figure changes underneath them.
    var hasPlayed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (hasPlayed || motionScale <= 0f) {
            progress.snapTo(1f)
        } else {
            val total = Durations.CHART + (bars.size - 1) * Durations.CHART_STAGGER
            progress.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = (total * motionScale).toInt().coerceAtLeast(1),
                    easing = StandardEasing,
                ),
            )
        }
        hasPlayed = true
    }

    val spoken = remember(bars, unitLabel) {
        val real = bars.mapNotNull { it.value }
        val gaps = bars.count { it.value == null }
        buildString {
            append("Consumption by span. ")
            append("${bars.size} values")
            if (real.isNotEmpty()) {
                append(", latest ${"%.1f".format(real.last())} $unitLabel")
            }
            if (gaps > 0) append(", $gaps with no figure because the fuel chain breaks there")
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(Dimens.chartHeight)
            .semantics { contentDescription = spoken }
    ) {
        drawBars(
            bars = bars,
            progress = progress.value,
            barCount = bars.size,
            measurer = measurer,
            valueStyle = valueStyle,
            captionStyle = captionStyle,
            activeColor = colors.yellow,
            inactiveColor = colors.barInactive,
            emptyColor = colors.barEmpty,
            hairlineColor = colors.hairline,
        )
    }
}

private fun DrawScope.drawBars(
    bars: List<ChartBar>,
    progress: Float,
    barCount: Int,
    measurer: TextMeasurer,
    valueStyle: TextStyle,
    captionStyle: TextStyle,
    activeColor: Color,
    inactiveColor: Color,
    emptyColor: Color,
    hairlineColor: Color,
) {
    val labelBand = 22.dp.toPx()
    val valueBand = 20.dp.toPx()
    val plotTop = valueBand
    val plotBottom = size.height - labelBand
    val plotHeight = plotBottom - plotTop
    if (plotHeight <= 0f) return

    val gap = Dimens.chartBarGap.toPx()
    val barWidth = (size.width - gap * (barCount - 1)) / barCount
    val maxValue = bars.mapNotNull { it.value }.maxOrNull() ?: 1.0

    // The index of the last bar that actually has a figure - that one is yellow.
    val latestRealIndex = bars.indexOfLast { it.value != null }

    // Stagger expressed as an OFFSET inside this one animation, never as a
    // delay: delay() is wall-clock and is not scaled, so with animations turned
    // off a delayed stagger would still make the user wait.
    val totalMillis = (Durations.CHART + (barCount - 1) * Durations.CHART_STAGGER).toFloat()
    val elapsed = progress * totalMillis

    bars.forEachIndexed { index, bar ->
        val barStart = (elapsed - index * Durations.CHART_STAGGER) / Durations.CHART
        val grow = barStart.coerceIn(0f, 1f)
        val x = index * (barWidth + gap)

        if (bar.value == null) {
            // A 3dp stub, with an EM DASH where the figure would be.
            //
            // The dash is the whole point: this span has no consumption figure
            // because the fuel chain breaks here, and showing a dash rather than
            // a zero is the difference between the app admitting it does not
            // know and the app quietly inventing a number.
            val stubHeight = 3.dp.toPx() * grow
            drawRect(
                color = emptyColor,
                topLeft = Offset(x, plotBottom - stubHeight),
                size = Size(barWidth, stubHeight),
            )
            if (grow > 0.6f) {
                val layout = measurer.measure(EM_DASH, captionStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x + (barWidth - layout.size.width) / 2f,
                        plotBottom - stubHeight - layout.size.height - 3.dp.toPx(),
                    ),
                )
            }
        } else {
            val fraction = (bar.value / maxValue).toFloat().coerceIn(0f, 1f)
            val barHeight = max(1f, plotHeight * fraction * 0.86f * grow)
            drawRect(
                color = if (index == latestRealIndex) activeColor else inactiveColor,
                topLeft = Offset(x, plotBottom - barHeight),
                size = Size(barWidth, barHeight),
            )
            if (grow > 0.6f) {
                val text = "%.1f".format(bar.value)
                val layout = measurer.measure(text, valueStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x + (barWidth - layout.size.width) / 2f,
                        plotBottom - barHeight - layout.size.height - 3.dp.toPx(),
                    ),
                )
            }
        }

        val labelLayout = measurer.measure(bar.label, captionStyle)
        drawText(
            textLayoutResult = labelLayout,
            topLeft = Offset(
                x + (barWidth - labelLayout.size.width) / 2f,
                plotBottom + 6.dp.toPx(),
            ),
        )
    }

    drawLine(
        color = hairlineColor,
        start = Offset(0f, plotBottom),
        end = Offset(size.width, plotBottom),
        strokeWidth = 1.dp.toPx(),
    )
}

/**
 * The garage sparkline. Same rules as the chart, at row scale: no axes, no
 * labels, the latest bar in yellow.
 */
@Composable
fun Sparkline(
    values: List<Double?>,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    val colors = FuelTheme.colors

    Canvas(
        modifier
            .fillMaxWidth()
            .height(Dimens.sparklineHeight)
    ) {
        val gap = Dimens.sparklineBarGap.toPx()
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        val maxValue = values.filterNotNull().maxOrNull() ?: 1.0
        val latestRealIndex = values.indexOfLast { it != null }

        values.forEachIndexed { index, value ->
            val x = index * (barWidth + gap)
            if (value == null) {
                drawRect(
                    color = colors.barEmpty,
                    topLeft = Offset(x, size.height - 3.dp.toPx()),
                    size = Size(barWidth, 3.dp.toPx()),
                )
            } else {
                val fraction = (value / maxValue).toFloat().coerceIn(0.15f, 1f)
                val barHeight = size.height * fraction
                drawRect(
                    color = if (index == latestRealIndex) colors.yellow else colors.barInactive,
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }
    }
}

/**
 * One month, split fuel against everything else, on the same bar. The remainder
 * is scaled against the largest month so the bars compare across rows.
 */
@Composable
fun SplitBar(
    fuel: Double,
    other: Double,
    maxTotal: Double,
    modifier: Modifier = Modifier,
) {
    val colors = FuelTheme.colors
    val total = fuel + other

    Box(
        modifier
            .fillMaxWidth()
            .height(Dimens.splitBarHeight)
            .semantics {
                contentDescription = if (total > 0) {
                    "Fuel ${(fuel / total * 100).toInt()} percent of this month"
                } else "No spend"
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(Dimens.splitBarHeight)) {
            val scale = if (maxTotal > 0) (total / maxTotal).toFloat().coerceIn(0f, 1f) else 0f
            val usedWidth = size.width * scale
            val fuelWidth = if (total > 0) usedWidth * (fuel / total).toFloat() else 0f

            drawRect(color = colors.barEmpty, size = Size(size.width, size.height))
            drawRect(color = colors.yellow, size = Size(fuelWidth, size.height))
            drawRect(
                color = colors.ink,
                topLeft = Offset(fuelWidth, 0f),
                size = Size((usedWidth - fuelWidth).coerceAtLeast(0f), size.height),
            )
        }
    }
}
