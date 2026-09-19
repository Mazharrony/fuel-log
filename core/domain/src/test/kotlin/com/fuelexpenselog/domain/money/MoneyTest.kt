package com.fuelexpenselog.domain.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {

    @Test
    fun `micros carry a three-decimal fuel price without rounding it away`() {
        // EUR 1.459 per litre is the ordinary case across the EU, and USD 3.459 9/10 per
        // gallon is on every US pump sign. Minor units would have destroyed both.
        val price = Money.of(1.459, "EUR")

        assertThat(price.micros).isEqualTo(1_459_000L)
        assertThat(price.asDouble).isWithin(1e-9).of(1.459)
    }

    @Test
    fun `currencies are never added together`() {
        val eur = Money.of(50.0, "EUR")
        val inr = Money.of(5000.0, "INR")

        assertThat(runCatching { eur + inr }.isFailure).isTrue()
        assertThat((eur + Money.of(10.0, "EUR")).asDouble).isWithin(1e-9).of(60.0)
    }

    @Test
    fun `mixed currencies produce subtotals, not a sum`() {
        val amounts = listOf(
            Money.of(100.0, "EUR"),
            Money.of(5000.0, "INR"),
            Money.of(50.0, "EUR"),
        )

        val subtotals = CurrencySubtotals.of(amounts)

        assertThat(subtotals).hasSize(2)
        assertThat(subtotals.map { it.currency }).containsExactly("INR", "EUR").inOrder()
        assertThat(subtotals.first { it.currency == "EUR" }.asDouble).isWithin(1e-9).of(150.0)
        assertThat(subtotals.first { it.currency == "INR" }.asDouble).isWithin(1e-9).of(5000.0)
    }

    @Test
    fun `a single-currency total is available, and a mixed one is refused`() {
        val single = listOf(Money.of(10.0, "GBP"), Money.of(5.5, "GBP"))
        assertThat(CurrencySubtotals.singleCurrencyTotal(single)!!.asDouble).isWithin(1e-9).of(15.5)

        val mixed = listOf(Money.of(10.0, "GBP"), Money.of(5.5, "USD"))
        assertThat(CurrencySubtotals.singleCurrencyTotal(mixed)).isNull()

        assertThat(CurrencySubtotals.singleCurrencyTotal(emptyList())).isNull()
    }

    @Test
    fun `a non-ISO currency code is refused at construction`() {
        assertThat(runCatching { Money(1L, "POUNDS") }.isFailure).isTrue()
        assertThat(runCatching { Money(1L, "") }.isFailure).isTrue()
    }

    @Test
    fun `totals stay exact over many additions`() {
        // The reason money is a Long. Summing 1000 doubles of 0.1 drifts visibly; summing
        // 1000 micros of 100_000 does not.
        val total = (1..1000).fold(Money.zero("USD")) { acc, _ -> acc + Money.of(0.1, "USD") }

        assertThat(total.micros).isEqualTo(100_000_000L)
        assertThat(total.asDouble).isWithin(1e-9).of(100.0)
    }
}
