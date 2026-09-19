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
}
