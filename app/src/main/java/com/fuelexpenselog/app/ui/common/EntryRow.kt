package com.fuelexpenselog.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/** What the 3dp bar down the left of a row says about the entry. */
enum class EntryBar {
    /** A fill-up that produced a consumption figure. */
    FUEL,

    /** A fill-up with no figure of its own: a partial, or the first tank. */
    PARTIAL,

    EXPENSE,
    NONE,
}

/**
 * The row used by history, garage, settings and months: title and meta on the left, value
 * and rate on the right, a hairline above. Height comes from the content, never a fixed dp,
 * so it grows with the user's font scale instead of clipping.
 */
@Composable
fun EntryRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    value: String? = null,
    rate: String? = null,
    bar: EntryBar = EntryBar.NONE,
    background: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
) {
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    val barColor = when (bar) {
        EntryBar.FUEL -> colors.primary
        EntryBar.PARTIAL -> colors.outlineStrong
        EntryBar.EXPENSE -> colors.textPrimary
        EntryBar.NONE -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(background)
            .topHairline(colors.outline)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Box(
            Modifier
                .width(Dimens.entryBar)
                .fillMaxHeight()
                .background(barColor),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = Dimens.gutter - Dimens.entryBar,
                    end = Dimens.gutter,
                    top = Dimens.rowPadding,
                    bottom = Dimens.rowPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = type.body, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta != null) {
                    Text(meta, style = type.meta, color = colors.textSecondary)
                }
            }
            if (value != null || rate != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (value != null) {
                        Text(value, style = type.body, color = colors.textPrimary, textAlign = TextAlign.End)
                    }
                    if (rate != null) {
                        Text(rate, style = type.meta, color = colors.textSecondary, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}
