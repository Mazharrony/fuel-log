package com.fuelexpenselog.csv

/**
 * Which character separates the columns. This matters in the EU: German and French Excel
 * write `;` because `,` is their decimal separator, so a file can hold `32,5;60,75`.
 *
 * Excel's own `sep=;` first line decides when present. Otherwise each candidate is counted,
 * outside quotes, on the first few non-empty lines: the one that splits every line into the
 * same number of columns wins, more columns before fewer.
 */
object DelimiterSniffer {

    data class Result(val delimiter: Char, val declared: Boolean)

    private val CANDIDATES = listOf(',', ';', '\t', '|')
    private const val SAMPLE_LINES = 5
    private val SEP_LINE = Regex("""^\s*"?sep=(.)"?\s*$""", RegexOption.IGNORE_CASE)

    /** The delimiter an Excel `sep=` line declares, or null when [line] is not one. */
    fun declared(line: String): Char? = SEP_LINE.find(line)?.groupValues?.get(1)?.single()

    fun sniff(lines: List<String>): Result {
        lines.firstOrNull { it.isNotBlank() }?.let(::declared)?.let { return Result(it, declared = true) }
        val sample = lines.filter { it.isNotBlank() }.take(SAMPLE_LINES)
        if (sample.isEmpty()) return Result(',', declared = false)

        val best = CANDIDATES
            .map { candidate -> candidate to sample.map { countOutsideQuotes(it, candidate) } }
            .filter { (_, counts) -> counts.min() > 0 }
            .maxWithOrNull(
                compareBy<Pair<Char, List<Int>>> { (_, counts) -> counts.distinct().size == 1 }
                    .thenBy { (_, counts) -> counts.min() },
            )
        return Result(best?.first ?: ',', declared = false)
    }

    private fun countOutsideQuotes(line: String, candidate: Char): Int {
        var inQuotes = false
        var count = 0
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == candidate && !inQuotes -> count++
            }
        }
        return count
    }
}
