package com.fuelexpenselog.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Radius is 0 everywhere in-app and there is no elevation. Structure comes from 1dp
 * hairlines and a single sunken panel per screen, not from cards and shadows.
 */
object Dimens {
    /** Screen gutter, left and right, on every screen. */
    val gutter = 22.dp
    val rowPadding = 13.dp
    val hairline = 1.dp

    /**
     * Android guidance is 48dp. The app is used one-handed at a pump, often in poor light
     * and sometimes in the rain, so take the larger figure rather than the 44dp minimum.
     */
    val minTouchTarget = 48.dp
    val actionHeight = 56.dp

    val sectionGap = 28.dp
    val labelToContent = 8.dp

    val chartHeight = 220.dp
    val chartBarGap = 7.dp
    val sparklineHeight = 26.dp
    val sparklineBarGap = 4.dp

    /** The type bar down the left of an entry row: yellow fill-up, grey partial, ink expense. */
    val entryBar = 3.dp

    val toggleWidth = 52.dp
    val toggleHeight = 28.dp
    val toggleKnobWidth = 22.dp
    val toggleKnobHeight = 20.dp

    val underlineFocused = 2.dp
    val underlineResting = 1.dp

    /** The yellow bar that stands in for a caret beside a figure input. */
    val caretWidth = 2.dp

    val warningRule = 3.dp
    val splitBar = 8.dp
    val categoryBar = 6.dp
    val statusDot = 7.dp

    /** Glyphs sit in 48dp boxes; the drawn stroke is 20dp. */
    val icon = 20.dp

    /** Last-N spans on the chart and the sparkline. */
    const val CHART_BARS = 9
}
