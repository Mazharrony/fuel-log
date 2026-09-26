package com.fuelexpenselog.app.ui.months

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.app.ui.vehicle.VehicleSnapshot
import com.fuelexpenselog.app.ui.vehicle.observeSnapshot
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.stats.Delta
import com.fuelexpenselog.domain.stats.MonthBucket
import com.fuelexpenselog.domain.stats.StatsCalculator
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.time.MonthKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.ZoneId

data class MonthRow(val bucket: MonthBucket, val delta: Delta?)

data class MonthsUiState(
    val snapshot: VehicleSnapshot? = null,
    val year: Int = 0,
    val firstYear: Int = 0,
    val currentMonth: MonthKey? = null,
    /** Only months with entries, newest first. */
    val rows: List<MonthRow> = emptyList(),
    /** Null when the year spans more than one currency: an average across them is invented. */
    val monthlyAverage: Money? = null,
    val highest: MonthBucket? = null,
    /** The largest single-currency total, for scaling the bars. */
    val scaleMicros: Long = 0,
    val missing: Boolean = false,
) {
    val canGoBack: Boolean get() = year > firstYear
    val canGoForward: Boolean get() = currentMonth != null && year < currentMonth.year
}

/** A year of months, each split into fuel and everything else. */
class MonthsViewModel(
    private val handle: SavedStateHandle,
    repository: FuelLogRepository,
    prefs: AppPrefs,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {

    private val vehicleId: Long = checkNotNull(handle.get<Long>(Routes.ARG_ID))
    private val today get() = CivilDate.today(clock, zone())
    private val year = handle.getStateFlow(KEY_YEAR, handle.get<Int>(Routes.ARG_YEAR)?.takeIf { it > 0 } ?: today.year)

    val state: StateFlow<MonthsUiState> = combine(repository.observeSnapshot(vehicleId, prefs), year) { snapshot, y ->
        if (snapshot == null) return@combine MonthsUiState(missing = true)
        val all = StatsCalculator.months(snapshot.history)
        val inYear = all.filter { it.month.year == y }
        val totals = inYear.map { it.total }
        val singleCurrency = totals.all { it.size == 1 } && totals.map { it.first().currency }.distinct().size == 1
        val previousByMonth = all.zipWithNext().associate { (newer, older) -> newer.month to older }

        MonthsUiState(
            snapshot = snapshot,
            year = y,
            firstYear = all.minOfOrNull { it.month.year } ?: y,
            currentMonth = today.monthKey,
            rows = inYear.map { bucket ->
                MonthRow(bucket, StatsCalculator.moneyDelta(bucket.total, previousByMonth[bucket.month]?.total.orEmpty()))
            },
            monthlyAverage = if (singleCurrency && inYear.isNotEmpty()) {
                val first = totals.first().first()
                Money(totals.sumOf { it.first().micros } / inYear.size, first.currency)
            } else {
                null
            },
            highest = if (singleCurrency) inYear.maxByOrNull { it.total.first().micros } else null,
            scaleMicros = if (singleCurrency) inYear.maxOfOrNull { it.total.first().micros } ?: 0 else 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthsUiState())

    fun previousYear() {
        handle[KEY_YEAR] = year.value - 1
    }

    fun nextYear() {
        handle[KEY_YEAR] = year.value + 1
    }

    private companion object {
        const val KEY_YEAR = "months_year"
    }
}
