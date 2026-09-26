package com.fuelexpenselog.csv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvWriterTest {

    private fun golden(name: String): String =
        String(checkNotNull(javaClass.getResourceAsStream("/golden/$name")).readBytes(), Charsets.UTF_8)

    @Test
    fun `quotes, commas, line breaks, CRLF and the BOM match the golden file byte for byte`() {
        val out = StringBuilder()
        val csv = CsvWriter(out, writeBom = true)
        csv.writeRow(listOf("plain", "with,comma", "with \"quote\"", "multi\nline", " leading space", ""))
        csv.writeRow(listOf("Café", null, "trailing space ", "\r\nCRLF inside", "=SUM(A1)", "12.50"))

        assertThat(out.toString()).isEqualTo(golden("writer.csv"))
    }

    @Test
    fun `a semicolon file quotes semicolons and leaves commas alone`() {
        val out = StringBuilder()
        CsvWriter(out, delimiter = ';').writeRow(listOf("32,5", "a;b"))
        assertThat(out.toString()).isEqualTo("32,5;\"a;b\"\r\n")
    }
}
