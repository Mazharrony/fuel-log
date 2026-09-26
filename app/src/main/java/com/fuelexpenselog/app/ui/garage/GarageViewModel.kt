package com.fuelexpenselog.app.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.vehicle.VehicleSnapshot
import com.fuelexpenselog.app.ui.vehicle.observeSnapshot
import com.fuelexpenselog.domain.consumption.TimelinePoint
import com.fuelexpenselog.domain.format.perDistanceUnit
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.money.CurrencySubtotals
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.reminder.ReminderEvaluator
import com.fuelexpenselog.domain.reminder.ReminderStatus
import com.fuelexpenselog.domain.stats.StatsCalculator
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.ZoneId

data class GarageVehicle(
    val vehicle: Vehicle,
    /** From fill-ups and expenses both. Null until something records a reading. */
    val odometerM: Long?,
    val format: ConsumptionFormat = ConsumptionFormat.L_PER_100KM,
    /** The newest slot: the last tank's figure, or why there is none. */
    val latest: TimelinePoint? = null,
    /** The sparkline: the last nine slots, gaps included. */
    val recent: List<TimelinePoint> = emptyList(),
    val thisMonth: List<Money> = emptyList(),
    val lastMonth: List<Money> = emptyList(),
    val costPerDistance: Money? = null,
    /** Reminders due soon on this vehicle, and overdue: the badge. */
    val dueCount: Int = 0,
    val overdueCount: Int = 0,
)

sealed interface GarageUiState {
    /** Before the first database read. Renders nothing, so the splash hands straight to data. */
    data object Loading : GarageUiState

    data class Ready(
        val active: List<GarageVehicle>,
        val archived: List<GarageVehicle>,
        val showArchived: Boolean,
        /** No vehicle at all and onboarding never finished: the first-run flow should show. */
        val needsOnboarding: Boolean,
        val year: Int = 0,
        /** Every vehicle's spending this year, one subtotal per currency. */
        val yearTotal: List<Money> = emptyList(),
        val thisMonthByCurrency: List<Money> = emptyList(),
        /** More than one currency in play: subtotals side by side, never summed. */
        val mixedCurrency: Boolean = false,
    ) : GarageUiState
}

class GarageViewModel(
    private val repository: FuelLogRepository,
    private val prefs: AppPrefs,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {

    private val showArchived = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val vehicles = repository.observeVehicles().flatMapLatest { list ->
        // combine() over zero flows never emits, so an empty garage needs its own branch or
        // the screen would sit on Loading forever for a brand-new user.
        if (list.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(list.map { v -> repository.observeSnapshot(v.id, prefs).map { it?.let(::toGarageVehicle) } }) {
                it.filterNotNull()
            }
        }
    }

    private fun toGarageVehicle(s: VehicleSnapshot): GarageVehicle {
        val month = CivilDate.today(clock, zone()).monthKey
        val buckets = StatsCalculator.months(s.history).associateBy { it.month }
        return GarageVehicle(
            vehicle = s.vehicle,
            odometerM = s.odometerM,
            format = s.format,
            latest = s.consumption.timeline.lastOrNull(),
            recent = s.consumption.timeline.takeLast(Dimens.CHART_BARS),
            thisMonth = buckets[month]?.total.orEmpty(),
            lastMonth = buckets[month.minusMonths(1)]?.total.orEmpty(),
            costPerDistance = s.consumption.summary?.costPerKm()?.perDistanceUnit(s.vehicle.distanceUnit),
        )
    }

    val state: StateFlow<GarageUiState> = combine(
        vehicles,
        repository.observeAllHistory(),
        showArchived,
        prefs.observeOnboardingDone(),
        repository.observeAllActiveReminders(),
    ) { vehicles, history, show, onboarded, reminders ->
        val today = CivilDate.today(clock, zone())
        val statuses = ReminderEvaluator.evaluate(reminders, vehicles.associate { it.vehicle.id to it.odometerM }, today)
            .groupBy({ it.first.vehicleId }, { it.second })
        val all = vehicles.map { item ->
            val mine = statuses[item.vehicle.id].orEmpty()
            item.copy(
                dueCount = mine.count { it is ReminderStatus.DueSoon },
                overdueCount = mine.count { it is ReminderStatus.Overdue },
            )
        }
        // observeVehicles() includes archived rows; the garage is for the ones still driven.
        val (archived, active) = all.partition { it.vehicle.isArchived }
        val yearTotal = CurrencySubtotals.of(history.filter { it.second.date.year == today.year }.mapNotNull { it.second.amount })
        val thisMonth = CurrencySubtotals.of(history.filter { it.second.date.monthKey == today.monthKey }.mapNotNull { it.second.amount })
        GarageUiState.Ready(
            active = active,
            archived = archived,
            showArchived = show && archived.isNotEmpty(),
            needsOnboarding = all.isEmpty() && !onboarded,
            year = today.year,
            yearTotal = yearTotal,
            thisMonthByCurrency = thisMonth,
            mixedCurrency = active.map { it.vehicle.currencyCode }.distinct().size > 1 || yearTotal.size > 1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarageUiState.Loading)

    fun toggleArchived() = showArchived.update { !it }
}
