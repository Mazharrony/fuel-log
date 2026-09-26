package com.fuelexpenselog.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Yellow and charcoal. One yellow, one ink, cool neutrals - no second accent, no gradients.
 *
 * Yellow is a marker, never a surface behind body text, and there is one yellow action per
 * screen. Two rules below exist because they are the traps:
 *
 *  - [FuelColors.onPrimary] NEVER inverts between light and dark. Yellow is identical in
 *    both schemes, so a naive paper/ink swap would put near-white text on #F5C518.
 *  - Yellow is never used as text on a light surface; it fails 4.5:1. Filled containers and
 *    accents only.
 */
@Immutable
data class FuelColors(
    val background: Color,
    val surface: Color,
    /** Grouped panels, footers, "last done at" blocks. One sunken panel per screen. */
    val surfaceAlt: Color,
    val outline: Color,
    /** Resting input underlines, chip outlines, the inactive sparkline bars. */
    val outlineStrong: Color,
    /** Every figure that matters is textPrimary, never textSecondary. */
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    /** Secondary text ON yellow - the splash subtitle, a meta line in a selected row. */
    val onPrimarySecondary: Color,
    val warningWash: Color,
    val warningInk: Color,
    /** The 3dp rule down the left of a warning block. */
    val warningRule: Color,
    val danger: Color,
    /** A bar that is not the latest one. */
    val chartInactive: Color,
    /** The stub standing in for a span that has no figure. */
    val chartEmpty: Color,
    val isDark: Boolean,
)

private val Yellow = Color(0xFFF5C518)
private val Charcoal = Color(0xFF16181C)

/** 5.0:1 on the yellow, which the handoff's #6B5B00 only reached on its lighter yellow. */
private val InkOnYellowSecondary = Color(0xFF5C4E00)

val LightFuelColors = FuelColors(
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1F1EC),
    outline = Color(0xFFE2E2DC),
    outlineStrong = Color(0xFFC6C6BE),
    textPrimary = Charcoal,
    textSecondary = Color(0xFF5A6069),
    primary = Yellow,
    onPrimary = Charcoal,
    onPrimarySecondary = InkOnYellowSecondary,
    warningWash = Color(0xFFFFF6D6),
    warningInk = Color(0xFF4A4218),
    warningRule = Yellow,
    danger = Color(0xFFA32A2A),
    chartInactive = Color(0xFFC6C6BE),
    chartEmpty = Color(0xFFE2E2DC),
    isDark = false,
)

/**
 * Also the Statistics screen's palette in either mode: that screen is always dark, so its
 * chart tokens here are the handoff's dark-screen values mapped onto this neutral ramp.
 */
val DarkFuelColors = FuelColors(
    background = Color(0xFF121418),
    surface = Color(0xFF1A1D22),
    surfaceAlt = Color(0xFF22262C),
    outline = Color(0xFF2E333A),
    outlineStrong = Color(0xFF474D56),
    textPrimary = Color(0xFFF2F3F5),
    textSecondary = Color(0xFF9BA3AD),
    primary = Yellow,
    onPrimary = Charcoal, // deliberately NOT inverted - see the KDoc above
    onPrimarySecondary = InkOnYellowSecondary,
    warningWash = Color(0xFF2A2410),
    warningInk = Color(0xFFE8DFA8),
    warningRule = Yellow,
    danger = Color(0xFFE06C6C),
    chartInactive = Color(0xFF3F454E),
    chartEmpty = Color(0xFF2A2F36),
    isDark = true,
)
