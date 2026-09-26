package com.fuelexpenselog.app.ui.importflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.Formatters
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.ChipGroup
import com.fuelexpenselog.app.ui.common.ErrorDialog
import com.fuelexpenselog.app.ui.common.FuelIcon
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.PrimaryButton
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.Segmented
import com.fuelexpenselog.app.ui.common.dashOr
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.common.longLabel
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.csv.CharsetSniffer
import com.fuelexpenselog.csv.imprt.DateFormatResolver
import com.fuelexpenselog.csv.imprt.DateOrder
import com.fuelexpenselog.csv.imprt.DialectId
import com.fuelexpenselog.csv.imprt.Field
import com.fuelexpenselog.csv.imprt.ImportPlan
import com.fuelexpenselog.csv.imprt.RowIssue
import com.fuelexpenselog.csv.imprt.StagedEntry
import com.fuelexpenselog.csv.imprt.StagedRow
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import java.text.NumberFormat

@Composable
fun ImportRoute(
    onDone: () -> Unit,
    viewModel: ImportViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImportScreen(
        state = state,
        onBack = onDone,
        actions = ImportActions(
            onTarget = viewModel::onTarget,
            onDateOrder = viewModel::onDateOrder,
            onDistance = viewModel::onDistance,
            onVolume = viewModel::onVolume,
            onSourceVehicle = viewModel::onSourceVehicle,
            onColumn = viewModel::onColumn,
            onFilter = viewModel::onFilter,
            onToggle = viewModel::onToggle,
            onImport = viewModel::import,
        ),
    )
    state.done?.let { done ->
        val f = LocalFormatters.current
        AlertDialog(
            onDismissRequest = onDone,
            text = {
                Text(pluralStringResource(R.plurals.import_done, done.count, whole(f, done.count), done.vehicleName))
            },
            confirmButton = {
                TextButton(onClick = onDone) {
                    Text(stringResource(android.R.string.ok), color = FuelTheme.colors.textPrimary)
                }
            },
        )
    }
    state.error?.let { ErrorDialog(it, viewModel::dismissError) }
}

class ImportActions(
    val onTarget: (Long) -> Unit,
    val onDateOrder: (DateOrder) -> Unit,
    val onDistance: (DistanceUnit) -> Unit,
    val onVolume: (EnergyUnit) -> Unit,
    val onSourceVehicle: (String) -> Unit,
    val onColumn: (Field, Int?) -> Unit,
    val onFilter: (RowFilter) -> Unit,
    val onToggle: (StagedRow) -> Unit,
    val onImport: () -> Unit,
)

/**
 * What the importer made of the file, the few things only the user can say, and every row
 * with its own switch. Duplicates start switched off; errors cannot be switched on.
 */
@Composable
fun ImportScreen(state: ImportUiState, onBack: () -> Unit, actions: ImportActions) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val gutter = Modifier.padding(horizontal = Dimens.gutter)

    FuelScreen {
        NavHeader(stringResource(R.string.import_title), onBack)
        val plan = state.plan
        val failure = state.failure
        if (failure != null || plan == null) {
            Text(
                stringResource(
                    when (failure) {
                        ImportFailure.UNREADABLE -> R.string.import_failed_unreadable
                        ImportFailure.TOO_LARGE -> R.string.import_failed_large
                        ImportFailure.NO_ROWS -> R.string.import_failed_empty
                        null -> R.string.import_reading
                    },
                ),
                style = FuelTheme.type.bodyRegular,
                color = colors.textSecondary,
                modifier = Modifier.padding(Dimens.gutter),
            )
            return@FuelScreen
        }

        LazyColumn(Modifier.weight(1f)) {
            item(key = "card") { PreviewCard(state, plan) }
            (plan.dates as? DateFormatResolver.Result.Ambiguous)?.let { ambiguous ->
                item(key = "dates") { DateQuestion(ambiguous, state.assumptions?.dateOrder, actions.onDateOrder) }
            }
            if (plan.dialect == DialectId.GENERIC) {
                item(key = "columns") { ColumnMapping(plan, actions.onColumn) }
            }
            val assumptions = state.assumptions
            if (plan.dialect != DialectId.FUEL_LOG && assumptions != null) {
                item(key = "units") {
                    Column(gutter.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        SectionLabel(stringResource(R.string.import_distance))
                        Segmented(
                            options = listOf(DistanceUnit.KILOMETRE, DistanceUnit.MILE),
                            selected = assumptions.distanceUnit,
                            onSelect = actions.onDistance,
                            label = { it.longLabel() },
                        )
                        SectionLabel(stringResource(R.string.import_volume), Modifier.padding(top = 9.dp))
                        Segmented(
                            options = listOf(EnergyUnit.LITRE, EnergyUnit.US_GALLON, EnergyUnit.IMP_GALLON),
                            selected = assumptions.volumeUnit,
                            onSelect = actions.onVolume,
                            label = { it.longLabel() },
                        )
                    }
                }
            }
            if (state.vehicles.size > 1) {
                item(key = "into") {
                    Column(gutter.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        SectionLabel(stringResource(R.string.import_into))
                        ChipGroup(state.vehicles, state.target, { actions.onTarget(it.id) }, { it.name })
                    }
                }
            }
            if (plan.vehicles.size > 1 && assumptions != null) {
                item(key = "source") {
                    Column(gutter.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        SectionLabel(stringResource(R.string.import_rows_for))
                        ChipGroup(plan.vehicles, assumptions.sourceVehicle, actions.onSourceVehicle, { it })
                    }
                }
            }
            // Until the dates are answered no row can be read, and listing every one as an
            // error would blame the file for a question the app has not had answered yet.
            if (state.needsDateAnswer) {
                item(key = "answer-first") {
                    Text(
                        stringResource(R.string.import_dates_first),
                        style = FuelTheme.type.meta,
                        color = colors.textSecondary,
                        modifier = gutter.padding(top = 22.dp, bottom = 16.dp),
                    )
                }
                return@LazyColumn
            }
            item(key = "filter") {
                Column(gutter.padding(top = 22.dp, bottom = 12.dp)) {
                    ChipGroup(
                        options = RowFilter.entries,
                        selected = state.filter,
                        onSelect = actions.onFilter,
                        label = { filter -> stringResource(R.string.import_filter, filter.label(), whole(f, state.matching(filter).size)) },
                    )
                }
            }
            if (state.visible.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.import_filter_empty),
                        style = FuelTheme.type.meta,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .topHairline(colors.outline)
                            .padding(horizontal = Dimens.gutter, vertical = 16.dp),
                    )
                }
            }
            items(state.visible, key = { it.line }) { row ->
                StagedRowItem(row, state.isIncluded(row), state.target, actions.onToggle)
            }
        }

        PrimaryButton(
            pluralStringResource(R.plurals.import_n, state.importCount, whole(f, state.importCount)),
            actions.onImport,
            enabled = state.canImport,
            modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 12.dp, bottom = 22.dp),
        )
    }
}

private fun whole(f: Formatters, n: Int): String = NumberFormat.getIntegerInstance(f.locale).format(n)

/** The handoff's card: an ink frame with a dark header, and what was detected. */
@Composable
private fun PreviewCard(state: ImportUiState, plan: ImportPlan) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val readable = state.rows.count { !it.isError }
    val sample = plan.tables.firstNotNullOfOrNull { t -> t.table.rows.firstOrNull()?.cell(t.mapping[Field.DATE]) }.orEmpty()
    val dateFormat = state.assumptions?.dateOrder?.let { DateFormatResolver.pattern(sample, it) }
        ?: stringResource(R.string.import_dates_ask)

    Column(
        Modifier
            .padding(start = Dimens.gutter, end = Dimens.gutter, top = 18.dp)
            .fillMaxWidth()
            .border(Dimens.hairline, colors.textPrimary),
    ) {
        SectionLabel(
            stringResource(R.string.import_preview, state.fileName),
            Modifier
                .fillMaxWidth()
                .background(colors.textPrimary)
                .padding(horizontal = 15.dp, vertical = 13.dp),
            color = colors.background,
        )
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            CardRow(stringResource(R.string.import_format), stringResource(plan.dialect.labelRes()))
            CardRow(stringResource(R.string.import_text), stringResource(plan.encoding.labelRes()))
            CardRow(stringResource(R.string.import_dates), dateFormat)
            CardRow(
                stringResource(R.string.import_rows),
                when {
                    state.needsDateAnswer -> dashOr(null)
                    state.staging && state.rows.isEmpty() -> stringResource(R.string.import_reading)
                    else -> stringResource(R.string.import_rows_value, whole(f, readable), whole(f, plan.rowCount))
                },
            )
        }
    }
}

@Composable
private fun CardRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = FuelTheme.type.meta, color = FuelTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = FuelTheme.type.meta.copy(fontWeight = FontWeight.SemiBold), color = FuelTheme.colors.textPrimary)
    }
}

/** "In this file, 01/02/2026 is" - with each reading of that very date to choose from. */
@Composable
private fun DateQuestion(ambiguous: DateFormatResolver.Result.Ambiguous, chosen: DateOrder?, onChoose: (DateOrder) -> Unit) {
    val f = LocalFormatters.current
    val readings = ambiguous.orders.associateWith { order -> DateFormatResolver.parse(ambiguous.example, order)?.let(f.date::medium) }
    Column(
        Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        SectionLabel(stringResource(R.string.import_date_question, ambiguous.example), color = FuelTheme.colors.warningInk)
        ChipGroup(
            options = ambiguous.orders,
            selected = chosen,
            onSelect = onChoose,
            label = { order -> readings[order] ?: DateFormatResolver.pattern(ambiguous.example, order) },
        )
    }
}

private val MAPPABLE = listOf(
    Field.DATE, Field.ODOMETER, Field.VOLUME, Field.TOTAL, Field.UNIT_PRICE, Field.FULL, Field.STATION, Field.NOTE,
)

/** For a file from no app this knows: which column is which, as guessed, for the user to fix. */
@Composable
private fun ColumnMapping(plan: ImportPlan, onColumn: (Field, Int?) -> Unit) {
    val colors = FuelTheme.colors
    var picking by rememberSaveable { mutableStateOf<Field?>(null) }
    val mapping = plan.tables.firstOrNull()?.mapping.orEmpty()

    Column(Modifier.padding(top = 20.dp)) {
        SectionLabel(stringResource(R.string.import_columns), Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, bottom = 6.dp))
        MAPPABLE.forEach { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget)
                    .topHairline(colors.outline)
                    .clickable(role = Role.Button) { picking = field }
                    .padding(horizontal = Dimens.gutter, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(field.labelRes()), style = FuelTheme.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    mapping[field]?.let { plan.header.getOrNull(it) } ?: stringResource(R.string.import_column_none),
                    style = FuelTheme.type.meta,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FuelIcon(R.drawable.ic_chevron_right, null, tint = colors.textSecondary)
            }
        }
    }

    picking?.let { field ->
        ColumnPicker(field, plan, onPick = { column ->
            onColumn(field, column)
            picking = null
        }, onDismiss = { picking = null })
    }
}

@Composable
private fun ColumnPicker(field: Field, plan: ImportPlan, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val colors = FuelTheme.colors
    val first = plan.tables.firstOrNull()?.table?.rows?.firstOrNull()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.background)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(stringResource(R.string.import_column_title, stringResource(field.labelRes())), Modifier.padding(Dimens.gutter))
            PickerRow(stringResource(R.string.import_column_none)) { onPick(null) }
            plan.header.forEachIndexed { index, name ->
                val example = first?.cell(index).orEmpty()
                PickerRow(if (example.isEmpty()) name else stringResource(R.string.import_column_sample, name, example)) { onPick(index) }
            }
        }
    }
}

@Composable
private fun PickerRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = FuelTheme.type.body,
        color = FuelTheme.colors.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .topHairline(FuelTheme.colors.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.gutter, vertical = 14.dp),
    )
}

/**
 * One row of the file: its box, what it would become, and anything to know about it. A row
 * that cannot be read says why and where, and has no box to tick.
 */
@Composable
private fun StagedRowItem(
    row: StagedRow,
    included: Boolean,
    target: Vehicle?,
    onToggle: (StagedRow) -> Unit,
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    val entry = row.entry
    val unit = target?.distanceUnit ?: DistanceUnit.KILOMETRE

    val (title, detail, amount) = when (entry) {
        is StagedEntry.Fuel -> {
            val fill = entry.fillUp
            Triple(
                listOfNotNull(f.date.medium(fill.date), fill.odometerM?.let { f.distance.format(it, unit) }).joinToString(" · "),
                listOfNotNull(
                    f.volume.format(fill.energy, fill.energyUnitEntered),
                    if (!fill.isFull) stringResource(R.string.import_partial) else null,
                    if (fill.missedPrevious) stringResource(R.string.import_missed) else null,
                    fill.station,
                ).joinToString(" · "),
                (fill.total ?: fill.unitPrice)?.let { if (fill.total != null) f.currency.format(it) else f.currency.formatRate(it) },
            )
        }
        is StagedEntry.Cost -> {
            val cost = entry.expense
            Triple(
                listOf(cost.category.label(), f.date.medium(cost.date)).joinToString(" · "),
                listOfNotNull(cost.odometerM?.let { f.distance.format(it, unit) }, cost.vendor, cost.note).joinToString(" · "),
                f.currency.format(cost.amount),
            )
        }
        null -> Triple(stringResource(R.string.import_line, whole(f, row.line)), "", null)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .topHairline(colors.outline)
            .toggleable(value = included, enabled = entry != null, role = Role.Checkbox, onValueChange = { onToggle(row) })
            .padding(horizontal = Dimens.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .border(Dimens.hairline, if (entry != null) colors.textPrimary else colors.outline)
                .background(if (included) colors.textPrimary else colors.background),
            contentAlignment = Alignment.Center,
        ) {
            // The row is the checkbox for TalkBack; the tick is only its picture.
            if (included) FuelIcon(R.drawable.ic_check, null, tint = colors.background)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = type.body, color = if (entry != null) colors.textPrimary else colors.textSecondary)
            if (detail.isNotEmpty()) {
                Text(detail, style = type.meta, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (row.duplicate) Text(stringResource(R.string.import_duplicate), style = type.meta, color = colors.textSecondary)
            row.issues.forEach { issue ->
                Text(stringResource(issue.labelRes()), style = type.meta, color = if (issue.blocking) colors.danger else colors.warningInk)
            }
        }
        amount?.let { Text(it, style = type.body, color = if (included) colors.textPrimary else colors.textSecondary) }
    }
}

private fun DialectId.labelRes(): Int = when (this) {
    DialectId.FUEL_LOG -> R.string.import_format_fuel_log
    DialectId.FUELIO -> R.string.import_format_fuelio
    DialectId.ACAR -> R.string.import_format_acar
    DialectId.DRIVVO -> R.string.import_format_drivvo
    DialectId.GENERIC -> R.string.import_format_generic
}

private fun CharsetSniffer.Encoding.labelRes(): Int = when (this) {
    CharsetSniffer.Encoding.UTF_8 -> R.string.import_text_utf8
    CharsetSniffer.Encoding.UTF_16LE, CharsetSniffer.Encoding.UTF_16BE -> R.string.import_text_utf16
    CharsetSniffer.Encoding.WINDOWS_1252 -> R.string.import_text_1252
}

private fun Field.labelRes(): Int = when (this) {
    Field.DATE -> R.string.import_field_date
    Field.ODOMETER -> R.string.import_field_odometer
    Field.VOLUME -> R.string.import_field_volume
    Field.TOTAL -> R.string.import_field_total
    Field.UNIT_PRICE -> R.string.import_field_unit_price
    Field.FULL -> R.string.import_field_full
    Field.STATION -> R.string.import_field_station
    else -> R.string.import_field_note
}

private fun RowIssue.labelRes(): Int = when (this) {
    RowIssue.DATE_UNREADABLE -> R.string.import_issue_date
    RowIssue.VOLUME_UNREADABLE -> R.string.import_issue_volume
    RowIssue.AMOUNT_UNREADABLE -> R.string.import_issue_amount
    RowIssue.ODOMETER_UNREADABLE -> R.string.import_issue_odometer
    RowIssue.VALUE_UNKNOWN -> R.string.import_issue_value
    RowIssue.ODOMETER_MISSING -> R.string.import_issue_no_odometer
    RowIssue.VOLUME_ZERO -> R.string.import_issue_zero
    RowIssue.FUTURE_DATE -> R.string.import_issue_future
    RowIssue.CATEGORY_GUESSED -> R.string.import_issue_category
}

@Composable
private fun RowFilter.label(): String = stringResource(
    when (this) {
        RowFilter.ALL -> R.string.import_filter_all
        RowFilter.WARNINGS -> R.string.import_filter_warnings
        RowFilter.DUPLICATES -> R.string.import_filter_duplicates
        RowFilter.ERRORS -> R.string.import_filter_errors
    },
)
