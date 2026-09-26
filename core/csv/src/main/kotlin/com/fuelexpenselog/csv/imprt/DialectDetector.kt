package com.fuelexpenselog.csv.imprt

import com.fuelexpenselog.csv.CsvTable

/** A file split into its sections. Most files are one unnamed section. */
data class CsvDocument(val sections: List<CsvSection>) {
    val isSectioned: Boolean get() = sections.any { it.name != null }

    fun section(name: String?): CsvTable? =
        sections.firstOrNull { it.name.equals(name, ignoreCase = true) }?.table

    /** The table a single-section reading works on: the only one, or the largest. */
    val main: CsvTable? get() = sections.maxByOrNull { it.table.rows.size }?.table
}

data class CsvSection(val name: String?, val table: CsvTable)

/**
 * Which app wrote a file. Score, never guess: a named dialect needs every required column
 * and at least one header only that app writes. Below the threshold, or on a tie, the answer
 * is [DialectId.GENERIC], where the user confirms the columns - a wrong silent guess would
 * put someone's odometer in their volume column.
 */
object DialectDetector {

    const val THRESHOLD = 4

    data class Scored(val dialect: DialectId, val score: Int)

    fun detect(document: CsvDocument): DialectId {
        val scored = Dialects.SCORED.map { Scored(it.id, score(it, document)) }.sortedByDescending { it.score }
        val best = scored.first()
        val runnerUp = scored.getOrNull(1)
        return when {
            best.score < THRESHOLD -> DialectId.GENERIC
            runnerUp != null && runnerUp.score == best.score -> DialectId.GENERIC
            else -> best.dialect
        }
    }

    /** Every scored dialect, best first, for tests and for the curious. */
    fun scores(document: CsvDocument): List<Scored> =
        Dialects.SCORED.map { Scored(it.id, score(it, document)) }.sortedByDescending { it.score }

    fun score(dialect: Dialect, document: CsvDocument): Int {
        // A sectioned file is only ever a sectioned dialect's, and the other way round.
        if ((dialect.sectionMarker != null) != document.isSectioned) return 0

        var matched = 0
        var anyTable = false
        for (spec in dialect.tables) {
            val table = (if (spec.section != null) document.section(spec.section) else document.main) ?: continue
            val mapping = Headers.map(table.header, spec.columns)
            if (spec.columns.any { it.required && it.field !in mapping }) continue
            anyTable = true
            matched += mapping.size
        }
        if (!anyTable) return 0

        val headers = document.sections.flatMap { it.table.header }.map { Headers.parse(it).name }.toSet()
        val signatures = dialect.signature.count { Headers.parse(it).name in headers }
        if (signatures == 0) return 0
        return matched + 2 * signatures
    }
}
