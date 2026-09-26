package com.fuelexpenselog.app.format

import android.text.format.DateFormat
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.time.MonthKey
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Dates for display. A [CivilDate] is already the date the user meant, so there is no
 * timezone anywhere in here - formatting it can never move it to another day.
 */
class DateFormatter(private val locale: Locale) {

    private val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    /** Locale skeletons rather than fixed patterns: "Sep 17" in en-US, "17 Sept" in en-GB. */
    private val dayMonth = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    private val monthYear = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMy"), locale)
    private val monthName = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMM"), locale)
    private val monthShort = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMM"), locale)

    /** "Sep 17, 2026". */
    fun medium(date: CivilDate): String = medium.format(date.toLocalDate())

    /** "Sep 17". */
    fun dayMonth(date: CivilDate): String = dayMonth.format(date.toLocalDate())

    /** "September 2026". */
    fun monthYear(month: MonthKey): String = monthYear.format(YearMonth.of(month.year, month.month))

    /** "September". */
    fun monthName(month: MonthKey): String = monthName.format(YearMonth.of(month.year, month.month))

    /** "Sep". */
    fun monthShort(month: MonthKey): String = monthShort.format(YearMonth.of(month.year, month.month))
}
