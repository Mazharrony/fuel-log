package com.fuelexpenselog.app.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * Back arrow, title, and an optional right-hand slot, over a hairline.
 *
 * The arrow's box starts 8dp in so the drawn glyph - 14dp inside its 48dp target - lands on
 * the 22dp gutter like every other left edge on the screen.
 */
@Composable
fun NavHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = FuelTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .bottomHairline(colors.outline)
            .padding(start = if (onBack != null) 8.dp else Dimens.gutter, end = Dimens.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconBox(R.drawable.ic_arrow_back, stringResource(R.string.cd_back), onBack)
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = title,
            style = FuelTheme.type.subtitle,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke(this)
    }
}
