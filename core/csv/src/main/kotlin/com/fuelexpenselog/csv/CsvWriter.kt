package com.fuelexpenselog.csv

/**
 * RFC 4180, written the way spreadsheets read it: CRLF line endings, fields quoted only when
 * they must be, quotes doubled inside quotes.
 *
 * The optional byte-order mark is for Excel, which otherwise opens UTF-8 as the system code
 * page and turns "Café" into "CafÃ©" - in a file whose whole point is to be opened in Excel.
 */
class CsvWriter(
    private val out: Appendable,
    private val delimiter: Char = ',',
    writeBom: Boolean = false,
) {
    init {
        if (writeBom) out.append(BOM)
    }

    fun writeRow(fields: List<String?>) {
        fields.forEachIndexed { i, field ->
            if (i > 0) out.append(delimiter)
            out.append(escape(field.orEmpty()))
        }
        out.append("\r\n")
    }

    /**
     * Quoted when the field holds the delimiter, a quote or a line break, or starts or ends
     * with a space that a spreadsheet would otherwise trim away.
     */
    fun escape(field: String): String {
        val needsQuotes = field.any { it == delimiter || it == '"' || it == '\n' || it == '\r' } ||
            field.startsWith(' ') || field.endsWith(' ')
        return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    companion object {
        const val BOM = '﻿'
    }
}
