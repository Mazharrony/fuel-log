package com.fuelexpenselog.app.ui.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.ui.history.HistoryRow
import com.fuelexpenselog.app.ui.history.historyRows
import com.fuelexpenselog.app.ui.nav.Routes
import com.fuelexpenselog.domain.consumption.OdometerAnomaly
import com.fuelexpenselog.domain.consumption.OdometerProposal
import com.fuelexpenselog.domain.consumption.SegmentReason
import com.fuelexpenselog.domain.consumption.TimelinePoint
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.HistoryEntry
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.reminder.Reminder
import com.fuelexpenselog.domain.reminder.ReminderEvaluator
import com.fuelexpenselog.domain.reminder.ReminderStatus
import com.fuelexpenselog.domain.stats.Delta
import com.fuelexpenselog.domain.stats.LastDone
import com.fuelexpenselog.domain.stats.StatsCalculator
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.time.MonthKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

data class VehicleUiState(
    val loading: Boolean = true,
    /** The vehicle is gone: deleted elsewhere, or replaced by a restore. */
    val missing: Boolean = false,
    val snapshot: VehicleSnapshot? = null,
    /** The newest timeline slot: the last tank's figure, or why there is none. */
    val latest: TimelinePoint? = null,
    val month: MonthKey? = null,
    val previousMonth: MonthKey? = null,
    val monthTotal: List<Money> = emptyList(),
    val monthDelta: Delta? = null,
    /** The newest few rows; the rest are one tap away. */
    val rows: List<HistoryRow> = emptyList(),
    val entryCount: Int = 0,
    val lastDone: List<LastDone> = emptyList(),
    val proposals: List<OdometerProposal> = emptyList(),
    /** Rollover proposals whose wrap two same-day fill-ups straddle - see ProposalActions. */
    val sameDayConflicts: Set<Long> = emptySet(),
    /** Active vehicles, for the switcher. */
    val vehicles: List<Vehicle> = emptyList(),
    /** Active reminders against the current reading, most urgent first. */
    val reminders: List<Pair<Reminder, ReminderStatus>> = emptyList(),
) {
    /** The rows at the top of the screen: what is due soon or overdue. */
    val due: List<Pair<Reminder, ReminderStatus>> get() = reminders.filter { it.second.isDue }
}

/** One vehicle's figures, history, maintenance and pending odometer questions. */
class VehicleViewModel(
    handle: SavedStateHandle,
    private val repository: FuelLogRepository,
    private val prefs: AppPrefs,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) : ViewModel() {

    val vehicleId: Long = checkNotNull(handle.get<Long>(Routes.ARG_ID))
    private val actions = ProposalActions(repository, prefs)
    private val sameDayConflicts = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<VehicleUiState> = combine(
        repository.observeSnapshot(vehicleId, prefs),
        prefs.observeDismissedProposals(),
        repository.observeVehicles().map { list -> list.filter { !it.isArchived } },
        sameDayConflicts,
        repository.observeReminders(vehicleId),
    ) { snapshot, dismissed, vehicles, conflicts, reminders ->
        if (snapshot == null) return@combine VehicleUiState(loading = false, missing = true)
        val today = CivilDate.today(clock, zone())
        val month = today.monthKey
        val previous = month.minusMonths(1)
        val buckets = StatsCalculator.months(snapshot.history).associateBy { it.month }
        val thisMonth = buckets[month]?.total.orEmpty()
        val proposals = actions.visible(vehicleId, snapshot.consumption.proposals, dismissed)

        VehicleUiState(
            loading = false,
            snapshot = snapshot,
            latest = snapshot.consumption.timeline.lastOrNull(),
            month = month,
            previousMonth = previous,
            monthTotal = thisMonth,
            monthDelta = StatsCalculator.moneyDelta(thisMonth, buckets[previous]?.total.orEmpty()),
            rows = historyRows(snapshot.history.take(HISTORY_PREVIEW), snapshot.consumption),
            entryCount = snapshot.history.size,
            lastDone = MAINTENANCE.mapNotNull { StatsCalculator.lastDone(snapshot.history, it, snapshot.odometerM) },
            proposals = proposals,
            sameDayConflicts = conflicts + sameDayRollovers(snapshot.history, proposals),
            vehicles = vehicles,
            reminders = ReminderEvaluator.evaluate(reminders, mapOf(vehicleId to snapshot.odometerM), today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleUiState())

    /** Rollovers two same-day fill-ups straddle cannot be bridged by a dated offset. */
    private fun sameDayRollovers(history: List<HistoryEntry>, proposals: List<OdometerProposal>): Set<Long> {
        val fillUps = history.filterIsInstance<HistoryEntry.Fuel>().map { it.fillUp }
        return proposals
            .filter { p ->
                p.anomaly == OdometerAnomaly.ROLLOVER && fillUps.any {
                    it.id != p.eventId && it.date == p.date && (it.odometerM ?: Long.MIN_VALUE) > p.recordedM
                }
            }
            .map { it.eventId }
            .toSet()
    }

    fun correct(proposal: OdometerProposal) {
        viewModelScope.launch { actions.correctTypo(proposal) }
    }

    fun bridge(proposal: OdometerProposal) {
        viewModelScope.launch {
            if (actions.bridgeRollover(vehicleId, proposal) == ProposalActions.Outcome.SAME_DAY_CONFLICT) {
                sameDayConflicts.value = sameDayConflicts.value + proposal.eventId
            }
        }
    }

    fun reset(proposal: OdometerProposal, reason: SegmentReason) {
        viewModelScope.launch { actions.declareReset(vehicleId, proposal, reason) }
    }

    fun dismiss(proposal: OdometerProposal) = actions.dismiss(vehicleId, proposal)

    companion object {
        const val HISTORY_PREVIEW = 5

        /** The "last done at" lines: the maintenance a reminder would be about. */
        val MAINTENANCE = listOf(ExpenseCategory.OIL_CHANGE, ExpenseCategory.SERVICE, ExpenseCategory.TYRES)
    }
}
