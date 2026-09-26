package com.fuelexpenselog.app.ui.vehicles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.fuelexpenselog.app.ui.common.CurrencyField
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.Note
import com.fuelexpenselog.app.ui.common.PrimaryButton
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.Segmented
import com.fuelexpenselog.app.ui.common.ToggleRow
import com.fuelexpenselog.app.ui.common.UnderlineField
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.common.longLabel
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.FuelType
import com.fuelexpenselog.domain.model.VehicleType
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyKind
import com.fuelexpenselog.domain.unit.EnergyUnit
import java.text.NumberFormat

@Composable
fun VehicleEditorRoute(
    onDone: () -> Unit,
    viewModel: VehicleEditorViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    VehicleEditorScreen(
        state = state,
        onBack = onDone,
        actions = VehicleEditorActions(
            onName = viewModel::onName,
            onDistanceUnit = viewModel::onDistanceUnit,
            onVolumeUnit = viewModel::onVolumeUnit,
            onCurrency = viewModel::onCurrency,
            onTank = viewModel::onTank,
            onType = viewModel::onType,
            onFuelType = viewModel::onFuelType,
            onDefaultTag = viewModel::onDefaultTag,
            onFormat = viewModel::onFormat,
            onActive = viewModel::onActive,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismissError = viewModel::dismissError,
        ),
    )
}

class VehicleEditorActions(
    val onName: (String) -> Unit,
    val onDistanceUnit: (DistanceUnit) -> Unit,
    val onVolumeUnit: (EnergyUnit) -> Unit,
    val onCurrency: (String) -> Unit,
    val onTank: (String) -> Unit,
    val onType: (VehicleType) -> Unit,
    val onFuelType: (FuelType) -> Unit,
    val onDefaultTag: (EntryTag) -> Unit,
    val onFormat: (ConsumptionFormat?) -> Unit,
    val onActive: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onDismissError: () -> Unit,
)

@Composable
fun VehicleEditorScreen(
    state: VehicleEditorUiState,
    onBack: () -> Unit,
    actions: VehicleEditorActions,
) {
    val colors = FuelTheme.colors
    val f = LocalFormatters.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val requestBack = { if (state.isDirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)

    FuelScreen(Modifier.imePadding()) {
        NavHeader(
            title = stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit),
            onBack = requestBack,
        ) {
            if (!state.isNew) {
                Text(
                    pluralStringResource(
                        R.plurals.editor_entry_count,
                        state.entryCount,
                        NumberFormat.getIntegerInstance(f.locale).format(state.entryCount),
                    ),
                    style = FuelTheme.type.meta,
                    color = colors.textSecondary,
                )
            }
        }

        val form = state.form ?: return@FuelScreen
        val editable = state.isEditable

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            val gutter = Modifier.padding(horizontal = Dimens.gutter)

            if (!editable) {
                Column(gutter, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.unreadable_badge), color = colors.danger)
                    Note(stringResource(R.string.editor_unreadable_body))
                }
            }

            UnderlineField(
                value = form.name,
                onValueChange = actions.onName,
                label = stringResource(R.string.editor_name),
                placeholder = stringResource(R.string.editor_name_placeholder),
                textStyle = FuelTheme.type.figureRow,
                capitalization = KeyboardCapitalization.Words,
                enabled = editable,
                modifier = gutter,
            )

            Labelled(stringResource(R.string.editor_distance), gutter) {
                Segmented(
                    options = listOf(DistanceUnit.MILE, DistanceUnit.KILOMETRE),
                    selected = form.distanceUnit,
                    onSelect = actions.onDistanceUnit,
                    label = { it.longLabel() },
                    enabled = editable,
                )
            }

            Labelled(stringResource(R.string.editor_volume), gutter) {
                Segmented(
                    options = listOf(EnergyUnit.US_GALLON, EnergyUnit.IMP_GALLON, EnergyUnit.LITRE),
                    selected = form.volumeUnit,
                    onSelect = actions.onVolumeUnit,
                    label = { it.longLabel() },
                    enabled = editable,
                )
            }

            Row(gutter, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CurrencyField(
                    code = form.currency,
                    onChange = actions.onCurrency,
                    enabled = editable,
                    modifier = Modifier.weight(1f),
                )
                UnderlineField(
                    value = form.tankText,
                    onValueChange = actions.onTank,
                    label = stringResource(R.string.editor_tank),
                    textStyle = FuelTheme.type.subtitle,
                    suffix = f.volume.unitLabel(form.volumeUnit),
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    enabled = editable,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.tankUnreadable) {
                Text(
                    stringResource(R.string.editor_tank_unreadable),
                    style = FuelTheme.type.meta,
                    color = colors.warningInk,
                    modifier = gutter,
                )
            }

            Labelled(stringResource(R.string.editor_type), gutter) {
                Segmented(
                    options = VehicleType.entries,
                    selected = form.type,
                    onSelect = actions.onType,
                    label = { it.label() },
                    enabled = editable,
                )
            }

            Labelled(stringResource(R.string.editor_fuel), gutter) {
                // Electric is kept only for a vehicle that already has it: v1 logs liquid fuel.
                val fuels = FuelType.entries.filter { it != FuelType.ELECTRIC || form.fuelType == FuelType.ELECTRIC }
                ChipGroup(fuels, form.fuelType, { if (editable) actions.onFuelType(it) }, { it.label() })
            }

            Labelled(stringResource(R.string.editor_format), gutter) {
                val formats = listOf<ConsumptionFormat?>(null) + ConsumptionFormat.forKind(EnergyKind.LIQUID)
                ChipGroup(
                    options = formats,
                    selected = form.format,
                    onSelect = { if (editable) actions.onFormat(it) },
                    label = { it?.label() ?: stringResource(R.string.format_app_default) },
                )
            }

            Labelled(stringResource(R.string.editor_default_tag), gutter) {
                Segmented(
                    options = listOf(EntryTag.PERSONAL, EntryTag.BUSINESS),
                    selected = form.defaultTag,
                    onSelect = actions.onDefaultTag,
                    label = { it.label() },
                    enabled = editable,
                )
            }

            if (!state.isNew) {
                ToggleRow(
                    title = stringResource(R.string.editor_active),
                    body = stringResource(R.string.editor_active_body),
                    checked = form.active,
                    onCheckedChange = actions.onActive,
                    modifier = gutter,
                )
                Note(stringResource(R.string.editor_units_note), gutter)
                DeleteRow(state.entryCount, f.locale, onClick = { confirmDelete = true })
            }
        }

        PrimaryButton(
            text = stringResource(R.string.editor_save),
            onClick = actions.onSave,
            enabled = state.canSave,
            modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 12.dp, bottom = 22.dp),
        )
    }

    if (confirmDelete) {
        DeleteDialog(
            name = state.storedName,
            entryCount = state.entryCount,
            locale = f.locale,
            onConfirm = {
                confirmDelete = false
                actions.onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.action_discard), color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.action_keep_editing), color = colors.textPrimary)
                }
            },
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = actions.onDismissError,
            text = { Text(stringResource(R.string.save_failed, message)) },
            confirmButton = {
                TextButton(onClick = actions.onDismissError) {
                    Text(stringResource(android.R.string.ok), color = colors.textPrimary)
                }
            },
        )
    }
}

@Composable
private fun Labelled(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel(label)
        content()
    }
}

@Composable
private fun DeleteRow(entryCount: Int, locale: java.util.Locale, onClick: () -> Unit) {
    val colors = FuelTheme.colors
    val text = if (entryCount == 0) {
        stringResource(R.string.editor_delete_empty)
    } else {
        pluralStringResource(R.plurals.editor_delete, entryCount, NumberFormat.getIntegerInstance(locale).format(entryCount))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .topHairline(colors.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.gutter, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = FuelTheme.type.button, color = colors.danger)
    }
}

@Composable
private fun DeleteDialog(
    name: String,
    entryCount: Int,
    locale: java.util.Locale,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FuelTheme.colors
    val body = if (entryCount == 0) {
        stringResource(R.string.editor_delete_body_empty)
    } else {
        pluralStringResource(R.plurals.editor_delete_body, entryCount, NumberFormat.getIntegerInstance(locale).format(entryCount))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_delete_title, name)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete), color = colors.danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.textPrimary) }
        },
    )
}
