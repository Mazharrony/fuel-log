package com.fuelexpenselog.app.ui.months

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.IconBox
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.OutlineButton
import com.fuelexpenselog.app.ui.common.PrimaryButton
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.SunkenPanel
import com.fuelexpenselog.app.ui.common.dashOr
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.time.MonthKey
import java.text.NumberFormat

@Composable
fun MonthDetailRoute(
    onBack: () -> Unit,
    onMonth: (MonthKey) -> Unit,
    onSeeEntries: (MonthKey) -> Unit,
    onExport: ((MonthKey) -> Unit)? = null,
    viewModel: MonthDetailViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    MonthDetailScreen(state, onBack, onMonth, onSeeEntries, onExport)
}

@Composable
fun MonthDetailScreen(
    state: MonthDetailUiState,
    onBack: () -> Unit,
    onMonth: (MonthKey) -> Unit,
    onSeeEntries: (MonthKey) -> Unit,
    onExport: ((MonthKey) -> Unit)?,
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    val month = state.month

    FuelScreen {
        NavHeader(f.date.monthYear(month), onBack) {
            IconBox(
                R.drawable.ic_chevron_right,
                stringResource(R.string.cd_previous_month),
                { onMonth(month.minusMonths(1)) },
                modifier = Modifier.rotate(180f),
            )
            // The next month is disabled at the current one: the future has no entries yet.
            IconBox(
                R.drawable.ic_chevron_right,
                stringResource(R.string.cd_next_month),
                { onMonth(month.plusMonths(1)) },
                enabled = !state.isCurrentMonth,
            )
        }
        val snapshot = state.snapshot ?: return@FuelScreen
        val bucket = state.bucket

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 22.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (bucket == null) dashOr(null) else f.currency.formatAll(bucket.total),
                        style = type.figureXxl,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    state.delta?.let {
                        Text(f.delta.format(it), style = type.meta, color = colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                val number = NumberFormat.getIntegerInstance(f.locale)
                val meta = if (bucket == null) {
                    stringResource(R.string.month_empty)
                } else {
                    listOfNotNull(
                        pluralStringResource(R.plurals.month_entries, bucket.entryCount, number.format(bucket.entryCount)),
                        pluralStringResource(R.plurals.month_fillups, bucket.fillUpCount, number.format(bucket.fillUpCount)),
                        bucket.distanceM?.let { stringResource(R.string.month_driven, f.distance.format(it, snapshot.vehicle.distanceUnit)) },
                    ).joinToString(" · ")
                }
                Text(meta, style = type.meta, color = colors.textSecondary)
            }

            if (state.categories.isNotEmpty()) {
                SectionLabel(
                    stringResource(R.string.month_by_category),
                    Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, bottom = 10.dp),
                )
                val scale = state.categories.mapNotNull { it.subtotal.singleOrNull()?.micros }.maxOrNull() ?: 0L
                state.categories.forEach { row ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .topHairline(colors.outline)
                            .padding(horizontal = Dimens.gutter, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                row.category?.label() ?: stringResource(R.string.fuel),
                                style = type.body,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(row.count.toString(), style = type.meta, color = colors.textSecondary)
                            Text(f.currency.formatAll(row.subtotal), style = type.body, color = colors.textPrimary)
                        }
                        val micros = row.subtotal.singleOrNull()?.micros
                        if (micros != null && scale > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(Dimens.categoryBar)
                                    .background(colors.outline),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(micros.toFloat() / scale)
                                        .background(if (row.category == null) colors.primary else colors.textPrimary),
                                )
                            }
                        }
                    }
                }
            }
        }

        SunkenPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.month_last_year), style = type.meta, color = colors.textSecondary, modifier = Modifier.weight(1f))
                Text(dashOr(state.lastYear?.let { f.currency.formatAll(it.total) }), style = type.body, color = colors.textPrimary)
            }
            if (bucket != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        pluralStringResource(R.plurals.month_see_entries, bucket.entryCount, NumberFormat.getIntegerInstance(f.locale).format(bucket.entryCount)),
                        { onSeeEntries(month) },
                        Modifier.weight(1f),
                    )
                    if (onExport != null) {
                        PrimaryButton(stringResource(R.string.month_export), { onExport(month) }, Modifier.weight(1f), textStyle = type.button)
                    }
                }
            }
        }
    }
}
