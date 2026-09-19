package com.fuelexpenselog.domain.time

import com.fuelexpenselog.domain.format.Rounding
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class CivilDateTest {

    private lateinit var original: Locale

    @Before fun save() { original = Locale.getDefault() }

    @After fun restore() { Locale.setDefault(original) }

    @Test
    fun `the integer encoding is the date, readable by eye`() {
        val d = CivilDate.of(2026, 9, 19)

        assertThat(d.value).isEqualTo(20_260_919)
        assertThat(d.year).isEqualTo(2026)
        assertThat(d.month).isEqualTo(9)
        assertThat(d.day).isEqualTo(19)
        assertThat(d.monthKey).isEqualTo(MonthKey.of(2026, 9))
        assertThat(d.toString()).isEqualTo("2026-09-19")
    }

    @Test
    fun `dates sort as integers`() {
        val dates = listOf(
            CivilDate.of(2026, 1, 5),
            CivilDate.of(2025, 12, 31),
            CivilDate.of(2026, 1, 4),
        )

        assertThat(dates.sorted().map { it.toString() })
            .containsExactly("2025-12-31", "2026-01-04", "2026-01-05").inOrder()
    }

    // -- the arithmetic reminders depend on ----------------------------------------------

    @Test
    fun `month arithmetic clamps to the end of a shorter month`() {
        // A yearly MOT set on 31 August must not land on a 31st of February.
        assertThat(CivilDate.of(2026, 1, 31).plusMonths(1).toString()).isEqualTo("2026-02-28")
        assertThat(CivilDate.of(2026, 8, 31).plusMonths(6).toString()).isEqualTo("2027-02-28")
        assertThat(CivilDate.of(2026, 1, 31).plusMonths(6).toString()).isEqualTo("2026-07-31")
        assertThat(CivilDate.of(2026, 3, 31).plusMonths(1).toString()).isEqualTo("2026-04-30")
    }

    @Test
    fun `a leap year is handled by the calendar, not by us`() {
        assertThat(CivilDate.of(2028, 1, 31).plusMonths(1).toString()).isEqualTo("2028-02-29")
        assertThat(CivilDate.of(2028, 2, 29).plusMonths(12).toString()).isEqualTo("2029-02-28")
        assertThat(CivilDate.of(2028, 2, 28).plusDays(1).toString()).isEqualTo("2028-02-29")
    }

    @Test
    fun `days between dates crosses month and year boundaries`() {
        assertThat(CivilDate.of(2026, 1, 1).daysUntil(CivilDate.of(2026, 2, 1))).isEqualTo(31L)
        assertThat(CivilDate.of(2025, 12, 31).daysUntil(CivilDate.of(2026, 1, 1))).isEqualTo(1L)
        assertThat(CivilDate.of(2026, 3, 1).daysUntil(CivilDate.of(2026, 1, 1))).isEqualTo(-59L)
    }

    @Test
    fun `same date last year, including from a leap day`() {
        assertThat(CivilDate.of(2026, 9, 19).minusYears(1).toString()).isEqualTo("2025-09-19")
        assertThat(CivilDate.of(2028, 2, 29).minusYears(1).toString()).isEqualTo("2027-02-28")
    }

    // -- the timezone invariant -------------------------------------------------------------

    @Test
    fun `today is decided by the zone passed in, never by ambient state`() {
        // The same moment is two different dates in two places. Because the zone is a
        // parameter, both are reachable and testable - and nothing shifts when the user
        // flies somewhere.
        val tokyo = CivilDate.today(ZoneId.of("Asia/Tokyo"))
        val la = CivilDate.today(ZoneId.of("America/Los_Angeles"))

        assertThat(tokyo.daysUntil(la)).isAnyOf(0L, -1L)
    }

    @Test
    fun `a stored date needs no timezone to be read back`() {
        // The point of the whole design: this survives any device, any zone, any DST rule
        // change, forever, because there is nothing to interpret.
        val stored = 20_251_231
        assertThat(CivilDate.parseOrNull(stored)!!.monthKey.toString()).isEqualTo("2025-12")
    }

    @Test
    fun `nonsense integers are rejected rather than producing a nonsense date`() {
        assertThat(CivilDate.parseOrNull(20_261_332)).isNull()   // month 13
        assertThat(CivilDate.parseOrNull(20_260_230)).isNull()   // 30 February
        assertThat(CivilDate.parseOrNull(0)).isNull()
    }

    // -- MonthKey ---------------------------------------------------------------------------

    @Test
    fun `month keys step across a year boundary`() {
        val jan = MonthKey.of(2026, 1)

        assertThat(jan.minusMonths(1).toString()).isEqualTo("2025-12")
        assertThat(jan.minusMonths(13).toString()).isEqualTo("2024-12")
        assertThat(jan.plusMonths(11).toString()).isEqualTo("2026-12")
        assertThat(jan.plusMonths(12).toString()).isEqualTo("2027-01")
        assertThat(jan.minusYears(1).toString()).isEqualTo("2025-01")
    }

    @Test
    fun `month keys sort as integers and bound their own range`() {
        val months = listOf(MonthKey.of(2026, 1), MonthKey.of(2025, 12), MonthKey.of(2026, 2))

        assertThat(months.sorted().map { it.toString() })
            .containsExactly("2025-12", "2026-01", "2026-02").inOrder()

        assertThat(MonthKey.of(2026, 2).firstDay().toString()).isEqualTo("2026-02-01")
        assertThat(MonthKey.of(2026, 2).lastDay().toString()).isEqualTo("2026-02-28")
        assertThat(MonthKey.of(2028, 2).lastDay().toString()).isEqualTo("2028-02-29")
    }

    @Test
    fun `a month bucket is one integer divide away from a stored date`() {
        val date = CivilDate.of(2026, 9, 19)

        // This is what the SQL range query relies on. No conversion table, no zone.
        assertThat(date.value / 100).isEqualTo(MonthKey.of(2026, 9).value)
    }

    // -- locale independence where it matters -------------------------------------------------

    @Test
    fun `date and month formatting stay ASCII under any locale`() {
        // A default-locale format under ar-EG emits Arabic-Indic digits. Those would reach a
        // CSV file and an accountant's spreadsheet.
        for (tag in listOf("ar-EG", "hi-IN", "fa-IR", "de-DE")) {
            Locale.setDefault(Locale.forLanguageTag(tag))

            assertThat(CivilDate.of(2026, 9, 19).toString()).isEqualTo("2026-09-19")
            assertThat(MonthKey.of(2026, 9).toString()).isEqualTo("2026-09")
            assertThat(Rounding.toPlainString(4550.0, 2)).isEqualTo("4550.00")
        }
    }

    @Test
    fun `rounding happens once and never on the way through`() {
        assertThat(Rounding.to(4549.999999999999, 2)).isWithin(1e-9).of(4550.0)
        assertThat(Rounding.to(14.5598, 1)).isWithin(1e-9).of(14.6)
        assertThat(Rounding.to(Double.NaN, 2).isNaN()).isTrue()
        assertThat(Rounding.toPlainString(Double.NaN, 2)).isEmpty()
    }

    @Test
    fun `a civil date matches the LocalDate it came from`() {
        val local = LocalDate.of(2026, 9, 19)
        assertThat(CivilDate.of(local).toLocalDate()).isEqualTo(local)
    }
}
