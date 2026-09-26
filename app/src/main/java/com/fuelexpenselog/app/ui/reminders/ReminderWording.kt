package com.fuelexpenselog.app.ui.reminders

import android.content.res.Resources
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.format.Formatters
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderStatus
import com.fuelexpenselog.domain.unit.DistanceUnit
import java.text.NumberFormat

/**
 * A reminder in words, for a screen and for a notification alike, so the two never describe
 * the same reminder differently.
 */
object ReminderWording {

    /** "Every 6 months or 10,000 km". Null for a reminder that does not repeat. */
    fun schedule(res: Resources, f: Formatters, reminder: Reminder, unit: DistanceUnit): String? {
        val months = reminder.repeatMonths?.takeIf { reminder.kind.usesDate }?.let { count ->
            res.getQuantityString(R.plurals.reminder_months, count, whole(f, count.toLong()))
        }
        val distance = reminder.repeatDistanceM?.takeIf { reminder.kind.usesDistance }?.let { f.distance.format(it, unit) }
        val every = joined(res, R.string.reminder_or, months, distance) ?: return null
        return res.getString(R.string.reminder_every, every)
    }

    /**
     * Where it stands: "12 days overdue", "Due in 300 km", "Due 26 Mar 2027 or at 58,700 km".
     * Null when there is nothing to count from - a dash on screen, never "overdue".
     */
    fun status(res: Resources, f: Formatters, reminder: Reminder, status: ReminderStatus, unit: DistanceUnit): String? = when (status) {
        is ReminderStatus.Overdue ->
            joined(res, R.string.reminder_and, days(res, f, status.daysOver), distance(f, status.metresOver, unit))
                ?.let { res.getString(R.string.reminder_overdue, it) }
        is ReminderStatus.DueSoon -> when {
            status.daysLeft == 0L -> res.getString(R.string.reminder_due_today)
            status.metresLeft == 0L -> res.getString(R.string.reminder_due_now)
            else -> joined(res, R.string.reminder_or, days(res, f, status.daysLeft), distance(f, status.metresLeft, unit))
                ?.let { res.getString(R.string.reminder_due_in, it) }
        }
        ReminderStatus.Ok -> due(res, f, reminder, unit)
        ReminderStatus.Unknown -> null
    }

    /** The next due date and reading, however far off. */
    fun due(res: Resources, f: Formatters, reminder: Reminder, unit: DistanceUnit): String? {
        val date = reminder.dueDate?.takeIf { reminder.kind.usesDate }?.let { f.date.medium(it) }
        val at = reminder.dueOdometerM?.takeIf { reminder.kind.usesDistance }?.let { f.distance.format(it, unit) }
        return when {
            date != null && at != null -> res.getString(R.string.reminder_due_on_or_at, date, at)
            date != null -> res.getString(R.string.reminder_due_on, date)
            at != null -> res.getString(R.string.reminder_due_at, at)
            else -> null
        }
    }

    private fun days(res: Resources, f: Formatters, count: Long?): String? =
        count?.let { res.getQuantityString(R.plurals.reminder_days, it.toInt(), whole(f, it)) }

    private fun distance(f: Formatters, metres: Long?, unit: DistanceUnit): String? = metres?.let { f.distance.format(it, unit) }

    private fun joined(res: Resources, pattern: Int, a: String?, b: String?): String? = when {
        a != null && b != null -> res.getString(pattern, a, b)
        else -> a ?: b
    }

    private fun whole(f: Formatters, n: Long): String = NumberFormat.getIntegerInstance(f.locale).format(n)
}
