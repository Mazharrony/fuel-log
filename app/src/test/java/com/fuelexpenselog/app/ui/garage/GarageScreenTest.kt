package com.fuelexpenselog.app.ui.garage

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.format.rememberFormatters
import com.fuelexpenselog.app.testing.ComposeHostRule
import com.fuelexpenselog.app.ui.theme.FuelLogTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves Compose renders under Robolectric (NATIVE graphics) before the critical path's
 * tests depend on it.
 */
@RunWith(RobolectricTestRunner::class)
class GarageScreenTest {

    @get:Rule(order = 0)
    val host = ComposeHostRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun `a vehicle with no fill-ups shows its name and asks for two full tanks`() {
        var opened = 0L
        val civic = DbFixtures.vehicle().copy(id = 1)
        compose.setContent {
            FuelLogTheme(darkTheme = false) {
                CompositionLocalProvider(LocalFormatters provides rememberFormatters()) {
                    GarageScreen(
                        state = GarageUiState.Ready(
                            active = listOf(GarageVehicle(civic, odometerM = null)),
                            archived = emptyList(),
                            showArchived = false,
                            needsOnboarding = false,
                        ),
                        onAddVehicle = {},
                        onOpenVehicle = { opened = it },
                        onToggleArchived = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Honda Civic").assertIsDisplayed()
        compose.onNodeWithText("Needs two full tanks").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()

        compose.onNodeWithText("Honda Civic").performClick()
        assertThat(opened).isEqualTo(1L)
    }
}
