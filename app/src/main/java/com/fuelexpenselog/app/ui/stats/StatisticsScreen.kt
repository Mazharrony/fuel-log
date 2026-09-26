package com.fuelexpenselog.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.chart.ConsumptionChart
import com.fuelexpenselog.app.ui.common.DarkSystemBars
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.dashOr
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelLogTheme
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.consumption.Gap

@Composable
fun StatisticsRoute(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    StatisticsScreen(state, onBack)
}

/** Always dark, whatever the phone's theme: a chart reads best on ink. */
@Composable
fun StatisticsScreen(state: StatisticsUiState, onBack: () -> Unit) {
    DarkSystemBars()
    FuelLogTheme(darkTheme = true) {
        StatisticsContent(state, onBack)
    }
}

@Composable
private fun StatisticsContent(state: StatisticsUiState, onBack: () -> Unit) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    val snapshot = state.snapshot

    FuelScreen {
        NavHeader(stringResource(R.string.stats_title), onBack)
        if (snapshot == null) return@FuelScreen
        val format = snapshot.format
        val vehicle = snapshot.vehicle

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionLabel(stringResource(R.string.stats_header, Dimens.CHART_BARS))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        f.consumption.value(state.recent.kmPerUnit?.let(format::fromKmPerUnit), format),
                        style = type.figureChart,
                        color = colors.textPrimary,
                    )
                    Text(
                        stringResource(R.string.stats_average, f.consumption.unitLabel(format)),
                        style = type.meta,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }

            if (state.recent.points.isEmpty()) {
                Text(
                    stringResource(R.string.stats_no_spans),
                    style = type.meta,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Dimens.gutter),
                )
            } else {
                ConsumptionChart(
                    points = state.recent.points,
                    format = format,
                    modifier = Modifier.padding(horizontal = Dimens.gutter),
                )
                // Every stub says why it is a stub, right under the chart.
                val gaps = state.recent.points.filterIsInstance<Gap>()
                if (gaps.isNotEmpty()) {
                    Column(
                        Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        gaps.forEach { gap ->
                            Text(
                                stringResource(R.string.stats_gap_line, f.date.numeric(gap.endDate), gap.reason.label()),
                                style = type.meta,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }

            Column(Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 26.dp)) {
                TotalRow(stringResource(R.string.stats_fuel), f.currency.formatAll(state.fuel))
                TotalRow(stringResource(R.string.stats_other), f.currency.formatAll(state.other))
                TotalRow(stringResource(R.string.stats_total), f.currency.formatAll(state.total))
                TotalRow(
                    stringResource(R.string.stats_cost_per, f.distance.unitLabel(vehicle.distanceUnit)),
                    dashOr(state.costPerDistance?.let(f.currency::formatRate)),
                )
                TotalRow(
                    stringResource(R.string.stats_distance),
                    dashOr(state.distanceLoggedM?.let { f.distance.format(it, vehicle.distanceUnit) }),
                )
            }

            Column(
                Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .topHairline(colors.outline),
            ) {
                SectionLabel(
                    stringResource(R.string.stats_month_on_month),
                    Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 14.dp, bottom = 10.dp),
                )
                Row(
                    Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    listOfNotNull(state.thisMonth, state.lastMonth).forEachIndexed { i, month ->
                        val label = if (i == 0) {
                            stringResource(R.string.stats_to_date, f.date.monthName(month.month))
                        } else {
                            f.date.monthName(month.month)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, style = type.meta, color = colors.textSecondary)
                            Text(f.consumption.value(month.value, format), style = type.figureS, color = colors.textPrimary)
                            month.delta?.let { delta ->
                                Text(
                                    f.delta.format(delta),
                                    style = type.delta,
                                    // On ink, yellow can mark the better month; it is never text on paper.
                                    color = if (delta.improved) colors.primary else colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .topHairline(FuelTheme.colors.outline)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = FuelTheme.type.bodyRegular, color = FuelTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = FuelTheme.type.body, color = FuelTheme.colors.textPrimary)
    }
}
