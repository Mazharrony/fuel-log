package com.fuelexpenselog.app.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.ChipGroup
import com.fuelexpenselog.app.ui.common.ConfirmDialog
import com.fuelexpenselog.app.ui.common.FuelIcon
import com.fuelexpenselog.app.ui.common.PrimaryButton
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.Segmented
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.time.CivilDate
import java.time.Instant
import java.time.ZoneOffset

/**
 * The header's date: "Today · Sep 17", or the date itself when back-dated. Tapping it opens
 * the platform date picker, which inherits the zero-radius theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(date: CivilDate, today: CivilDate?, onDate: (CivilDate) -> Unit, enabled: Boolean = true) {
    val f = LocalFormatters.current
    var open by rememberSaveable { mutableStateOf(false) }
    val text = if (date == today) stringResource(R.string.date_today, f.date.dayMonth(date)) else f.date.medium(date)
    val description = stringResource(R.string.cd_change_date)

    Text(
        text = text,
        style = FuelTheme.type.meta,
        color = FuelTheme.colors.textSecondary,
        modifier = Modifier
            .heightIn(min = Dimens.minTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description) { open = true }
            .padding(horizontal = 4.dp, vertical = 15.dp),
    )

    if (open) {
        // The picker speaks UTC midnight; a civil date is converted at that edge and nowhere
        // else, so no zone can move the day the user picked.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // Not LocalDate.ofInstant: that is API 34, and this app starts at 26.
                        onDate(CivilDate.of(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    open = false
                }) { Text(stringResource(android.R.string.ok), color = FuelTheme.colors.textPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.action_cancel), color = FuelTheme.colors.textPrimary)
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** For a new entry when more than one vehicle is active: which one this belongs to. */
@Composable
fun VehicleChooser(vehicles: List<Vehicle>, selected: Vehicle, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel(stringResource(R.string.entry_vehicle))
        ChipGroup(vehicles, vehicles.firstOrNull { it.id == selected.id }, { onSelect(it.id) }, { it.name })
    }
}

@Composable
fun TagChooser(tag: EntryTag, onTag: (EntryTag) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel(stringResource(R.string.entry_tag))
        Segmented(listOf(EntryTag.PERSONAL, EntryTag.BUSINESS), tag, onTag, { it.label() }, enabled = enabled)
    }
}

/**
 * The bottom row of an entry screen: the 56dp outlined "+" that opens the extra fields, and
 * the yellow save. The "+" is never yellow - there is one yellow action per screen.
 */
@Composable
fun EntryActions(
    saveText: String,
    canSave: Boolean,
    onSave: () -> Unit,
    moreOpen: Boolean,
    onToggleMore: () -> Unit,
) {
    val colors = FuelTheme.colors
    val moreLabel = stringResource(if (moreOpen) R.string.entry_fewer else R.string.entry_more)
    Row(
        modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 16.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.actionHeight)
                .border(Dimens.hairline, colors.textPrimary)
                .background(if (moreOpen) colors.surfaceAlt else colors.background)
                .clickable(role = Role.Button, onClickLabel = moreLabel, onClick = onToggleMore),
            contentAlignment = Alignment.Center,
        ) {
            FuelIcon(R.drawable.ic_plus, moreLabel)
        }
        PrimaryButton(saveText, onSave, enabled = canSave, modifier = Modifier.weight(1f))
    }
}

/** The two-cell strip above the actions: what this tank produced, and what a unit of distance cost. */
@Composable
fun SummaryStrip(leftLabel: String, leftValue: String, leftNote: String?, rightLabel: String, rightValue: String) {
    val colors = FuelTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .topHairline(colors.outline),
    ) {
        SummaryCell(leftLabel, leftValue, leftNote, Modifier.weight(1f))
        Box(
            Modifier
                .width(Dimens.hairline)
                .fillMaxHeight()
                .background(colors.outline),
        )
        SummaryCell(rightLabel, rightValue, null, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(label: String, value: String, note: String?, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = Dimens.gutter, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SectionLabel(label)
        Text(value, style = FuelTheme.type.figureS, color = FuelTheme.colors.textPrimary)
        if (note != null) {
            Text(note, style = FuelTheme.type.meta, color = FuelTheme.colors.textSecondary)
        }
    }
}

/** The red delete row an edit ends with, confirmed before anything happens. */
@Composable
fun DeleteEntryRow(text: String, onDelete: () -> Unit) {
    val colors = FuelTheme.colors
    var confirm by rememberSaveable { mutableStateOf(false) }
    Text(
        text,
        style = FuelTheme.type.button,
        color = colors.danger,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .topHairline(colors.outline)
            .clickable(role = Role.Button) { confirm = true }
            .padding(horizontal = Dimens.gutter, vertical = 15.dp),
    )
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.entry_delete_title),
            body = stringResource(R.string.entry_delete_body),
            confirm = stringResource(R.string.action_delete),
            danger = true,
            onConfirm = {
                confirm = false
                onDelete()
            },
            onDismiss = { confirm = false },
        )
    }
}

/** A grey-ruled hint: advice about the form, not a warning about the input. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    val colors = FuelTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.surfaceAlt),
    ) {
        Box(
            Modifier
                .width(Dimens.warningRule)
                .fillMaxHeight()
                .background(colors.outlineStrong),
        )
        Text(
            text,
            style = FuelTheme.type.meta,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 12.dp, end = 15.dp, top = 13.dp, bottom = 13.dp),
        )
    }
}
