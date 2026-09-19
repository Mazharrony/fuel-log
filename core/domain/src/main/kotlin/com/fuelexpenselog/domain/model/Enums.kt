package com.fuelexpenselog.domain.model

/**
 * Every enum in this app is stored **by name, never by ordinal**. Reordering a declaration
 * must not silently rewrite somebody's history.
 */

enum class EntryTag {
    /** Deductible. The reason this app is useful to a sole trader at tax time. */
    BUSINESS,
    PERSONAL,
}

enum class VehicleType { CAR, BIKE, VAN, OTHER }

enum class FuelType {
    PETROL, DIESEL, LPG, CNG, HYBRID, PLUGIN_HYBRID, ELECTRIC, OTHER
}

/**
 * Fixed at twelve. Not user-extensible, deliberately: a closed set keeps CSV exports stable
 * across versions and keeps tax categorisation meaningful.
 *
 * The escape hatch is the free-text `vendor` field on an expense, so "Congestion Charge" or
 * "Dartford Crossing" can be recorded under [TOLL] without the enum ever having to grow.
 * Tolls and parking are the highest-frequency categories for delivery riders, which is
 * exactly who needs the records.
 */
enum class ExpenseCategory(val isMaintenance: Boolean) {
    OIL_CHANGE(true),
    SERVICE(true),
    REPAIR(true),
    TYRES(true),
    PARTS(true),
    TOLL(false),
    PARKING(false),
    INSURANCE(false),
    TAX(false),
    FINE(false),
    WASH(false),
    OTHER(false),
    ;

    companion object {
        /** UI order, by how often people actually log them - not declaration order. */
        val chipOrder: List<ExpenseCategory> = listOf(
            OIL_CHANGE, SERVICE, REPAIR, TYRES, TOLL, PARKING,
            INSURANCE, TAX, FINE, WASH, PARTS, OTHER,
        )
    }
}

/**
 * The result of reading a stored enum value.
 *
 * Two decode policies exist because the cost of guessing wrong is not the same everywhere.
 */
sealed interface Decoded<out T> {
    @JvmInline
    value class Known<T>(val value: T) : Decoded<T>

    /**
     * A value this build does not recognise, kept verbatim.
     *
     * A row holding one of these must be treated as **read-only**. The scenario is a backup
     * written by a newer version and restored onto an older build: if an unrecognised
     * category silently became OTHER and the user then edited the note and saved, the real
     * category would be permanently destroyed by an app that was only trying to be helpful.
     */
    @JvmInline
    value class Unknown(val raw: String) : Decoded<Nothing>
}

/**
 * Forgiving decode, for values where a sensible catch-all exists and losing the distinction
 * is survivable. A category that does not resolve becomes OTHER so the row still opens and
 * its cost still counts toward the month.
 */
inline fun <reified T : Enum<T>> decodeOr(raw: String?, fallback: T): T =
    raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

/**
 * Strict decode, for values where there is no honest fallback.
 *
 * A unit or a currency has no catch-all: guessing litres for an unrecognised volume unit
 * would silently change what every figure computed from that row means. These surface as
 * [Decoded.Unknown] and the repository refuses to write the row back.
 */
inline fun <reified T : Enum<T>> decodeStrict(raw: String?): Decoded<T> {
    if (raw == null) return Decoded.Unknown("")
    val value = runCatching { enumValueOf<T>(raw) }.getOrNull()
    return if (value != null) Decoded.Known(value) else Decoded.Unknown(raw)
}
