package com.fuelexpenselog.csv.imprt

import com.fuelexpenselog.domain.time.CivilDate
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

/** Which way round a file writes its dates. Separators do not matter; the order does. */
enum class DateOrder { YMD, DMY, MDY }

/** A date as written in the file, with the odometer on the same row when there is one. */
data class DateSample(val text: String, val odometer: Double?)

/**
 * Reads a file's dates without guessing. `01/02/2026` is the first of February to most of the
 * world and the second of January to the US, and a silent guess would file a year of fuel
 * under the wrong months.
 *
 * In order: an order is possible only if it reads every readable date in the file. Any date
 * with a day above 12 settles day-month against month-day on its own, since the other
 * reading has no such month. Failing that, each reading is checked against the odometer,
 * which only goes up: if exactly one reading keeps it rising, that one is it. Still two
 * readings: ask, showing a date from the file where they differ.
 */
object DateFormatResolver {

    sealed interface Result {
        data class Resolved(val order: DateOrder) : Result

        /** Every date reads more than one way. [example] is one from the file that shows it. */
        data class Ambiguous(val orders: List<DateOrder>, val example: String) : Result

        /** No date in the file reads any way at all. */
        data class Unreadable(val example: String) : Result

        data object NoDates : Result
    }

    fun resolve(samples: List<DateSample>): Result {
        val dated = samples.filter { it.text.isNotBlank() }
        if (dated.isEmpty()) return Result.NoDates
        // A row no order can read is that row's problem, not a vote on the file's format.
        val readable = dated.filter { s -> DateOrder.entries.any { parse(s.text, it) != null } }
        if (readable.isEmpty()) return Result.Unreadable(dated.first().text)

        val viable = DateOrder.entries.filter { order -> readable.all { parse(it.text, order) != null } }
        if (viable.size == 1) return Result.Resolved(viable.single())
        if (viable.isEmpty()) {
            // Mixed formats: offer every order that reads at least one row.
            val partial = DateOrder.entries.filter { order -> readable.any { parse(it.text, order) != null } }
            return Result.Ambiguous(partial, readable.first().text)
        }

        // When every date reads the same either way - 01/01, 02/02 - the order makes no difference.
        val differing = readable.firstOrNull { s -> viable.map { parse(s.text, it) }.distinct().size > 1 }
            ?: return Result.Resolved(viable.first())

        val consistent = viable.filter { order -> odometerRises(readable, order) }
        if (consistent.size == 1) return Result.Resolved(consistent.single())
        return Result.Ambiguous(viable, differing.text)
    }

    /** Whether, with dates read this way, the odometer never goes down from one day to a later one. */
    private fun odometerRises(samples: List<DateSample>, order: DateOrder): Boolean {
        val points = samples.mapNotNull { s -> s.odometer?.let { parse(s.text, order)!! to it } }
        if (points.size < 2) return false
        val sorted = points.sortedWith(compareBy<Pair<CivilDate, Double>> { it.first }.thenBy { it.second })
        return sorted.zipWithNext().all { (a, b) -> b.second >= a.second }
    }

    /**
     * The date at the start of [text] in this order. A time after it - "2026-01-15 10:30",
     * "2026-01-15T10:30" - is ignored here and read by [time]. Two-digit years are 2000-2069
     * and 1970-1999.
     */
    fun parse(text: String, order: DateOrder): CivilDate? {
        val token = datePart(text)
        val groups = Regex("""\d+""").findAll(token).map { it.value }.toList()
        val (year, month, day) = when {
            groups.size == 1 && groups[0].length == 8 && order == DateOrder.YMD ->
                Triple(groups[0].substring(0, 4), groups[0].substring(4, 6), groups[0].substring(6, 8))
            groups.size != 3 -> return null
            order == DateOrder.YMD -> if (groups[0].length == 4) Triple(groups[0], groups[1], groups[2]) else return null
            order == DateOrder.DMY -> Triple(groups[2], groups[1], groups[0])
            else -> Triple(groups[2], groups[0], groups[1])
        }
        if (order != DateOrder.YMD && year.length != 4 && year.length != 2) return null
        if (month.length > 2 || day.length > 2) return null
        val y = year.toInt().let { if (year.length == 2) (if (it < 70) 2000 + it else 1900 + it) else it }
        return try {
            CivilDate.of(LocalDate.of(y, month.toInt(), day.toInt()))
        } catch (invalid: DateTimeException) {
            null
        }
    }

    /**
     * How a date from the file is laid out, for the preview to show: "15.01.2026" read
     * day-first is `DD.MM.YYYY`, "1/4/26" read month-first is `MM/DD/YY`.
     */
    fun pattern(sample: String, order: DateOrder): String {
        val token = datePart(sample)
        val groups = Regex("""\d+""").findAll(token).toList()
        val names = when (order) {
            DateOrder.YMD -> listOf("YYYY", "MM", "DD")
            DateOrder.DMY -> listOf("DD", "MM", "YYYY")
            DateOrder.MDY -> listOf("MM", "DD", "YYYY")
        }
        if (groups.size != 3) return names.joinToString(if (order == DateOrder.YMD) "-" else "/")
        val out = StringBuilder(token)
        // Right to left, so earlier ranges stay where they are.
        for (i in 2 downTo 0) {
            val name = if (names[i] == "YYYY" && groups[i].value.length == 2) "YY" else names[i]
            out.replace(groups[i].range.first, groups[i].range.last + 1, name)
        }
        return out.toString()
    }

    /** The time of day after the date in [text], when there is one: "15/01/2026 07:30". */
    fun time(text: String): LocalTime? {
        val rest = text.trim().substringAfter(datePart(text), "").trim().removePrefix("T")
        return parseTime(rest)
    }

    /** "07:30", "7:30 PM", "19:30:05". Null for anything else. */
    fun parseTime(text: String): LocalTime? {
        val match = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([AaPp][Mm])?""").find(text.trim()) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues[3].ifEmpty { "0" }.toInt()
        when (match.groupValues[4].lowercase()) {
            "am" -> if (hour == 12) hour = 0
            "pm" -> if (hour < 12) hour += 12
        }
        return try {
            LocalTime.of(hour, minute, second)
        } catch (invalid: DateTimeException) {
            null
        }
    }

    private fun datePart(text: String): String = text.trim().split(' ', 'T').first()
}
