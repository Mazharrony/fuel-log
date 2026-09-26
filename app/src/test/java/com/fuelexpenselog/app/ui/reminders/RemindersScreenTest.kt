package com.fuelexpenselog.app.ui.reminders

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.format.rememberFormatters
import com.fuelexpenselog.app.testing.ComposeHostRule
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderKind
import com.fuelexpenselog.domain.reminder.ReminderStatus
import com.fuelexpenselog.domain.time.CivilDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A reminder with nothing to count from is a dash with its reason - never "overdue", never a zero. */
@RunWith(RobolectricTestRunner::class)
class RemindersScreenTest {

    @get:Rule(order = 0)
    val host = ComposeHostRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun `an Unknown reminder shows a dash and why, and nothing says overdue`() {
        val vehicle = DbFixtures.vehicle().copy(id = 1)
        val chain = Reminder(
            id = 1,
            vehicleId = 1,
            title = "Chain",
            kind = ReminderKind.DISTANCE,
            category = ExpenseCategory.PARTS,
            dueDate = null,
            repeatMonths = null,
            dueOdometerM = 1_500_000,
            repeatDistanceM = 500_000,
            anchorDate = CivilDate.of(2025, 8, 22),
            anchorOdometerM = 1_000_000,
        )

        compose.setContent {
            CompositionLocalProvider(LocalFormatters provides rememberFormatters()) {
                RemindersScreen(
                    state = RemindersUiState(loading = false, vehicle = vehicle, rows = listOf(chain to ReminderStatus.Unknown)),
                    onBack = {},
                    onAdd = {},
                    onOpen = {},
                    onDone = {},
                )
            }
        }

        compose.onNodeWithText("Chain").assertIsDisplayed()
        compose.onNodeWithText("Every 500 km").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNodeWithText("No odometer reading to count from yet").assertIsDisplayed()
        compose.onAllNodesWithText("overdue", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("0").assertCountEquals(0)
    }
}
