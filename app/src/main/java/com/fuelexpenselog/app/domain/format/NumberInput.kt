package com.fuelexpenselog.app.domain.format

enum class NumberKind { ODOMETER, VOLUME, MONEY }

/**
 * Locale-tolerant numeric parsing.
 *
 * This exists because `NumberFormat.parse` under a comma-decimal locale reads
 * "32,5" as 325 - a tenfold error in a fuel volume, which silently poisons every
 * consumption figure downstream. It is the single most reported bug class in
 * this category of app, and the reviews it produces ("the mileage numbers are
 * wrong") are unrecoverable.
 *
 * The rules are deliberately LOCALE-INDEPENDENT. A user typing 32,5 litres means
 * 32.5 litres whether their phone is set to de-DE or en-US, and a parser that
 * changes its mind based on a system setting is a parser that will eventually be
 * wrong. Locale affects how numbers are DISPLAYED, never how they are read.
 *
 * Order of operations:
 *  1. Normalise any Unicode decimal digits to ASCII (Arabic-Indic, Devanagari...)
 *  2. Strip grouping noise: spaces, NBSP, narrow NBSP, apostrophes
 *  3. Decide which of '.' / ',' is the decimal separator
 *  4. Reject anything that is not then a plain number
 */
fun parseNumber(raw: String, kind: NumberKind): Double? {
    val normalised = buildString(raw.length) {
        for (c in raw) {
            val digit = Character.digit(c, 10)
            when {
                // Character.digit accepts A-Z as digits in higher radixes, but at
                // radix 10 only actual decimal digits come back 0..9.
                digit in 0..9 -> append('0' + digit)
                c == '.' || c == ',' -> append(c)
                // Grouping noise. U+00A0 and U+202F are French/CLDR groupers,
                // the apostrophe is Swiss.
                c == ' ' || c == ' ' || c == ' ' || c == '\'' || c == '’' -> Unit
                c == '-' || c == '+' -> append(c)
                else -> return null
            }
        }
    }
    if (normalised.isEmpty()) return null

    // A sign is only meaningful in the leading position, and never for these
    // quantities - an odometer or a volume cannot be negative.
    if (normalised.any { it == '-' || it == '+' }) return null

    val dots = normalised.count { it == '.' }
    val commas = normalised.count { it == ',' }

    val digitsOnly: String = when {
        dots == 0 && commas == 0 -> normalised

        // Both present: whichever comes last is the decimal separator, the
        // other is grouping. "1.234,56" and "1,234.56" both work.
        dots > 0 && commas > 0 -> {
            val decimalChar = if (normalised.lastIndexOf('.') > normalised.lastIndexOf(',')) '.' else ','
            normalised.filter { it != (if (decimalChar == '.') ',' else '.') }
                .replace(decimalChar, '.')
        }

        else -> {
            val sep = if (dots > 0) '.' else ','
            val count = if (dots > 0) dots else commas
            if (count > 1) {
                // "1,234,567" - repeated separators can only be grouping.
                normalised.filter { it != sep }
            } else {
                val idx = normalised.indexOf(sep)
                val tail = normalised.length - idx - 1
                // An odometer written "48,210" means 48210. A volume written
                // "32,5" means 32.5. The three-digit tail is what distinguishes
                // them, and it only applies where grouping is plausible.
                val isGrouping = kind == NumberKind.ODOMETER && tail == 3 && idx > 0
                if (isGrouping) normalised.filter { it != sep }
                else normalised.replace(sep, '.')
            }
        }
    }

    if (digitsOnly.isEmpty() || digitsOnly == ".") return null
    return digitsOnly.toDoubleOrNull()?.takeIf { it.isFinite() }
}
