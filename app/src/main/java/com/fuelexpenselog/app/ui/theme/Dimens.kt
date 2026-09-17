package com.fuelexpenselog.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The grid. 22dp screen gutter, hairline structure, no radius anywhere.
 */
object Dimens {
    /** Screen gutter, left and right, on every screen. */
    val gutter = 22.dp

    /** Row vertical padding. */
    val rowPadding = 13.dp

    /** 1px rules. Hairlines divide rows; the frame bounds a screen. */
    val hairline = 1.dp

    /** The bar that codes an entry type down the left edge of a row. */
    val entryBar = 3.dp

    /** Bottom action bar, and standalone buttons. */
    val actionHeight = 56.dp

    /**
     * Android guidance is 48dp; the handoff says 44. Take the larger, since the
     * app is used one-handed at a pump, often in poor light.
     */
    val minTouchTarget = 48.dp

    val toggleWidth = 52.dp
    val toggleHeight = 28.dp
    val toggleKnobWidth = 22.dp
    val toggleKnobHeight = 20.dp

    /** Input underline: 2dp when focused, 1dp otherwise. */
    val underlineFocused = 2.dp
    val underlineResting = 1.dp

    /** Left rule on a warning block. */
    val warningRule = 3.dp

    val sectionGap = 28.dp

    // Chart and sparkline metrics come straight from the design source.
    val chartHeight = 220.dp
    val chartBarGap = 7.dp
    val sparklineHeight = 26.dp
    val sparklineBarGap = 4.dp
    /** Bars per sparkline and per consumption chart. */
    const val CHART_BARS = 9

    /** Months screen. */
    val splitBarHeight = 8.dp
    /** Month-detail category bars. */
    val categoryBarHeight = 6.dp
}
