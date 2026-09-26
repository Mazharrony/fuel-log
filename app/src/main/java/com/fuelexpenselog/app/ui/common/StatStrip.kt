package com.fuelexpenselog.app.ui.common

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

data class StatCell(
    val label: String,
    val value: String,
    /** The month cell's delta line. */
    val note: String? = null,
)

/**
 * Equal cells between vertical rules, bounded by hairlines. Each cell is read as one unit
 * by TalkBack: "Average, 8.3" rather than two unrelated fragments.
 */
@Composable
fun StatStrip(cells: List<StatCell>, modifier: Modifier = Modifier) {
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .topHairline(colors.outline)
            .bottomHairline(colors.outline),
    ) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(Dimens.hairline)
                        .fillMaxHeight()
                        .background(colors.outline),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {}
                    .padding(
                        start = if (index == 0) Dimens.gutter else 14.dp,
                        end = 10.dp,
                        top = 14.dp,
                        bottom = 14.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionLabel(cell.label)
                Text(cell.value, style = type.figureS, color = colors.textPrimary, maxLines = 1)
                if (cell.note != null) {
                    Text(cell.note, style = type.meta, color = colors.textSecondary)
                }
            }
        }
    }
}
