package com.fuelexpenselog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * The app's own tokens, not Material's. M3's ColorScheme has no slot for "surfaceAlt" or a
 * hairline, and this design has no use for most of what it does have.
 *
 * MaterialTheme is still wrapped, with a mapped scheme and zero-radius shapes, so that any
 * stray M3 component (Scaffold, DatePicker, AlertDialog) inherits the brand instead of
 * rendering in Material purple with rounded corners.
 *
 * Dynamic colour is deliberately NOT used: it would override the brand yellow.
 */
val LocalFuelColors = staticCompositionLocalOf { LightFuelColors }
val LocalFuelType = staticCompositionLocalOf { DefaultFuelType }

object FuelTheme {
    val colors: FuelColors
        @Composable @ReadOnlyComposable get() = LocalFuelColors.current

    val type: FuelType
        @Composable @ReadOnlyComposable get() = LocalFuelType.current
}

private val ZeroRadius = RoundedCornerShape(0.dp)

/** M3's own type scale in Instrument Sans, so a stray dialog or picker is not in Roboto. */
private val MaterialType: Typography = Typography().run {
    fun TextStyle.inBrand() = copy(fontFamily = InstrumentSans)
    copy(
        displayLarge = displayLarge.inBrand(),
        displayMedium = displayMedium.inBrand(),
        displaySmall = displaySmall.inBrand(),
        headlineLarge = headlineLarge.inBrand(),
        headlineMedium = headlineMedium.inBrand(),
        headlineSmall = headlineSmall.inBrand(),
        titleLarge = titleLarge.inBrand(),
        titleMedium = titleMedium.inBrand(),
        titleSmall = titleSmall.inBrand(),
        bodyLarge = bodyLarge.inBrand(),
        bodyMedium = bodyMedium.inBrand(),
        bodySmall = bodySmall.inBrand(),
        labelLarge = labelLarge.inBrand(),
        labelMedium = labelMedium.inBrand(),
        labelSmall = labelSmall.inBrand(),
    )
}

@Composable
fun FuelLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkFuelColors else LightFuelColors

    // Every slot is mapped, not just the obvious ones: M3 builds dialogs and pickers from the
    // surface-container and secondary slots, and any slot left at its default renders in
    // Material's own lavender. There is no second accent, so secondary and tertiary are ink.
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val material = base.copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primary,
        onPrimaryContainer = colors.onPrimary,
        inversePrimary = colors.primary,
        secondary = colors.textPrimary,
        onSecondary = colors.background,
        secondaryContainer = colors.surfaceAlt,
        onSecondaryContainer = colors.textPrimary,
        tertiary = colors.textPrimary,
        onTertiary = colors.background,
        tertiaryContainer = colors.surfaceAlt,
        onTertiaryContainer = colors.textPrimary,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceAlt,
        onSurfaceVariant = colors.textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = colors.textPrimary,
        inverseOnSurface = colors.background,
        error = colors.danger,
        onError = colors.background,
        outline = colors.outlineStrong,
        outlineVariant = colors.outline,
        surfaceBright = colors.surface,
        surfaceDim = colors.surfaceAlt,
        surfaceContainerLowest = colors.surface,
        surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surface,
        surfaceContainerHighest = colors.surfaceAlt,
    )

    CompositionLocalProvider(
        LocalFuelColors provides colors,
        LocalFuelType provides DefaultFuelType,
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = MaterialType,
            shapes = Shapes(
                extraSmall = ZeroRadius,
                small = ZeroRadius,
                medium = ZeroRadius,
                large = ZeroRadius,
                extraLarge = ZeroRadius,
            ),
            content = content,
        )
    }
}
