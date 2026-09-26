package com.fuelexpenselog.domain.reminder

import com.fuelexpenselog.domain.time.CivilDate

/**
 * Reminders are evaluated on read: no background work and no permission. Every screen that
 * shows one asks here with the vehicle's current reading and today's date, and gets the same
 * answer.
 */
object ReminderEvaluator {

    /** The due dates and readings a reminder anchored at this date and reading would have. */
    data class Schedule(val dueDate: CivilDate?, val dueOdometerM: Long?)

    /**
     * Active reminders with their status, most urgent first: overdue, due soon, fine, and
     * last the ones that cannot be counted yet. [latestOdometerByVehicle] is each vehicle's
     * current reading, the highest from fill-ups and expenses both.
     */
    fun evaluate(
        reminders: List<Reminder>,
        latestOdometerByVehicle: Map<Long, Long?>,
        today: CivilDate,
    ): List<Pair<Reminder, ReminderStatus>> =
        reminders
            .filter { it.isActive }
            .map { it to status(it, latestOdometerByVehicle[it.vehicleId], today) }
            .sortedWith(URGENCY)

    fun status(reminder: Reminder, odometerM: Long?, today: CivilDate): ReminderStatus {
        val byDate = if (reminder.kind.usesDate) byDate(reminder, today) else null
        val byDistance = if (reminder.kind.usesDistance) byDistance(reminder, odometerM) else null
        return worse(byDate, byDistance)
    }

    private fun byDate(reminder: Reminder, today: CivilDate): ReminderStatus {
        val due = reminder.dueDate ?: return ReminderStatus.Unknown
        val left = today.daysUntil(due)
        return when {
            left < 0 -> ReminderStatus.Overdue(daysOver = -left, metresOver = null)
            left <= reminder.warnDaysBefore -> ReminderStatus.DueSoon(daysLeft = left, metresLeft = null)
            else -> ReminderStatus.Ok
        }
    }

    /** No reading means nothing to count against: Unknown, never Overdue. */
    private fun byDistance(reminder: Reminder, odometerM: Long?): ReminderStatus {
        val due = reminder.dueOdometerM ?: return ReminderStatus.Unknown
        val now = odometerM ?: return ReminderStatus.Unknown
        val left = due - now
        return when {
            left < 0 -> ReminderStatus.Overdue(daysOver = null, metresOver = -left)
            left <= reminder.warnDistanceM -> ReminderStatus.DueSoon(daysLeft = null, metresLeft = left)
            else -> ReminderStatus.Ok
        }
    }

    /**
     * BOTH reports the worse of its two halves. A half that cannot be counted defers to the
     * one that can, so a car with no reading yet is still told when its date comes round.
     * When both halves are equally bad, both numbers are kept.
     */
    private fun worse(a: ReminderStatus?, b: ReminderStatus?): ReminderStatus {
        if (a == null) return b ?: ReminderStatus.Unknown
        if (b == null) return a
        val ra = rank(a)
        val rb = rank(b)
        return when {
            ra > rb -> a
            rb > ra -> b
            a is ReminderStatus.Overdue && b is ReminderStatus.Overdue ->
                ReminderStatus.Overdue(a.daysOver ?: b.daysOver, a.metresOver ?: b.metresOver)
            a is ReminderStatus.DueSoon && b is ReminderStatus.DueSoon ->
                ReminderStatus.DueSoon(a.daysLeft ?: b.daysLeft, a.metresLeft ?: b.metresLeft)
            else -> a
        }
    }

    private fun rank(status: ReminderStatus): Int = when (status) {
        is ReminderStatus.Overdue -> 3
        is ReminderStatus.DueSoon -> 2
        ReminderStatus.Ok -> 1
        ReminderStatus.Unknown -> 0
    }

    private val URGENCY: Comparator<Pair<Reminder, ReminderStatus>> =
        compareByDescending<Pair<Reminder, ReminderStatus>> { rank(it.second) }
            .thenBy { it.first.dueDate?.value ?: Int.MAX_VALUE }
            .thenBy { it.first.dueOdometerM ?: Long.MAX_VALUE }
            .thenBy { it.first.id }

    /**
     * Due values from an anchor: the date plus the repeat in months, the reading plus the
     * repeat in distance. `plusMonths` clamps month ends, so 31 Jan + 6 is 31 Jul and
     * 31 Aug + 6 is the last day of February. A half the kind does not count stays null.
     */
    fun schedule(
        kind: ReminderKind,
        repeatMonths: Int?,
        repeatDistanceM: Long?,
        anchorDate: CivilDate?,
        anchorOdometerM: Long?,
    ): Schedule = Schedule(
        dueDate = if (kind.usesDate && repeatMonths != null) anchorDate?.plusMonths(repeatMonths.toLong()) else null,
        dueOdometerM = if (kind.usesDistance && repeatDistanceM != null) anchorOdometerM?.plus(repeatDistanceM) else null,
    )

    /**
     * The reminder after being done on [completedOn] at [completedOdometerM].
     *
     * The anchor moves to the completion and the due values are recomputed from it - never
     * the old due value plus the repeat, so an oil change done late moves the next one out
     * instead of leaving the schedule behind for good. The notification memory resets, since
     * this is a new round. A one-off has nothing to come round again and is retired.
     */
    fun advance(reminder: Reminder, completedOn: CivilDate, completedOdometerM: Long?): Reminder {
        val next = schedule(reminder.kind, reminder.repeatMonths, reminder.repeatDistanceM, completedOn, completedOdometerM)
        return reminder.copy(
            dueDate = next.dueDate,
            dueOdometerM = next.dueOdometerM,
            anchorDate = completedOn,
            anchorOdometerM = completedOdometerM,
            isActive = reminder.repeats,
            lastNotified = null,
        )
    }

    /**
     * Whether the daily check should post about this reminder today: once when it first
     * comes due, and once more when it goes overdue - never twice in a day, and never every
     * morning. A notice given before the date passed is followed by one more; a distance
     * leaves no date it was passed on, so a reminder overdue only by distance has had its
     * one notice. Completing or rescheduling it starts a new round.
     */
    fun shouldNotify(status: ReminderStatus, lastNotified: CivilDate?, today: CivilDate): Boolean = when (status) {
        ReminderStatus.Ok, ReminderStatus.Unknown -> false
        is ReminderStatus.DueSoon -> lastNotified == null
        is ReminderStatus.Overdue -> {
            // daysOver 1 is the first overdue day, the day after the due date.
            val overdueSince = status.daysOver?.let { today.plusDays(1 - it) }
            lastNotified == null || (overdueSince != null && lastNotified < overdueSince)
        }
    }
}
