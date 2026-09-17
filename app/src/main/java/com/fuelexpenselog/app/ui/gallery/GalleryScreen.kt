package com.fuelexpenselog.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.components.ActionBar
import com.fuelexpenselog.app.ui.components.ChartBar
import com.fuelexpenselog.app.ui.components.ConsumptionBarChart
import com.fuelexpenselog.app.ui.components.EmptyState
import com.fuelexpenselog.app.ui.components.EntryKind
import com.fuelexpenselog.app.ui.components.EntryRow
import com.fuelexpenselog.app.ui.components.FigureInput
import com.fuelexpenselog.app.ui.components.Hairline
import com.fuelexpenselog.app.ui.components.PrimaryAction
import com.fuelexpenselog.app.ui.components.SecondaryAction
import com.fuelexpenselog.app.ui.components.SectionLabel
import com.fuelexpenselog.app.ui.components.Sparkline
import com.fuelexpenselog.app.ui.components.SplitBar
import com.fuelexpenselog.app.ui.components.SquareChip
import com.fuelexpenselog.app.ui.components.StatStrip
import com.fuelexpenselog.app.ui.components.SunkenPanel
import com.fuelexpenselog.app.ui.components.ToggleRow
import com.fuelexpenselog.app.ui.components.TotalRow
import com.fuelexpenselog.app.ui.components.WarningBlock
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * Every component on one screen, debug builds only.
 *
 * This exists because there is no Android Studio on this machine, so Compose
 * @Preview will not render reliably. Installing the debug build and opening this
 * is the substitute, and it doubles as the place to check the design against the
 * exported screenshots at 390dp.
 */
@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    val colors = FuelTheme.colors
    var fullTank by remember { mutableStateOf(true) }
    var selectedChip by remember { mutableStateOf(0) }
    val odometer = rememberTextFieldState("84210")
    val volume = rememberTextFieldState("11.8")

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        Column(
            Modifier
                .weight(1f)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "Component gallery",
                style = FuelTheme.type.title,
                color = colors.ink,
                modifier = Modifier.padding(top = 40.dp),
            )

            // The tabular-figures check. If tnum is not honoured these three
            // lines will not be the same width, and every column of numbers in
            // the app is quietly misaligned.
            Section("Tabular figures - all three rows must be identical width") {
                Text("1111111111", style = FuelTheme.type.figureL, color = colors.ink)
                Text("0000000000", style = FuelTheme.type.figureL, color = colors.ink)
                Text("8888888888", style = FuelTheme.type.figureL, color = colors.ink)
            }

            Section("Type scale") {
                Text("34.2", style = FuelTheme.type.figureXl, color = colors.ink)
                Text("84,210", style = FuelTheme.type.figureL, color = colors.ink)
                Text("$4,218.90", style = FuelTheme.type.figureM, color = colors.ink)
                Text("Garage", style = FuelTheme.type.title, color = colors.ink)
                Text("Row title", style = FuelTheme.type.body, color = colors.ink)
                Text("Row subtitle meta", style = FuelTheme.type.meta, color = colors.bodyGrey)
                SectionLabel("Section label")
            }

            Section("Entry rows - bar colour codes the type") {
                EntryRow(
                    kind = EntryKind.FULL_TANK, title = "Fill-up", meta = "17 Sep - 84,210 mi",
                    value = "$41.30", rate = "34.2 mpg",
                )
                EntryRow(
                    kind = EntryKind.PARTIAL, title = "Partial fill", meta = "12 Sep - 83,900 mi",
                    value = "$22.10", rate = "-",
                )
                EntryRow(
                    kind = EntryKind.EXPENSE, title = "Oil change", meta = "2 Sep - 83,600 mi",
                    value = "$64.00",
                )
                EntryRow(
                    kind = EntryKind.FULL_TANK, title = "Flagged figure", meta = "1 Sep",
                    value = "$38.00", rate = "894.0 mpg", flagged = true,
                )
            }

            Section("Stat strip") {
                StatStrip(
                    cells = listOf(
                        "This month" to "$212.40",
                        "Average" to "33.6",
                        "Cost/mi" to "$0.13",
                    )
                )
            }

            Section("Inputs") {
                FigureInput(label = "Odometer - mi", state = odometer)
                FigureInput(label = "Gallons", state = volume)
            }

            Section("Toggle - yellow row means on") {
                ToggleRow(label = "Full tank", checked = fullTank, onCheckedChange = { fullTank = it })
            }

            Section("Chips") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Oil change", "Service", "Tyres").forEachIndexed { index, label ->
                        SquareChip(
                            label = label,
                            selected = selectedChip == index,
                            onClick = { selectedChip = index },
                        )
                    }
                }
            }

            Section("Warning - warn, never block") {
                WarningBlock(
                    listOf(
                        "Odometer is lower than the last entry. Save anyway if it was reset.",
                        "That is more than the tank holds. Fine for a jerry can.",
                    )
                )
            }

            Section("Chart - dashes where the chain breaks") {
                ConsumptionBarChart(
                    bars = listOf(
                        ChartBar(33.1, "Apr"), ChartBar(34.8, "May"), ChartBar(32.9, "Jun"),
                        ChartBar(null, "Jun"), ChartBar(33.8, "Jul"), ChartBar(31.7, "Jul"),
                        ChartBar(null, "Aug"), ChartBar(35.1, "Aug"), ChartBar(34.2, "Sep"),
                    ),
                    unitLabel = "mpg",
                )
            }

            Section("Sparkline") {
                Sparkline(listOf(33.1, 34.8, 32.9, null, 33.8, 31.7, 35.1, 34.2))
            }

            Section("Split bar - fuel against everything else") {
                SplitBar(fuel = 180.0, other = 60.0, maxTotal = 412.6)
                SplitBar(fuel = 210.0, other = 202.6, maxTotal = 412.6)
            }

            Section("Totals") {
                TotalRow("Fuel", "$1,842.40")
                TotalRow("Maintenance and expenses", "$986.20")
                TotalRow("Total running cost", "$2,828.60", emphasised = true)
            }

            Section("Empty state - says why, never just a dash") {
                EmptyState(
                    title = "No consumption figure yet",
                    explanation = "Log two full tanks and the mileage appears here. " +
                        "A single fill-up has nothing to measure against.",
                )
            }

            SunkenPanel {
                SectionLabel("Across all vehicles - 2026")
                Text("$4,218.90", style = FuelTheme.type.figureM, color = colors.ink)
            }
        }

        ActionBar {
            PrimaryAction(label = "Add fill-up", onClick = {})
            SecondaryAction(label = "Add expense", onClick = {})
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(title)
        Hairline()
        content()
    }
}
