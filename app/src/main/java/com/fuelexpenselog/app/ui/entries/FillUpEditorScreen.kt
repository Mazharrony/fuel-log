package com.fuelexpenselog.app.ui.entries

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.domain.units.ConsumptionConvention
import com.fuelexpenselog.app.ui.components.ActionBar
import com.fuelexpenselog.app.ui.components.FigureInput
import com.fuelexpenselog.app.ui.components.FigureSize
import com.fuelexpenselog.app.ui.components.Hairline
import com.fuelexpenselog.app.ui.components.PrimaryAction
import com.fuelexpenselog.app.ui.components.SectionLabel
import com.fuelexpenselog.app.ui.components.ToggleRow
import com.fuelexpenselog.app.ui.components.VerticalHairline
import com.fuelexpenselog.app.ui.components.WarningBlock
import com.fuelexpenselog.app.ui.format.DOT
import com.fuelexpenselog.app.ui.format.EM_DASH
import com.fuelexpenselog.app.ui.format.fieldLabel
import com.fuelexpenselog.app.ui.format.formatConsumption
import com.fuelexpenselog.app.ui.format.formatDistance
import com.fuelexpenselog.app.ui.format.formatMoney
import com.fuelexpenselog.app.ui.format.formatShortDate
import com.fuelexpenselog.app.ui.format.formatVolume
import com.fuelexpenselog.app.ui.format.shortLabel
import com.fuelexpenselog.app.ui.format.unitLabel
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * Screen 08. Used standing at a pump, one-handed, often in poor light,
 * sometimes in the rain.
 *
 * Everything on it follows from that: the date is already today, the cursor is
 * already in the odometer, the keypad is already numeric, full tank is already
 * on, and the two optional fields are behind a disclosure so nothing optional
 * sits above the fold. Three taps for the common case.
 */
@Composable
fun FillUpEditorScreen(
    viewModel: FillUpEditorViewModel,
    convention: ConsumptionConvention,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = FuelTheme.colors
    val vehicle = state.vehicle
    val focus = remember { FocusRequester() }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    // Warnings and the live "this tank" figures recompute as the user types.
    LaunchedEffect(viewModel) {
        snapshotFlow {
            Triple(
                viewModel.odometer.text.toString(),
                viewModel.volume.text.toString(),
                viewModel.totalPaid.text.toString(),
            )
        }.collect { viewModel.onFieldChanged() }
    }

    // Cursor lands in the odometer, keypad up, without the user touching anything.
    LaunchedEffect(vehicle) {
        if (vehicle != null && viewModel.odometer.text.isEmpty()) {
            runCatching { focus.requestFocus() }
        }
    }

    if (vehicle == null) {
        Box(modifier.fillMaxSize().background(colors.paper))
        return
    }

    val warnings = state.validation.warnings.map { warning ->
        // Capacity is stored in litres but must read in the vehicle's own unit.
        val capacity = vehicle.tankCapacityLitres?.let { litres ->
            "${formatVolume(litres, vehicle.volumeUnit)} ${vehicle.volumeUnit.shortLabel()}"
        }
        warning.message(capacity)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        // Nav header
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(Dimens.minTouchTarget)
                            .clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = colors.ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = if (state.isEditing) "Edit fill-up" else "Fill-up",
                        style = FuelTheme.type.subtitle,
                        color = colors.ink,
                    )
                }
                Text(
                    text = "Today $DOT ${formatShortDate(state.dateMillis)}",
                    style = FuelTheme.type.bodyRegular,
                    color = colors.metaGrey,
                )
            }
            Hairline()
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Odometer, at figure scale, with the last reading as a hint.
            Column {
                FigureInput(
                    label = "Odometer $DOT ${vehicle.distanceUnit.shortLabel()}",
                    state = viewModel.odometer,
                    modifier = Modifier.focusRequester(focus),
                )
                if (state.previousOdometerKm != null) {
                    Text(
                        // A hint, deliberately not a pre-filled value: pre-filling
                        // invites a save that silently repeats the last reading.
                        text = "Last reading ${formatDistance(state.previousOdometerKm!!, vehicle.distanceUnit)}",
                        style = FuelTheme.type.meta,
                        color = colors.labelInk,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                FigureInput(
                    label = vehicle.volumeUnit.fieldLabel(),
                    state = viewModel.volume,
                    modifier = Modifier.weight(1f),
                    figureSize = FigureSize.Medium,
                )
                FigureInput(
                    label = "Total paid",
                    state = viewModel.totalPaid,
                    modifier = Modifier.weight(1f),
                    figureSize = FigureSize.Medium,
                    imeAction = ImeAction.Done,
                )
            }

            ToggleRow(
                label = "Full tank",
                hint = if (state.isFullTank) {
                    "Consumption is calculated from this entry"
                } else {
                    "Partial fill $EM_DASH no figure until the next full tank"
                },
                checked = state.isFullTank,
                onCheckedChange = viewModel::setFullTank,
            )

            WarningBlock(messages = warnings)

            // Everything optional lives behind this, so the three-tap path stays
            // three taps.
            MoreDisclosure(
                expanded = state.showMore,
                onToggle = viewModel::toggleMore,
                missedEntry = state.isMissedEntry,
                onMissedEntryChange = viewModel::setMissedEntry,
                fuelTypeState = viewModel.fuelType,
                noteState = viewModel.note,
            )

            Box(Modifier.height(8.dp))
        }

        Column(Modifier.imePadding().navigationBarsPadding()) {
            // Two-cell summary: what this tank actually did.
            Hairline()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                SummaryCell(
                    label = "This tank",
                    value = state.thisTankKmPerLitre
                        ?.let { "${formatConsumption(it, convention)} ${convention.unitLabel()}" }
                        ?: EM_DASH,
                    modifier = Modifier.weight(1f),
                )
                VerticalHairline(Modifier.fillMaxHeight())
                SummaryCell(
                    label = "Cost per ${vehicle.distanceUnit.shortLabel()}",
                    value = state.thisTankCostPerKm
                        ?.let { formatMoney(costPerDisplayUnit(it, vehicle), vehicle.currency) }
                        ?: EM_DASH,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .border(Dimens.hairline, colors.ink)
                        .clickable(role = Role.Button, onClick = viewModel::toggleMore),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ic_plus),
                        contentDescription = "Add a note or fuel type",
                        tint = colors.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(if (state.validation.canSave) colors.yellow else colors.sunken)
                        .clickable(
                            enabled = state.validation.canSave,
                            role = Role.Button,
                            onClick = viewModel::save,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.isEditing) "Save changes" else "Save fill-up",
                        style = FuelTheme.type.body,
                        color = if (state.validation.canSave) colors.onYellow else colors.bodyGrey,
                    )
                }
            }
        }
    }
}

private fun costPerDisplayUnit(
    costPerKm: Double,
    vehicle: com.fuelexpenselog.app.domain.model.Vehicle,
): Double = when (vehicle.distanceUnit) {
    com.fuelexpenselog.app.domain.model.DistanceUnit.KILOMETRE -> costPerKm
    com.fuelexpenselog.app.domain.model.DistanceUnit.MILE ->
        costPerKm * com.fuelexpenselog.app.domain.units.Units.KM_PER_MILE
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Note the 0.14em tracking here against 0.18em on form labels above -
        // that difference is in the design source, not an accident.
        Text(
            text = label.uppercase(),
            style = FuelTheme.type.eyebrow,
            color = FuelTheme.colors.labelInk,
        )
        Text(value, style = FuelTheme.type.figureS, color = FuelTheme.colors.ink)
    }
}

@Composable
private fun MoreDisclosure(
    expanded: Boolean,
    onToggle: () -> Unit,
    missedEntry: Boolean,
    onMissedEntryChange: (Boolean) -> Unit,
    fuelTypeState: androidx.compose.foundation.text.input.TextFieldState,
    noteState: androidx.compose.foundation.text.input.TextFieldState,
) {
    val colors = FuelTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (expanded) "Less" else "+ More",
                style = FuelTheme.type.body,
                color = colors.bodyGrey,
            )
        }

        if (expanded) {
            // isMissedEntry is the one that matters here. It breaks the
            // consumption chain, so getting it wrong silently corrupts every
            // figure downstream - which is why it is an explicit control and not
            // an inference.
            ToggleRow(
                label = "I missed a fill-up before this",
                hint = "Breaks the chain, so the next figure shows a dash instead of a wrong number",
                checked = missedEntry,
                onCheckedChange = onMissedEntryChange,
            )
            FigureInput(
                label = "Fuel type $DOT optional",
                state = fuelTypeState,
                figureSize = FigureSize.Small,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
            )
            FigureInput(
                label = "Note $DOT optional",
                state = noteState,
                figureSize = FigureSize.Small,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                imeAction = ImeAction.Done,
            )
        }
    }
}
