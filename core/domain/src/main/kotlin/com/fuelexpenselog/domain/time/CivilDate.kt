package com.fuelexpenselog.domain.time

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The calendar date the user actually typed, as a single `yyyymmdd` integer.
 *
 * This is deliberately NOT a timestamp, and that is the whole point. An instant has to be
 * interpreted in a timezone before you know what month it belongs to, and a timezone is a
 * runtime value: a user who flies Tokyo to London would watch entries hop between months,
 * and historical UTC offset changes can rewrite a date that was correct when it was entered.
 *
 * As an integer it is timezone-free forever, sorts correctly, indexes perfectly, and a month
 * range is a plain `BETWEEN`. A timezone is consulted in exactly one place in this app -
 * deciding what "today" is - and it is passed in there, never read from ambient state.
 */
@JvmInline
value class CivilDate(val value: Int) : Comparable<CivilDate> {

    val year: Int get() = value / 10_000
    val month: Int get() = (value / 100) % 100
    val day: Int get() = value % 100

    val monthKey: MonthKey get() = MonthKey(value / 100)

    fun toLocalDate(): LocalDate = LocalDate.of(year, month, day)

    /**
     * Month arithmetic via java.time, so end-of-month clamping and leap years are somebody
     * else's already-correct problem: 31 Jan plus one month is 28 or 29 Feb, and 31 Aug plus
     * six months is the last day of February. Reminder repeats depend on this.
     */
    fun plusMonths(months: Long): CivilDate = of(toLocalDate().plusMonths(months))

    fun plusDays(days: Long): CivilDate = of(toLocalDate().plusDays(days))

    fun minusYears(years: Long): CivilDate = of(toLocalDate().minusYears(years))

    /** Days from this date to [other]; negative when [other] is earlier. */
    fun daysUntil(other: CivilDate): Long =
        ChronoUnit.DAYS.between(toLocalDate(), other.toLocalDate())

    override fun compareTo(other: CivilDate): Int = value.compareTo(other.value)

    /** Always ROOT: under an Arabic or Devanagari locale a default-locale format would emit
     *  non-ASCII digits, which would then be written into a CSV. */
    override fun toString(): String = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)

    companion object {
        fun of(date: LocalDate): CivilDate =
            CivilDate(date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth)

        fun of(year: Int, month: Int, day: Int): CivilDate = of(LocalDate.of(year, month, day))

        /** The zone is a parameter, never `ZoneId.systemDefault()` read in here. Bucketing
         *  that depends on ambient state cannot be tested deterministically. */
        fun today(zone: ZoneId): CivilDate = of(LocalDate.now(zone))

        /** Null rather than an exception for values arriving from a database or a CSV. */
        fun parseOrNull(value: Int): CivilDate? =
            runCatching { of(value / 10_000, (value / 100) % 100, value % 100) }.getOrNull()
    }
}

/**
 * A calendar month as one comparable integer, `yyyymm`.
 *
 * Stored in the same shape the date column yields with a single divide, so a month bucket is
 * `occurred_local_date / 100` with no conversion table in between. Arithmetic routes through
 * a month ordinal because `202601 - 1` is not December.
 */
@JvmInline
value class MonthKey(val value: Int) : Comparable<MonthKey> {

    val year: Int get() = value / 100
    val month: Int get() = value % 100

    private val ordinal: Int get() = year * 12 + (month - 1)

    fun plusMonths(months: Int): MonthKey = fromOrdinal(ordinal + months)

    fun minusMonths(months: Int): MonthKey = fromOrdinal(ordinal - months)

    fun minusYears(years: Int): MonthKey = MonthKey(value - years * 100)

    /** The first and last dates of this month, for range queries. */
    fun firstDay(): CivilDate = CivilDate(value * 100 + 1)

    fun lastDay(): CivilDate =
        CivilDate.of(java.time.YearMonth.of(year, month).atEndOfMonth())

    override fun compareTo(other: MonthKey): Int = value.compareTo(other.value)

    override fun toString(): String = String.format(Locale.ROOT, "%04d-%02d", year, month)

    companion object {
        fun of(year: Int, month: Int): MonthKey {
            require(month in 1..12) { "Month must be 1..12, got $month" }
            return MonthKey(year * 100 + month)
        }

        fun fromOrdinal(ordinal: Int): MonthKey =
            MonthKey(Math.floorDiv(ordinal, 12) * 100 + Math.floorMod(ordinal, 12) + 1)
    }
}
