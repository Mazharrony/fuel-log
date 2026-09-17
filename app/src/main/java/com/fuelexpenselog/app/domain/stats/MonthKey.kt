package com.fuelexpenselog.app.domain.stats

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * A calendar month as a single comparable int: year * 12 + (month - 1).
 * Cheap to sort, hash and step through, and it makes "the month before" a
 * subtraction rather than a date-library call.
 */
@JvmInline
value class MonthKey(val value: Int) : Comparable<MonthKey> {
    val year: Int get() = Math.floorDiv(value, 12)
    val month: Int get() = Math.floorMod(value, 12) + 1

    fun minusMonths(n: Int) = MonthKey(value - n)
    fun minusYears(n: Int) = MonthKey(value - 12 * n)
    fun toYearMonth(): YearMonth = YearMonth.of(year, month)

    override fun compareTo(other: MonthKey) = value.compareTo(other.value)
    override fun toString() = "%04d-%02d".format(year, month)

    companion object {
        fun of(year: Int, month: Int) = MonthKey(year * 12 + (month - 1))
        fun from(date: LocalDate) = of(date.year, date.monthValue)

        /**
         * The zone is always passed in, never read from the system. Bucketing
         * that depends on ambient state cannot be tested deterministically, and
         * a user who flies to another timezone should not see their month
         * totals shift.
         */
        fun from(epochMillis: Long, zone: ZoneId) =
            from(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())
    }
}
