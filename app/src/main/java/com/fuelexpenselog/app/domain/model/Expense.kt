package com.fuelexpenselog.app.domain.model

/**
 * A fixed enum rather than free text. Custom categories produce messy data,
 * inconsistent totals and a settings screen nobody wanted; "other" plus a note
 * covers the long tail until someone actually asks for more.
 *
 * [isMaintenance] drives the "last done at" lines on the vehicle screen, which
 * is service-reminder value without any of the notification fragility.
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
}

/**
 * Deliberately the same shape as a fill-up minus the fuel fields, which is what
 * lets the editor screen, the history list, the CSV format and the totals be
 * almost entirely shared code.
 *
 * [odometer] is optional and in kilometres: a service has a reading, an
 * insurance payment does not.
 */
data class Expense(
    val id: Long = 0,
    val vehicleId: Long,
    /** Epoch millis. */
    val date: Long,
    /** Kilometres, when known. */
    val odometer: Double? = null,
    val category: ExpenseCategory,
    val totalCost: Double,
    val note: String? = null,
)
