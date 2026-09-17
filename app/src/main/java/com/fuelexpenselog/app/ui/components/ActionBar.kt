package com.fuelexpenselog.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.motion.Durations
import com.fuelexpenselog.app.ui.motion.motionTween
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * Pinned to the bottom edge, split 50/50 when there are two actions. The yellow
 * half is always the primary one.
 *
 * imePadding and navigationBarsPadding are not optional here. This app exists
 * for the add-fill-up screen, and a keyboard covering the save button on that
 * screen would be the single worst bug it could ship with.
 */
@Composable
fun ActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(FuelTheme.colors.paper)
            .imePadding()
            .navigationBarsPadding()
    ) {
        Hairline(color = FuelTheme.colors.frame)
        Row(
            Modifier.fillMaxWidth().height(Dimens.actionHeight),
            content = content,
        )
    }
}

/**
 * The primary action. Compresses to 96% for 90ms on press.
 *
 * That compression IS the confirmation - there is no success toast anywhere in
 * this app. On save the new row simply appears, which is a stronger signal than
 * a message that covers it.
 */
@Composable
fun RowScope.PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    weight: Float = 1f,
) {
    val colors = FuelTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = motionTween(Durations.PRESS),
        label = "press",
    )

    Box(
        modifier
            .weight(weight)
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (enabled) colors.yellow else colors.sunken)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                // The compression is the feedback; an overlay on top of it
                // would just muddy the colour.
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FuelTheme.type.body,
            color = if (enabled) colors.onYellow else colors.bodyGrey,
        )
    }
}

/** The non-primary half. Paper ground, ink label, hairline divider to its left. */
@Composable
fun RowScope.SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    weight: Float = 1f,
) {
    val colors = FuelTheme.colors
    Box(
        modifier
            .weight(weight)
            .fillMaxHeight()
            .background(colors.paper)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FuelTheme.type.body,
            color = if (destructive) colors.destructive else colors.ink,
        )
    }
}

/**
 * Yellow wash with a 3dp yellow left rule, expanding over 160ms.
 *
 * Inline, never a dialog and never a snackbar: it must not take focus out of the
 * field being edited. It also carries no focusable node for the same reason.
 */
@Composable
fun WarningBlock(
    messages: List<String>,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return
    val colors = FuelTheme.colors

    Row(
        modifier
            .fillMaxWidth()
            .background(colors.yellowWash)
            .height(IntrinsicSize.Min)
            .animateContentSize(animationSpec = motionTween(Durations.WARNING))
    ) {
        Box(Modifier.width(Dimens.warningRule).fillMaxHeight().background(colors.yellow))
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            messages.forEach { message ->
                Text(
                    text = message,
                    style = FuelTheme.type.meta,
                    color = colors.warningInk,
                )
            }
        }
    }
}
