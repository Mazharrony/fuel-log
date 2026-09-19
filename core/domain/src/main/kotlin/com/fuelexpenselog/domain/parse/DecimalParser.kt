package com.fuelexpenselog.domain.parse

/**
 * What the number is for. This exists for exactly one reason: a single separator with three
 * trailing digits is genuinely ambiguous, and only the field can resolve it.
 *
 * `48,210` in an odometer is forty-eight thousand. `32,5` in a volume is thirty-two and a
 * half. `1,459` in a unit price is one-point-four-five-nine, because fuel is priced to three
 * decimals across the EU and at the pump in the US.
 */
enum class NumberKind {
    ODOMETER,
    VOLUME,
    MONEY_TOTAL,
    UNIT_PRICE,
}

enum class RejectReason {
    EMPTY,

    /** Contained something that is not a digit or a separator this parser accepts. */
    NOT_A_NUMBER,

    /** A sign was present. An odometer, a volume and a price cannot be negative. */
    NEGATIVE,

    /**
     * Separators were present but the groups they imply are not groups. `8,471,01` is not a
     * grouped number, and guessing would turn it into 847101.
     */
    MALFORMED_GROUPING,
}

sealed interface ParseOutcome {
    @JvmInline
    value class Ok(val value: Double) : ParseOutcome

    @JvmInline
    value class Rejected(val reason: RejectReason) : ParseOutcome
}

/**
 * Parses a number the way a human typed it, **independently of the device locale**.
 *
 * This is the single most dangerous function in the app. `NumberFormat.parse` under a
 * comma-decimal locale reads `"32,5"` as 325 - a tenfold error in a fuel volume that never
 * announces itself and silently poisons every consumption figure computed from it.
 *
 * The rule the whole file follows: **a user typing `32,5` litres means 32.5 litres whether
 * their phone is set to de-DE or en-US.** Locale governs how numbers are DISPLAYED. It never
 * governs how they are read. A parser that changes its mind based on a system setting is a
 * parser that will eventually be wrong, on someone else's phone, in a way neither of you can
 * reproduce.
 *
 * Nothing here half-parses. Input that cannot be read unambiguously is rejected, because a
 * refused entry is a visible problem and a misread one is not.
 */
object DecimalParser {

    private const val NBSP = ' '
    private const val NARROW_NBSP = ' '
    private const val THIN_SPACE = ' '
    private const val APOSTROPHE = '’'
    private const val ARABIC_DECIMAL = '٫'
    private const val ARABIC_THOUSANDS = '٬'

    /** Separators people use purely to group digits, which carry no value. */
    private val GROUPING_NOISE = setOf(' ', NBSP, NARROW_NBSP, THIN_SPACE, '\'', APOSTROPHE)

    fun parse(raw: String, kind: NumberKind): ParseOutcome {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParseOutcome.Rejected(RejectReason.EMPTY)

        val normalised = StringBuilder(trimmed.length)
        for (ch in trimmed) {
            when {
                ch in GROUPING_NOISE -> Unit // drop it; it never carries value

                ch == '-' || ch == '+' || ch == '−' ->
                    return ParseOutcome.Rejected(RejectReason.NEGATIVE)

                ch == ARABIC_DECIMAL -> normalised.append('.')
                ch == ARABIC_THOUSANDS -> normalised.append(',')
                ch == '.' || ch == ',' -> normalised.append(ch)

                else -> {
                    // Character.digit maps every Unicode decimal digit to its value, so
                    // Arabic-Indic and Devanagari numerals arrive as themselves rather than
                    // as junk. A user typing on an Arabic keypad is typing a number.
                    val digit = Character.digit(ch, 10)
                    if (digit < 0) return ParseOutcome.Rejected(RejectReason.NOT_A_NUMBER)
                    normalised.append('0' + digit)
                }
            }
        }

        val s = normalised.toString()
        if (s.none { it.isDigit() }) return ParseOutcome.Rejected(RejectReason.NOT_A_NUMBER)

        val dots = s.count { it == '.' }
        val commas = s.count { it == ',' }

        return when {
            dots == 0 && commas == 0 -> ok(s, "")

            // Both present: whichever comes LAST is the decimal separator and the other is
            // grouping. `1.234,56` and `1,234.56` are the same number written by two
            // continents, and this is the one reading that satisfies both.
            dots > 0 && commas > 0 -> {
                val decimalSep = if (s.lastIndexOf('.') > s.lastIndexOf(',')) '.' else ','
                val groupSep = if (decimalSep == '.') ',' else '.'
                if (s.count { it == decimalSep } != 1) {
                    return ParseOutcome.Rejected(RejectReason.MALFORMED_GROUPING)
                }
                split(s, decimalSep, groupSep)
            }

            else -> {
                val sep = if (dots > 0) '.' else ','
                val count = dots + commas
                if (count > 1) {
                    // Repeated single separator can only be grouping - but only if the
                    // groups really are groups. Without that check `8471014.652.10` would
                    // quietly become 847,101,465,210.
                    split(s, decimalSep = null, groupSep = sep)
                } else {
                    val idx = s.indexOf(sep)
                    val tail = s.length - idx - 1
                    if (isGrouping(kind, idx, tail)) {
                        split(s, decimalSep = null, groupSep = sep)
                    } else {
                        split(s, decimalSep = sep, groupSep = null)
                    }
                }
            }
        }
    }

    /** Convenience for callers that only care whether a value came back. */
    fun parseOrNull(raw: String, kind: NumberKind): Double? =
        (parse(raw, kind) as? ParseOutcome.Ok)?.value

    /**
     * The ambiguity rule, and the only place [NumberKind] is consulted.
     *
     * A lone separator followed by exactly three digits is the shape of both a grouped
     * thousand and a three-decimal fraction. Odometers and totals are grouped in thousands;
     * volumes and unit prices are not, and fuel is routinely priced to three decimals
     * (EUR 1.459/L, USD 3.459/gal), so for those the separator is always decimal.
     *
     * `idx > 0` matters: `,500` has no leading group, so it is a fraction, not grouping.
     *
     * The residual risk is a three-decimal currency (KWD, BHD) where a total of `1,234`
     * could mean either. It parses as 1234. The entry screen shows the parsed figure back
     * live as it is typed, so a misread is visible before anything is saved - which is the
     * real mitigation, not a cleverer guess here.
     */
    private fun isGrouping(kind: NumberKind, idx: Int, tail: Int): Boolean = when (kind) {
        NumberKind.ODOMETER, NumberKind.MONEY_TOTAL -> tail == 3 && idx > 0
        NumberKind.VOLUME, NumberKind.UNIT_PRICE -> false
    }

    private fun split(s: String, decimalSep: Char?, groupSep: Char?): ParseOutcome {
        val integerPart: String
        val fractionPart: String

        if (decimalSep != null) {
            val at = s.lastIndexOf(decimalSep)
            integerPart = s.substring(0, at)
            fractionPart = s.substring(at + 1)
        } else {
            integerPart = s
            fractionPart = ""
        }

        if (fractionPart.any { !it.isDigit() }) {
            return ParseOutcome.Rejected(RejectReason.NOT_A_NUMBER)
        }

        val digits = if (groupSep != null) {
            ungroup(integerPart, groupSep) ?: return ParseOutcome.Rejected(RejectReason.MALFORMED_GROUPING)
        } else {
            if (integerPart.any { !it.isDigit() }) {
                return ParseOutcome.Rejected(RejectReason.NOT_A_NUMBER)
            }
            integerPart
        }

        return ok(digits, fractionPart)
    }

    /**
     * Returns the digits with grouping removed, or null if the groups are not well formed:
     * the first group is 1-3 digits and every later group is exactly 3.
     *
     * This rejects `1,23,456` and `12,3456,789`. The Indian grouping convention
     * (`12,34,567`) is deliberately not accepted as grouping - it is rejected rather than
     * misread, and a user can always type the digits without separators.
     */
    private fun ungroup(integerPart: String, groupSep: Char): String? {
        if (!integerPart.contains(groupSep)) {
            return integerPart.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        }

        val groups = integerPart.split(groupSep)
        if (groups.size < 2) return null
        if (groups.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        if (groups.first().length > 3) return null
        if (groups.drop(1).any { it.length != 3 }) return null

        return groups.joinToString("")
    }

    private fun ok(integerDigits: String, fractionDigits: String): ParseOutcome {
        // A trailing or leading separator is incomplete rather than wrong - "32," is
        // thirty-two and ",5" is a half. Reading them charitably costs nothing and avoids
        // refusing input that has exactly one sensible meaning.
        val whole = integerDigits.ifEmpty { "0" }
        val text = if (fractionDigits.isEmpty()) whole else "$whole.$fractionDigits"

        val value = text.toDoubleOrNull() ?: return ParseOutcome.Rejected(RejectReason.NOT_A_NUMBER)
        if (!value.isFinite()) return ParseOutcome.Rejected(RejectReason.NOT_A_NUMBER)

        return ParseOutcome.Ok(value)
    }
}
