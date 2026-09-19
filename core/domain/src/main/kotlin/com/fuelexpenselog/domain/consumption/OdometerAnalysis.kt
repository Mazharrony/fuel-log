package com.fuelexpenselog.domain.consumption

import com.fuelexpenselog.domain.unit.DistanceUnit
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Finds the places where an odometer series stops being a monotonically increasing record of
 * one vehicle's life.
 *
 * This layer exists because the naive engine is fooled by a reset in a way its own tests do
 * not catch. Readings of 100, 200, 300 followed by 0, 50 after a replaced cluster sort into
 * 0, 50, 100, 200, 300, and the resulting span charges 50 km of distance with fuel that
 * actually covered 100. The figure comes out roughly twice wrong and entirely plausible
 * looking - which is the worst failure mode this app has.
 */
internal object OdometerAnalysis {

    data class Result(
        /** Excluded as anchors, but their energy still counts. The chain does NOT split here. */
        val suspectIds: Set<Long>,
        /** A new segment starts at this event. The chain cannot cross it. */
        val breakBeforeIds: Set<Long>,
        val proposals: List<OdometerProposal>,
    )

    fun analyse(ordered: List<Walked>, displayUnit: DistanceUnit): Result {
        val withOdo = ordered.filter { it.odometerM != null }
        if (withOdo.size < 2) return Result(emptySet(), emptySet(), emptyList())

        val suspects = mutableSetOf<Long>()
        val breaks = mutableSetOf<Long>()
        val proposals = mutableListOf<OdometerProposal>()

        var highWater = Long.MIN_VALUE

        for ((i, w) in withOdo.withIndex()) {
            val odo = w.odometerM!!

            if (highWater == Long.MIN_VALUE || odo >= highWater) {
                highWater = maxOf(highWater, odo)
                continue
            }

            // The reading went backwards. The question is whether the odometer went
            // backwards or the typing did.
            val next = withOdo.getOrNull(i + 1)?.odometerM

            if (next != null && next >= highWater) {
                // It dipped and then recovered above where it already was. An odometer that
                // was genuinely reset does not climb back to its old total, so this single
                // reading is wrong rather than the history.
                //
                // Crucially the chain is NOT split here. Splitting at the dip would pair the
                // bad reading with the recovery and invent an enormous span. Excluding it as
                // an anchor is lossless: full-to-full only needs the tank full at both ends
                // and every drop of energy in between counted, and both still hold.
                suspects += w.event.id
                proposals += OdometerProposal(
                    eventId = w.event.id,
                    date = w.event.date,
                    anomaly = OdometerAnomaly.SUSPECTED_TYPO,
                    recordedM = odo,
                    correctedM = singleCorrection(odo, highWater, next, displayUnit),
                )
                // highWater deliberately not lowered - the bad reading does not rewrite it.
            } else {
                // Readings continue from the new low base. The odometer really did change.
                breaks += w.event.id

                val offset = rolloverOffset(highWater, odo, displayUnit)
                proposals += OdometerProposal(
                    eventId = w.event.id,
                    date = w.event.date,
                    anomaly = if (offset != null) OdometerAnomaly.ROLLOVER else OdometerAnomaly.RESET,
                    recordedM = odo,
                    offsetM = offset,
                )
                highWater = odo
            }
        }

        return Result(suspects, breaks, proposals)
    }

    /**
     * The single correction that fits, or null when the engine cannot tell.
     *
     * Ambiguity produces null on purpose: two plausible corrections means the user looks at
     * it, not that the app picks one. Nothing here is ever applied automatically.
     */
    private fun singleCorrection(
        recordedM: Long,
        previousM: Long,
        nextM: Long,
        unit: DistanceUnit,
    ): Long? {
        val fits = candidates(recordedM, unit)
            .filter { it > previousM && it < nextM }
            .distinct()

        return fits.singleOrNull()
    }

    /**
     * Candidate corrections, generated on the number as the user SEES it.
     *
     * Digit-level slips are a property of the typed decimal string, so they have to be
     * generated in the display unit. A transposition in miles is not a transposition once
     * the value has been multiplied by 1609.344.
     */
    private fun candidates(recordedM: Long, unit: DistanceUnit): List<Long> {
        val whole = unit.fromMetres(recordedM).roundToLong()
        if (whole <= 0) return emptyList()

        val digits = whole.toString()
        if (digits.length > 12) return emptyList()

        val out = LinkedHashSet<Long>()

        out += whole * 10                                   // dropped the last digit
        if (whole >= 10) out += whole / 10                  // typed one digit too many

        for (d in '1'..'9') {                               // dropped the first digit
            ("$d$digits").toLongOrNull()?.let { out += it }
        }

        for (i in 0 until digits.length - 1) {              // transposed a pair
            val c = digits.toCharArray()
            val t = c[i]; c[i] = c[i + 1]; c[i + 1] = t
            String(c).toLongOrNull()?.let { out += it }
        }

        for (i in digits.indices) {                         // hit the wrong key
            for (d in '0'..'9') {
                if (digits[i] == d) continue
                val c = digits.toCharArray()
                c[i] = d
                String(c).toLongOrNull()?.let { out += it }
            }
        }

        return out.filter { it > 0 }.map { unit.toMetres(it.toDouble()) }
    }

    /**
     * A five- or six-digit odometer that has wrapped past its maximum. The offset is what to
     * add to everything after the wrap so the chain can continue rather than break.
     */
    private fun rolloverOffset(previousM: Long, currentM: Long, unit: DistanceUnit): Long? {
        val previous = unit.fromMetres(previousM)
        val current = unit.fromMetres(currentM)

        for (width in listOf(6, 5)) {
            val ceiling = 10.0.pow(width)
            val nearCeiling = previous >= ceiling * 0.95 && previous < ceiling
            val wrappedLow = current < ceiling * 0.10

            if (nearCeiling && wrappedLow) return unit.toMetres(ceiling)
        }
        return null
    }
}

/** An event paired with its odometer after any declared offset has been applied. */
internal data class Walked(
    val event: FuelEvent,
    val odometerM: Long?,
)
