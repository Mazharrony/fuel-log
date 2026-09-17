package com.fuelexpenselog.app.domain.stats

import com.fuelexpenselog.app.domain.model.Expense
import com.fuelexpenselog.app.domain.model.ExpenseCategory

/**
 * "Oil change - 48,200 km, 4,300 km ago."
 *
 * Service reminders without the reminders. Because maintenance entries carry an
 * odometer reading, this delivers most of what people want from service alerts
 * as a computed line on a screen they are already looking at - with no
 * notification scheduling, no exact-alarm permission, no per-manufacturer
 * battery-optimisation fights and no background work at all. Those are the
 * single largest source of one-star reviews in every reminder app going.
 */
data class LastDone(
    val category: ExpenseCategory,
    val atOdometerKm: Double?,
    val date: Long,
    /** Null when the entry had no odometer reading - render the date alone. */
    val sinceKm: Double?,
)

fun lastDone(expenses: List<Expense>, currentOdometerKm: Double?): List<LastDone> =
    expenses.asSequence()
        .filter { it.category.isMaintenance }
        .groupBy { it.category }
        .map { (category, entries) ->
            // Highest odometer wins; fall back to the latest date when no entry
            // in the group recorded a reading.
            val latest = entries.filter { it.odometer != null }.maxByOrNull { it.odometer!! }
                ?: entries.maxBy { it.date }
            LastDone(
                category = category,
                atOdometerKm = latest.odometer,
                date = latest.date,
                sinceKm = if (latest.odometer != null && currentOdometerKm != null) {
                    (currentOdometerKm - latest.odometer).takeIf { it >= 0.0 }
                } else null,
            )
        }
        .sortedByDescending { it.date }
