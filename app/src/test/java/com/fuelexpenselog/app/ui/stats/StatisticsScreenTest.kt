package com.fuelexpenselog.app.ui.stats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.format.rememberFormatters
import com.fuelexpenselog.app.testing.ComposeHostRule
import com.fuelexpenselog.app.ui.vehicle.VehicleSnapshot
import com.fuelexpenselog.domain.consumption.Confidence
import com.fuelexpenselog.domain.consumption.ConsumptionResult
import com.fuelexpenselog.domain.consumption.ConsumptionSummary
import com.fuelexpenselog.domain.consumption.Gap
import com.fuelexpenselog.domain.consumption.GapReason
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.stats.StatsCalculator
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyKind
import com.fuelexpenselog.domain.unit.EnergyUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The master plan's mandated chart test: a gap slot is a dash with its reason, never a zero. */
@RunWith(RobolectricTestRunner::class)
class StatisticsScreenTest {

    @get:Rule(order = 0)
    val host = ComposeHostRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun `a gap slot shows a dash and its reason, not a zero`() {
        val day = CivilDate.of(2026, 9, 3)
        val measured = Measured(
            index = 0,
            endDate = day,
            startEventId = 1,
            endEventId = 2,
            distanceM = 500_000,
            energy = Energy.of(EnergyUnit.LITRE, 40.0),
            cost = null,
            fillUpsSpanned = 1,
            confidence = Confidence.EXACT,
            otherKindMicro = 0,
        )
        val gap = Gap(1, day.plusDays(14), GapReason.MISSED_FILL_UP, listOf(3))
        val timeline = listOf(measured, gap)
        val snapshot = VehicleSnapshot(
            vehicle = DbFixtures.vehicle().copy(id = 1),
            history = emptyList(),
            consumption = ConsumptionResult(timeline, ConsumptionSummary.of(EnergyKind.LIQUID, listOf(measured)), emptyList()),
            format = ConsumptionFormat.KM_PER_L,
            odometerM = null,
        )

        compose.setContent {
            CompositionLocalProvider(LocalFormatters provides rememberFormatters()) {
                StatisticsScreen(
                    state = StatisticsUiState(snapshot = snapshot, recent = StatsCalculator.recent(timeline, 9)),
                    onBack = {},
                )
            }
        }

        // The header average and the bar's own label both read 12.50 with a single span.
        compose.onAllNodesWithText("12.50").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("—").fetchSemanticsNodes().let { check(it.isNotEmpty()) { "no dash" } }
        compose.onNodeWithText("A fill-up in this stretch was not recorded", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("0").assertCountEquals(0)
        compose.onAllNodesWithText("0.00").assertCountEquals(0)
    }
}
