package com.fuelexpenselog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * Equal-width segments sharing one outline, selected segment filled yellow.
 *
 * Used instead of a wrapping chip group wherever the option set is fixed and
 * short: Distance (2-up), Volume (3-up), and "Consumption shown as" (4-up).
 * Chips remain for the twelve expense categories, which wrap.
 *
 * The internal dividers are 1dp Boxes rather than borders, matching the design
 * source - a border on each segment would double up at every seam.
 */
@Composable
fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = FuelTheme.colors

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .height(IntrinsicSize.Min)
            .border(Dimens.hairline, colors.frame)
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(Dimens.hairline)
                        .fillMaxHeight()
                        .background(colors.frame)
                )
            }
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) colors.yellow else colors.paper)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .padding(horizontal = 6.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = FuelTheme.type.body,
                    color = if (isSelected) colors.onYellow else colors.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
