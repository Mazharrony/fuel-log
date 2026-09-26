package com.fuelexpenselog.app.ui.reminders

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.pluralStringResource
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
import java.text.NumberFormat

/** "Every 6 months or 10,000 km". Null for a reminder that does not repeat. */
@Composable
fun scheduleText(reminder: Reminder, unit: DistanceUnit): String? {
    val f = LocalFormatters.current
    val months = reminder.repeatMonths?.takeIf { reminder.kind.usesDate }?.let { count ->
        pluralStringResource(R.plurals.reminder_months, count, NumberFormat.getIntegerInstance(f.locale).format(count))
    }
    val distance = reminder.repeatDistanceM?.takeIf { reminder.kind.usesDistance }?.let { f.distance.format(it, unit) }
    val every = joined(R.string.reminder_or, months, distance) ?: return null
    return stringResource(R.string.reminder_every, every)
}

/**
 * Where it stands, in words: "12 days overdue", "Due in 300 km", "Due 26 Mar 2027 or at
 * 58,700 km". Null when there is nothing to count from; [StatusLine] shows a dash for it.
 */
@Composable
fun statusText(reminder: Reminder, status: ReminderStatus, unit: DistanceUnit): String? = when (status) {
    is ReminderStatus.Overdue ->
        joined(R.string.reminder_and, days(status.daysOver), distance(status.metresOver, unit))
            ?.let { stringResource(R.string.reminder_overdue, it) }
    is ReminderStatus.DueSoon -> when {
        status.daysLeft == 0L -> stringResource(R.string.reminder_due_today)
        status.metresLeft == 0L -> stringResource(R.string.reminder_due_now)
        else -> joined(R.string.reminder_or, days(status.daysLeft), distance(status.metresLeft, unit))
            ?.let { stringResource(R.string.reminder_due_in, it) }
    }
    ReminderStatus.Ok -> dueText(reminder, unit)
    ReminderStatus.Unknown -> null
}

/** The next due date and reading, however far off: "Due 26 Mar 2027 or at 58,700 km". */
@Composable
fun dueText(reminder: Reminder, unit: DistanceUnit): String? {
    val f = LocalFormatters.current
    val date = reminder.dueDate?.takeIf { reminder.kind.usesDate }?.let { f.date.medium(it) }
    val at = reminder.dueOdometerM?.takeIf { reminder.kind.usesDistance }?.let { f.distance.format(it, unit) }
    return when {
        date != null && at != null -> stringResource(R.string.reminder_due_on_or_at, date, at)
        date != null -> stringResource(R.string.reminder_due_on, date)
        at != null -> stringResource(R.string.reminder_due_at, at)
        else -> null
    }
}

@Composable
private fun days(count: Long?): String? = count?.let {
    val f = LocalFormatters.current
    pluralStringResource(R.plurals.reminder_days, it.toInt(), NumberFormat.getIntegerInstance(f.locale).format(it))
}

@Composable
private fun distance(metres: Long?, unit: DistanceUnit): String? =
    metres?.let { LocalFormatters.current.distance.format(it, unit) }

@Composable
private fun joined(@StringRes pattern: Int, a: String?, b: String?): String? = when {
    a != null && b != null -> stringResource(pattern, a, b)
    else -> a ?: b
}

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
