package com.fuelexpenselog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun FuelLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkFuelColors else LightFuelColors

    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outline,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outline,
            error = colors.danger,
        )
    }

    CompositionLocalProvider(
        LocalFuelColors provides colors,
        LocalFuelType provides DefaultFuelType,
    ) {
        MaterialTheme(
            colorScheme = material,
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
