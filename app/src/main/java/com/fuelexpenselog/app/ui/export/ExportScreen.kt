package com.fuelexpenselog.app.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.ChipGroup
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.Note
import com.fuelexpenselog.app.ui.common.PrimaryButton
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.Segmented
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.entry.Hint
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.model.EntryTag
import java.text.NumberFormat

@Composable
fun ExportRoute(
    onBack: () -> Unit,
    viewModel: ExportViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The system picker: the user chooses where the file goes. No storage permission.
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::export)
    }
    ExportScreen(
        state = state,
        onBack = onBack,
        onVehicle = viewModel::onVehicle,
        onPeriod = viewModel::onPeriod,
        onTag = viewModel::onTag,
        onExport = { create.launch(state.suggestedName) },
    )
}

@Composable
fun ExportScreen(
    state: ExportUiState,
    onBack: () -> Unit,
    onVehicle: (Long) -> Unit,
    onPeriod: (ExportPeriod) -> Unit,
    onTag: (EntryTag?) -> Unit,
    onExport: () -> Unit,
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val number = NumberFormat.getIntegerInstance(f.locale)

    FuelScreen {
        NavHeader(stringResource(R.string.export_title), onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SectionLabel(stringResource(R.string.export_vehicle))
                val all = stringResource(R.string.export_all_vehicles)
                ChipGroup(
                    options = listOf(0L) + state.vehicles.map { it.id },
                    selected = state.vehicleId,
                    onSelect = onVehicle,
                    label = { id -> if (id == 0L) all else state.vehicles.first { it.id == id }.name },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SectionLabel(stringResource(R.string.export_period))
                val periods = listOfNotNull(ExportPeriod.ALL, ExportPeriod.YEAR, ExportPeriod.MONTH.takeIf { state.month != null })
                Segmented(
                    options = periods,
                    selected = state.period,
                    onSelect = onPeriod,
                    label = { period ->
                        when (period) {
                            ExportPeriod.ALL -> stringResource(R.string.export_all_time)
                            ExportPeriod.YEAR -> state.year.toString()
                            ExportPeriod.MONTH -> f.date.monthYear(state.month!!)
                        }
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SectionLabel(stringResource(R.string.export_entries))
                Segmented(
                    options = listOf(null, EntryTag.PERSONAL, EntryTag.BUSINESS),
                    selected = state.tag,
                    onSelect = onTag,
                    label = { it?.label() ?: stringResource(R.string.history_all) },
                )
            }
            Text(
                if (state.count == 0) {
                    stringResource(R.string.export_none)
                } else {
                    pluralStringResource(R.plurals.export_count, state.count, number.format(state.count))
                },
                style = FuelTheme.type.body,
                color = colors.textPrimary,
            )
            Hint(stringResource(R.string.export_note))
            when (val status = state.status) {
                is ExportStatus.Done -> Note(
                    pluralStringResource(
                        R.plurals.export_done,
                        status.rows,
                        number.format(status.rows),
                        status.fileName ?: stringResource(R.string.export_file_fallback),
                    ),
                )
                is ExportStatus.Failed -> Note(stringResource(R.string.export_failed, status.message))
                ExportStatus.Working -> Text(stringResource(R.string.working), style = FuelTheme.type.meta, color = colors.textSecondary)
                ExportStatus.Idle -> Unit
            }
        }
        PrimaryButton(
            stringResource(R.string.export_title),
            onExport,
            enabled = state.count > 0 && state.status != ExportStatus.Working,
            modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 12.dp, bottom = 22.dp),
        )
    }
}
