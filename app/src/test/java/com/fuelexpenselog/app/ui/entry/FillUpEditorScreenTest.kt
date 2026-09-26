package com.fuelexpenselog.app.ui.entry

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.format.rememberFormatters
import com.fuelexpenselog.app.testing.ComposeHostRule
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.app.ui.theme.FuelLogTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.ZoneOffset

/**
 * The master plan's mandated test for the critical path: a warning is visible AND the save
 * button is enabled AND pressing it stores the row. Warn, never block - end to end.
 */
@RunWith(RobolectricTestRunner::class)
class FillUpEditorScreenTest {

    @get:Rule(order = 0)
    val host = ComposeHostRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val t = TestDb()

    @After
    fun tearDown() = t.close()

    @Test
    fun `a lower odometer shows its warning, leaves save enabled, and saves`() {
        val vehicleId = runBlocking {
            val v = t.repo.addVehicle(DbFixtures.vehicle(currency = "EUR"))
            t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 48_700.0, litres = 40.0))
            v
        }
        val vm = FillUpEditorViewModel(
            handle = SavedStateHandle(mapOf(Routes.ARG_VEHICLE to vehicleId)),
            repository = t.repo,
            prefs = t.prefs,
            clock = Clock.systemUTC(),
            zone = { ZoneOffset.UTC },
        )
        var closed = false
        compose.setContent {
            FuelLogTheme(darkTheme = false) {
                CompositionLocalProvider(LocalFormatters provides rememberFormatters()) {
                    FillUpEditorRoute(onDone = { closed = true }, viewModel = vm)
                }
            }
        }

        val context = t.context
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(context.getString(R.string.fillup_total, "EUR")))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(context.getString(R.string.fillup_odometer, "km")).performTextInput("48100")
        compose.onNodeWithContentDescription(context.getString(R.string.unit_litres_long)).performTextInput("30")
        compose.onNodeWithContentDescription(context.getString(R.string.fillup_total, "EUR")).performTextInput("45")

        compose.onNodeWithText("Lower than the last reading", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.fillup_save)).assertIsEnabled().performClick()

        awaitMainLooper { closed || vm.state.value.error != null }
        assertThat(vm.state.value.error).isNull()
        assertThat(runBlocking { t.repo.allFillUps() }).hasSize(2)
    }

    /**
     * The save's continuation is posted to the main looper once Room's background write
     * finishes. `waitUntil` only sleeps, so the looper is idled here between checks.
     */
    private fun awaitMainLooper(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met within $timeoutMs ms" }
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }
}
