package com.fuelexpenselog.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fuelexpenselog.app.R

/**
 * Instrument Sans, bundled rather than downloaded - a downloadable font is a network call,
 * and this app has no network.
 *
 * The file is one variable font (wght 400-700), so every weight is the same resource with
 * its own `wght` setting rather than four static files.
 */
@OptIn(ExperimentalTextApi::class)
private fun instrumentSans(weight: Int) = Font(
    R.font.instrument_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val InstrumentSans = FontFamily(
    instrumentSans(400),
    instrumentSans(500),
    instrumentSans(600),
    instrumentSans(700),
)

private val Text = TextStyle(fontFamily = InstrumentSans)

/**
 * Every numeric style carries `tnum`. Money, distance, volume and consumption have to align
 * down a list; without tabular figures a column of numbers visibly wobbles.
 */
private val Figures = Text.copy(fontFeatureSettings = "tnum")

@Immutable
data class FuelType(
    /** Headline consumption on the vehicle screen. */
    val figureXl: TextStyle,
    /** The month total on month detail. */
    val figureXxl: TextStyle,
    /** Odometer and amount inputs. */
    val figureL: TextStyle,
    /** The average above the statistics chart. */
    val figureChart: TextStyle,
    /** Screen totals, month figures. */
    val figureM: TextStyle,
    /** Volume and total-paid inputs. */
    val figureInput: TextStyle,
    /** Garage headline figures, the vehicle name input, per-currency subtotals. */
    val figureRow: TextStyle,
    /** Stat strip cells. */
    val figureS: TextStyle,
    val wordmark: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val bodyRegular: TextStyle,
    val button: TextStyle,
    /** The 56dp standalone buttons: Save vehicle, Start logging. */
    val buttonLarge: TextStyle,
    val chip: TextStyle,
    val meta: TextStyle,
    /** Month-on-month deltas. */
    val delta: TextStyle,
    /** 10sp uppercase field and section labels. */
    val label: TextStyle,
    /** 11sp uppercase status chips and summary-cell labels. */
    val eyebrow: TextStyle,
    val chartValue: TextStyle,
    val chartLabel: TextStyle,
)

val DefaultFuelType = FuelType(
    figureXl = Figures.copy(fontSize = 72.sp, lineHeight = 62.sp, fontWeight = FontWeight.W600, letterSpacing = (-3.2).sp),
    figureXxl = Figures.copy(fontSize = 52.sp, lineHeight = 52.sp, fontWeight = FontWeight.W600, letterSpacing = (-2.1).sp),
    figureL = Figures.copy(fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.W600, letterSpacing = (-1.8).sp),
    figureChart = Figures.copy(fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.W600, letterSpacing = (-1.2).sp),
    figureM = Figures.copy(fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.9).sp),
    figureInput = Figures.copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.9).sp),
    figureRow = Figures.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.78).sp),
    figureS = Figures.copy(fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600),
    wordmark = Text.copy(fontSize = 36.sp, lineHeight = 36.sp, fontWeight = FontWeight.W600, letterSpacing = (-1.26).sp),
    title = Text.copy(fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.75).sp),
    subtitle = Text.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600),
    body = Figures.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600),
    bodyRegular = Text.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.W400),
    button = Text.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.W600),
    buttonLarge = Text.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600),
    chip = Text.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.W500),
    meta = Figures.copy(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.W400),
    delta = Figures.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.W600),
    label = Text.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.W600, letterSpacing = 1.8.sp),
    eyebrow = Text.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.W600, letterSpacing = 1.54.sp),
    chartValue = Figures.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.W600),
    chartLabel = Text.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.W500),
)
