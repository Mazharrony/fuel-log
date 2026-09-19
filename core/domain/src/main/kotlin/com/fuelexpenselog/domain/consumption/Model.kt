package com.fuelexpenselog.domain.consumption

import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyKind
import com.fuelexpenselog.domain.unit.Plausibility

/**
 * One energy event: a tank of petrol, or a charge. The engine sees nothing else.
 *
 * @param odometerM null when the user did not record a reading. This is common and must not
 *   be treated as zero.
 * @param missedPrevious **a fill-up happened before this one that is not recorded.** The
 *   direction is stated here because it is the single easiest thing to get backwards, and
 *   getting it backwards makes manual entry and CSV import disagree silently. The UI copy
 *   must read "I missed a fill-up BEFORE this one".
 */
data class FuelEvent(
    val id: Long,
    val date: CivilDate,
    val instantMillis: Long,
    val odometerM: Long?,
    val energy: Energy,
    val isFull: Boolean,
    val missedPrevious: Boolean = false,
    val cost: Money? = null,
)

/**
 * A break the user has explicitly confirmed: they replaced the instrument cluster, bought
 * the vehicle second-hand, or the odometer rolled over.
 *
 * The engine only ever *proposes* these. Nothing is written without confirmation, because an
 * automatic segment silently discards the consumption figures that cross it.
 *
 * @param offsetM non-null means "bridge the gap by this much" - the old total is known, so
 *   the chain can continue. Null is a hard break.
 */
data class DeclaredSegment(
    val startsAt: CivilDate,
    val reason: SegmentReason,
    val offsetM: Long? = null,
)

enum class SegmentReason { UNIT_REPLACED, ROLLOVER, PURCHASED_USED, MANUAL_CORRECTION }

enum class Confidence {
    /** The span runs between two consecutive recorded full tanks. */
    EXACT,

    /**
     * An intermediate anchor was skipped because its reading could not be trusted, so this
     * point covers more than one fill-up. Arithmetically still correct - every drop of
     * energy between the surviving anchors is counted - but less granular, and the UI should
     * say "covers N fill-ups".
     */
    AVERAGED,
}

enum class GapReason {
    /** The first full tank is a starting line, not a result. "Needs two full tanks." */
    FIRST_FILL_UP,

    /** A fill-up in this span was never recorded, so its fuel is missing from the sum. */
    MISSED_FILL_UP,

    /** Only partial fills since the last full tank. Their energy carries to the next full one. */
    PARTIAL_ONLY,

    ODOMETER_RESET,

    /**
     * No full tank in this stretch carried an odometer reading, so nothing can anchor a
     * measurement.
     *
     * Note this is NOT emitted for a single full tank with a missing reading. Such a tank
     * simply cannot be an anchor; the span either side of it stays valid, because
     * full-to-full only requires that the tank is full at both ends and that every drop of
     * energy in between is counted - and it is. Breaking the chain there would discard a
     * correct measurement.
     */
    MISSING_ODOMETER,

    /** Two readings the same, or going backwards, inside one segment. */
    ZERO_OR_NEGATIVE_DISTANCE,

    /** A full tank recorded with no energy at all, so there is nothing to divide by. */
    NO_ENERGY_RECORDED,
}

/**
 * One slot on the consumption chart.
 *
 * The engine returns these **densely, gaps included**, never a filtered list of values. That
 * is what makes "a dash, never a zero and never an interpolation" a property of the type
 * rather than a convention someone has to remember: a gap occupies a slot, so the UI cannot
 * accidentally elide one and quietly draw a smooth line through a hole in the data.
 */
sealed interface TimelinePoint {
    val index: Int
    val endDate: CivilDate
}

data class Measured(
    override val index: Int,
    override val endDate: CivilDate,
    val startEventId: Long,
    val endEventId: Long,
    val distanceM: Long,
    val energy: Energy,
    /** Null when the span mixes currencies. The consumption figure survives that; the cost does not. */
    val cost: Money?,
    val fillUpsSpanned: Int,
    val confidence: Confidence,
    /**
     * Energy of the *other* kind recorded inside this span - always zero for an ordinary
     * car. Non-zero on a plug-in hybrid, where some of this distance was covered by the
     * other source, so the figure flatters whichever one you are looking at.
     */
    val otherKindMicro: Long,
) : TimelinePoint {

    val kind: EnergyKind get() = energy.kind

    val kmPerUnit: Double
        get() = ConsumptionFormat.kmPerUnit(distanceM, energy) ?: Double.NaN

    /**
     * False flags the figure, it does not hide it. Suppressing a number reads as a bug;
     * marking one reads as the app paying attention - and the user is the only one who knows
     * whether they really did tow a caravan over a mountain that week.
     */
    val isPlausible: Boolean
        get() = Plausibility.isPlausible(kmPerUnit, kind)

    fun shownAs(format: ConsumptionFormat): Double? = format.of(distanceM, energy)

    /** Cost per canonical unit of distance, or null when the cost is not known. */
    fun costPerKm(): Money? {
        if (cost == null || distanceM <= 0L) return null
        return cost * (1000.0 / distanceM)
    }
}

data class Gap(
    override val index: Int,
    override val endDate: CivilDate,
    val reason: GapReason,
    val eventIds: List<Long>,
) : TimelinePoint

enum class OdometerAnomaly {
    /** A single reading that dipped and then recovered. A correction may be offered. */
    SUSPECTED_TYPO,

    /** The reading went past its maximum and wrapped. The offset bridges it. */
    ROLLOVER,

    /** The instrument was replaced, or the vehicle was bought used. A hard break. */
    RESET,
}

/**
 * Something the engine noticed and wants the user to confirm. **Never applied automatically.**
 *
 * An automatically "corrected" odometer is a number the user never typed silently changing
 * every figure downstream of it.
 */
data class OdometerProposal(
    val eventId: Long,
    val date: CivilDate,
    val anomaly: OdometerAnomaly,
    val recordedM: Long,
    /** A single unambiguous correction, when one exists. Null means "we cannot tell, you look". */
    val correctedM: Long? = null,
    /** For a rollover: how much to add to everything after this point. */
    val offsetM: Long? = null,
)

data class ConsumptionResult(
    /** Dense, ordered oldest to newest, one slot per span attempt. */
    val timeline: List<TimelinePoint>,
    val summary: ConsumptionSummary?,
    val proposals: List<OdometerProposal>,
) {
    val measured: List<Measured> get() = timeline.filterIsInstance<Measured>()

    companion object {
        val Empty = ConsumptionResult(emptyList(), null, emptyList())
    }
}
