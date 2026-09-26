package com.fuelexpenselog.domain.stats

import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.consumption.TimelinePoint
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.HistoryEntry
import com.fuelexpenselog.domain.money.CurrencySubtotals
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.time.MonthKey
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.Energy

/**
 * One calendar month of one vehicle. Money is always a list - one subtotal per currency -
 * because the app never adds two currencies together.
 */
data class MonthBucket(
    val month: MonthKey,
    val entryCount: Int,
    val fillUpCount: Int,
    val fuel: List<Money>,
    val other: List<Money>,
    val total: List<Money>,
    /** Null when the month's readings cannot say how far the vehicle went. */
    val distanceM: Long?,
)

/** A change against a previous period. [improved] already accounts for which way is good. */
data class Delta(val percent: Double, val improved: Boolean)

/** One row of "by category". A null category is fuel. */
data class CategoryBreakdown(
    val category: ExpenseCategory?,
    val count: Int,
    val subtotal: List<Money>,
)

/** "Oil change: 83,898, 312 km ago". */
data class LastDone(
    val category: ExpenseCategory,
    val date: CivilDate,
    val odometerM: Long?,
    val distanceAgoM: Long?,
)

/** The last N timeline slots, gaps included, and the figure across the measured ones. */
data class RecentSpans(
    val points: List<TimelinePoint>,
    /** Sum over sum across the Measured slots among [points]; null when there are none. */
    val kmPerUnit: Double?,
)

/**
 * Month buckets, deltas and category breakdowns - everything the statistics and months
 * screens show that is not a consumption span.
 *
 * Two rules run through all of it. **Months with no entries are omitted, never shown as
 * zero** - an empty month is not a month in which nothing was spent, it is a month nobody
 * logged. And **money goes through CurrencySubtotals**, so mixed currencies come out as
 * separate subtotals and never as one invented sum.
 */
object StatsCalculator {

    /** Only months that have entries, newest first. */
    fun months(entries: List<HistoryEntry>): List<MonthBucket> =
        entries.groupBy { it.date.monthKey }
            .map { (month, inMonth) ->
                val fuel = inMonth.filterIsInstance<HistoryEntry.Fuel>()
                val other = inMonth.filterIsInstance<HistoryEntry.Cost>()
                MonthBucket(
                    month = month,
                    entryCount = inMonth.size,
                    fillUpCount = fuel.size,
                    fuel = CurrencySubtotals.of(fuel.mapNotNull { it.amount }),
                    other = CurrencySubtotals.of(other.map { it.expense.amount }),
                    total = CurrencySubtotals.of(inMonth.mapNotNull { it.amount }),
                    distanceM = distanceDriven(entries, month),
                )
            }
            .sortedByDescending { it.month }

    /**
     * Spending against the previous period. Only comparable within one currency, and not
     * against nothing: null when either side is empty, mixed, or in another currency.
     * Spending less is the improvement.
     */
    fun moneyDelta(current: List<Money>, previous: List<Money>): Delta? {
        val now = current.singleOrNull() ?: return null
        val before = previous.singleOrNull() ?: return null
        if (now.currency != before.currency || before.micros <= 0L) return null
        val percent = (now.micros - before.micros) * 100.0 / before.micros
        return Delta(percent, improved = now.micros < before.micros)
    }

    /**
     * A consumption change, with both values already in [format]'s own units. L/100 km falls
     * as efficiency rises while MPG climbs, so "improved" inverts for the lower-is-better
     * formats - getting this backwards congratulates people for burning more fuel.
     */
    fun consumptionDelta(current: Double?, previous: Double?, format: ConsumptionFormat): Delta? {
        if (current == null || previous == null || previous <= 0.0 || !current.isFinite()) return null
        val percent = (current - previous) * 100.0 / previous
        val improved = if (format.lowerIsBetter) current < previous else current > previous
        return Delta(percent, improved)
    }

    /** Fuel first when there is any, then each expense category, largest first. */
    fun categories(entries: List<HistoryEntry>, month: MonthKey): List<CategoryBreakdown> {
        val inMonth = entries.filter { it.date.monthKey == month }
        val fuel = inMonth.filterIsInstance<HistoryEntry.Fuel>()
        val fuelRow = if (fuel.isEmpty()) {
            emptyList()
        } else {
            listOf(CategoryBreakdown(null, fuel.size, CurrencySubtotals.of(fuel.mapNotNull { it.amount })))
        }
        val expenseRows = inMonth.filterIsInstance<HistoryEntry.Cost>()
            .groupBy { it.expense.category }
            .map { (category, rows) -> CategoryBreakdown(category, rows.size, CurrencySubtotals.of(rows.map { it.expense.amount })) }
            .sortedByDescending { row -> row.subtotal.firstOrNull()?.micros ?: 0L }
        return fuelRow + expenseRows
    }

    fun yearTotals(buckets: List<MonthBucket>, year: Int): List<Money> =
        CurrencySubtotals.of(buckets.filter { it.month.year == year }.flatMap { it.total })

    /**
     * How far the vehicle went in [month]: its highest reading that month, less the highest
     * reading before the month began. Without an earlier reading, the spread of the month's
     * own readings. Null when there is nothing to measure against - never a zero, which
     * would claim the vehicle stood still.
     */
    fun distanceDriven(entries: List<HistoryEntry>, month: MonthKey): Long? {
        val start = month.firstDay()
        val readings = entries.filter { it.date.monthKey == month }.mapNotNull { it.odometerM }
        if (readings.isEmpty()) return null
        val before = entries.filter { it.date < start }.mapNotNull { it.odometerM }.maxOrNull()
        val distance = when {
            before != null -> readings.max() - before
            readings.size >= 2 -> readings.max() - readings.min()
            else -> return null
        }
        return distance.takeIf { it > 0 }
    }

    /** The most recent expense in [category], and how far the vehicle has gone since. */
    fun lastDone(entries: List<HistoryEntry>, category: ExpenseCategory, currentOdometerM: Long?): LastDone? {
        val last = entries.filterIsInstance<HistoryEntry.Cost>()
            .filter { it.expense.category == category }
            .maxWithOrNull(compareBy<HistoryEntry.Cost> { it.date.value }.thenBy { it.instantMillis })
            ?: return null
        val ago = if (currentOdometerM != null && last.odometerM != null) {
            (currentOdometerM - last.odometerM!!).takeIf { it >= 0 }
        } else {
            null
        }
        return LastDone(category, last.date, last.odometerM, ago)
    }

    /**
     * The last [n] slots of the timeline, gaps included, so a hole in the data keeps its
     * place. The header figure is sum over sum across the measured slots among them, so the
     * header and the bars always describe the same data.
     */
    fun recent(timeline: List<TimelinePoint>, n: Int): RecentSpans {
        val points = timeline.takeLast(n)
        return RecentSpans(points, sumOverSum(points.filterIsInstance<Measured>()))
    }

    /** The month's consumption: every span that ended in it, summed, never averaged. */
    fun monthKmPerUnit(measured: List<Measured>, month: MonthKey): Double? =
        sumOverSum(measured.filter { it.endDate.monthKey == month })

    private fun sumOverSum(measured: List<Measured>): Double? {
        if (measured.isEmpty()) return null
        return ConsumptionFormat.kmPerUnit(
            measured.sumOf { it.distanceM },
            Energy(measured.first().kind, measured.sumOf { it.energy.micro }),
        )
    }
}
