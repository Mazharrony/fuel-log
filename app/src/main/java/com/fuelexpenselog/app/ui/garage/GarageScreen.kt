package com.fuelexpenselog.app.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.FuelIcon
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.SplitActionBar
import com.fuelexpenselog.app.ui.common.bottomHairline
import com.fuelexpenselog.app.ui.common.dashOr
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

@Composable
fun GarageRoute(
    onAddVehicle: () -> Unit,
    onOpenVehicle: (Long) -> Unit,
    onAddFillUp: () -> Unit,
    onAddExpense: () -> Unit,
    viewModel: GarageViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GarageScreen(
        state = state,
        onAddVehicle = onAddVehicle,
        onOpenVehicle = onOpenVehicle,
        onToggleArchived = viewModel::toggleArchived,
        onAddFillUp = onAddFillUp,
        onAddExpense = onAddExpense,
    )
}

/** Home. Every other screen returns here. */
@Composable
fun GarageScreen(
    state: GarageUiState,
    onAddVehicle: () -> Unit,
    onOpenVehicle: (Long) -> Unit,
    onToggleArchived: () -> Unit,
    onAddFillUp: () -> Unit = {},
    onAddExpense: () -> Unit = {},
) {
    FuelScreen {
        GarageHeader()
        when (state) {
            GarageUiState.Loading -> Unit
            is GarageUiState.Ready -> {
                GarageList(state, onAddVehicle, onOpenVehicle, onToggleArchived, Modifier.weight(1f))
                // Entries need a vehicle to belong to; with none active there is nothing to log.
                if (state.active.isNotEmpty()) {
                    SplitActionBar(
                        primary = stringResource(R.string.garage_add_fillup),
                        onPrimary = onAddFillUp,
                        secondary = stringResource(R.string.garage_add_expense),
                        onSecondary = onAddExpense,
                    )
                }
            }
        }
    }
}

@Composable
private fun GarageHeader() {
    val colors = FuelTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.gutter, end = Dimens.gutter, top = 22.dp, bottom = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            stringResource(R.string.garage_title),
            style = FuelTheme.type.title,
            color = colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Box(
                Modifier
                    .size(Dimens.statusDot)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
            Text(
                stringResource(R.string.garage_offline).toUpperCase(Locale.current),
                style = FuelTheme.type.eyebrow,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun GarageList(
    state: GarageUiState.Ready,
    onAddVehicle: () -> Unit,
    onOpenVehicle: (Long) -> Unit,
    onToggleArchived: () -> Unit,
    modifier: Modifier,
) {
    val colors = FuelTheme.colors
    LazyColumn(modifier.fillMaxWidth()) {
        if (state.active.isEmpty() && state.archived.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.garage_empty),
                    style = FuelTheme.type.bodyRegular,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, bottom = 20.dp),
                )
            }
        }
        items(state.active, key = { it.vehicle.id }) { item ->
            VehicleBlock(item, onClick = { onOpenVehicle(item.vehicle.id) })
        }
        item(key = "add") {
            AddVehicleRow(onAddVehicle)
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived-toggle") {
                ArchivedToggle(state.showArchived, state.archived.size, onToggleArchived)
            }
            if (state.showArchived) {
                items(state.archived, key = { "a${it.vehicle.id}" }) { item ->
                    VehicleBlock(item, archived = true, onClick = { onOpenVehicle(item.vehicle.id) })
                }
            }
        }
    }
}

/**
 * One vehicle: name and meta on the left, the headline figure on the right. Until two full
 * tanks exist there is no figure, and the block says so rather than showing a zero.
 */
@Composable
private fun VehicleBlock(item: GarageVehicle, onClick: () -> Unit, archived: Boolean = false) {
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    val f = LocalFormatters.current
    val vehicle = item.vehicle

    val meta = listOfNotNull(
        item.odometerM?.let { f.distance.format(it, vehicle.distanceUnit) },
        vehicle.fuelType.label(),
        if (archived) stringResource(R.string.garage_archived) else null,
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .topHairline(colors.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.gutter, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    vehicle.name,
                    style = type.subtitle,
                    color = if (archived) colors.textSecondary else colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(meta, style = type.meta, color = colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(dashOr(null), style = type.figureRow, color = colors.textPrimary)
                Text(
                    stringResource(R.string.garage_needs_two_full_tanks),
                    style = type.meta,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun AddVehicleRow(onClick: () -> Unit) {
    val colors = FuelTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .topHairline(colors.outline)
            .bottomHairline(colors.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.gutter, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        FuelIcon(R.drawable.ic_plus, contentDescription = null, tint = colors.textSecondary)
        Text(stringResource(R.string.garage_add_vehicle), style = FuelTheme.type.button, color = colors.textSecondary)
    }
}

@Composable
private fun ArchivedToggle(showing: Boolean, count: Int, onClick: () -> Unit) {
    val text = if (showing) {
        stringResource(R.string.garage_hide_archived)
    } else {
        pluralStringResource(R.plurals.garage_show_archived, count, count)
    }
    Text(
        text,
        style = FuelTheme.type.meta,
        color = FuelTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.gutter, vertical = 15.dp),
    )
}
