package com.fuelexpenselog.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/** The one grouped panel a screen is allowed: year totals, "last done at", a footer. */
@Composable
fun SunkenPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FuelTheme.colors.surfaceAlt)
            .topHairline(FuelTheme.colors.outline)
            .padding(horizontal = Dimens.gutter, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
