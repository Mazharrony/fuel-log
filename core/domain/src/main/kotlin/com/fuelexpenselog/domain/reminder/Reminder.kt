package com.fuelexpenselog.domain.reminder

import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.time.CivilDate

/** What a reminder counts: the calendar, the odometer, or whichever comes first. */
enum class ReminderKind {
    DATE,
    DISTANCE,
    BOTH,
    ;

    val usesDate: Boolean get() = this != DISTANCE
    val usesDistance: Boolean get() = this != DATE
}

/**
 * A job that comes round again: "Oil change every 6 months or 10,000 km".
 *
 * The anchor is when it was last done. Each due value is the anchor plus its repeat, written
 * when the reminder is saved or completed and never nudged along by the calendar. The due
 * values are stored rather than derived because the daily check scans them through an index.
 * A reminder with no repeat is a one-off, and completing it retires it.
 */
data class Reminder(
    val id: Long,
    val vehicleId: Long,
    val title: String,
    val kind: ReminderKind,
    /** What completing it logs the cost as. Null files it under Other. */
    val category: ExpenseCategory?,
    val dueDate: CivilDate?,
    val repeatMonths: Int?,
    val warnDaysBefore: Int = DEFAULT_WARN_DAYS,
    val dueOdometerM: Long?,
    val repeatDistanceM: Long?,
    val warnDistanceM: Long = DEFAULT_WARN_DISTANCE_M,
    val anchorDate: CivilDate?,
    val anchorOdometerM: Long?,
    val isActive: Boolean = true,
    /**
     * Whether the opt-in daily check may post about it. On by default: turning notifications
     * on in Settings is the opt-in, and a reminder that silently never notified would be a
     * trap nobody could see.
     */
    val notifyEnabled: Boolean = true,
    /** The day the daily check last posted about it, so it cannot nag every morning. */
    val lastNotified: CivilDate? = null,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    /** Something comes round again after completion. Otherwise completing it is the end. */
    val repeats: Boolean
        get() = (kind.usesDate && repeatMonths != null) || (kind.usesDistance && repeatDistanceM != null)

    companion object {
        const val DEFAULT_WARN_DAYS = 30
        const val DEFAULT_WARN_DISTANCE_M = 500_000L

        /**
         * The warning window for a repeat. Thirty days ahead of a monthly car wash would make
         * it permanently "due soon", so a short repeat gets a proportionally short window: a
         * week per month, up to the default.
         */
        fun warnDaysFor(repeatMonths: Int?): Int =
            repeatMonths?.let { minOf(DEFAULT_WARN_DAYS, it * 7) } ?: DEFAULT_WARN_DAYS

        /** The same for distance: a tenth of the repeat, up to the default 500 km. */
        fun warnDistanceFor(repeatDistanceM: Long?): Long =
            repeatDistanceM?.let { minOf(DEFAULT_WARN_DISTANCE_M, it / 10) } ?: DEFAULT_WARN_DISTANCE_M
    }
}

/** One time the job was done. The expense it logged, if any, lives on as an ordinary expense. */
data class ReminderCompletion(
    val id: Long,
    val reminderId: Long,
    val vehicleId: Long,
    val date: CivilDate,
    val odometerM: Long?,
    val expenseId: Long?,
    val note: String?,
    val createdAtMillis: Long = 0,
)

/**
 * Where a reminder stands today.
 *
 * Zero left means due today, or due at this very reading. A distance reminder on a vehicle
 * with no reading at all is [Unknown] and renders as a dash - never as overdue.
 */
sealed interface ReminderStatus {
    data object Ok : ReminderStatus

    /** Inside the warning window. Each field is null when that half is not the one due. */
    data class DueSoon(val daysLeft: Long?, val metresLeft: Long?) : ReminderStatus

    /** Past it. Each field is null when that half is not the one overdue. */
    data class Overdue(val daysOver: Long?, val metresOver: Long?) : ReminderStatus

    data object Unknown : ReminderStatus

    val isDue: Boolean get() = this is DueSoon || this is Overdue
}
