package com.fuelexpenselog.app.ui.months

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.IconBox
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.dashOr
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.time.MonthKey

@Composable
fun MonthsRoute(
    onBack: () -> Unit,
    onOpenMonth: (MonthKey) -> Unit,
    viewModel: MonthsViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    MonthsScreen(state, onBack, viewModel::previousYear, viewModel::nextYear, onOpenMonth)
}

@Composable
fun MonthsScreen(
    state: MonthsUiState,
    onBack: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onOpenMonth: (MonthKey) -> Unit,
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val type = FuelTheme.type

    FuelScreen {
        NavHeader(stringResource(R.string.months_title), onBack) {
            IconBox(
                R.drawable.ic_chevron_right,
                stringResource(R.string.cd_previous_year),
                onPreviousYear,
                enabled = state.canGoBack,
                modifier = Modifier.rotate(180f),
            )
            Text(state.year.toString(), style = type.button, color = colors.textPrimary)
            IconBox(R.drawable.ic_chevron_right, stringResource(R.string.cd_next_year), onNextYear, enabled = state.canGoForward)
        }
        if (state.snapshot == null) return@FuelScreen

        LazyColumn(Modifier.weight(1f)) {
            item(key = "summary") {
                Row(
                    Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 16.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        SectionLabel(stringResource(R.string.months_average))
                        Text(dashOr(state.monthlyAverage?.let(f.currency::format)), style = type.figureM, color = colors.textPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        SectionLabel(stringResource(R.string.months_highest))
                        Text(dashOr(state.highest?.let { f.date.monthShort(it.month) }), style = type.body, color = colors.textPrimary)
                    }
                }
            }
            item(key = "legend") {
                Row(
                    Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Legend(colors.primary, stringResource(R.string.months_legend_fuel))
                    Legend(colors.textPrimary, stringResource(R.string.months_legend_other))
                }
            }
            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.months_empty, state.year),
                        style = type.meta,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .topHairline(colors.outline)
                            .padding(Dimens.gutter),
                    )
                }
            }
            items(state.rows, key = { it.bucket.month.value }) { row ->
                MonthRowItem(row, isCurrent = row.bucket.month == state.currentMonth, scale = state.scaleMicros, onClick = { onOpenMonth(row.bucket.month) })
            }
            item(key = "footer") {
                Text(
                    stringResource(R.string.months_footer),
                    style = type.meta,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .topHairline(colors.outline)
                        .background(colors.surfaceAlt)
                        .padding(horizontal = Dimens.gutter, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .background(color),
        )
        Text(label, style = FuelTheme.type.eyebrow, color = FuelTheme.colors.textSecondary)
    }
}

/** A month: its label, its change, its total, and the fuel/everything-else split bar. */
@Composable
private fun MonthRowItem(row: MonthRow, isCurrent: Boolean, scale: Long, onClick: () -> Unit) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val bucket = row.bucket
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) colors.warningWash else Color.Transparent)
            .topHairline(colors.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(f.date.monthName(bucket.month), style = FuelTheme.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            row.delta?.let { Text(f.delta.format(it), style = FuelTheme.type.meta, color = colors.textSecondary) }
            Text(f.currency.formatAll(bucket.total), style = FuelTheme.type.body, color = colors.textPrimary)
        }
        // Scaled against the year's highest month; with mixed currencies there is no honest
        // common scale, so the bar is left out rather than drawn wrong.
        if (scale > 0 && bucket.total.size == 1) {
            val fuel = bucket.fuel.firstOrNull()?.micros ?: 0
            val other = bucket.other.firstOrNull()?.micros ?: 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.splitBar)
                    .background(colors.outline),
            ) {
                if (fuel > 0) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fuel.toFloat() / scale)
                            .background(colors.primary),
                    )
                }
                if (other > 0) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(other.toFloat() / (scale - fuel).coerceAtLeast(1))
                            .background(colors.textPrimary),
                    )
                }
            }
        }
    }
}
