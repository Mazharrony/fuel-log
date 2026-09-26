package com.fuelexpenselog.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

private const val EXPAND_MS = 160

/**
 * Yellow wash, a 3dp yellow rule, meta-size text. **Warn, never block**: nothing in here can
 * disable a Save button, and it announces itself politely without taking focus from the
 * field being typed in.
 */
@Composable
fun WarningBlock(messages: List<String>, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = messages.isNotEmpty(),
        enter = expandVertically(tween(EXPAND_MS)) + fadeIn(tween(EXPAND_MS)),
        exit = shrinkVertically(tween(EXPAND_MS)) + fadeOut(tween(EXPAND_MS)),
        modifier = modifier,
    ) {
        Note(
            text = messages.joinToString("\n"),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The same wash and rule for a standing note that is not a warning about the input. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    val colors = FuelTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.warningWash),
    ) {
        Box(
            Modifier
                .width(Dimens.warningRule)
                .fillMaxHeight()
                .background(colors.warningRule),
        )
        Column(
            Modifier.padding(start = 12.dp, end = 15.dp, top = 13.dp, bottom = 13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text, style = FuelTheme.type.meta, color = colors.warningInk)
        }
    }
}
