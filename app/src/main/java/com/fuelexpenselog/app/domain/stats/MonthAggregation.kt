package com.fuelexpenselog.app.domain.stats

import com.fuelexpenselog.app.domain.consumption.Span
import com.fuelexpenselog.app.domain.model.Expense
import com.fuelexpenselog.app.domain.model.ExpenseCategory
import com.fuelexpenselog.app.domain.model.FillUp
import java.time.ZoneId

/**
 * One month's figures for one vehicle.
 *
 * Money is exact: every record whose local date falls in the month is counted.
 *
 * Distance is an APPROXIMATION: a span's whole distance is credited to the month
 * containing its end date, because a span routinely straddles a month boundary
 * and there is no honest way to split it. The approximation is applied here and
 * only here, so the vehicle, months and month-detail screens cannot disagree -
 * two screens differing by 3 km is exactly the kind of thing that reads as a bug.
 */
data class MonthTotals(
    val month: MonthKey,
    val currency: String,
    val fuelCost: Double,
    val otherCost: Double,
    val byCategory: Map<ExpenseCategory, Double>,
    val distanceKm: Double,
    val fuelLitres: Double,
) {
    val totalCost: Double get() = fuelCost + otherCost

    /** Share of the month's spend that was fuel, for the split bar. */
    val fuelShare: Double get() = if (totalCost > 0.0) fuelCost / totalCost else 0.0
}

fun monthlyTotals(
    fillUps: List<FillUp>,
    expenses: List<Expense>,
    spans: List<Span>,
    currency: String,
    zone: ZoneId,
): List<MonthTotals> {
    val fuelByMonth = fillUps.groupBy { MonthKey.from(it.date, zone) }
    val expensesByMonth = expenses.groupBy { MonthKey.from(it.date, zone) }
    val spansByMonth = spans.groupBy { MonthKey.from(it.endDate, zone) }

    val months = (fuelByMonth.keys + expensesByMonth.keys + spansByMonth.keys).sorted()
    return months.map { month ->
        val monthFillUps = fuelByMonth[month].orEmpty()
        val monthExpenses = expensesByMonth[month].orEmpty()
        MonthTotals(
            month = month,
            currency = currency,
            fuelCost = monthFillUps.sumOf { it.totalCost },
            otherCost = monthExpenses.sumOf { it.totalCost },
            byCategory = monthExpenses.groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.totalCost } },
            distanceKm = spansByMonth[month].orEmpty().sumOf { it.distance },
            fuelLitres = monthFillUps.sumOf { it.volume },
        )
    }
}

/**
 * Change against the previous calendar month, or null when there is nothing to
 * compare against. Null renders as blank, not as 0%.
 */
fun monthOverMonthDelta(totals: List<MonthTotals>, month: MonthKey): Double? {
    val current = totals.firstOrNull { it.month == month } ?: return null
    val previous = totals.firstOrNull { it.month == month.minusMonths(1) } ?: return null
    if (previous.totalCost <= 0.0) return null
    return (current.totalCost - previous.totalCost) / previous.totalCost
}

/** The same month a year earlier, for the month-detail comparison. */
fun sameMonthLastYear(totals: List<MonthTotals>, month: MonthKey): MonthTotals? =
    totals.firstOrNull { it.month == month.minusYears(1) }
