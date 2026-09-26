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
import com.fuelexpenselog.app.ui.common.ChipGroup
import com.fuelexpenselog.app.ui.common.DiscardDialog
import com.fuelexpenselog.app.ui.common.ErrorDialog
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.Note
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.UnderlineField
import com.fuelexpenselog.app.ui.common.WarningBlock
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.common.message
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.time.CivilDate

@Composable
fun ExpenseEditorRoute(
    onDone: () -> Unit,
    viewModel: ExpenseEditorViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ExpenseEditorScreen(
        state = state,
        onBack = onDone,
        actions = ExpenseEditorActions(
            onVehicle = viewModel::onVehicle,
            onAmount = viewModel::onAmount,
            onOdometer = viewModel::onOdometer,
            onCategory = viewModel::onCategory,
            onDate = viewModel::onDate,
            onTag = viewModel::onTag,
            onVendor = viewModel::onVendor,
            onNote = viewModel::onNote,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismissError = viewModel::dismissError,
        ),
    )
}

class ExpenseEditorActions(
    val onVehicle: (Long) -> Unit,
    val onAmount: (String) -> Unit,
    val onOdometer: (String) -> Unit,
    val onCategory: (ExpenseCategory) -> Unit,
    val onDate: (CivilDate) -> Unit,
    val onTag: (EntryTag) -> Unit,
    val onVendor: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onDismissError: () -> Unit,
)

/** A non-fuel cost: amount, category, and - for maintenance - the odometer. */
@Composable
fun ExpenseEditorScreen(
    state: ExpenseEditorUiState,
    onBack: () -> Unit,
    actions: ExpenseEditorActions,
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (state.isDirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)

    val form = state.form
    val vehicle = state.vehicle
    var moreOpen by rememberSaveable(form != null) {
        mutableStateOf(form != null && form.vendor.isNotEmpty())
    }

    FuelScreen(Modifier.imePadding()) {
        NavHeader(
            title = stringResource(if (state.isNew) R.string.expense_title_new else R.string.expense_title_edit),
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

        val amountFocus = remember { FocusRequester() }
        if (state.isNew) {
            LaunchedEffect(Unit) { amountFocus.requestFocus() }
        }
        val gutter = Modifier.padding(horizontal = Dimens.gutter)
        val previous = state.previousOdometerM?.let { f.distance.number(it, vehicle.distanceUnit) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (!state.isEditable) {
                SectionLabel(stringResource(R.string.unreadable_badge), gutter, color = colors.danger)
            }
            if (state.isNew && state.vehicles.size > 1) {
                VehicleChooser(state.vehicles, vehicle, actions.onVehicle, gutter)
            }

            UnderlineField(
                value = form.amountText,
                onValueChange = actions.onAmount,
                label = stringResource(R.string.expense_amount, vehicle.currencyCode),
                textStyle = FuelTheme.type.figureL,
                keyboardType = KeyboardType.Decimal,
                focusRequester = amountFocus,
                enabled = state.isEditable,
                modifier = gutter,
            )

            Column(gutter, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.expense_category))
                ChipGroup(
                    options = state.categories,
                    selected = form.category,
                    onSelect = { if (state.isEditable) actions.onCategory(it) },
                    label = { it.label() },
                )
            }

            Row(gutter, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                UnderlineField(
                    value = form.odometerText,
                    onValueChange = actions.onOdometer,
                    label = stringResource(R.string.expense_odometer),
                    textStyle = FuelTheme.type.figureRow,
                    supporting = previous?.let { stringResource(R.string.fillup_last_reading, it) },
                    keyboardType = KeyboardType.Decimal,
                    enabled = state.isEditable,
                    modifier = Modifier.weight(1f),
                )
                UnderlineField(
                    value = form.note,
                    onValueChange = actions.onNote,
                    label = stringResource(R.string.entry_note),
                    textStyle = FuelTheme.type.bodyRegular,
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                    enabled = state.isEditable,
                    modifier = Modifier.weight(1f),
                )
            }

            Hint(stringResource(R.string.expense_odometer_hint), gutter)

            WarningBlock(state.warnings.map { it.message(previous) }, gutter)

            AnimatedVisibility(
                visible = moreOpen,
                enter = expandVertically(tween(160)),
                exit = shrinkVertically(tween(160)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    TagChooser(form.tag, actions.onTag, state.isEditable, gutter)
                    UnderlineField(
                        value = form.vendor,
                        onValueChange = actions.onVendor,
                        label = stringResource(R.string.expense_vendor),
                        textStyle = FuelTheme.type.body,
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                        enabled = state.isEditable,
                        modifier = gutter,
                    )
                }
            }

            if (!state.isEditable) Note(stringResource(R.string.editor_unreadable_body), gutter)
            if (!state.isNew) DeleteEntryRow(stringResource(R.string.expense_delete), actions.onDelete)
        }

        EntryActions(
            saveText = stringResource(R.string.expense_save),
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
