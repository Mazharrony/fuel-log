package com.fuelexpenselog.domain.parse

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * The parser is the review-killer. Every assertion here is a bug that would otherwise reach
 * someone's tax records as a plausible-looking wrong number.
 */
class DecimalParserTest {

    private lateinit var original: Locale

    @Before
    fun saveLocale() {
        original = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    private fun value(raw: String, kind: NumberKind): Double? =
        DecimalParser.parseOrNull(raw, kind)

    private fun reason(raw: String, kind: NumberKind): RejectReason? =
        (DecimalParser.parse(raw, kind) as? ParseOutcome.Rejected)?.reason

    // -- the core promise ---------------------------------------------------------------

    @Test
    fun `a comma decimal means the same thing in every locale`() {
        val locales = listOf(
            Locale.US,
            Locale.GERMANY,
            Locale.FRANCE,
            Locale.forLanguageTag("de-CH"),
            Locale.forLanguageTag("ar-EG"),
            Locale.forLanguageTag("hi-IN"),
            Locale.forLanguageTag("pt-BR"),
        )

        for (locale in locales) {
            Locale.setDefault(locale)

            assertThat(value("32,5", NumberKind.VOLUME)).isWithin(1e-9).of(32.5)
            assertThat(value("32.5", NumberKind.VOLUME)).isWithin(1e-9).of(32.5)
        }
    }

    @Test
    fun `mixed separators resolve by last occurrence`() {
        // The same number as written on two continents.
        assertThat(value("1.234,56", NumberKind.ODOMETER)).isWithin(1e-9).of(1234.56)
        assertThat(value("1,234.56", NumberKind.ODOMETER)).isWithin(1e-9).of(1234.56)
        assertThat(value("1.234.567,89", NumberKind.ODOMETER)).isWithin(1e-9).of(1234567.89)
        assertThat(value("1,234,567.89", NumberKind.ODOMETER)).isWithin(1e-9).of(1234567.89)
    }

    // -- the ambiguity that needs the field to resolve it --------------------------------

    @Test
    fun `three trailing digits mean grouping in an odometer and a fraction in a price`() {
        assertThat(value("48,210", NumberKind.ODOMETER)).isWithin(1e-9).of(48210.0)
        assertThat(value("1,234", NumberKind.MONEY_TOTAL)).isWithin(1e-9).of(1234.0)

        // Fuel is priced to three decimals across the EU and at the US pump.
        assertThat(value("1,459", NumberKind.UNIT_PRICE)).isWithin(1e-9).of(1.459)
        assertThat(value("1.459", NumberKind.UNIT_PRICE)).isWithin(1e-9).of(1.459)
        assertThat(value("48,210", NumberKind.VOLUME)).isWithin(1e-9).of(48.210)
    }

    @Test
    fun `a genuine odometer decimal is not mistaken for grouping`() {
        // tail is 1, not 3, so this is 48210 and a half - not 482105.
        assertThat(value("48210,5", NumberKind.ODOMETER)).isWithin(1e-9).of(48210.5)
        assertThat(value("48210.5", NumberKind.ODOMETER)).isWithin(1e-9).of(48210.5)
    }

    @Test
    fun `a leading separator is a fraction, never grouping`() {
        assertThat(value(",500", NumberKind.ODOMETER)).isWithin(1e-9).of(0.5)
    }

    // -- grouping has to actually be grouping ---------------------------------------------

    @Test
    fun `malformed grouping is rejected rather than guessed`() {
        // Without group-width validation this becomes 847,101,465,210.
        assertThat(reason("8471014.652.10", NumberKind.ODOMETER))
            .isEqualTo(RejectReason.MALFORMED_GROUPING)

        assertThat(reason("1,23,456", NumberKind.ODOMETER))
            .isEqualTo(RejectReason.MALFORMED_GROUPING)
        assertThat(reason("12,3456,789", NumberKind.ODOMETER))
            .isEqualTo(RejectReason.MALFORMED_GROUPING)
        assertThat(reason("1234,567,89", NumberKind.ODOMETER))
            .isEqualTo(RejectReason.MALFORMED_GROUPING)
    }

    @Test
    fun `well formed grouping is accepted`() {
        assertThat(value("1,234,567", NumberKind.ODOMETER)).isWithin(1e-9).of(1234567.0)
        assertThat(value("123,456", NumberKind.ODOMETER)).isWithin(1e-9).of(123456.0)
        assertThat(value("12,345,678", NumberKind.ODOMETER)).isWithin(1e-9).of(12345678.0)
    }

    // -- noise that carries no value -------------------------------------------------------

    @Test
    fun `space, non-breaking space and apostrophe grouping are stripped`() {
        assertThat(value("1 234,5", NumberKind.ODOMETER)).isWithin(1e-9).of(1234.5)
        assertThat(value("1\u00A0234,5", NumberKind.ODOMETER)).isWithin(1e-9).of(1234.5)
        assertThat(value("1\u202F234,5", NumberKind.ODOMETER)).isWithin(1e-9).of(1234.5)
        assertThat(value("1\u2009234,5", NumberKind.ODOMETER)).isWithin(1e-9).of(1234.5)
        assertThat(value("1'234.5", NumberKind.ODOMETER)).isWithin(1e-9).of(1234.5)   // de-CH
        assertThat(value("1\u2019234.5", NumberKind.ODOMETER)).isWithin(1e-9).of(1234.5)
    }

    @Test
    fun `non-ascii digits are digits`() {
        // Arabic-Indic with the Arabic decimal separator U+066B.
        assertThat(value("\u0661\u0662\u0663\u066B\u0665", NumberKind.VOLUME))
            .isWithin(1e-9).of(123.5)
        // Devanagari.
        assertThat(value("\u0968\u0966", NumberKind.VOLUME)).isWithin(1e-9).of(20.0)
        // Arabic-Indic with the Arabic thousands separator U+066C in an odometer.
        assertThat(value("\u0664\u0668\u066C\u0662\u0661\u0660", NumberKind.ODOMETER))
            .isWithin(1e-9).of(48210.0)
    }

    // -- incomplete but unambiguous ---------------------------------------------------------

    @Test
    fun `a dangling separator is read charitably`() {
        assertThat(value("32,", NumberKind.VOLUME)).isWithin(1e-9).of(32.0)
        assertThat(value("32.", NumberKind.VOLUME)).isWithin(1e-9).of(32.0)
        assertThat(value(",5", NumberKind.VOLUME)).isWithin(1e-9).of(0.5)
    }

    // -- refusal --------------------------------------------------------------------------

    @Test
    fun `junk is refused, never half-parsed`() {
        assertThat(reason("", NumberKind.VOLUME)).isEqualTo(RejectReason.EMPTY)
        assertThat(reason("   ", NumberKind.VOLUME)).isEqualTo(RejectReason.EMPTY)
        assertThat(reason("abc", NumberKind.VOLUME)).isEqualTo(RejectReason.NOT_A_NUMBER)
        assertThat(reason("32l", NumberKind.VOLUME)).isEqualTo(RejectReason.NOT_A_NUMBER)
        assertThat(reason("32 l", NumberKind.VOLUME)).isEqualTo(RejectReason.NOT_A_NUMBER)
        assertThat(reason(".", NumberKind.VOLUME)).isEqualTo(RejectReason.NOT_A_NUMBER)
        assertThat(reason(",", NumberKind.VOLUME)).isEqualTo(RejectReason.NOT_A_NUMBER)
        assertThat(reason("1.2.3,4.5", NumberKind.VOLUME)).isEqualTo(RejectReason.MALFORMED_GROUPING)
    }

    @Test
    fun `a sign is refused because none of these quantities can be negative`() {
        assertThat(reason("-5", NumberKind.ODOMETER)).isEqualTo(RejectReason.NEGATIVE)
        assertThat(reason("+5", NumberKind.ODOMETER)).isEqualTo(RejectReason.NEGATIVE)
        assertThat(reason("\u22125", NumberKind.ODOMETER)).isEqualTo(RejectReason.NEGATIVE)
    }

    @Test
    fun `plain integers and plain decimals need no special handling`() {
        assertThat(value("0", NumberKind.MONEY_TOTAL)).isWithin(1e-9).of(0.0)
        assertThat(value("48210", NumberKind.ODOMETER)).isWithin(1e-9).of(48210.0)
        assertThat(value("  41.30  ", NumberKind.MONEY_TOTAL)).isWithin(1e-9).of(41.30)
    }
}
