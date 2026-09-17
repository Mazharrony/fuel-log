package com.fuelexpenselog.app.domain.model

/** The combined, newest-first history shown on the vehicle screen. */
sealed interface HistoryEntry {
    val date: Long
    val totalCost: Double
    val odometer: Double?

    data class Fuel(val fillUp: FillUp) : HistoryEntry {
        override val date get() = fillUp.date
        override val totalCost get() = fillUp.totalCost
        override val odometer get() = fillUp.odometer
    }

    data class Cost(val expense: Expense) : HistoryEntry {
        override val date get() = expense.date
        override val totalCost get() = expense.totalCost
        override val odometer get() = expense.odometer
    }
}

fun buildHistory(fillUps: List<FillUp>, expenses: List<Expense>): List<HistoryEntry> =
    (fillUps.map(HistoryEntry::Fuel) + expenses.map(HistoryEntry::Cost))
        .sortedByDescending { it.date }
