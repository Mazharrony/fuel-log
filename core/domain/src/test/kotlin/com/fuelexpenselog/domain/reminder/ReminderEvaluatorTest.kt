package com.fuelexpenselog.domain.reminder

import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.reminder.ReminderStatus.DueSoon
import com.fuelexpenselog.domain.reminder.ReminderStatus.Ok
import com.fuelexpenselog.domain.reminder.ReminderStatus.Overdue
import com.fuelexpenselog.domain.reminder.ReminderStatus.Unknown
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReminderEvaluatorTest {

    private val today = CivilDate.of(2026, 9, 26)
    private val km = 1_000L

    /** "Oil change every 6 months or 10,000 km", last done on [anchor] at [anchorKm]. */
    private fun oil(
        kind: ReminderKind = ReminderKind.BOTH,
        anchor: CivilDate? = CivilDate.of(2026, 3, 26),
        anchorKm: Long? = 48_700,
        id: Long = 1,
        vehicleId: Long = 1,
        repeatMonths: Int? = 6,
        repeatKm: Long? = 10_000,
    ): Reminder {
        val next = ReminderEvaluator.schedule(kind, repeatMonths, repeatKm?.times(km), anchor, anchorKm?.times(km))
        return Reminder(
            id = id,
            vehicleId = vehicleId,
            title = "Oil change",
            kind = kind,
            category = ExpenseCategory.OIL_CHANGE,
            dueDate = next.dueDate,
            repeatMonths = repeatMonths,
            dueOdometerM = next.dueOdometerM,
            repeatDistanceM = repeatKm?.times(km),
            anchorDate = anchor,
            anchorOdometerM = anchorKm?.times(km),
        )
    }

    private fun status(reminder: Reminder, odometerKm: Double?, on: CivilDate = today) =
        ReminderEvaluator.status(reminder, odometerKm?.let { (it * km).toLong() }, on)

    // -- thresholds ------------------------------------------------------------------------

    @Test
    fun `a date reminder is fine until 30 days out, due soon inside them, and overdue the day after`() {
        val r = oil(kind = ReminderKind.DATE) // due 26 Sep 2026
        val due = CivilDate.of(2026, 9, 26)

        assertThat(status(r, null, due.plusDays(-31))).isEqualTo(Ok)
        assertThat(status(r, null, due.plusDays(-30))).isEqualTo(DueSoon(daysLeft = 30, metresLeft = null))
        assertThat(status(r, null, due)).isEqualTo(DueSoon(daysLeft = 0, metresLeft = null))
        assertThat(status(r, null, due.plusDays(1))).isEqualTo(Overdue(daysOver = 1, metresOver = null))
    }

    @Test
    fun `a distance reminder warns inside 500 km and is overdue past the reading`() {
        val r = oil(kind = ReminderKind.DISTANCE) // due at 58,700 km

        assertThat(status(r, 58_199.0)).isEqualTo(Ok)
        assertThat(status(r, 58_200.0)).isEqualTo(DueSoon(daysLeft = null, metresLeft = 500_000))
        assertThat(status(r, 58_700.0)).isEqualTo(DueSoon(daysLeft = null, metresLeft = 0))
        assertThat(status(r, 58_701.0)).isEqualTo(Overdue(daysOver = null, metresOver = 1_000))
    }

    @Test
    fun `both reports the worse of its two halves`() {
        val r = oil() // due 26 Sep 2026 or at 58,700 km

        // Fine by date a month early, overdue by distance.
        assertThat(status(r, 59_000.0, today.plusDays(-40))).isEqualTo(Overdue(daysOver = null, metresOver = 300_000))
        // Due soon by date, fine by distance.
        assertThat(status(r, 50_000.0, today.plusDays(-10))).isEqualTo(DueSoon(daysLeft = 10, metresLeft = null))
        // Overdue by both: both numbers are kept.
        assertThat(status(r, 59_000.0, today.plusDays(3))).isEqualTo(Overdue(daysOver = 3, metresOver = 300_000))
        assertThat(status(r, 50_000.0, today.plusDays(-60))).isEqualTo(Ok)
    }

    // -- no reading ------------------------------------------------------------------------

    @Test
    fun `a distance reminder on a vehicle with no reading is Unknown, never Overdue`() {
        assertThat(status(oil(kind = ReminderKind.DISTANCE), null)).isEqualTo(Unknown)
        // Created before the vehicle had any reading: nothing to count from either.
        assertThat(status(oil(kind = ReminderKind.DISTANCE, anchorKm = null), 99_999.0)).isEqualTo(Unknown)
    }

    @Test
    fun `with no reading, both still counts its date`() {
        val r = oil()
        assertThat(status(r, null, today.plusDays(-60))).isEqualTo(Ok)
        assertThat(status(r, null, today.plusDays(2))).isEqualTo(Overdue(daysOver = 2, metresOver = null))
    }

    // -- the repeat -------------------------------------------------------------------------

    @Test
    fun `31 January plus six months is 31 July`() {
        val done = ReminderEvaluator.advance(oil(kind = ReminderKind.DATE), CivilDate.of(2026, 1, 31), null)
        assertThat(done.dueDate).isEqualTo(CivilDate.of(2026, 7, 31))
    }

    @Test
    fun `31 August plus six months clamps to the last day of February, leap years included`() {
        val r = oil(kind = ReminderKind.DATE)
        assertThat(ReminderEvaluator.advance(r, CivilDate.of(2026, 8, 31), null).dueDate)
            .isEqualTo(CivilDate.of(2027, 2, 28))
        assertThat(ReminderEvaluator.advance(r, CivilDate.of(2027, 8, 31), null).dueDate)
            .isEqualTo(CivilDate.of(2028, 2, 29))
    }

    @Test
    fun `completing it re-anchors on the completion, so a late job moves the next one out`() {
        // Due 26 Sep at 58,700 km, done three weeks late at 60,100 km.
        val late = ReminderEvaluator.advance(oil(), CivilDate.of(2026, 10, 17), 60_100 * km)

        assertThat(late.anchorDate).isEqualTo(CivilDate.of(2026, 10, 17))
        assertThat(late.anchorOdometerM).isEqualTo(60_100 * km)
        assertThat(late.dueDate).isEqualTo(CivilDate.of(2027, 4, 17))
        assertThat(late.dueOdometerM).isEqualTo(70_100 * km)
        assertThat(late.isActive).isTrue()
    }

    @Test
    fun `completing it without a reading leaves the distance uncounted rather than stale`() {
        val done = ReminderEvaluator.advance(oil(), CivilDate.of(2026, 9, 26), null)
        assertThat(done.dueOdometerM).isNull()
        assertThat(done.dueDate).isEqualTo(CivilDate.of(2027, 3, 26))
    }

    @Test
    fun `completing a one-off retires it, and a new round forgets the last notice`() {
        val once = oil(repeatMonths = null, repeatKm = null).copy(
            dueDate = CivilDate.of(2026, 10, 1),
            lastNotified = CivilDate.of(2026, 9, 20),
        )
        assertThat(once.repeats).isFalse()
        val done = ReminderEvaluator.advance(once, today, 58_000 * km)
        assertThat(done.isActive).isFalse()
        assertThat(done.lastNotified).isNull()

        assertThat(ReminderEvaluator.advance(oil().copy(lastNotified = today), today, null).lastNotified).isNull()
    }

    // -- evaluate ---------------------------------------------------------------------------

    @Test
    fun `evaluate puts the most urgent first, reads each vehicle's own reading, and skips retired ones`() {
        val fine = oil(id = 1, vehicleId = 1)
        val overdue = oil(id = 2, vehicleId = 2)
        val unknown = oil(id = 3, vehicleId = 3, kind = ReminderKind.DISTANCE)
        val retired = oil(id = 4, vehicleId = 1).copy(isActive = false)

        val result = ReminderEvaluator.evaluate(
            listOf(unknown, fine, retired, overdue),
            mapOf(1L to 50_000 * km, 2L to 59_000 * km, 3L to null),
            today.plusDays(-60),
        )

        assertThat(result.map { it.first.id }).containsExactly(2L, 1L, 3L).inOrder()
        assertThat(result.map { it.second }).containsExactly(Overdue(null, 300_000), Ok, Unknown).inOrder()
    }

    // -- notifications ---------------------------------------------------------------------

    @Test
    fun `never notifies twice on the same day`() {
        assertThat(ReminderEvaluator.shouldNotify(DueSoon(5, null), lastNotified = today, today = today)).isFalse()
        assertThat(ReminderEvaluator.shouldNotify(Overdue(3, null), lastNotified = today, today = today)).isFalse()
        assertThat(ReminderEvaluator.shouldNotify(Overdue(null, 1_000), lastNotified = today, today = today)).isFalse()
    }

    @Test
    fun `notifies once when it comes due, once more when the date passes, then stays quiet`() {
        assertThat(ReminderEvaluator.shouldNotify(DueSoon(20, null), null, today)).isTrue()
        // Told 20 days ahead; still due soon: quiet.
        assertThat(ReminderEvaluator.shouldNotify(DueSoon(10, null), today.plusDays(-10), today)).isFalse()
        // The date passed yesterday, and the last notice came before it: one more.
        assertThat(ReminderEvaluator.shouldNotify(Overdue(1, null), today.plusDays(-21), today)).isTrue()
        // Told on the first overdue day: quiet from then on.
        assertThat(ReminderEvaluator.shouldNotify(Overdue(5, null), today.plusDays(-4), today)).isFalse()
        assertThat(ReminderEvaluator.shouldNotify(Ok, null, today)).isFalse()
        assertThat(ReminderEvaluator.shouldNotify(Unknown, null, today)).isFalse()
    }

    @Test
    fun `overdue by distance alone after a due-soon notice has had its one notice`() {
        assertThat(ReminderEvaluator.shouldNotify(Overdue(null, 2_000), null, today)).isTrue()
        assertThat(ReminderEvaluator.shouldNotify(Overdue(null, 2_000), today.plusDays(-30), today)).isFalse()
    }

    // -- the editor's input --------------------------------------------------------------------

    @Test
    fun `months are whole numbers from one to ten years`() {
        assertThat(ReminderInput.months("6")).isEqualTo(6)
        assertThat(ReminderInput.months("120")).isEqualTo(120)
        assertThat(ReminderInput.months("0")).isNull()
        assertThat(ReminderInput.months("6.5")).isNull()
        assertThat(ReminderInput.months("121")).isNull()
        assertThat(ReminderInput.months("")).isNull()
    }

    @Test
    fun `a distance is typed in the vehicle's unit and must be more than nothing`() {
        assertThat(ReminderInput.distanceM("10000", DistanceUnit.KILOMETRE)).isEqualTo(10_000_000)
        assertThat(ReminderInput.distanceM("6000", DistanceUnit.MILE)).isEqualTo(9_656_064)
        assertThat(ReminderInput.distanceM("0", DistanceUnit.KILOMETRE)).isNull()
    }

    @Test
    fun `saving needs a name and a repeat for each thing the reminder counts`() {
        assertThat(ReminderInput.canSave("Oil change", ReminderKind.BOTH, 6, 10_000_000)).isTrue()
        assertThat(ReminderInput.canSave("Oil change", ReminderKind.BOTH, 6, null)).isFalse()
        assertThat(ReminderInput.canSave("Insurance", ReminderKind.DATE, 12, null)).isTrue()
        assertThat(ReminderInput.canSave("Tyres", ReminderKind.DISTANCE, null, 40_000_000)).isTrue()
        assertThat(ReminderInput.canSave("  ", ReminderKind.DATE, 12, null)).isFalse()
    }

    @Test
    fun `a short repeat gets a short warning window`() {
        assertThat(Reminder.warnDaysFor(1)).isEqualTo(7)
        assertThat(Reminder.warnDaysFor(6)).isEqualTo(30)
        assertThat(Reminder.warnDistanceFor(3_000_000)).isEqualTo(300_000)
        assertThat(Reminder.warnDistanceFor(10_000_000)).isEqualTo(500_000)
    }
}
