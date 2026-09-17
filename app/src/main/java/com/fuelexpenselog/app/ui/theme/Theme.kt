package com.fuelexpenselog.app.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Structure comes from 1px hairlines and one sunken panel per screen, so
 * Material 3 is used for almost nothing: MaterialTheme itself (so text and
 * content-colour defaults resolve), Scaffold for layout and insets,
 * BasicAlertDialog as an unstyled shell, and DatePicker. Everything else is
 * drawn by hand, because overriding M3 into this shape costs more than not
 * using it.
 */
@Composable
fun FuelLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) FuelColors.Dark else FuelColors.Light

    // A mapped ColorScheme so any stray M3 surface picks up the right colours
    // rather than Material purple.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.yellow, onPrimary = colors.onYellow,
            background = colors.paper, onBackground = colors.ink,
            surface = colors.paper, onSurface = colors.ink,
            surfaceVariant = colors.sunken, onSurfaceVariant = colors.bodyGrey,
            outline = colors.frame, outlineVariant = colors.hairline,
            error = colors.destructive,
        )
    } else {
        lightColorScheme(
            primary = colors.yellow, onPrimary = colors.onYellow,
            background = colors.paper, onBackground = colors.ink,
            surface = colors.paper, onSurface = colors.ink,
            surfaceVariant = colors.sunken, onSurfaceVariant = colors.bodyGrey,
            outline = colors.frame, outlineVariant = colors.hairline,
            error = colors.destructive,
        )
    }

    CompositionLocalProvider(
        LocalFuelColors provides colors,
        LocalFuelType provides FuelType(),
        LocalIndication provides FlatIndication,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            // Radius 0 everywhere in-app. The only rounded shapes in the whole
            // product are the launcher icon and the status dots.
            shapes = Shapes(
                extraSmall = Square, small = Square,
                medium = Square, large = Square,
                extraLarge = Square,
            ),
            content = content,
        )
    }
}

private val Square = RoundedCornerShape(0.dp)

/** Shorthand, so screens read as FuelTheme.colors.yellow. */
object FuelTheme {
    val colors: FuelColors
        @Composable get() = LocalFuelColors.current
    val type: FuelType
        @Composable get() = LocalFuelType.current
}

/**
 * Replaces the Material ripple.
 *
 * A ripple is a circular, expanding, rounded thing, in a design language with
 * no radius and no elevation. This is a flat 4% ink wash instead: present enough
 * to confirm a tap, invisible as a shape.
 */
internal object FlatIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource) =
        FlatIndicationNode(interactionSource)

    override fun hashCode() = -1
    override fun equals(other: Any?) = other === this
}

internal class FlatIndicationNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {

    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            var presses = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses--
                }
                val nowPressed = presses > 0
                if (nowPressed != pressed) {
                    pressed = nowPressed
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(color = Color.Black.copy(alpha = 0.04f), size = size)
    }
}
