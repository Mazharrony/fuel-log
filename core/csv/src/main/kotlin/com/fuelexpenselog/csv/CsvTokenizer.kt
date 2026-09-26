package com.fuelexpenselog.csv

/** One record: its cells, and the line of the file it starts on, for the preview to point at. */
data class CsvRow(val line: Int, val cells: List<String>) {
    /** The cell at [index], trimmed, or empty when the column is not mapped or the row is short. */
    fun cell(index: Int?): String = index?.let { cells.getOrNull(it) }?.trim().orEmpty()
}

/** A header and the rows under it. */
data class CsvTable(val header: List<String>, val rows: List<CsvRow>)

/**
 * RFC 4180, read leniently: quoted fields may hold the delimiter, doubled quotes and line
 * breaks; CRLF, LF and a lone CR all end a record. Blank lines are skipped. Nothing is
 * trimmed here - the whitespace inside a quoted note is the note.
 */
object CsvTokenizer {

    fun tokenize(text: String, delimiter: Char, firstLine: Int = 1): List<CsvRow> {
        val rows = mutableListOf<CsvRow>()
        val cells = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var quotedField = false
        var line = firstLine
        var recordLine = firstLine
        var i = 0

        fun endField() {
            cells += field.toString()
            field.setLength(0)
            quotedField = false
        }

        fun endRecord() {
            endField()
            val blank = cells.size == 1 && cells[0].isEmpty()
            if (!blank) rows += CsvRow(recordLine, cells.toList())
            cells.clear()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> {
                        if (c == '\n' || (c == '\r' && (i + 1 >= text.length || text[i + 1] != '\n'))) line++
                        field.append(c)
                    }
                }
            } else {
                when (c) {
                    // A quote opens a quoted field only at its start; anywhere else it is text.
                    '"' -> if (field.isEmpty() && !quotedField) {
                        inQuotes = true
                        quotedField = true
                    } else {
                        field.append(c)
                    }
                    delimiter -> endField()
                    '\r', '\n' -> {
                        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        endRecord()
                        line++
                        recordLine = line
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || cells.isNotEmpty() || quotedField) endRecord()
        return rows
    }
}
