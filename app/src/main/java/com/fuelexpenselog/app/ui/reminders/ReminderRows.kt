package com.fuelexpenselog.app.ui.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.dashOr
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderStatus
import com.fuelexpenselog.domain.unit.DistanceUnit

/** "Every 6 months or 10,000 km". Null for a reminder that does not repeat. */
@Composable
fun scheduleText(reminder: Reminder, unit: DistanceUnit): String? =
    ReminderWording.schedule(LocalResources.current, LocalFormatters.current, reminder, unit)

/**
 * Where it stands, in words: "12 days overdue", "Due in 300 km", "Due 26 Mar 2027 or at
 * 58,700 km". Null when there is nothing to count from; [StatusLine] shows a dash for it.
 */
@Composable
fun statusText(reminder: Reminder, status: ReminderStatus, unit: DistanceUnit): String? =
    ReminderWording.status(LocalResources.current, LocalFormatters.current, reminder, status, unit)

/** The next due date and reading, however far off: "Due 26 Mar 2027 or at 58,700 km". */
@Composable
fun dueText(reminder: Reminder, unit: DistanceUnit): String? =
    ReminderWording.due(LocalResources.current, LocalFormatters.current, reminder, unit)

/**
 * The status in its colour: overdue in the danger ink, due soon in the warning ink. Nothing
 * to count from is a dash with the reason - never "overdue", never a zero.
 */
@Composable
fun StatusLine(reminder: Reminder, status: ReminderStatus, unit: DistanceUnit, modifier: Modifier = Modifier) {
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    val text = statusText(reminder, status, unit)
    if (text == null) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(dashOr(null), style = type.meta, color = colors.textPrimary)
            Text(stringResource(R.string.reminder_unknown), style = type.meta, color = colors.textSecondary)
        }
    } else {
        val ink = when (status) {
            is ReminderStatus.Overdue -> colors.danger
            is ReminderStatus.DueSoon -> colors.warningInk
            else -> colors.textSecondary
        }
        Text(text, style = type.meta, color = ink, modifier = modifier)
    }
}

/**
 * One reminder: its name, how often it comes round, and where it stands. The row opens the
 * editor; "Done" marks it done.
 */
@Composable
fun ReminderRow(
    reminder: Reminder,
    status: ReminderStatus,
    unit: DistanceUnit,
    onOpen: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .topHairline(colors.outline)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = Dimens.gutter, end = 6.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(reminder.title, style = type.body, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            scheduleText(reminder, unit)?.let { Text(it, style = type.meta, color = colors.textSecondary) }
            StatusLine(reminder, status, unit)
        }
        Text(
            stringResource(R.string.reminder_done_action),
            style = type.button,
            color = colors.textPrimary,
            modifier = Modifier
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_reminder_done, reminder.title),
                    onClick = onDone,
                )
                .padding(horizontal = 14.dp, vertical = 15.dp),
        )
    }
}
