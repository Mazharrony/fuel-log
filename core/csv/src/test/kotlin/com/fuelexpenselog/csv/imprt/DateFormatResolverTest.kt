package com.fuelexpenselog.csv.imprt

import com.fuelexpenselog.csv.imprt.DateFormatResolver.Result
import com.fuelexpenselog.domain.time.CivilDate
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime

class DateFormatResolverTest {

    private fun samples(vararg rows: Pair<String, Double?>) = rows.map { (text, odometer) -> DateSample(text, odometer) }

    @Test
    fun `dates that only ever read both ways are asked about, with one from the file`() {
        // Feb 1 / Apr 3 / Jun 5, or Jan 2 / Mar 4 / May 6: the odometer rises either way.
        val result = DateFormatResolver.resolve(
            samples("01/02/2026" to 1_000.0, "03/04/2026" to 1_400.0, "05/06/2026" to 1_900.0),
        )

        assertThat(result).isInstanceOf(Result.Ambiguous::class.java)
        result as Result.Ambiguous
        assertThat(result.orders).containsExactly(DateOrder.DMY, DateOrder.MDY)
        assertThat(result.example).isEqualTo("01/02/2026")
    }

    @Test
    fun `one day above 12 settles day-month against month-day`() {
        assertThat(DateFormatResolver.resolve(samples("01/02/2026" to null, "18/02/2026" to null)))
            .isEqualTo(Result.Resolved(DateOrder.DMY))
        assertThat(DateFormatResolver.resolve(samples("01/02/2026" to null, "02/18/2026" to null)))
            .isEqualTo(Result.Resolved(DateOrder.MDY))
    }

    @Test
    fun `failing that, the reading that keeps the odometer rising wins`() {
        // As day-month: 2 Jan, 1 Feb, 3 Feb, the odometer rising. As month-day: Feb 1, Jan 2,
        // Mar 2, where it would have to run backwards from January to February.
        val result = DateFormatResolver.resolve(
            samples("02/01/2026" to 1_000.0, "01/02/2026" to 2_000.0, "03/02/2026" to 2_500.0).reversed(),
        )
        assertThat(result).isEqualTo(Result.Resolved(DateOrder.DMY))
    }

    @Test
    fun `when every date reads the same either way, there is nothing to ask`() {
        assertThat(DateFormatResolver.resolve(samples("01/01/2026" to null, "02/02/2026" to null)))
            .isEqualTo(Result.Resolved(DateOrder.DMY))
    }

    @Test
    fun `ISO dates resolve, times after them are read, and a bad row does not vote`() {
        val result = DateFormatResolver.resolve(samples("2026-01-15 10:30" to null, "2026-01-16T07:05" to null, "yesterday" to null))
        assertThat(result).isEqualTo(Result.Resolved(DateOrder.YMD))
        assertThat(DateFormatResolver.parse("2026-01-16T07:05", DateOrder.YMD)).isEqualTo(CivilDate.of(2026, 1, 16))
        assertThat(DateFormatResolver.time("15/01/2026 7:30 PM")).isEqualTo(LocalTime.of(19, 30))
        assertThat(DateFormatResolver.time("2026-01-16T07:05")).isEqualTo(LocalTime.of(7, 5))
    }

    @Test
    fun `separators do not matter, two-digit years land in this century, and nonsense is refused`() {
        assertThat(DateFormatResolver.parse("15.01.2026", DateOrder.DMY)).isEqualTo(CivilDate.of(2026, 1, 15))
        assertThat(DateFormatResolver.parse("1-15-26", DateOrder.MDY)).isEqualTo(CivilDate.of(2026, 1, 15))
        assertThat(DateFormatResolver.parse("20260115", DateOrder.YMD)).isEqualTo(CivilDate.of(2026, 1, 15))
        assertThat(DateFormatResolver.parse("31/02/2026", DateOrder.DMY)).isNull()
        assertThat(DateFormatResolver.parse("2026-01-15", DateOrder.DMY)).isNull()
        assertThat(DateFormatResolver.resolve(samples("soon" to null))).isEqualTo(Result.Unreadable("soon"))
        assertThat(DateFormatResolver.resolve(samples("" to null))).isEqualTo(Result.NoDates)
    }

    @Test
    fun `the fixtures resolve on their own`() {
        assertThat(Fixtures.plan("acar.csv").dates).isEqualTo(Result.Resolved(DateOrder.MDY))
        assertThat(Fixtures.plan("drivvo.csv").dates).isEqualTo(Result.Resolved(DateOrder.DMY))
        assertThat(Fixtures.plan("german-semicolon.csv").dates).isEqualTo(Result.Resolved(DateOrder.DMY))
        assertThat(Fixtures.plan("fuelio.csv").dates).isEqualTo(Result.Resolved(DateOrder.YMD))
    }
}
