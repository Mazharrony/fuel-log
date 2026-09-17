package com.fuelexpenselog.app.domain

import com.fuelexpenselog.app.domain.consumption.computeAllSpans
import com.fuelexpenselog.app.domain.model.ExpenseCategory
import com.fuelexpenselog.app.domain.stats.MonthKey
import com.fuelexpenselog.app.domain.stats.monthOverMonthDelta
import com.fuelexpenselog.app.domain.stats.monthlyTotals
import com.fuelexpenselog.app.domain.stats.sameMonthLastYear
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class MonthAggregationTest {

    private val utc = ZoneOffset.UTC

    private fun millis(y: Int, m: Int, d: Int, zone: ZoneId = utc) =
        LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `month keys order and step correctly across a year boundary`() {
        val dec = MonthKey.of(2025, 12)
        val jan = MonthKey.of(2026, 1)

        assertThat(jan).isGreaterThan(dec)
        assertThat(jan.minusMonths(1)).isEqualTo(dec)
        assertThat(jan.minusYears(1)).isEqualTo(MonthKey.of(2025, 1))
        assertThat(jan.year).isEqualTo(2026)
        assertThat(jan.month).isEqualTo(1)
        assertThat(dec.toString()).isEqualTo("2025-12")
    }

    /**
     * The bucket is decided by the LOCAL date, so one instant can belong to
     * different months in different zones. Passing the zone in explicitly is what
     * makes this testable at all.
     */
    @Test
    fun `an instant near new year buckets by local date not UTC`() {
        val instant = LocalDate.of(2025, 12, 31)
            .atTime(20, 0).atZone(utc).toInstant().toEpochMilli()

        assertThat(MonthKey.from(instant, utc)).isEqualTo(MonthKey.of(2025, 12))
        // UTC+13 has already rolled into January.
        assertThat(MonthKey.from(instant, ZoneId.of("Pacific/Apia")))
            .isEqualTo(MonthKey.of(2026, 1))
    }

    @Test
    fun `bucketing survives a DST transition`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-03-29 is the spring-forward date in central Europe.
        assertThat(MonthKey.from(millis(2026, 3, 28, berlin), berlin))
            .isEqualTo(MonthKey.of(2026, 3))
        assertThat(MonthKey.from(millis(2026, 3, 30, berlin), berlin))
            .isEqualTo(MonthKey.of(2026, 3))
    }

    @Test
    fun `money is split exactly between fuel and everything else`() {
        val fillUps = listOf(
            fill(odometer = 100.0, volume = 40.0, cost = 80.0, day = 0),
            fill(odometer = 600.0, volume = 50.0, cost = 100.0, day = 10),
        )
        val expenses = listOf(
            expense(ExpenseCategory.OIL_CHANGE, cost = 60.0, day = 5),
            expense(ExpenseCategory.INSURANCE, cost = 200.0, day = 6),
        )

        val totals = monthlyTotals(fillUps, expenses, computeAllSpans(fillUps), "USD", utc)

        assertThat(totals).hasSize(1)
        val january = totals[0]
        assertThat(january.fuelCost).isEqualTo(180.0)
        assertThat(january.otherCost).isEqualTo(260.0)
        assertThat(january.totalCost).isEqualTo(440.0)
        assertThat(january.byCategory[ExpenseCategory.OIL_CHANGE]).isEqualTo(60.0)
        assertThat(january.fuelShare).isWithin(1e-9).of(180.0 / 440.0)
    }

    /**
     * A span routinely straddles a month boundary. Its distance is credited
     * wholly to the month it ended in - documented once, applied in one place,
     * so no two screens can disagree.
     */
    @Test
    fun `a span crossing a month boundary is credited to the month it ended in`() {
        val fillUps = listOf(
            fill(odometer = 100.0, volume = 40.0, day = 25),   // 26 Jan
            fill(odometer = 600.0, volume = 50.0, day = 40),   // 10 Feb
        )

        val totals = monthlyTotals(fillUps, emptyList(), computeAllSpans(fillUps), "USD", utc)

        assertThat(totals.first { it.month == MonthKey.of(2026, 1) }.distanceKm).isEqualTo(0.0)
        assertThat(totals.first { it.month == MonthKey.of(2026, 2) }.distanceKm).isEqualTo(500.0)
    }

    @Test
    fun `month over month delta is null when there is nothing to compare against`() {
        val fillUps = listOf(fill(odometer = 100.0, volume = 40.0, cost = 80.0, day = 0))
        val totals = monthlyTotals(fillUps, emptyList(), emptyList(), "USD", utc)

        assertThat(monthOverMonthDelta(totals, MonthKey.of(2026, 1))).isNull()
    }

    @Test
    fun `month over month delta is a proportion of the previous month`() {
        val fillUps = listOf(
            fill(odometer = 100.0, volume = 40.0, cost = 100.0, day = 5),    // Jan
            fill(odometer = 600.0, volume = 50.0, cost = 150.0, day = 40),   // Feb
        )
        val totals = monthlyTotals(fillUps, emptyList(), emptyList(), "USD", utc)

        assertThat(monthOverMonthDelta(totals, MonthKey.of(2026, 2))).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `same month last year is found when it exists`() {
        val fillUps = listOf(
            fill(odometer = 100.0, volume = 40.0, cost = 100.0, day = 5),
            fill(odometer = 9000.0, volume = 40.0, cost = 130.0, day = 370),
        )
        val totals = monthlyTotals(fillUps, emptyList(), emptyList(), "USD", utc)

        assertThat(sameMonthLastYear(totals, MonthKey.of(2027, 1))?.totalCost).isEqualTo(100.0)
        assertThat(sameMonthLastYear(totals, MonthKey.of(2026, 1))).isNull()
    }
}
