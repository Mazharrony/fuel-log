package com.fuelexpenselog.app.ui.settings

import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.testing.MainDispatcherRule
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyKind
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
class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val t = TestDb()

    @After
    fun tearDown() = t.close()

    @Test
    fun `a format change reaches every vehicle without its own, and no other`() = runTest {
        val follows = t.repo.addVehicle(DbFixtures.vehicle(name = "Follows the app"))
        val ownOpinion = t.repo.addVehicle(DbFixtures.vehicle(name = "British").copy(consumptionFormat = ConsumptionFormat.MPG_UK))

        val vm = SettingsViewModel(t.prefs) { Locale.US }
        vm.onFormat(ConsumptionFormat.MPG_US)
        assertThat(vm.state.first { it.format == ConsumptionFormat.MPG_US }.format).isEqualTo(ConsumptionFormat.MPG_US)

        val appDefault = t.prefs.consumptionFormat
        assertThat(t.repo.vehicle(follows)!!.formatFor(EnergyKind.LIQUID, appDefault)).isEqualTo(ConsumptionFormat.MPG_US)
        assertThat(t.repo.vehicle(ownOpinion)!!.formatFor(EnergyKind.LIQUID, appDefault)).isEqualTo(ConsumptionFormat.MPG_UK)
    }

    @Test
    fun `a region change sets new-vehicle defaults and touches no existing vehicle`() = runTest {
        val id = t.repo.addVehicle(DbFixtures.vehicle(distance = DistanceUnit.KILOMETRE, currency = "EUR"))
        val before = t.db.vehicleDao().byId(id)

        val vm = SettingsViewModel(t.prefs) { Locale.GERMANY }
        vm.onRegion("US")

        val state = vm.state.first { it.region.countryCode == "US" }
        assertThat(state.defaultDistance).isEqualTo(DistanceUnit.MILE)
        assertThat(state.defaultVolume).isEqualTo(EnergyUnit.US_GALLON)
        assertThat(state.defaultCurrency).isEqualTo("USD")
        assertThat(t.db.vehicleDao().byId(id)).isEqualTo(before)
    }

    @Test
    fun `with nothing chosen yet, the phone's region fills the blanks`() = runTest {
        val state = SettingsViewModel(t.prefs) { Locale.UK }.state.first()
        assertThat(state.region.countryCode).isEqualTo("GB")
        assertThat(state.defaultDistance).isEqualTo(DistanceUnit.MILE)
        assertThat(state.defaultCurrency).isEqualTo("GBP")
        assertThat(state.format).isEqualTo(ConsumptionFormat.L_PER_100KM)
    }
}
