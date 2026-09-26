package com.fuelexpenselog.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * An equal-width row of options for a short, fixed set - units, tags, formats. Each option
 * is a radio button to TalkBack and at least 48dp tall; the selected one is filled yellow.
 * Every option stretches to the tallest, so a label that wraps at a large font scale does
 * not leave its neighbours short.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FuelTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = Dimens.minTouchTarget)
                    .border(Dimens.hairline, if (enabled) colors.textPrimary else colors.outlineStrong)
                    .background(if (isSelected) colors.primary else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = FuelTheme.type.button,
                    color = if (isSelected) colors.onPrimary else colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
