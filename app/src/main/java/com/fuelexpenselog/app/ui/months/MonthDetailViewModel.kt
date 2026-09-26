package com.fuelexpenselog.app.ui.months

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.app.ui.vehicle.VehicleSnapshot
import com.fuelexpenselog.app.ui.vehicle.observeSnapshot
import com.fuelexpenselog.domain.stats.CategoryBreakdown
import com.fuelexpenselog.domain.stats.Delta
import com.fuelexpenselog.domain.stats.MonthBucket
import com.fuelexpenselog.domain.stats.StatsCalculator
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.time.MonthKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.ZoneId

data class MonthDetailUiState(
    val snapshot: VehicleSnapshot? = null,
    val month: MonthKey,
    /** Null for a month with nothing logged: shown as such, not as zero. */
    val bucket: MonthBucket? = null,
    val delta: Delta? = null,
    val lastYear: MonthBucket? = null,
    val categories: List<CategoryBreakdown> = emptyList(),
    val isCurrentMonth: Boolean = false,
    val missing: Boolean = false,
)

/** One month, audited: its total, what it went on, and how it compares. */
class MonthDetailViewModel(
    handle: SavedStateHandle,
    repository: FuelLogRepository,
    prefs: AppPrefs,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {

    private val vehicleId: Long = checkNotNull(handle.get<Long>(Routes.ARG_ID))
    val month: MonthKey = MonthKey(checkNotNull(handle.get<Int>(Routes.ARG_MONTH)))

    val state: StateFlow<MonthDetailUiState> = repository.observeSnapshot(vehicleId, prefs).map { snapshot ->
        if (snapshot == null) return@map MonthDetailUiState(month = month, missing = true)
        val buckets = StatsCalculator.months(snapshot.history).associateBy { it.month }
        val bucket = buckets[month]
        MonthDetailUiState(
            snapshot = snapshot,
            month = month,
            bucket = bucket,
            delta = bucket?.let { StatsCalculator.moneyDelta(it.total, buckets[month.minusMonths(1)]?.total.orEmpty()) },
            lastYear = buckets[month.minusYears(1)],
            categories = StatsCalculator.categories(snapshot.history, month),
            isCurrentMonth = month >= CivilDate.today(clock, zone()).monthKey,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthDetailUiState(month = month))
}
