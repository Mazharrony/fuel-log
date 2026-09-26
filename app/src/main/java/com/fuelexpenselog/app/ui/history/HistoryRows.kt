package com.fuelexpenselog.app.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.EntryBar
import com.fuelexpenselog.app.ui.common.EntryRow
import com.fuelexpenselog.app.ui.common.dashOr
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.domain.consumption.ConsumptionResult
import com.fuelexpenselog.domain.consumption.Measured
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.model.HistoryEntry
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.unit.ConsumptionFormat

/** One history line and what the engine said about it. */
data class HistoryRow(
    val entry: HistoryEntry,
    /** The span that ends at this fill-up, when there is one. */
    val measured: Measured?,
    val bar: EntryBar,
)

/**
 * Pairs each entry with its figure. A fill-up is yellow only when a measured span ends on
 * it: a partial, the first tank, or one that closes a gap gets the grey bar, because it
 * produced no figure of its own.
 */
fun historyRows(history: List<HistoryEntry>, consumption: ConsumptionResult): List<HistoryRow> {
    val byEnd = consumption.measured.associateBy { it.endEventId }
    return history.map { entry ->
        when (entry) {
            is HistoryEntry.Fuel -> {
                val measured = byEnd[entry.fillUp.id]
                HistoryRow(entry, measured, if (measured != null) EntryBar.FUEL else EntryBar.PARTIAL)
            }
            is HistoryEntry.Cost -> HistoryRow(entry, null, EntryBar.EXPENSE)
        }
    }
}

/**
 * What a unit of fuel cost, per the vehicle's own volume unit. From the total when there is
 * one; otherwise from the stored unit price, which is per the unit it was entered in.
 */
fun pricePerVehicleUnit(fillUp: FillUp, vehicle: Vehicle): Money? {
    fillUp.total?.let { total ->
        val units = fillUp.energy.inUnit(vehicle.volumeUnit)
        return if (units > 0.0) Money.of(total.asDouble / units, total.currency) else null
    }
    return fillUp.unitPrice?.let { it * (vehicle.volumeUnit.microPerUnit / fillUp.energyUnitEntered.microPerUnit) }
}

/**
 * A fill-up reads "41.50 L" over its date and reading, with the total and the tank's figure
 * on the right. An expense reads as its category, with the amount.
 */
@Composable
fun HistoryRowItem(row: HistoryRow, vehicle: Vehicle, format: ConsumptionFormat, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val f = LocalFormatters.current
    val entry = row.entry
    val meta = listOfNotNull(
        f.date.medium(entry.date),
        entry.odometerM?.let { f.distance.format(it, vehicle.distanceUnit) },
    ).joinToString(" · ")

    when (entry) {
        is HistoryEntry.Fuel -> {
            val fillUp = entry.fillUp
            val volume = f.volume.format(fillUp.energy, vehicle.volumeUnit)
            val rate = row.measured?.let { m ->
                stringResource(R.string.value_with_unit, f.consumption.value(m.shownAs(format), format), f.consumption.unitLabel(format))
            } ?: pricePerVehicleUnit(fillUp, vehicle)?.let { perUnit ->
                stringResource(R.string.history_per_unit, f.currency.formatRate(perUnit), f.volume.unitLabel(vehicle.volumeUnit))
            }
            EntryRow(
                title = if (fillUp.isFull) volume else stringResource(R.string.history_partial, volume),
                meta = meta,
                value = dashOr(fillUp.totalOrDerived()?.let(f.currency::format)),
                rate = rate,
                bar = row.bar,
                onClick = onClick,
                modifier = modifier,
            )
        }
        is HistoryEntry.Cost -> {
            val expense = entry.expense
            val title = listOfNotNull(expense.category.label(), expense.vendor).joinToString(" · ")
            EntryRow(
                title = title,
                meta = meta,
                value = f.currency.format(expense.amount),
                rate = if (expense.tag == EntryTag.BUSINESS) expense.tag.label() else null,
                bar = row.bar,
                onClick = onClick,
                modifier = modifier,
            )
        }
    }
}
