package com.fuelexpenselog.app.ui.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.FuelIcon
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.IconBox
import com.fuelexpenselog.app.ui.common.OutlineButton
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.SplitActionBar
import com.fuelexpenselog.app.ui.common.StatCell
import com.fuelexpenselog.app.ui.common.StatStrip
import com.fuelexpenselog.app.ui.common.SunkenPanel
import com.fuelexpenselog.app.ui.common.bottomHairline
import com.fuelexpenselog.app.ui.common.dashOr
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.history.HistoryRowItem
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.consumption.Confidence
import com.fuelexpenselog.domain.consumption.Gap
import com.fuelexpenselog.domain.consumption.GapReason
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.format.perDistanceUnit
import com.fuelexpenselog.domain.model.HistoryEntry
import com.fuelexpenselog.domain.model.Vehicle
import java.text.NumberFormat

class VehicleNavigation(
    val onBack: () -> Unit,
    val onEdit: (Long) -> Unit,
    val onSwitch: (Long) -> Unit,
    val onStatistics: (Long) -> Unit,
    val onMonths: (Long) -> Unit,
    val onHistory: (Long) -> Unit,
    val onOpenEntry: (HistoryEntry) -> Unit,
    val onOpenFillUp: (Long) -> Unit,
    val onAddFillUp: (Long) -> Unit,
    val onAddExpense: (Long) -> Unit,
)

@Composable
fun VehicleRoute(
    navigation: VehicleNavigation,
    viewModel: VehicleViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Deleted from the editor, or gone after a restore: nothing left to show here.
    LaunchedEffect(state.missing) { if (state.missing) navigation.onBack() }
    VehicleScreen(
        state = state,
        navigation = navigation,
        proposals = ProposalCallbacks(
            onCorrect = viewModel::correct,
            onOpen = { navigation.onOpenFillUp(it.eventId) },
            onBridge = viewModel::bridge,
            onReset = viewModel::reset,
            onDismiss = viewModel::dismiss,
        ),
    )
}

/** One vehicle: the last tank, the running figures, what happened lately, and what is due. */
@Composable
fun VehicleScreen(
    state: VehicleUiState,
    navigation: VehicleNavigation,
    proposals: ProposalCallbacks,
) {
    val snapshot = state.snapshot
    val f = LocalFormatters.current
    var switching by rememberSaveable { mutableStateOf(false) }

    FuelScreen {
        if (snapshot == null) return@FuelScreen
        val vehicle = snapshot.vehicle
        VehicleHeader(
            vehicle = vehicle,
            odometer = snapshot.odometerM?.let { f.distance.format(it, vehicle.distanceUnit) },
            canSwitch = state.vehicles.size > 1,
            onBack = navigation.onBack,
            onSwitch = { switching = true },
            onEdit = { navigation.onEdit(vehicle.id) },
        )

        LazyColumn(Modifier.weight(1f)) {
            item(key = "headline") { Headline(state) }
            item(key = "strip") { Strip(state) }
            items(state.proposals, key = { "p${it.eventId}${it.anomaly}" }) { proposal ->
                ProposalCard(
                    proposal = proposal,
                    unit = vehicle.distanceUnit,
                    sameDayConflict = proposal.eventId in state.sameDayConflicts,
                    callbacks = proposals,
                    modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 16.dp),
                )
            }
            item(key = "links") {
                Row(
                    Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 18.dp, bottom = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlineButton(stringResource(R.string.vehicle_statistics), { navigation.onStatistics(vehicle.id) }, Modifier.weight(1f))
                    OutlineButton(stringResource(R.string.vehicle_months), { navigation.onMonths(vehicle.id) }, Modifier.weight(1f))
                }
            }
            item(key = "history-label") {
                SectionLabel(
                    stringResource(R.string.vehicle_history),
                    Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, bottom = 8.dp),
                )
            }
            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.vehicle_no_entries),
                        style = FuelTheme.type.meta,
                        color = FuelTheme.colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .topHairline(FuelTheme.colors.outline)
                            .padding(horizontal = Dimens.gutter, vertical = 16.dp),
                    )
                }
            }
            items(state.rows, key = { row -> row.entry.key() }) { row ->
                HistoryRowItem(row, vehicle, snapshot.format, onClick = { navigation.onOpenEntry(row.entry) })
            }
            if (state.entryCount > state.rows.size) {
                item(key = "see-all") {
                    Text(
                        pluralStringResource(
                            R.plurals.vehicle_see_all,
                            state.entryCount,
                            NumberFormat.getIntegerInstance(f.locale).format(state.entryCount),
                        ),
                        style = FuelTheme.type.button,
                        color = FuelTheme.colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouchTarget)
                            .topHairline(FuelTheme.colors.outline)
                            .clickable(role = Role.Button) { navigation.onHistory(vehicle.id) }
                            .padding(horizontal = Dimens.gutter, vertical = 15.dp),
                    )
                }
            }
            if (state.lastDone.isNotEmpty()) {
                item(key = "last-done") { LastDonePanel(state, vehicle) }
            }
        }

        SplitActionBar(
            primary = stringResource(R.string.garage_add_fillup),
            onPrimary = { navigation.onAddFillUp(vehicle.id) },
            secondary = stringResource(R.string.garage_add_expense),
            onSecondary = { navigation.onAddExpense(vehicle.id) },
        )
    }

    if (switching) {
        VehicleSwitcher(state.vehicles, state.snapshot?.vehicle?.id, onPick = {
            switching = false
            navigation.onSwitch(it)
        }, onDismiss = { switching = false })
    }
}

/** A stable list key across fill-ups and expenses, whose ids overlap. */
private fun HistoryEntry.key(): String = when (this) {
    is HistoryEntry.Fuel -> "f${fillUp.id}"
    is HistoryEntry.Cost -> "e${expense.id}"
}

@Composable
private fun VehicleHeader(
    vehicle: Vehicle,
    odometer: String?,
    canSwitch: Boolean,
    onBack: () -> Unit,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = FuelTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .bottomHairline(colors.outline)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(R.drawable.ic_arrow_back, stringResource(R.string.cd_back), onBack)
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(enabled = canSwitch, role = Role.Button, onClickLabel = stringResource(R.string.cd_switch_vehicle), onClick = onSwitch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                vehicle.name,
                style = FuelTheme.type.subtitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { heading() },
            )
            if (canSwitch) FuelIcon(R.drawable.ic_chevron_down, null, tint = colors.textSecondary)
        }
        if (odometer != null) {
            Text(odometer, style = FuelTheme.type.meta, color = colors.textSecondary)
        }
        Text(
            stringResource(R.string.vehicle_edit),
            style = FuelTheme.type.button,
            color = colors.textPrimary,
            modifier = Modifier
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(role = Role.Button, onClick = onEdit)
                .padding(horizontal = 14.dp, vertical = 15.dp),
        )
    }
}

/**
 * The last tank at Figure XL. No figure is a dash with the reason under it - never a zero,
 * never a stale number standing in for the missing one.
 */
@Composable
private fun Headline(state: VehicleUiState) {
    val snapshot = state.snapshot ?: return
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val format = snapshot.format
    val unit = f.consumption.unitLabel(format)

    Column(
        Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 22.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val latest = state.latest) {
            is Measured -> {
                // Baseline-aligned, so the label sits on the figure's line whatever the glyph.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        f.consumption.value(latest.shownAs(format), format),
                        style = FuelTheme.type.figureXl,
                        color = colors.textPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        stringResource(R.string.vehicle_last_tank, unit),
                        style = FuelTheme.type.body,
                        color = colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                if (latest.confidence == Confidence.AVERAGED) {
                    Text(
                        pluralStringResource(R.plurals.vehicle_covers_fillups, latest.fillUpsSpanned, latest.fillUpsSpanned),
                        style = FuelTheme.type.meta,
                        color = colors.textSecondary,
                    )
                }
                if (!latest.isPlausible) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FuelIcon(R.drawable.ic_warning, null, tint = colors.warningInk)
                        Text(stringResource(R.string.vehicle_implausible), style = FuelTheme.type.meta, color = colors.warningInk)
                    }
                }
            }
            else -> {
                val reason = (latest as? Gap)?.reason ?: GapReason.FIRST_FILL_UP
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dashOr(null), style = FuelTheme.type.figureXl, color = colors.textPrimary, modifier = Modifier.alignByBaseline())
                    Text(unit, style = FuelTheme.type.body, color = colors.textSecondary, modifier = Modifier.alignByBaseline())
                }
                Text(reason.label(), style = FuelTheme.type.meta, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun Strip(state: VehicleUiState) {
    val snapshot = state.snapshot ?: return
    val f = LocalFormatters.current
    val vehicle = snapshot.vehicle
    val summary = snapshot.consumption.summary
    val format = snapshot.format
    val unit = f.distance.unitLabel(vehicle.distanceUnit)
    val month = state.month ?: return

    StatStrip(
        listOf(
            StatCell(
                stringResource(R.string.vehicle_average),
                // Sum over sum across every measured span, never a mean of the bars.
                f.consumption.value(summary?.lifetimeAs(format), format),
            ),
            StatCell(
                stringResource(R.string.vehicle_per, unit),
                dashOr(summary?.costPerKm()?.perDistanceUnit(vehicle.distanceUnit)?.let(f.currency::formatRate)),
            ),
            StatCell(
                f.date.monthName(month),
                f.currency.formatAll(state.monthTotal),
                state.monthDelta?.let { delta ->
                    stringResource(R.string.vehicle_delta_vs, f.delta.format(delta), f.date.monthShort(state.previousMonth!!))
                },
            ),
        ),
    )
}

@Composable
private fun LastDonePanel(state: VehicleUiState, vehicle: Vehicle) {
    val f = LocalFormatters.current
    SunkenPanel(Modifier.padding(top = 18.dp)) {
        SectionLabel(stringResource(R.string.vehicle_last_done))
        state.lastDone.forEach { done ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(done.category.label(), style = FuelTheme.type.body, color = FuelTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                val odometer = done.odometerM
                val ago = done.distanceAgoM
                val value = if (odometer != null && ago != null) {
                    stringResource(
                        R.string.vehicle_last_done_value,
                        f.distance.number(odometer, vehicle.distanceUnit),
                        f.distance.format(ago, vehicle.distanceUnit),
                    )
                } else {
                    f.date.medium(done.date)
                }
                Text(value, style = FuelTheme.type.meta, color = FuelTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun VehicleSwitcher(vehicles: List<Vehicle>, current: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(FuelTheme.colors.background),
        ) {
            SectionLabel(stringResource(R.string.cd_switch_vehicle), Modifier.padding(Dimens.gutter))
            vehicles.forEach { v ->
                Text(
                    v.name,
                    style = FuelTheme.type.body,
                    color = FuelTheme.colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouchTarget)
                        .topHairline(FuelTheme.colors.outline)
                        .clickable(enabled = v.id != current, role = Role.Button) { onPick(v.id) }
                        .padding(horizontal = Dimens.gutter, vertical = 15.dp),
                )
            }
        }
    }
}
