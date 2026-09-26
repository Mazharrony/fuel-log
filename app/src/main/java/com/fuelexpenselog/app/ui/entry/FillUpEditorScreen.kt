package com.fuelexpenselog.app.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.DiscardDialog
import com.fuelexpenselog.app.ui.common.ErrorDialog
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.Note
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.ToggleRow
import com.fuelexpenselog.app.ui.common.UnderlineField
import com.fuelexpenselog.app.ui.common.WarningBlock
import com.fuelexpenselog.app.ui.common.dashOr
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.common.longLabel
import com.fuelexpenselog.app.ui.common.message
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.consumption.Gap
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.format.perDistanceUnit
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.time.CivilDate

@Composable
fun FillUpEditorRoute(
    onDone: () -> Unit,
    viewModel: FillUpEditorViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    FillUpEditorScreen(
        state = state,
        onBack = onDone,
        actions = FillUpEditorActions(
            onVehicle = viewModel::onVehicle,
            onOdometer = viewModel::onOdometer,
            onVolume = viewModel::onVolume,
            onTotal = viewModel::onTotal,
            onFull = viewModel::onFull,
            onMissedPrevious = viewModel::onMissedPrevious,
            onDate = viewModel::onDate,
            onTag = viewModel::onTag,
            onStation = viewModel::onStation,
            onNote = viewModel::onNote,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismissError = viewModel::dismissError,
        ),
    )
}

class FillUpEditorActions(
    val onVehicle: (Long) -> Unit,
    val onOdometer: (String) -> Unit,
    val onVolume: (String) -> Unit,
    val onTotal: (String) -> Unit,
    val onFull: (Boolean) -> Unit,
    val onMissedPrevious: (Boolean) -> Unit,
    val onDate: (CivilDate) -> Unit,
    val onTag: (EntryTag) -> Unit,
    val onStation: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Log a fill-up at the pump in three taps: odometer, volume, total. The date is today, the
 * tank is full, and the keypad is already up on the odometer.
 */
@Composable
fun FillUpEditorScreen(
    state: FillUpEditorUiState,
    onBack: () -> Unit,
    actions: FillUpEditorActions,
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (state.isDirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)

    val form = state.form
    val vehicle = state.vehicle
    // The extra fields open by themselves on an edit that already uses them.
    var moreOpen by rememberSaveable(form != null) {
        mutableStateOf(form != null && (form.missedPrevious || form.station.isNotEmpty() || form.note.isNotEmpty()))
    }

    FuelScreen(Modifier.imePadding()) {
        NavHeader(
            title = stringResource(if (state.isNew) R.string.fillup_title_new else R.string.fillup_title_edit),
            onBack = requestBack,
        ) {
            if (form != null) DateButton(form.date, state.today, actions.onDate, enabled = state.isEditable)
        }

        if (state.noVehicle) {
            Text(
                stringResource(R.string.entry_no_vehicle),
                style = FuelTheme.type.bodyRegular,
                color = colors.textSecondary,
                modifier = Modifier.padding(Dimens.gutter),
            )
            return@FuelScreen
        }
        if (form == null || vehicle == null) return@FuelScreen

        val odometerFocus = remember { FocusRequester() }
        if (state.isNew) {
            LaunchedEffect(Unit) { odometerFocus.requestFocus() }
        }
        val gutter = Modifier.padding(horizontal = Dimens.gutter)
        val previous = state.previousOdometerM?.let { f.distance.number(it, vehicle.distanceUnit) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!state.isEditable) {
                Column(gutter, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.unreadable_badge), color = colors.danger)
                }
            }
            if (state.isNew && state.vehicles.size > 1) {
                VehicleChooser(state.vehicles, vehicle, actions.onVehicle, gutter)
            }

            UnderlineField(
                value = form.odometerText,
                onValueChange = actions.onOdometer,
                label = stringResource(R.string.fillup_odometer, f.distance.unitLabel(vehicle.distanceUnit)),
                textStyle = FuelTheme.type.figureL,
                // A hint, never a prefill: a prefilled reading invites a save that repeats it.
                supporting = previous?.let { stringResource(R.string.fillup_last_reading, it) },
                keyboardType = KeyboardType.Decimal,
                focusRequester = odometerFocus,
                enabled = state.isEditable,
                modifier = gutter,
            )

            Row(gutter, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                UnderlineField(
                    value = form.volumeText,
                    onValueChange = actions.onVolume,
                    label = vehicle.volumeUnit.longLabel(),
                    keyboardType = KeyboardType.Decimal,
                    enabled = state.isEditable,
                    modifier = Modifier.weight(1f),
                )
                UnderlineField(
                    value = form.totalText,
                    onValueChange = actions.onTotal,
                    label = stringResource(R.string.fillup_total, vehicle.currencyCode),
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    enabled = state.isEditable,
                    modifier = Modifier.weight(1f),
                )
            }

            ToggleRow(
                title = stringResource(R.string.fillup_full_tank),
                body = stringResource(if (form.isFull) R.string.fillup_full_on else R.string.fillup_full_off),
                checked = form.isFull,
                onCheckedChange = actions.onFull,
                highlight = true,
                enabled = state.isEditable,
                modifier = gutter,
            )

            WarningBlock(state.warnings.map { it.message(previous) }, gutter)

            AnimatedVisibility(
                visible = moreOpen,
                enter = expandVertically(tween(160)),
                exit = shrinkVertically(tween(160)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    ToggleRow(
                        title = stringResource(R.string.fillup_missed),
                        body = stringResource(R.string.fillup_missed_body),
                        checked = form.missedPrevious,
                        onCheckedChange = actions.onMissedPrevious,
                        enabled = state.isEditable,
                        modifier = gutter,
                    )
                    TagChooser(form.tag, actions.onTag, state.isEditable, gutter)
                    UnderlineField(
                        value = form.station,
                        onValueChange = actions.onStation,
                        label = stringResource(R.string.fillup_station),
                        textStyle = FuelTheme.type.body,
                        capitalization = KeyboardCapitalization.Words,
                        enabled = state.isEditable,
                        modifier = gutter,
                    )
                    UnderlineField(
                        value = form.note,
                        onValueChange = actions.onNote,
                        label = stringResource(R.string.entry_note),
                        textStyle = FuelTheme.type.bodyRegular,
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                        enabled = state.isEditable,
                        modifier = gutter,
                    )
                }
            }

            if (!state.isEditable) Note(stringResource(R.string.editor_unreadable_body), gutter)
            if (!state.isNew) DeleteEntryRow(stringResource(R.string.fillup_delete), actions.onDelete)
        }

        val (tankValue, tankNote) = when (val point = state.preview) {
            is Measured -> {
                val value = f.consumption.value(point.shownAs(state.format), state.format)
                stringResource(R.string.value_with_unit, value, f.consumption.unitLabel(state.format)) to null
            }
            is Gap -> dashOr(null) to point.reason.label()
            null -> dashOr(null) to null
        }
        val rate = (state.preview as? Measured)?.costPerKm()?.perDistanceUnit(vehicle.distanceUnit)
        SummaryStrip(
            leftLabel = stringResource(R.string.fillup_this_tank),
            leftValue = tankValue,
            leftNote = tankNote,
            rightLabel = stringResource(R.string.fillup_cost_per, f.distance.unitLabel(vehicle.distanceUnit)),
            rightValue = dashOr(rate?.let(f.currency::formatRate)),
        )
        EntryActions(
            saveText = stringResource(R.string.fillup_save),
            canSave = state.canSave,
            onSave = actions.onSave,
            moreOpen = moreOpen,
            onToggleMore = { moreOpen = !moreOpen },
        )
    }

    if (confirmDiscard) {
        DiscardDialog(
            onDiscard = {
                confirmDiscard = false
                onBack()
            },
            onKeepEditing = { confirmDiscard = false },
        )
    }
    state.error?.let { ErrorDialog(it, actions.onDismissError) }
}
