package com.fuelexpenselog.app.domain.stats

/**
 * Totals grouped by currency, never summed across them.
 *
 * The app holds no exchange rates - it has no network to fetch them with and no
 * business inventing them. A garage with a car priced in EUR and a bike priced
 * in INR shows two subtotals, not one wrong number.
 */
data class CurrencySubtotal(val currency: String, val total: Double)

fun currencySubtotals(amounts: List<Pair<String, Double>>): List<CurrencySubtotal> =
    amounts.groupBy({ it.first }, { it.second })
        .map { (currency, values) -> CurrencySubtotal(currency, values.sum()) }
        .sortedByDescending { it.total }
