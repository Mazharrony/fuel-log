package com.fuelexpenselog.domain.reminder

import com.fuelexpenselog.domain.parse.DecimalParser
import com.fuelexpenselog.domain.parse.NumberKind
import com.fuelexpenselog.domain.unit.DistanceUnit

/** Reads what the reminder editor was typed. The same parser as every other number in the app. */
object ReminderInput {

    /** Ten years. Longer is a typo, not a service interval. */
    const val MAX_MONTHS = 120

    /** A whole number of months, one to [MAX_MONTHS]. */
    fun months(text: String): Int? {
        val value = DecimalParser.parseOrNull(text, NumberKind.ODOMETER) ?: return null
        if (value % 1.0 != 0.0 || value < 1.0 || value > MAX_MONTHS) return null
        return value.toInt()
    }

    /** A positive distance, typed in the vehicle's unit, in metres. */
    fun distanceM(text: String, unit: DistanceUnit): Long? =
        DecimalParser.parseOrNull(text, NumberKind.ODOMETER)
            ?.takeIf { it > 0.0 }
            ?.let { unit.toMetres(it) }

    /**
     * A reminder needs a name and a repeat for each thing it counts. That is the whole rule:
     * everything else, a missing reading included, is saved and shown as it stands.
     */
    fun canSave(title: String, kind: ReminderKind, repeatMonths: Int?, repeatDistanceM: Long?): Boolean =
        title.isNotBlank() &&
            (!kind.usesDate || repeatMonths != null) &&
            (!kind.usesDistance || repeatDistanceM != null)
}
