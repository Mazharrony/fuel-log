package com.fuelexpenselog.app.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * A stored number rendered back into an editable field: no grouping, at most [maxDecimals],
 * no trailing zeros, the locale's decimal mark. DecimalParser reads it back whichever mark it
 * carries, and without grouping there is no three-digit ambiguity to resolve.
 *
 * Fields that show a stored value keep an "edited" flag: until the user touches the text,
 * saving writes the stored value back untouched instead of re-parsing this rounded copy.
 */
object InputText {
    fun of(value: Double, maxDecimals: Int, locale: Locale): String {
        if (!value.isFinite()) return ""
        val plain = BigDecimal.valueOf(value)
            .setScale(maxDecimals, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        val mark = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return if (mark == '.') plain else plain.replace('.', mark)
    }
}
