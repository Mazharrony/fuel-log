package com.fuelexpenselog.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.testing.MainDispatcherRule
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t = TestDb()

    @After
    fun tearDown() = t.close()

    private fun onboarding(locale: Locale = Locale.US) =
        OnboardingViewModel(SavedStateHandle(), t.repo, t.prefs) { locale }

    @Test
    fun `the phone's own country is the first guess`() = runTest {
        val state = onboarding(Locale.UK).state.first()
        assertThat(state.step).isEqualTo(OnboardingStep.COUNTRY)
        assertThat(state.region.countryCode).isEqualTo("GB")
        assertThat(state.preset).isEqualTo(UnitPreset.UK)
        assertThat(state.localeTag).isEqualTo("en-GB")
    }

    @Test
    fun `finishing writes the vehicle with the chosen units, and the defaults`() = runTest {
        val vm = onboarding(Locale.US)
        vm.onCountry("GB")
        vm.next()
        vm.onPreset(UnitPreset.UK_GALLONS)
        vm.next()
        vm.onName("  Ford Focus ")
        assertThat(vm.state.first { it.name.isNotBlank() }.step).isEqualTo(OnboardingStep.VEHICLE)

        vm.finish()
        vm.state.first { it.done }

        val vehicle = t.repo.allVehicles().single()
        assertThat(vehicle.name).isEqualTo("Ford Focus")
        assertThat(vehicle.distanceUnit).isEqualTo(DistanceUnit.MILE)
        assertThat(vehicle.volumeUnit).isEqualTo(EnergyUnit.IMP_GALLON)
        assertThat(vehicle.currencyCode).isEqualTo("GBP")
        assertThat(vehicle.consumptionFormat).isNull()
        assertThat(vehicle.defaultTag).isEqualTo(EntryTag.PERSONAL)

        with(t.prefs) {
            assertThat(onboardingDone).isTrue()
            assertThat(regionCountry).isEqualTo("GB")
            assertThat(defaultDistanceUnit).isEqualTo(DistanceUnit.MILE)
            assertThat(defaultVolumeUnit).isEqualTo(EnergyUnit.IMP_GALLON)
            assertThat(defaultCurrency).isEqualTo("GBP")
            assertThat(consumptionFormat).isEqualTo(ConsumptionFormat.MPG_UK)
            assertThat(lastVehicleId).isEqualTo(vehicle.id)
        }
    }

    @Test
    fun `a new country resets the unit set to what it implies`() = runTest {
        val vm = onboarding(Locale.US)
        vm.onPreset(UnitPreset.METRIC)
        vm.onCountry("IN")
        val state = vm.state.first { it.region.countryCode == "IN" }
        assertThat(state.preset).isEqualTo(UnitPreset.METRIC)
        assertThat(state.preset.format(state.region)).isEqualTo(ConsumptionFormat.KM_PER_L)
    }

    @Test
    fun `back walks the steps and then lets the system have it`() = runTest {
        val vm = onboarding()
        vm.next()
        vm.next()
        assertThat(vm.back()).isTrue()
        assertThat(vm.back()).isTrue()
        assertThat(vm.back()).isFalse()
        assertThat(vm.state.first().step).isEqualTo(OnboardingStep.COUNTRY)
    }

    @Test
    fun `someone who already has vehicles skips straight past`() = runTest {
        t.repo.addVehicle(DbFixtures.vehicle())
        val vm = onboarding()
        vm.state.first { it.done }
        assertThat(t.prefs.onboardingDone).isTrue()
        assertThat(t.repo.allVehicles()).hasSize(1)
    }

    @Test
    fun `no name, no vehicle`() = runTest {
        val vm = onboarding()
        vm.next()
        vm.next()
        vm.finish()
        assertThat(vm.state.first().canFinish).isFalse()
        assertThat(t.repo.allVehicles()).isEmpty()
        assertThat(t.prefs.onboardingDone).isFalse()
    }
}
