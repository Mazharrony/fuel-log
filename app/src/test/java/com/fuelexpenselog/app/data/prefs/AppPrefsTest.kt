package com.fuelexpenselog.app.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppPrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val raw = context.getSharedPreferences(AppPrefs.FILE_NAME, Context.MODE_PRIVATE)
    private lateinit var prefs: AppPrefs

    @Before
    fun setUp() {
        raw.edit().clear().commit()
        prefs = AppPrefs(context)
    }

    @Test
    fun `a format written by a newer version reads as the default, not a crash`() {
        raw.edit().putString(AppPrefs.KEY_CONSUMPTION_FORMAT, "MILES_PER_KILOWATT_FORTNIGHT").commit()
        assertThat(prefs.consumptionFormat).isEqualTo(ConsumptionFormat.L_PER_100KM)
    }

    @Test
    fun `a kWh default volume unit is refused - a vehicle's volume is always a liquid`() {
        raw.edit().putString(AppPrefs.KEY_DEFAULT_VOLUME_UNIT, EnergyUnit.KWH.name).commit()
        assertThat(prefs.defaultVolumeUnit).isNull()
    }

    @Test
    fun `observing emits the current value, then each change`() = runTest {
        prefs.observeConsumptionFormat().test {
            assertThat(awaitItem()).isEqualTo(ConsumptionFormat.L_PER_100KM)
            prefs.consumptionFormat = ConsumptionFormat.MPG_UK
            assertThat(awaitItem()).isEqualTo(ConsumptionFormat.MPG_UK)
            // A change to another key is not a change to this one.
            prefs.lastVehicleId = 7
            expectNoEvents()
        }
    }

    @Test
    fun `dismissed proposals round-trip as a set`() {
        prefs.dismissedProposals = setOf("v1:e2:ROLLOVER:1000", "v1:e3:SUSPECTED_TYPO:2000")
        assertThat(prefs.dismissedProposals).containsExactly("v1:e2:ROLLOVER:1000", "v1:e3:SUSPECTED_TYPO:2000")
    }
}
