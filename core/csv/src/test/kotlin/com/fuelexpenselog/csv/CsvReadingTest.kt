package com.fuelexpenselog.csv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The first two stages of import: bytes to text, text to rows. */
class CsvReadingTest {

    @Test
    fun `quoted fields keep delimiters, doubled quotes and line breaks, and rows remember their line`() {
        val text = "a,b,c\r\n\"1,5\",\"say \"\"hi\"\"\",\"two\nlines\"\r\n\r\nx,,z"

        val rows = CsvTokenizer.tokenize(text, ',')

        assertThat(rows.map { it.cells }).containsExactly(
            listOf("a", "b", "c"),
            listOf("1,5", "say \"hi\"", "two\nlines"),
            listOf("x", "", "z"),
        ).inOrder()
        // The blank line is skipped, and the row after a two-line field starts on line 5.
        assertThat(rows.map { it.line }).containsExactly(1, 2, 5).inOrder()
    }

    @Test
    fun `a lone CR ends a record too, and a missing final newline loses nothing`() {
        assertThat(CsvTokenizer.tokenize("a;b\rc;d", ';').map { it.cells })
            .containsExactly(listOf("a", "b"), listOf("c", "d")).inOrder()
    }

    @Test
    fun `excel's sep line decides the delimiter`() {
        assertThat(DelimiterSniffer.sniff(listOf("sep=;", "a,b;c", "1,5;2")).delimiter).isEqualTo(';')
        assertThat(DelimiterSniffer.declared("\"sep=|\"")).isEqualTo('|')
    }

    @Test
    fun `a semicolon file with comma decimals is read as semicolons`() {
        val lines = listOf("Datum;Liter;Betrag", "04.01.2026;40,0;60,00", "18.01.2026;32,5;48,90")
        assertThat(DelimiterSniffer.sniff(lines).delimiter).isEqualTo(';')
    }

    @Test
    fun `commas inside quotes do not vote, and tabs are found`() {
        assertThat(DelimiterSniffer.sniff(listOf("a;b", "\"1,234.5\";\"2,0\"")).delimiter).isEqualTo(';')
        assertThat(DelimiterSniffer.sniff(listOf("a\tb\tc", "1\t2\t3")).delimiter).isEqualTo('\t')
    }

    @Test
    fun `a byte-order mark decides, strict UTF-8 is next, and anything else is Windows-1252`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Straße".toByteArray(Charsets.UTF_8)
        assertThat(CharsetSniffer.decode(bom)).isEqualTo(CharsetSniffer.Decoded("Straße", CharsetSniffer.Encoding.UTF_8, hadBom = true))

        val utf8 = CharsetSniffer.decode("Odômetro".toByteArray(Charsets.UTF_8))
        assertThat(utf8.encoding).isEqualTo(CharsetSniffer.Encoding.UTF_8)
        assertThat(utf8.text).isEqualTo("Odômetro")

        // "Wäsche" in Windows-1252 is not valid UTF-8; read strictly, it is not mangled either.
        val latin = CharsetSniffer.decode("Wäsche €".toByteArray(charset("windows-1252")))
        assertThat(latin.encoding).isEqualTo(CharsetSniffer.Encoding.WINDOWS_1252)
        assertThat(latin.text).isEqualTo("Wäsche €")
    }
}
