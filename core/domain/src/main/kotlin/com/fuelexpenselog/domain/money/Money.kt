package com.fuelexpenselog.domain.money

import com.fuelexpenselog.domain.unit.Canonical
import kotlin.math.roundToLong

/**
 * An amount in a single currency, stored as integer micros (1e-6 of a major unit).
 *
 * Micros rather than minor units because fuel is priced to three decimals or more -
 * EUR 1.459/L, USD 3.459 9/10 per gallon - and rounding a unit price to cents at entry time
 * makes the total disagree with the receipt.
 *
 * The currency is part of the value, not context around it, because the app must never add
 * two currencies together and a bare Long makes that mistake easy to write.
 */
data class Money(val micros: Long, val currency: String) {

    init {
        require(currency.length == 3) { "Expected an ISO-4217 code, got '$currency'" }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Refusing to add $currency to ${other.currency}. This app holds no exchange rates " +
                "and will not invent one. Group by currency instead."
        }
        return Money(micros + other.micros, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) {
            "Refusing to subtract ${other.currency} from $currency."
        }
        return Money(micros - other.micros, currency)
    }

    operator fun times(factor: Double): Money = Money((micros * factor).roundToLong(), currency)

    val asDouble: Double get() = micros / Canonical.MICROS_PER_MAJOR_UNIT

    val isZero: Boolean get() = micros == 0L

    companion object {
        fun of(amount: Double, currency: String) =
            Money((amount * Canonical.MICROS_PER_MAJOR_UNIT).roundToLong(), currency)

        fun zero(currency: String) = Money(0L, currency)
    }
}

/**
 * Totals grouped by currency, **never summed across them**.
 *
 * The app holds no exchange rates: it has no network to fetch them with and no business
 * inventing them. A garage with a car priced in EUR and a bike priced in INR shows two
 * subtotals, not one wrong number that looks authoritative.
 */
object CurrencySubtotals {

    fun of(amounts: List<Money>): List<Money> =
        amounts.groupBy { it.currency }
            .map { (currency, values) -> Money(values.sumOf { it.micros }, currency) }
            .sortedWith(compareByDescending<Money> { it.micros }.thenBy { it.currency })

    /**
     * The total when every amount shares one currency, or null when they do not. Callers
     * that get null must render subtotals rather than a single figure.
     */
    fun singleCurrencyTotal(amounts: List<Money>): Money? {
        if (amounts.isEmpty()) return null
        val currency = amounts.first().currency
        if (amounts.any { it.currency != currency }) return null
        return Money(amounts.sumOf { it.micros }, currency)
    }
}
