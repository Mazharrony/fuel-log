package com.fuelexpenselog.app.domain

import com.fuelexpenselog.app.domain.format.NumberKind
import com.fuelexpenselog.app.domain.format.Rounding
import com.fuelexpenselog.app.domain.format.parseNumber
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The review-killer. A decimal-comma locale silently parsing "32,5" as 325 is a
 * tenfold error in a fuel volume, and it corrupts every consumption figure
 * downstream without ever looking broken.
 *
 * The locale is saved and restored around every test so one cannot leak into
 * its neighbours.
 */
class NumberInputTest {

    private lateinit var original: Locale

    @Before fun saveLocale() { original = Locale.getDefault() }
    @After fun restoreLocale() { Locale.setDefault(original) }

    private val locales = listOf(
        Locale.US, Locale.GERMANY, Locale.FRANCE, Locale.UK,
        Locale.forLanguageTag("de-CH"), Locale.forLanguageTag("ar-EG"),
        Locale.forLanguageTag("hi-IN"),
    )

    @Test
    fun `a comma decimal means the same thing in every locale`() {
        for (locale in locales) {
            Locale.setDefault(locale)
            assertThat(parseNumber("32,5", NumberKind.VOLUME)).isEqualTo(32.5)
            assertThat(parseNumber("32.5", NumberKind.VOLUME)).isEqualTo(32.5)
            assertThat(parseNumber("4550,00", NumberKind.MONEY)).isEqualTo(4550.0)
        }
    }

    @Test
    fun `a grouped odometer is read as a whole number`() {
        assertThat(parseNumber("48,210", NumberKind.ODOMETER)).isEqualTo(48210.0)
        assertThat(parseNumber("48.210", NumberKind.ODOMETER)).isEqualTo(48210.0)
        assertThat(parseNumber("1,234,567", NumberKind.ODOMETER)).isEqualTo(1234567.0)
        assertThat(parseNumber("126540", NumberKind.ODOMETER)).isEqualTo(126540.0)
    }

    @Test
    fun `an odometer with a genuine decimal is not mistaken for grouping`() {
        assertThat(parseNumber("48210.5", NumberKind.ODOMETER)).isEqualTo(48210.5)
        assertThat(parseNumber("48210,5", NumberKind.ODOMETER)).isEqualTo(48210.5)
    }

    @Test
    fun `mixed separators resolve by which one comes last`() {
        assertThat(parseNumber("1.234,56", NumberKind.MONEY)).isEqualTo(1234.56)
        assertThat(parseNumber("1,234.56", NumberKind.MONEY)).isEqualTo(1234.56)
    }

    @Test
    fun `grouping whitespace and apostrophes are stripped`() {
        // French NBSP, French narrow NBSP, Swiss apostrophe, plain space.
        assertThat(parseNumber("48 210", NumberKind.ODOMETER)).isEqualTo(48210.0)
        assertThat(parseNumber("48 210", NumberKind.ODOMETER)).isEqualTo(48210.0)
        assertThat(parseNumber("48'210", NumberKind.ODOMETER)).isEqualTo(48210.0)
        assertThat(parseNumber("48 210", NumberKind.ODOMETER)).isEqualTo(48210.0)
        assertThat(parseNumber("1 234,56", NumberKind.MONEY)).isEqualTo(1234.56)
    }

    @Test
    fun `non-ASCII keypad digits are accepted`() {
        // Arabic-Indic and Devanagari keyboards produce these directly.
        assertThat(parseNumber("٣٢,٥", NumberKind.VOLUME)).isEqualTo(32.5)
        assertThat(parseNumber("२०", NumberKind.VOLUME)).isEqualTo(20.0)
    }

    @Test
    fun `junk is rejected rather than half-parsed`() {
        assertThat(parseNumber("", NumberKind.VOLUME)).isNull()
        assertThat(parseNumber("   ", NumberKind.VOLUME)).isNull()
        assertThat(parseNumber("abc", NumberKind.VOLUME)).isNull()
        assertThat(parseNumber("32l", NumberKind.VOLUME)).isNull()
        assertThat(parseNumber(".", NumberKind.VOLUME)).isNull()
        assertThat(parseNumber("-5", NumberKind.ODOMETER)).isNull()
    }

    @Test
    fun `machine-readable output stays dot-separated under a comma locale`() {
        Locale.setDefault(Locale.GERMANY)

        // String.format("%.2f") here would emit "4550,00" and corrupt the CSV
        // for everyone who opened it afterwards.
        assertThat(Rounding.toPlainString(4550.0, 2)).isEqualTo("4550.00")
        assertThat(Rounding.toPlainString(32.5, 2)).isEqualTo("32.50")
        assertThat(Rounding.toPlainString(4549.999999999999, 2)).isEqualTo("4550.00")
    }
}
