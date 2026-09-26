package com.fuelexpenselog.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.backup.BackupReader
import com.fuelexpenselog.app.data.repo.ImportBatch
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.ConfirmDialog
import com.fuelexpenselog.app.ui.common.CurrencyField
import com.fuelexpenselog.app.ui.common.FuelIcon
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.Segmented
import com.fuelexpenselog.app.ui.common.longLabel
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.onboarding.CountryList
import com.fuelexpenselog.app.ui.onboarding.countryName
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.csv.imprt.DialectId
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import java.text.NumberFormat

/** What the "Your data" rows do. Null means the row is not available in this build. */
class DataActions(
    val onExport: (() -> Unit)? = null,
    val onBackup: (() -> Unit)? = null,
    val onRestore: (() -> Unit)? = null,
    val onImport: (() -> Unit)? = null,
)

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onCollects: () -> Unit,
    onExport: () -> Unit,
    onImport: (uri: String) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = FuelViewModels.Factory),
    dataViewModel: DataViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dataStatus by dataViewModel.status.collectAsStateWithLifecycle()
    val imports by dataViewModel.imports.collectAsStateWithLifecycle()
    var pendingRestore by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingUndo by rememberSaveable { mutableStateOf<Long?>(null) }

    // The system pickers: the user chooses where a backup goes, which one comes back, and
    // which file to import. Nothing else of their storage is ever visible to the app.
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(dataViewModel::backup)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestore = uri
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it.toString()) }
    }

    SettingsScreen(
        state = state,
        onBack = onBack,
        onFormat = viewModel::onFormat,
        onDistance = viewModel::onDistance,
        onVolume = viewModel::onVolume,
        onCurrency = viewModel::onCurrency,
        onRegion = viewModel::onRegion,
        onCollects = onCollects,
        data = DataActions(
            onExport = onExport,
            onBackup = { backup.launch(dataViewModel.backupName()) },
            onRestore = { restore.launch(arrayOf("*/*")) },
            // Any type: a CSV arrives as text/csv, text/plain or octet-stream depending on
            // where it was saved, and a file that is not one says so in the preview.
            onImport = { import.launch(arrayOf("*/*")) },
        ),
        imports = imports,
        onUndoImport = { pendingUndo = it.id },
    )

    imports.firstOrNull { it.first.id == pendingUndo }?.let { (batch, _) ->
        ConfirmDialog(
            title = stringResource(R.string.undo_import_title),
            body = stringResource(R.string.undo_import_body),
            confirm = stringResource(R.string.undo_import_confirm),
            danger = true,
            onConfirm = {
                pendingUndo = null
                dataViewModel.undoImport(batch)
            },
            onDismiss = { pendingUndo = null },
        )
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            body = stringResource(R.string.restore_confirm_body),
            confirm = stringResource(R.string.restore_confirm),
            danger = true,
            onConfirm = {
                pendingRestore = null
                dataViewModel.restore(uri)
            },
            onDismiss = { pendingRestore = null },
        )
    }
    DataStatusDialog(dataStatus, dataViewModel::dismiss)
}

@Composable
private fun DataStatusDialog(status: DataStatus, onDismiss: () -> Unit) {
    val message = when (status) {
        DataStatus.Idle -> return
        DataStatus.Working -> stringResource(R.string.working)
        is DataStatus.BackedUp -> stringResource(R.string.backup_done, status.fileName ?: stringResource(R.string.export_file_fallback))
        is DataStatus.Refused -> when (status.why) {
            BackupReader.Refusal.NEWER_SCHEMA -> stringResource(R.string.restore_newer)
            BackupReader.Refusal.NOT_A_BACKUP -> stringResource(R.string.restore_not_backup)
        }
        is DataStatus.Failed -> stringResource(if (status.restoring) R.string.restore_failed else R.string.backup_failed, status.message)
        is DataStatus.Undone -> pluralStringResource(
            R.plurals.undo_import_done,
            status.removed,
            NumberFormat.getIntegerInstance(LocalFormatters.current.locale).format(status.removed),
        )
    }
    AlertDialog(
        onDismissRequest = { if (status != DataStatus.Working) onDismiss() },
        text = { Text(message) },
        confirmButton = {
            if (status != DataStatus.Working) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok), color = FuelTheme.colors.textPrimary)
                }
            }
        },
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onFormat: (ConsumptionFormat) -> Unit,
    onDistance: (DistanceUnit) -> Unit,
    onVolume: (EnergyUnit) -> Unit,
    onCurrency: (String) -> Unit,
    onRegion: (String) -> Unit,
    onCollects: () -> Unit,
    data: DataActions,
    imports: List<Pair<ImportBatch, CivilDate>> = emptyList(),
    onUndoImport: (ImportBatch) -> Unit = {},
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    var pickRegion by rememberSaveable { mutableStateOf(false) }

    FuelScreen {
        NavHeader(stringResource(R.string.settings_title), onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 18.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionLabel(stringResource(R.string.settings_format))
                    Segmented(
                        options = listOf(
                            ConsumptionFormat.MPG_US,
                            ConsumptionFormat.MPG_UK,
                            ConsumptionFormat.L_PER_100KM,
                            ConsumptionFormat.KM_PER_L,
                        ),
                        selected = state.format,
                        onSelect = onFormat,
                        label = { stringResource(shortFormatLabel(it)) },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionLabel(stringResource(R.string.settings_new_vehicles))
                    Segmented(
                        options = listOf(DistanceUnit.MILE, DistanceUnit.KILOMETRE),
                        selected = state.defaultDistance,
                        onSelect = onDistance,
                        label = { it.longLabel() },
                    )
                    Segmented(
                        options = listOf(EnergyUnit.US_GALLON, EnergyUnit.IMP_GALLON, EnergyUnit.LITRE),
                        selected = state.defaultVolume,
                        onSelect = onVolume,
                        label = { it.longLabel() },
                    )
                }
                CurrencyField(state.defaultCurrency, onCurrency)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouchTarget)
                        .clickable(role = Role.Button) { pickRegion = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_region),
                        style = FuelTheme.type.meta,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(
                            R.string.settings_region_value,
                            countryName(state.region.countryCode, f.locale).ifEmpty { stringResource(R.string.country_no_name) },
                        ),
                        style = FuelTheme.type.body,
                        color = colors.textPrimary,
                    )
                }
            }

            SectionLabel(
                stringResource(R.string.settings_your_data),
                Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 14.dp, bottom = 6.dp),
            )
            DataRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_hint), data.onExport)
            DataRow(stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_hint), data.onBackup)
            DataRow(stringResource(R.string.settings_restore), stringResource(R.string.settings_restore_hint), data.onRestore)
            DataRow(stringResource(R.string.settings_import), stringResource(R.string.settings_import_hint), data.onImport)
            imports.forEach { (batch, day) ->
                DataRow(
                    stringResource(R.string.settings_undo_import, batch.fileName),
                    pluralStringResource(
                        R.plurals.settings_undo_import_detail,
                        batch.rowCount,
                        stringResource(importSourceLabel(batch.source)),
                        NumberFormat.getIntegerInstance(f.locale).format(batch.rowCount),
                        f.date.medium(day),
                    ),
                    onClick = { onUndoImport(batch) },
                )
            }
            DataRow(stringResource(R.string.settings_collects), stringResource(R.string.settings_collects_hint), onCollects)

            Text(
                stringResource(R.string.settings_footer),
                style = FuelTheme.type.meta,
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .topHairline(colors.outline)
                    .padding(horizontal = Dimens.gutter, vertical = 18.dp),
            )
        }
    }

    if (pickRegion) {
        RegionDialog(
            selected = state.region.countryCode,
            onSelect = {
                onRegion(it)
                pickRegion = false
            },
            onDismiss = { pickRegion = false },
        )
    }
}

/** The app an import came from, as stored on its batch. */
private fun importSourceLabel(source: String): Int = when (source) {
    DialectId.FUEL_LOG.name -> R.string.import_format_fuel_log
    DialectId.FUELIO.name -> R.string.import_format_fuelio
    DialectId.ACAR.name -> R.string.import_format_acar
    DialectId.DRIVVO.name -> R.string.import_format_drivvo
    else -> R.string.import_source_spreadsheet
}

private fun shortFormatLabel(format: ConsumptionFormat): Int = when (format) {
    ConsumptionFormat.MPG_US -> R.string.format_short_mpg_us
    ConsumptionFormat.MPG_UK -> R.string.format_short_mpg_uk
    ConsumptionFormat.KM_PER_L -> R.string.format_short_km_per_l
    else -> R.string.format_short_l_per_100km
}

/** A "Your data" row. With no action it shows as not yet available rather than vanishing. */
@Composable
private fun DataRow(title: String, hint: String, onClick: (() -> Unit)?) {
    val colors = FuelTheme.colors
    val enabled = onClick != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .topHairline(colors.outline)
            .clickable(enabled = enabled, role = Role.Button) { onClick?.invoke() }
            .padding(horizontal = Dimens.gutter, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = FuelTheme.type.body, color = if (enabled) colors.textPrimary else colors.textSecondary)
            Text(
                if (enabled) hint else stringResource(R.string.settings_not_yet),
                style = FuelTheme.type.meta,
                color = colors.textSecondary,
            )
        }
        if (enabled) FuelIcon(R.drawable.ic_chevron_right, null, tint = colors.textSecondary)
    }
}

@Composable
private fun RegionDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FuelScreen(Modifier.padding(horizontal = 16.dp, vertical = 48.dp)) {
            NavHeader(stringResource(R.string.onboarding_row_region), onDismiss)
            Text(
                stringResource(R.string.settings_region_note),
                style = FuelTheme.type.meta,
                color = FuelTheme.colors.textSecondary,
                modifier = Modifier.padding(Dimens.gutter),
            )
            CountryList(query, { query = it }, selected, onSelect, Modifier.weight(1f))
        }
    }
}
