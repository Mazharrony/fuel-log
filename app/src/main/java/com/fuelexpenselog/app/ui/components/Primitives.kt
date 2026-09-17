package com.fuelexpenselog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/** A 1px rule. The only structure this design has. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color? = null) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Dimens.hairline)
            .background(color ?: FuelTheme.colors.hairline)
    )
}

@Composable
fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(modifier.width(Dimens.hairline).background(FuelTheme.colors.hairline))
}

/** 10sp, uppercase, wide tracking. Sits above a figure or a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text.uppercase(),
        style = FuelTheme.type.label,
        color = color ?: FuelTheme.colors.labelInk,
        modifier = modifier,
    )
}

/** The one recessed panel a screen is allowed. */
@Composable
fun SunkenPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(FuelTheme.colors.sunken)
            .padding(horizontal = Dimens.gutter, vertical = 18.dp),
        content = content,
    )
}

/**
 * Empty states explain themselves.
 *
 * "Needs two full tanks" is the important one: a vehicle with a single fill-up
 * legitimately has no consumption figure, and a bare dash there reads as a bug
 * rather than as the app being careful.
 */
@Composable
fun EmptyState(
    title: String,
    explanation: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gutter, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = FuelTheme.type.body,
            color = FuelTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = explanation,
            style = FuelTheme.type.bodyRegular,
            color = FuelTheme.colors.bodyGrey,
            textAlign = TextAlign.Center,
        )
    }
}

/** A labelled value on one line: label left, figure right. */
@Composable
fun TotalRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = FuelTheme.type.bodyRegular,
                color = FuelTheme.colors.bodyGrey,
            )
            Text(
                text = value,
                style = if (emphasised) FuelTheme.type.figureM else FuelTheme.type.body,
                color = FuelTheme.colors.ink,
            )
        }
    }
}
