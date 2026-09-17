package com.fuelexpenselog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * What the 3dp bar down the left edge of a row means.
 *
 * Colour alone would fail for a colour-blind user and in greyscale mode, so the
 * accessibility description always names the type in words too.
 */
enum class EntryKind(val label: String) {
    /** A fill-up that produced a usable consumption figure. */
    FULL_TANK("Fill-up"),

    /** A partial fill: real, but it cannot close a span on its own. */
    PARTIAL("Partial fill-up"),

    EXPENSE("Expense"),
}

/**
 * One history row: 13dp padding, a hairline above, title and meta on the left,
 * value and rate right-aligned.
 *
 * Merged semantics, so a screen reader announces the row as a single item
 * instead of five disconnected fragments.
 */
@Composable
fun EntryRow(
    kind: EntryKind,
    title: String,
    meta: String,
    value: String,
    modifier: Modifier = Modifier,
    rate: String? = null,
    flagged: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = FuelTheme.colors
    val barColor = when (kind) {
        EntryKind.FULL_TANK -> colors.yellow
        EntryKind.PARTIAL -> colors.strongHairline
        EntryKind.EXPENSE -> colors.ink
    }

    val spoken = buildString {
        append(kind.label); append(", ")
        append(title); append(", ")
        append(meta); append(", ")
        append(value)
        rate?.let { append(", "); append(it) }
        if (flagged) append(", figure looks unusual")
    }

    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = spoken }
    ) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .padding(vertical = Dimens.rowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(Dimens.entryBar)
                    .height(34.dp)
                    .background(barColor)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = FuelTheme.type.body, color = colors.ink)
                Text(meta, style = FuelTheme.type.meta, color = colors.bodyGrey)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = value,
                    style = FuelTheme.type.body,
                    color = colors.ink,
                    textAlign = TextAlign.End,
                )
                if (rate != null) {
                    Text(
                        text = if (flagged) "$rate  !" else rate,
                        style = FuelTheme.type.meta,
                        // Flagged, never suppressed: a hidden number reads as a
                        // bug, a marked one reads as the app paying attention.
                        color = if (flagged) colors.destructive else colors.bodyGrey,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/**
 * Three equal cells divided by verticals, bounded above and below by hairlines.
 * Label above, figure below.
 */
@Composable
fun StatStrip(
    cells: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    valueColors: List<Color?> = emptyList(),
) {
    Column(modifier.fillMaxWidth()) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(72.dp)) {
            cells.forEachIndexed { index, (label, value) ->
                if (index > 0) VerticalHairline(Modifier.fillMaxHeight())
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SectionLabel(label)
                    Text(
                        text = value,
                        style = FuelTheme.type.figureS,
                        color = valueColors.getOrNull(index) ?: FuelTheme.colors.ink,
                    )
                }
            }
        }
        Hairline()
    }
}
