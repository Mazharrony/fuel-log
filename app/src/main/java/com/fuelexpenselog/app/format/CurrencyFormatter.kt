package com.fuelexpenselog.app.format

import com.fuelexpenselog.domain.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Money for display, in the user's locale and the amount's own currency.
 *
 * The locale decides the separators and where the symbol goes; the currency decides the
 * symbol and the number of decimals. A euro amount on an en-US phone is "€1,234.50", and on
 * a de-DE phone "1.234,50 €". The app never converts - it only writes down what it holds.
 */
class CurrencyFormatter(private val locale: Locale) {

    fun format(money: Money): String = format(money, minimumDecimals = null)

    /**
     * A cost per distance: at least two decimals, because "€0.09/km" rounded to the euro's
     * own precision is fine but rounded to the yen's would be "¥0".
     */
    fun formatRate(money: Money): String = format(money, minimumDecimals = 2)

    /** "€", "$", "£", or the code itself when the locale has no symbol for it. */
    fun symbol(code: String): String =
        currencyOrNull(code)?.getSymbol(locale) ?: code

    fun displayName(code: String): String =
        currencyOrNull(code)?.getDisplayName(locale) ?: code

    private fun format(money: Money, minimumDecimals: Int?): String {
        val amount = BigDecimal.valueOf(money.micros, 6)
        val currency = currencyOrNull(money.currency)
            ?: return fallback(money.currency, amount, minimumDecimals ?: 2)

        val digits = maxOf(currency.defaultFractionDigits.coerceAtLeast(0), minimumDecimals ?: 0)
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            // NumberFormat defaults to half-even, which shows ¥1,234.5 as ¥1,234 on one row
            // and a half-up domain total beside it. One rounding rule, everywhere.
            roundingMode = RoundingMode.HALF_UP
        }
        return format.format(amount)
    }

    /**
     * A code this platform's currency table does not know - written by a newer version, or
     * a currency introduced after this phone's ICU data. Shown plainly rather than dropped.
     */
    private fun fallback(code: String, amount: BigDecimal, digits: Int): String {
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            roundingMode = RoundingMode.HALF_UP
        }
        return "$code ${number.format(amount)}"
    }

    private fun currencyOrNull(code: String): Currency? =
        runCatching { Currency.getInstance(code) }.getOrNull()
}
