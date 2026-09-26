package com.fuelexpenselog.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * The bottom action bar, split 50/50. The yellow half is always the primary - there is one
 * yellow action per screen, and this is where it lives.
 */
@Composable
fun SplitActionBar(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
) {
    val colors = FuelTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .topHairline(colors.textPrimary),
    ) {
        ActionCell(primary, onPrimary, yellow = true, enabled = primaryEnabled)
        Box(
            Modifier
                .width(Dimens.hairline)
                .fillMaxHeight()
                .background(colors.outlineStrong),
        )
        ActionCell(secondary, onSecondary, yellow = false, enabled = true)
    }
}

@Composable
private fun RowScope.ActionCell(text: String, onClick: () -> Unit, yellow: Boolean, enabled: Boolean) {
    PressScaleBox(
        onClick = onClick,
        enabled = enabled,
        background = if (yellow && enabled) FuelTheme.colors.primary else Color.Transparent,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = Dimens.actionHeight),
    ) {
        Text(
            text,
            style = FuelTheme.type.button,
            color = actionTextColor(yellow, enabled),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
        )
    }
}

/** The 56dp standalone primary: Save vehicle, Start logging, Continue. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = FuelTheme.type.buttonLarge,
) {
    PressScaleBox(
        onClick = onClick,
        enabled = enabled,
        background = if (enabled) FuelTheme.colors.primary else FuelTheme.colors.surfaceAlt,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.actionHeight),
    ) {
        Text(
            text,
            style = textStyle,
            color = actionTextColor(yellow = true, enabled = enabled),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
        )
    }
}

/** The outlined secondary beside or instead of a yellow one. Never yellow itself. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FuelTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = Dimens.actionHeight)
            .border(Dimens.hairline, if (enabled) colors.textPrimary else colors.outlineStrong)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = FuelTheme.type.button,
            color = if (enabled) colors.textPrimary else colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun actionTextColor(yellow: Boolean, enabled: Boolean): Color = when {
    !enabled -> FuelTheme.colors.textSecondary
    yellow -> FuelTheme.colors.onPrimary
    else -> FuelTheme.colors.textPrimary
}

/**
 * Compresses to 96% for 90ms while pressed - the only feedback a save gets. There is no
 * success toast: the new row appearing is the confirmation.
 */
@Composable
private fun PressScaleBox(
    onClick: () -> Unit,
    enabled: Boolean,
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(90), label = "press")
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
