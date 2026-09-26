package com.fuelexpenselog.app.ui.entry

import androidx.lifecycle.SavedStateHandle
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.testing.MainDispatcherRule
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.consumption.Gap
import com.fuelexpenselog.domain.consumption.GapReason
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.validate.EntryWarning
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class FillUpEditorViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t = TestDb()

    /** "Now" is 1 March 2026; the fixtures' January dates are all in the past. */
    private val clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC)
    private val today = CivilDate.of(2026, 3, 1)

    @After
    fun tearDown() = t.close()

    private fun editor(vehicleId: Long = 0, fillUpId: Long = 0) = FillUpEditorViewModel(
        handle = SavedStateHandle(
            buildMap {
                if (vehicleId != 0L) put(Routes.ARG_VEHICLE, vehicleId)
                if (fillUpId != 0L) put(Routes.ARG_ID, fillUpId)
            },
        ),
        repository = t.repo,
        prefs = t.prefs,
        clock = clock,
        zone = { ZoneOffset.UTC },
        locale = { Locale.US },
    )

    private suspend fun FillUpEditorViewModel.ready() = state.first { it.form != null && it.vehicle != null }

    @Test
    fun `a reading below the last one warns, and the entry still saves`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 48_700.0, litres = 40.0))

        val vm = editor(v)
        vm.ready()
        vm.onOdometer("48100")
        vm.onVolume("30")
        vm.onTotal("45")

        val state = vm.state.first { it.form?.totalText == "45" }
        assertThat(state.warnings).contains(EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS)
        assertThat(state.canSave).isTrue()

        vm.save()
        vm.state.first { it.done }
        assertThat(t.repo.allFillUps()).hasSize(2)
    }

    @Test
    fun `an empty total is the one thing that blocks`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        val vm = editor(v)
        vm.ready()
        vm.onOdometer("1000")
        vm.onVolume("40")

        assertThat(vm.state.first { it.form?.volumeText == "40" }.canSave).isFalse()

        vm.onTotal("0")
        // Zero is accepted in silence: a fuel card, a friend, a warranty.
        val state = vm.state.first { it.form?.totalText == "0" }
        assertThat(state.canSave).isTrue()
        assertThat(state.warnings).isEmpty()
    }

    @Test
    fun `lowering the latest fill-up's own reading does not warn against itself`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 48_200.0, litres = 40.0))
        val latest = t.repo.addFillUp(DbFixtures.fillUp(v, day = 7, odometerKm = 48_700.0, litres = 41.5))

        val vm = editor(fillUpId = latest)
        assertThat(vm.ready().form!!.odometerText).isEqualTo("48700")
        vm.onOdometer("48650")

        val state = vm.state.first { it.form?.odometerText == "48650" }
        assertThat(state.previousOdometerM).isEqualTo(48_200_000)
        assertThat(state.warnings).doesNotContain(EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS)
    }

    @Test
    fun `a missed fill-up before this one breaks only the span it belongs to`() = runTest {
        val vehicle = DbFixtures.vehicle()
        val v = t.repo.addVehicle(vehicle)
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 10_000.0, litres = 40.0))
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 5, odometerKm = 10_500.0, litres = 35.0))

        val vm = editor(v)
        vm.ready()
        vm.onOdometer("11200")
        vm.onVolume("45")
        vm.onTotal("70")
        vm.onMissedPrevious(true)
        vm.state.first { it.form?.missedPrevious == true && it.form?.totalText == "70" }
        vm.save()
        vm.state.first { it.done }

        val newId = t.repo.allFillUps().maxOf { it.id }
        val timeline = t.repo.observeConsumption(t.repo.vehicle(v)!!).first().timeline
        val gap = timeline.filterIsInstance<Gap>().single { it.reason == GapReason.MISSED_FILL_UP }
        assertThat(gap.eventIds).contains(newId)
        // The span before the missed fill-up is untouched.
        assertThat(timeline.filterIsInstance<Measured>().single().distanceM).isEqualTo(500_000)
    }

    @Test
    fun `the figure previewed while typing is the figure that exists after saving`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 48_200.0, litres = 40.0, total = 60.0))

        val vm = editor(v)
        vm.ready()
        vm.onOdometer("48700")
        vm.onVolume("41.5")
        vm.onTotal("62")
        val preview = vm.state.first { it.preview is Measured }.preview as Measured
        val shown = preview.shownAs(ConsumptionFormat.L_PER_100KM)!!
        assertThat(shown).isWithin(0.005).of(8.3)

        vm.save()
        vm.state.first { it.done }

        val saved = t.repo.observeConsumption(t.repo.vehicle(v)!!).first().measured.last()
        assertThat(saved.shownAs(ConsumptionFormat.L_PER_100KM)).isEqualTo(shown)
    }

    @Test
    fun `four warnings at once, and it still saves`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle(tankLitres = 47.0))
        // Five days ahead of "now", at noon: the same instant a back- or forward-dated entry gets.
        val ahead = CivilDate.of(2026, 3, 6)
        val noon = ahead.toLocalDate().atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC).toEpochMilli()
        t.repo.addFillUp(
            DbFixtures.fillUp(v, day = 0, odometerKm = 50_000.0, litres = 40.0)
                .copy(date = ahead, instantMillis = noon),
        )

        val vm = editor(v)
        vm.ready()
        vm.onDate(ahead) // the future
        vm.onOdometer("49999.8") // 200 m below the last reading - and within 500 m of it
        vm.onVolume("60") // a 47 L tank
        vm.onTotal("90")

        val state = vm.state.first { it.form?.totalText == "90" && it.warnings.size >= 4 }
        assertThat(state.warnings).containsAtLeast(
            EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS,
            EntryWarning.VOLUME_OVER_TANK_CAPACITY,
            EntryWarning.FUTURE_DATE,
            EntryWarning.LOOKS_LIKE_DUPLICATE,
        )
        assertThat(state.canSave).isTrue()

        vm.save()
        vm.state.first { it.done }
        assertThat(t.repo.allFillUps()).hasSize(2)
    }

    @Test
    fun `an untouched edit writes the stored values back exactly`() = runTest {
        val miles = DbFixtures.vehicle(distance = DistanceUnit.MILE)
        val v = t.repo.addVehicle(miles)
        val id = t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 48_200.123, litres = 40.0, total = 58.396))
        val before = t.db.fillUpDao().byId(id)!!

        val vm = editor(fillUpId = id)
        vm.ready()
        vm.onNote("receipt in the glovebox")
        vm.state.first { it.form?.note?.isNotEmpty() == true }
        vm.save()
        vm.state.first { it.done }

        val after = t.db.fillUpDao().byId(id)!!
        assertThat(after.odometerM).isEqualTo(before.odometerM)
        assertThat(after.energyMicro).isEqualTo(before.energyMicro)
        assertThat(after.totalMicros).isEqualTo(before.totalMicros)
        assertThat(after.note).isEqualTo("receipt in the glovebox")
    }

    @Test
    fun `a new fill-up starts on today, full, with no odometer and the last reading as a hint`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 83_898.0, litres = 40.0))

        val state = editor(v).ready()
        assertThat(state.form!!.date).isEqualTo(today)
        assertThat(state.form!!.isFull).isTrue()
        assertThat(state.form!!.odometerText).isEmpty()
        assertThat(state.previousOdometerM).isEqualTo(83_898_000)
        // Nothing typed yet, so nothing to warn about.
        assertThat(state.warnings).isEmpty()
    }
}
