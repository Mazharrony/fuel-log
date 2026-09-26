package com.fuelexpenselog.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.format.Rounding

/** Every screen's frame: paper ground, content kept clear of the system bars. */
@Composable
fun FuelScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FuelTheme.colors.background)
            .systemBarsPadding(),
        content = content,
    )
}

/** A 1dp rule. Structure in this design comes from these, never from cards or shadows. */
@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    color: Color = FuelTheme.colors.outline,
    thickness: Dp = Dimens.hairline,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color),
    )
}

/** Draws a hairline along the top edge without taking layout space. */
fun Modifier.topHairline(color: Color, width: Dp = Dimens.hairline): Modifier = drawBehind {
    val stroke = width.toPx()
    drawLine(color, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
}

fun Modifier.bottomHairline(color: Color, width: Dp = Dimens.hairline): Modifier = drawBehind {
    val stroke = width.toPx()
    drawLine(color, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
}

/** 10sp uppercase label above a field or a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = FuelTheme.colors.textSecondary) {
    Text(
        text = text.toUpperCase(Locale.current),
        style = FuelTheme.type.label,
        color = color,
        modifier = modifier,
    )
}

/** A stroke glyph from the app's own set - there is no icon library. */
@Composable
fun FuelIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = FuelTheme.colors.textPrimary,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(Dimens.icon),
    )
}

/** A glyph-only button: a 48dp target around a 20dp glyph, and never without a description. */
@Composable
fun IconBox(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = FuelTheme.colors.textPrimary,
) {
    Box(
        modifier = modifier
            .size(Dimens.minTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FuelIcon(icon, contentDescription, tint = if (enabled) tint else FuelTheme.colors.outlineStrong)
    }
}

/** A figure, or the em dash that means "not known". Never a zero standing in for one. */
fun dashOr(value: String?): String = value ?: Rounding.EM_DASH
