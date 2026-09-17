package com.fuelexpenselog.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * One yellow, one ink, cool blue-grey neutrals. No second accent, no gradients.
 *
 * Yellow is a MARKER, never a surface for text-heavy areas: one yellow action
 * per screen, the full-tank state, a selected chip, the latest bar in a chart.
 * Every figure that matters is ink on paper, never grey.
 *
 * These live in their own CompositionLocal rather than a Material ColorScheme
 * because M3 has no slot for "sunken" or "hairline", and forcing them into
 * surfaceVariant/outline loses the meaning that makes the design readable.
 */
@Immutable
data class FuelColors(
    val yellow: Color,
    val ink: Color,
    val paper: Color,
    val sunken: Color,
    val hairline: Color,
    val frame: Color,
    val strongHairline: Color,
    val labelInk: Color,
    val bodyGrey: Color,
    /**
     * A fifth grey, used 52 times in the design prototype and absent from the
     * handoff token table entirely. It - not bodyGrey - is the colour of
     * vehicle meta, unit labels beside a figure, odometer readouts, chevrons,
     * and history row meta and rate.
     *
     * The three greys are genuinely distinct roles:
     *   metaGrey  #6B7280  meta sitting beside a figure
     *   labelInk  #5E6773  uppercase micro-labels
     *   bodyGrey  #55606C  explanatory prose
     */
    val metaGrey: Color,
    val yellowWash: Color,
    val warningInk: Color,
    /** Warning-block icon stroke. Distinct from the warningInk beside it. */
    val warningIcon: Color,
    /**
     * Text on yellow. FIXED in both schemes.
     *
     * Yellow does not invert between light and dark, so this must not follow the
     * ink/paper swap. A naive swap puts near-white text on #F5D211, which is
     * unreadable and the easiest mistake to make in this palette.
     */
    val onYellow: Color,
    val onYellowSecondary: Color,
    val destructive: Color,
    val barInactive: Color,
    val barEmpty: Color,
    val isDark: Boolean,
) {
    companion object {
        private val Yellow = Color(0xFFF5D211)
        private val Ink = Color(0xFF0D1117)

        val Light = FuelColors(
            yellow = Yellow,
            ink = Ink,
            paper = Color(0xFFF7F8FA),
            sunken = Color(0xFFEEF1F5),
            hairline = Color(0xFFE3E7ED),
            frame = Color(0xFFD2D8E0),
            strongHairline = Color(0xFFBFC7D1),
            labelInk = Color(0xFF5E6773),   // 5.4:1 on paper
            bodyGrey = Color(0xFF55606C),   // 6.0:1 on paper
            metaGrey = Color(0xFF6B7280),
            yellowWash = Color(0xFFFFF6CE),
            warningInk = Color(0xFF4A4218),
            warningIcon = Color(0xFF8A7300),
            onYellow = Ink,
            onYellowSecondary = Color(0xFF6B5B00),  // 4.5:1 on yellow
            destructive = Color(0xFF9B2C2C),
            barInactive = Color(0xFFBFC7D1),
            barEmpty = Color(0xFFE3E7ED),
            isDark = false,
        )

        /**
         * The same palette with paper and ink swapped; yellow is unchanged.
         *
         * Two tokens are NOT defined in the design handoff, which draws only the
         * two dark screens. These are proposals pending sign-off:
         *   sunken     - #151B22, sitting between ground #0D1117 and divider #222A33
         *   yellowWash - #2A2410 ground with #E8DFA8 ink, keeping the yellow rule
         */
        val Dark = FuelColors(
            yellow = Yellow,
            ink = Color(0xFFF7F8FA),
            paper = Ink,
            sunken = Color(0xFF151B22),          // proposed
            hairline = Color(0xFF222A33),
            frame = Color(0xFF2A323C),
            strongHairline = Color(0xFF3D4752),
            labelInk = Color(0xFF8794A3),
            bodyGrey = Color(0xFF9AA5B1),
            metaGrey = Color(0xFF8C97A5),        // derived
            yellowWash = Color(0xFF2A2410),      // proposed
            warningInk = Color(0xFFE8DFA8),      // proposed
            warningIcon = Color(0xFFD8C36A),     // derived
            onYellow = Ink,                      // fixed, never inverted
            onYellowSecondary = Color(0xFF6B5B00),
            destructive = Color(0xFFE06C6C),
            barInactive = Color(0xFF3D4752),
            barEmpty = Color(0xFF2A323C),
            isDark = true,
        )
    }
}

val LocalFuelColors = staticCompositionLocalOf { FuelColors.Light }
