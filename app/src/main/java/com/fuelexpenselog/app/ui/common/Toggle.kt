package com.fuelexpenselog.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

private const val TOGGLE_MS = 140

/**
 * The square toggle: a 1dp ink outline, a solid ink knob, no track fill. Purely visual - the
 * row it sits in is the touch target, so the whole row toggles, not a 52dp sliver of it.
 */
@Composable
fun SquareToggle(checked: Boolean, modifier: Modifier = Modifier, ink: Color = FuelTheme.colors.textPrimary) {
    // 52 wide, 3dp of padding each side, a 22dp knob: 24dp of travel, as the handoff says.
    val travel = Dimens.toggleWidth - Dimens.toggleKnobWidth - 6.dp
    val offset by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = tween(TOGGLE_MS, easing = FastOutSlowInEasing),
        label = "knob",
    )
    Box(
        modifier = modifier
            .size(Dimens.toggleWidth, Dimens.toggleHeight)
            .border(Dimens.hairline, ink)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                // The lambda overload reads the animated value in layout, not composition.
                .offset { IntOffset(offset.roundToPx(), 0) }
                .size(Dimens.toggleKnobWidth, Dimens.toggleKnobHeight)
                .background(ink),
        )
    }
}

/**
 * A title, an optional explanation, and the toggle, as one switch for TalkBack.
 *
 * [highlight] is the full-tank treatment: the row itself turns yellow when on, crossfading
 * over the same 140ms the knob takes to travel. Otherwise the row is outlined.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    highlight: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    val background by animateColorAsState(
        targetValue = if (highlight && checked) colors.primary else Color.Transparent,
        animationSpec = tween(TOGGLE_MS),
        label = "row",
    )
    // On yellow the ink stays ink in both themes; onPrimary never inverts.
    val onRow = if (highlight && checked) colors.onPrimary else colors.textPrimary
    val onRowSecondary = if (highlight && checked) colors.onPrimarySecondary else colors.textSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .then(if (highlight) Modifier else Modifier.border(Dimens.hairline, colors.outlineStrong))
            .background(background)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = if (highlight) Dimens.gutter else 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = type.body, color = onRow)
            if (body != null) Text(body, style = type.meta, color = onRowSecondary)
        }
        SquareToggle(checked, ink = if (highlight && checked) colors.onPrimary else colors.textPrimary)
    }
}
