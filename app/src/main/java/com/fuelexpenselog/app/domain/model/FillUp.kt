package com.fuelexpenselog.app.domain.model

/**
 * A fill-up. [odometer] is always kilometres and [volume] always litres:
 * canonical units are stored, and conversion happens only at the display layer.
 *
 * [isFullTank] is what makes consumption computable at all - see computeSpans.
 * [isMissedEntry] is the user saying "I skipped logging one before this", which
 * breaks the chain and invalidates the span it falls in.
 */
data class FillUp(
    val id: Long = 0,
    val vehicleId: Long,
    /** Epoch millis. */
    val date: Long,
    /** Kilometres. */
    val odometer: Double,
    /** Litres. */
    val volume: Double,
    val totalCost: Double,
    val isFullTank: Boolean,
    val isMissedEntry: Boolean = false,
    val fuelType: String? = null,
    val note: String? = null,
)
