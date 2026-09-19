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
    /** Every figure that matters is textPrimary, never textSecondary. */
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val warningWash: Color,
    val warningInk: Color,
    val danger: Color,
    val isDark: Boolean,
)

private val Yellow = Color(0xFFF5C518)
private val Charcoal = Color(0xFF16181C)

val LightFuelColors = FuelColors(
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1F1EC),
    outline = Color(0xFFE2E2DC),
    textPrimary = Charcoal,
    textSecondary = Color(0xFF5A6069),
    primary = Yellow,
    onPrimary = Charcoal,
    warningWash = Color(0xFFFFF6D6),
    warningInk = Color(0xFF4A4218),
    danger = Color(0xFFA32A2A),
    isDark = false,
)

val DarkFuelColors = FuelColors(
    background = Color(0xFF121418),
    surface = Color(0xFF1A1D22),
    surfaceAlt = Color(0xFF22262C),
    outline = Color(0xFF2E333A),
    textPrimary = Color(0xFFF2F3F5),
    textSecondary = Color(0xFF9BA3AD),
    primary = Yellow,
    onPrimary = Charcoal, // deliberately NOT inverted - see the KDoc above
    warningWash = Color(0xFF2A2410),
    warningInk = Color(0xFFE8DFA8),
    danger = Color(0xFFE06C6C),
    isDark = true,
)
