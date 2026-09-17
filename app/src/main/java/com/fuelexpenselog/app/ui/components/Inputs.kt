package com.fuelexpenselog.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.motion.Durations
import com.fuelexpenselog.app.ui.motion.EaseOut
import com.fuelexpenselog.app.ui.motion.motionTween
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * No box. Label above, value at figure scale, a single underline beneath:
 * 2dp ink when focused, 1dp otherwise.
 *
 * Deliberately does NOT filter input while typing. Accepting whatever the user
 * enters and parsing it at save time avoids the whole class of IME cursor-jump
 * bugs, and it is what "warn, never block" means at the keyboard.
 */
@Composable
fun FigureInput(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    val colors = FuelTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val underlineColor by animateColorAsState(
        targetValue = if (focused) colors.ink else colors.strongHairline,
        animationSpec = motionTween(Durations.TOGGLE),
        label = "underline",
    )
    val underlineHeight by animateDpAsState(
        targetValue = if (focused) Dimens.underlineFocused else Dimens.underlineResting,
        animationSpec = motionTween(Durations.TOGGLE),
        label = "underlineHeight",
    )

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(label)
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.minTouchTarget)
                    .drawBehind {
                        val stroke = underlineHeight.toPx()
                        drawRect(
                            color = underlineColor,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - stroke),
                            size = androidx.compose.ui.geometry.Size(size.width, stroke),
                        )
                    }
                    .padding(bottom = 10.dp),
                textStyle = FuelTheme.type.figureL.copy(color = colors.ink),
                // BasicTextField draws a 2dp caret, which IS the design's
                // "2px yellow bar" - no custom caret needed.
                cursorBrush = SolidColor(colors.yellow),
                lineLimits = TextFieldLineLimits.SingleLine,
                interactionSource = interactionSource,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                decorator = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.text.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = FuelTheme.type.figureL,
                                color = colors.strongHairline,
                            )
                        }
                        inner()
                    }
                },
            )
            if (suffix != null) {
                Text(
                    text = suffix,
                    style = FuelTheme.type.body,
                    color = colors.bodyGrey,
                    modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
                )
            }
        }
    }
}

/**
 * A 52x28 outline with a 22x20 ink knob and no track fill. The whole row is the
 * touch target, and its background crossfades to yellow when on - which is how
 * "full tank" reads at a glance from arm's length.
 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FuelTheme.colors

    val background by animateColorAsState(
        targetValue = if (checked) colors.yellow else colors.paper,
        animationSpec = motionTween(Durations.TOGGLE, EaseOut),
        label = "toggleRowBackground",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = motionTween(Durations.TOGGLE, EaseOut),
        label = "knob",
    )

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .background(background)
            .border(Dimens.hairline, if (checked) colors.ink else colors.frame)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = FuelTheme.type.body,
            color = if (checked) colors.onYellow else colors.ink,
        )
        Box(
            Modifier
                .size(Dimens.toggleWidth, Dimens.toggleHeight)
                .border(Dimens.hairline, colors.ink),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = knobOffset)
                    .size(Dimens.toggleKnobWidth, Dimens.toggleKnobHeight)
                    .background(colors.ink)
            )
        }
    }
}

/** 1px outline, filled yellow when selected, no radius. */
@Composable
fun SquareChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FuelTheme.colors
    Box(
        modifier
            .heightIn(min = 40.dp)
            .background(if (selected) colors.yellow else colors.paper)
            .border(Dimens.hairline, if (selected) colors.ink else colors.frame)
            .toggleable(value = selected, role = Role.RadioButton) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FuelTheme.type.body,
            color = if (selected) colors.onYellow else colors.ink,
        )
    }
}
