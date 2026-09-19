package com.fuelexpenselog.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Every numeric style carries `tnum`. Money, distance, volume and consumption have to align
 * down a list; without tabular figures a column of numbers visibly wobbles.
 */
private val Figures = TextStyle(fontFeatureSettings = "tnum")

@Immutable
data class FuelType(
    /** Headline consumption on the vehicle screen. */
    val figureXl: TextStyle,
    /** Odometer and amount inputs. */
    val figureL: TextStyle,
    /** Screen totals, month figures. */
    val figureM: TextStyle,
    /** Stat strip cells. */
    val figureS: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val bodyRegular: TextStyle,
    val meta: TextStyle,
    /** 10sp uppercase field and section labels. */
    val label: TextStyle,
    /** 11sp uppercase status chips and summary-cell labels. */
    val eyebrow: TextStyle,
)

val DefaultFuelType = FuelType(
    figureXl = Figures.copy(fontSize = 72.sp, lineHeight = 62.sp, fontWeight = FontWeight.W600, letterSpacing = (-3.2).sp),
    figureL = Figures.copy(fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.W600, letterSpacing = (-1.8).sp),
    figureM = Figures.copy(fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.9).sp),
    figureS = Figures.copy(fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600),
    title = TextStyle(fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.75).sp),
    subtitle = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600),
    body = Figures.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600),
    bodyRegular = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.W400),
    meta = Figures.copy(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.W400),
    label = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.W600, letterSpacing = 1.8.sp),
    eyebrow = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.W600, letterSpacing = 1.54.sp),
)
