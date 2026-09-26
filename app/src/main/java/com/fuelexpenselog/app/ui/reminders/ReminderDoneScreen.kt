package com.fuelexpenselog.app.ui.reminders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.fuelexpenselog.app.ui.common.PrimaryButton
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.UnderlineField
import com.fuelexpenselog.app.ui.common.WarningBlock
import com.fuelexpenselog.app.ui.common.message
import com.fuelexpenselog.app.ui.entry.DateButton
import com.fuelexpenselog.app.ui.entry.Hint
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.time.CivilDate

@Composable
fun ReminderDoneRoute(
    onDone: () -> Unit,
    viewModel: ReminderDoneViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ReminderDoneScreen(
        state = state,
        onBack = onDone,
        actions = ReminderDoneActions(
            onDate = viewModel::onDate,
            onAmount = viewModel::onAmount,
            onOdometer = viewModel::onOdometer,
            onNote = viewModel::onNote,
            onSave = viewModel::save,
            onDismissError = viewModel::dismissError,
        ),
    )
}

class ReminderDoneActions(
    val onDate: (CivilDate) -> Unit,
    val onAmount: (String) -> Unit,
    val onOdometer: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onSave: () -> Unit,
    val onDismissError: () -> Unit,
)

/** Mark a reminder done. A cost logs an expense in its category; the next round is shown before saving. */
@Composable
fun ReminderDoneScreen(
    state: ReminderDoneUiState,
    onBack: () -> Unit,
    actions: ReminderDoneActions,
) {
    val f = LocalFormatters.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (state.isDirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)

    val form = state.form
    val vehicle = state.vehicle
    val reminder = state.reminder

    FuelScreen(Modifier.imePadding()) {
        NavHeader(title = reminder?.title.orEmpty(), onBack = requestBack) {
            if (form != null) DateButton(form.date, state.today, actions.onDate)
        }
        if (form == null || vehicle == null || reminder == null) return@FuelScreen

        val unit = vehicle.distanceUnit
        val gutter = Modifier.padding(horizontal = Dimens.gutter)
        val previous = state.previousOdometerM?.let { f.distance.number(it, unit) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            UnderlineField(
                value = form.amountText,
                onValueChange = actions.onAmount,
                label = stringResource(R.string.reminder_done_cost, vehicle.currencyCode),
                textStyle = FuelTheme.type.figureL,
                keyboardType = KeyboardType.Decimal,
                modifier = gutter,
            )

            UnderlineField(
                value = form.odometerText,
                onValueChange = actions.onOdometer,
                label = stringResource(R.string.reminder_done_odometer, f.distance.unitLabel(unit)),
                textStyle = FuelTheme.type.figureRow,
                supporting = previous?.let { stringResource(R.string.reminder_last_reading, it) },
                keyboardType = KeyboardType.Decimal,
                modifier = gutter,
            )

            UnderlineField(
                value = form.note,
                onValueChange = actions.onNote,
                label = stringResource(R.string.entry_note),
                textStyle = FuelTheme.type.bodyRegular,
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
                modifier = gutter,
            )

            WarningBlock(state.warnings.map { it.message(previous) }, gutter)

            state.next?.let { next ->
                dueText(next, unit)?.let { due ->
                    Column(gutter, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel(stringResource(R.string.reminder_next))
                        Text(due, style = FuelTheme.type.meta, color = FuelTheme.colors.textSecondary)
                    }
                }
            }

            Hint(stringResource(R.string.reminder_done_hint), gutter)
        }

        PrimaryButton(
            stringResource(R.string.reminder_done_save),
            actions.onSave,
            Modifier
                .fillMaxWidth()
                .padding(start = Dimens.gutter, end = Dimens.gutter, top = 16.dp, bottom = 22.dp),
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
