package com.fuelexpenselog.app.data.repo

import com.fuelexpenselog.app.data.db.entity.ReminderCompletionEntity
import com.fuelexpenselog.app.data.db.entity.ReminderEntity
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderCompletion
import com.fuelexpenselog.domain.reminder.ReminderKind
import com.fuelexpenselog.domain.time.CivilDate

/**
 * A kind this build does not know reads as BOTH, which counts whatever due values the row
 * has: a lenient decode, like a category, because no unit or amount hangs on it.
 */
fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id,
    vehicleId = vehicleId,
    title = title,
    kind = decodeOr(kind, ReminderKind.BOTH),
    category = category?.let { decodeOr(it, ExpenseCategory.OTHER) },
    dueDate = dueLocalDate?.let { CivilDate(it) },
    repeatMonths = repeatMonths,
    warnDaysBefore = warnDaysBefore,
    dueOdometerM = dueOdometerM,
    repeatDistanceM = repeatDistanceM,
    warnDistanceM = warnDistanceM,
    anchorDate = anchorLocalDate?.let { CivilDate(it) },
    anchorOdometerM = anchorOdometerM,
    isActive = isActive,
    notifyEnabled = notifyEnabled,
    lastNotified = lastNotifiedLocalDate?.let { CivilDate(it) },
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun Reminder.toEntity(nowMillis: Long, createdAtMillis: Long = nowMillis): ReminderEntity = ReminderEntity(
    id = id,
    vehicleId = vehicleId,
    title = title,
    kind = kind.name,
    category = category?.name,
    dueLocalDate = dueDate?.value,
    repeatMonths = repeatMonths,
    warnDaysBefore = warnDaysBefore,
    dueOdometerM = dueOdometerM,
    repeatDistanceM = repeatDistanceM,
    warnDistanceM = warnDistanceM,
    anchorLocalDate = anchorDate?.value,
    anchorOdometerM = anchorOdometerM,
    isActive = isActive,
    notifyEnabled = notifyEnabled,
    lastNotifiedLocalDate = lastNotified?.value,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = nowMillis,
)

fun ReminderCompletionEntity.toDomain(): ReminderCompletion = ReminderCompletion(
    id = id,
    reminderId = reminderId,
    vehicleId = vehicleId,
    date = CivilDate(completedLocalDate),
    odometerM = odometerM,
    expenseId = expenseId,
    note = note,
    createdAtMillis = createdAtMillis,
)
