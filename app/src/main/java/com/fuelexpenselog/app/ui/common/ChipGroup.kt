package com.fuelexpenselog.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * A wrapping group of single-choice chips, for sets too long for a segmented row: expense
 * categories, fuel types. Outlined at rest, yellow with an ink edge when selected.
 */
@Composable
fun <T> ChipGroup(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = FuelTheme.colors
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .heightIn(min = Dimens.minTouchTarget)
                    .border(Dimens.hairline, if (isSelected) colors.textPrimary else colors.outlineStrong)
                    .background(if (isSelected) colors.primary else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = FuelTheme.type.chip,
                    color = if (isSelected) colors.onPrimary else colors.textPrimary,
                )
            }
        }
    }
}
