package com.fuelexpenselog.app.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.vehicle.VehicleSnapshot
import com.fuelexpenselog.app.ui.vehicle.observeSnapshot
import com.fuelexpenselog.domain.format.perDistanceUnit
import com.fuelexpenselog.domain.model.HistoryEntry
import com.fuelexpenselog.domain.money.CurrencySubtotals
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.stats.Delta
import com.fuelexpenselog.domain.stats.RecentSpans
import com.fuelexpenselog.domain.stats.StatsCalculator
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.time.MonthKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.ZoneId

/** A month's consumption in the vehicle's format, and its change on the month before. */
data class MonthFigure(val month: MonthKey, val value: Double?, val delta: Delta?)

data class StatisticsUiState(
    val snapshot: VehicleSnapshot? = null,
    val recent: RecentSpans = RecentSpans(emptyList(), null),
    val fuel: List<Money> = emptyList(),
    val other: List<Money> = emptyList(),
    val total: List<Money> = emptyList(),
    val costPerDistance: Money? = null,
    val distanceLoggedM: Long? = null,
    val thisMonth: MonthFigure? = null,
    val lastMonth: MonthFigure? = null,
    val missing: Boolean = false,
)

/** The chart, the running totals, and month on month - one vehicle, everything recomputed. */
class StatisticsViewModel(
    handle: SavedStateHandle,
    repository: FuelLogRepository,
    prefs: AppPrefs,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {

    private val vehicleId: Long = checkNotNull(handle.get<Long>(Routes.ARG_ID))

    val state: StateFlow<StatisticsUiState> = repository.observeSnapshot(vehicleId, prefs).map { snapshot ->
        if (snapshot == null) return@map StatisticsUiState(missing = true)
        val history = snapshot.history
        val measured = snapshot.consumption.measured
        val format = snapshot.format
        val readings = history.mapNotNull { it.odometerM }

        fun figure(month: MonthKey): Double? =
            StatsCalculator.monthKmPerUnit(measured, month)?.let(format::fromKmPerUnit)

        val month = CivilDate.today(clock, zone()).monthKey
        val thisValue = figure(month)
        val lastValue = figure(month.minusMonths(1))
        val beforeValue = figure(month.minusMonths(2))

        StatisticsUiState(
            snapshot = snapshot,
            recent = StatsCalculator.recent(snapshot.consumption.timeline, Dimens.CHART_BARS),
            fuel = CurrencySubtotals.of(history.filterIsInstance<HistoryEntry.Fuel>().mapNotNull { it.amount }),
            other = CurrencySubtotals.of(history.filterIsInstance<HistoryEntry.Cost>().map { it.expense.amount }),
            total = CurrencySubtotals.of(history.mapNotNull { it.amount }),
            costPerDistance = snapshot.consumption.summary?.costPerKm()?.perDistanceUnit(snapshot.vehicle.distanceUnit),
            distanceLoggedM = if (readings.size >= 2) readings.max() - readings.min() else null,
            thisMonth = MonthFigure(month, thisValue, StatsCalculator.consumptionDelta(thisValue, lastValue, format)),
            lastMonth = MonthFigure(month.minusMonths(1), lastValue, StatsCalculator.consumptionDelta(lastValue, beforeValue, format)),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())
}
