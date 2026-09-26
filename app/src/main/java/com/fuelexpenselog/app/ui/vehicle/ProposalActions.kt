package com.fuelexpenselog.app.ui.vehicle

import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.domain.consumption.DeclaredSegment
import com.fuelexpenselog.domain.consumption.OdometerProposal
import com.fuelexpenselog.domain.consumption.SegmentReason
import kotlinx.coroutines.flow.first

/**
 * What confirming an odometer proposal does. The engine only ever proposes; nothing is
 * written until the user taps, because an automatically "corrected" reading is a number
 * they never typed silently changing every figure after it.
 */
class ProposalActions(
    private val repository: FuelLogRepository,
    private val prefs: AppPrefs,
) {

    enum class Outcome { APPLIED, NOTHING_TO_DO, SAME_DAY_CONFLICT }

    /**
     * Keyed on the recorded reading, so editing the reading produces a new key and the
     * question comes back - the old answer was about a different number.
     */
    fun key(vehicleId: Long, proposal: OdometerProposal) =
        "v$vehicleId:e${proposal.eventId}:${proposal.anomaly}:${proposal.recordedM}"

    fun visible(vehicleId: Long, proposals: List<OdometerProposal>, dismissed: Set<String>) =
        proposals.filter { key(vehicleId, it) !in dismissed }

    fun dismiss(vehicleId: Long, proposal: OdometerProposal) {
        prefs.dismissedProposals = prefs.dismissedProposals + key(vehicleId, proposal)
    }

    /** Writes the single correction the engine found into the fill-up. */
    suspend fun correctTypo(proposal: OdometerProposal): Outcome {
        val corrected = proposal.correctedM ?: return Outcome.NOTHING_TO_DO
        val fillUp = repository.fillUp(proposal.eventId) ?: return Outcome.NOTHING_TO_DO
        repository.updateFillUp(fillUp.copy(odometerM = corrected))
        return Outcome.APPLIED
    }

    /**
     * Declares the wrap, dated on the anomalous fill-up so the offset lifts it and every
     * reading after it. An offset applies by date, and same-day readings sort by reading,
     * so a pre-wrap fill-up on the same day would be lifted too and the chain would jump by
     * the whole odometer width. That case is refused rather than bridged wrongly.
     */
    suspend fun bridgeRollover(vehicleId: Long, proposal: OdometerProposal): Outcome {
        val offset = proposal.offsetM ?: return Outcome.NOTHING_TO_DO
        val sameDayBeforeWrap = repository.observeFillUps(vehicleId).first().any {
            it.id != proposal.eventId && it.date == proposal.date && (it.odometerM ?: Long.MIN_VALUE) > proposal.recordedM
        }
        if (sameDayBeforeWrap) return Outcome.SAME_DAY_CONFLICT
        repository.addSegment(vehicleId, DeclaredSegment(proposal.date, SegmentReason.ROLLOVER, offset))
        return Outcome.APPLIED
    }

    /**
     * A hard break: the old readings and the new ones are never measured across. The drop is
     * still in the data, so the engine would keep asking; the answer is recorded as well.
     */
    suspend fun declareReset(vehicleId: Long, proposal: OdometerProposal, reason: SegmentReason): Outcome {
        repository.addSegment(vehicleId, DeclaredSegment(proposal.date, reason, offsetM = null))
        dismiss(vehicleId, proposal)
        return Outcome.APPLIED
    }
}
