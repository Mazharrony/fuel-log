package com.fuelexpenselog.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fuelexpenselog.app.R

/**
 * Instrument Sans ships from Google Fonts only as a variable font, so one file
 * serves all three weights: register it three times with explicit weight axis
 * settings, which Android honours from API 26 and this app targets 26 as its
 * floor anyway.
 */
@OptIn(ExperimentalTextApi::class)
private fun instrumentSans(weight: Int) = Font(
    resId = R.font.instrument_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(100f),
    ),
)

val InstrumentSans = FontFamily(
    instrumentSans(400),
    instrumentSans(500),
    instrumentSans(600),
)

/**
 * Every numeric style carries tnum, set once here rather than at call sites, so
 * money, distance, volume and consumption all align down a column.
 *
 * Verified present in the shipped font - a missing tnum feature makes
 * fontFeatureSettings a silent no-op, and the damage only shows up on a screen
 * with sixty rows of figures.
 */
private val Figures = TextStyle(
    fontFamily = InstrumentSans,
    fontFeatureSettings = "tnum",
)

private val Prose = TextStyle(fontFamily = InstrumentSans)

@Immutable
data class FuelType(
    /** Headline consumption on the vehicle screen. */
    val figureXl: TextStyle = Figures.copy(
        fontSize = 72.sp, lineHeight = 62.sp,
        fontWeight = FontWeight.W600, letterSpacing = (-0.045).em,
    ),
    /** Odometer and amount inputs. */
    val figureL: TextStyle = Figures.copy(
        fontSize = 44.sp, lineHeight = 44.sp,
        fontWeight = FontWeight.W600, letterSpacing = (-0.04).em,
    ),
    /** Screen totals, month figures. */
    val figureM: TextStyle = Figures.copy(
        fontSize = 31.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.W600, letterSpacing = (-0.03).em,
    ),
    /** Stat cells. */
    val figureS: TextStyle = Figures.copy(
        fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600,
    ),
    /** Screen titles. */
    val title: TextStyle = Prose.copy(
        fontSize = 30.sp, lineHeight = 33.sp,
        fontWeight = FontWeight.W600, letterSpacing = (-0.025).em,
    ),
    /** Nav bar titles. */
    val subtitle: TextStyle = Prose.copy(
        fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600,
    ),
    /** Row titles and primary copy. */
    val body: TextStyle = Figures.copy(
        fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600,
    ),
    /** Long-form copy. */
    val bodyRegular: TextStyle = Prose.copy(
        fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.W400,
    ),
    /** Row subtitles. */
    val meta: TextStyle = Figures.copy(
        fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.W400,
    ),
    /** Field and section labels. */
    val label: TextStyle = Prose.copy(
        fontSize = 10.sp, lineHeight = 13.sp,
        fontWeight = FontWeight.W600, letterSpacing = 0.18.em,
    ),
    /** Status chips. */
    val eyebrow: TextStyle = Prose.copy(
        fontSize = 11.sp, lineHeight = 14.sp,
        fontWeight = FontWeight.W600, letterSpacing = 0.14.em,
    ),
)

val LocalFuelType = androidx.compose.runtime.staticCompositionLocalOf { FuelType() }
