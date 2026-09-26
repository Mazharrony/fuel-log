package com.fuelexpenselog.app.ui.reminders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.fuelexpenselog.app.ui.common.ChipGroup
import com.fuelexpenselog.app.ui.common.DiscardDialog
import com.fuelexpenselog.app.ui.common.ErrorDialog
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.PrimaryButton
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.Segmented
import com.fuelexpenselog.app.ui.common.UnderlineField
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.entry.DateButton
import com.fuelexpenselog.app.ui.entry.DeleteEntryRow
import com.fuelexpenselog.app.ui.entry.Hint
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.reminder.ReminderKind
import com.fuelexpenselog.domain.time.CivilDate

@Composable
fun ReminderEditorRoute(
    onDone: () -> Unit,
    viewModel: ReminderEditorViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ReminderEditorScreen(
        state = state,
        onBack = onDone,
        actions = ReminderEditorActions(
            onTitle = viewModel::onTitle,
            onCategory = viewModel::onCategory,
            onKind = viewModel::onKind,
            onEveryMonths = viewModel::onEveryMonths,
            onEveryDistance = viewModel::onEveryDistance,
            onLastDone = viewModel::onLastDone,
            onLastOdometer = viewModel::onLastOdometer,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismissError = viewModel::dismissError,
        ),
    )
}

class ReminderEditorActions(
    val onTitle: (String) -> Unit,
    val onCategory: (ExpenseCategory, String) -> Unit,
    val onKind: (ReminderKind) -> Unit,
    val onEveryMonths: (String) -> Unit,
    val onEveryDistance: (String) -> Unit,
    val onLastDone: (CivilDate) -> Unit,
    val onLastOdometer: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onDismissError: () -> Unit,
)

/** What comes round, how often, and when it was last done. Undesigned: tokens only. */
@Composable
fun ReminderEditorScreen(
    state: ReminderEditorUiState,
    onBack: () -> Unit,
    actions: ReminderEditorActions,
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (state.isDirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)

    val form = state.form
    val vehicle = state.vehicle

    FuelScreen(Modifier.imePadding()) {
        NavHeader(
            title = stringResource(if (state.isNew) R.string.reminder_title_new else R.string.reminder_title_edit),
            onBack = requestBack,
        )
        if (form == null || vehicle == null) return@FuelScreen

        val unit = vehicle.distanceUnit
        val unitLabel = f.distance.unitLabel(unit)
        val gutter = Modifier.padding(horizontal = Dimens.gutter)
        val labels = state.categories.associateWith { it.label() }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(gutter, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.reminder_logs_as))
                ChipGroup(
                    options = state.categories,
                    selected = form.category,
                    onSelect = { actions.onCategory(it, labels.getValue(it)) },
                    label = { labels.getValue(it) },
                )
            }

            UnderlineField(
                value = form.title,
                onValueChange = actions.onTitle,
                label = stringResource(R.string.reminder_name),
                textStyle = FuelTheme.type.figureRow,
                capitalization = KeyboardCapitalization.Sentences,
                modifier = gutter,
            )

            Column(gutter, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SectionLabel(stringResource(R.string.reminder_counts))
                Segmented(
                    options = listOf(ReminderKind.DATE, ReminderKind.DISTANCE, ReminderKind.BOTH),
                    selected = form.kind,
                    onSelect = actions.onKind,
                    label = { kind ->
                        stringResource(
                            when (kind) {
                                ReminderKind.DATE -> R.string.reminder_kind_date
                                ReminderKind.DISTANCE -> R.string.reminder_kind_distance
                                ReminderKind.BOTH -> R.string.reminder_kind_both
                            },
                        )
                    },
                )
            }

            Row(gutter, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                if (form.kind.usesDate) {
                    UnderlineField(
                        value = form.everyMonthsText,
                        onValueChange = actions.onEveryMonths,
                        label = stringResource(R.string.reminder_every_months),
                        textStyle = FuelTheme.type.figureRow,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (form.kind.usesDistance) {
                    UnderlineField(
                        value = form.everyDistanceText,
                        onValueChange = actions.onEveryDistance,
                        label = stringResource(R.string.reminder_every_distance, unitLabel),
                        textStyle = FuelTheme.type.figureRow,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // The date and the reading side by side, each under its own label, so the date
            // lines up with the field beside it instead of floating between its lines.
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel(stringResource(R.string.reminder_last_done))
                    DateButton(form.lastDone, state.today, actions.onLastDone)
                }
                if (form.kind.usesDistance) {
                    UnderlineField(
                        value = form.lastOdometerText,
                        onValueChange = actions.onLastOdometer,
                        label = stringResource(R.string.reminder_last_odometer, unitLabel),
                        textStyle = FuelTheme.type.figureRow,
                        supporting = state.currentOdometerM?.let {
                            stringResource(R.string.reminder_last_reading, f.distance.number(it, unit))
                        },
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            state.preview?.let { (reminder, status) ->
                Column(gutter, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel(stringResource(R.string.reminder_next))
                    StatusLine(reminder, status, unit)
                }
            }

            Hint(stringResource(R.string.reminder_hint), gutter)

            if (state.history.isNotEmpty()) {
                Column(gutter, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel(stringResource(R.string.reminder_history))
                    state.history.take(HISTORY_SHOWN).forEach { done ->
                        val line = listOfNotNull(
                            f.date.medium(done.date),
                            done.odometerM?.let { f.distance.format(it, unit) },
                        ).joinToString(" · ")
                        Text(line, style = FuelTheme.type.meta, color = colors.textSecondary)
                    }
                }
            }

            if (!state.isNew) {
                DeleteEntryRow(
                    text = stringResource(R.string.reminder_delete),
                    onDelete = actions.onDelete,
                    title = stringResource(R.string.reminder_delete_title),
                    body = stringResource(R.string.reminder_delete_body),
                )
            }
        }

        PrimaryButton(
            stringResource(R.string.reminder_save),
            actions.onSave,
            Modifier
                .fillMaxWidth()
                .padding(start = Dimens.gutter, end = Dimens.gutter, top = 16.dp, bottom = 22.dp),
            enabled = state.canSave,
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

private const val HISTORY_SHOWN = 5
