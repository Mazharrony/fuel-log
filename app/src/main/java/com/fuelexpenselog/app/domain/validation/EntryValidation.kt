package com.fuelexpenselog.app.domain.validation

/**
 * The governing principle is WARN, NEVER BLOCK.
 *
 * Every hard validation rule in a competitor generates one-star reviews from
 * people whose situation is genuinely unusual: a replaced odometer, a jerry can,
 * a twin-tank truck, a vehicle bought second-hand mid-life. Warn them, let them
 * save, let them fix it later.
 *
 * Exactly one condition blocks a save, and it is the only one that is really an
 * error rather than a surprise: a required field is empty or unparseable.
 */
enum class EntryWarning {
    ODOMETER_LOWER_THAN_PREVIOUS,
    VOLUME_OVER_TANK_CAPACITY,
    FUTURE_DATE,
    LOOKS_LIKE_DUPLICATE,

    /**
     * Shown against the figure, never instead of it. A hidden number reads as a
     * bug; a flagged number reads as the app paying attention.
     */
    IMPLAUSIBLE_CONSUMPTION,
}

enum class EntryBlock { REQUIRED_FIELD_MISSING }

data class ValidationResult(
    val blocks: Set<EntryBlock> = emptySet(),
    val warnings: List<EntryWarning> = emptyList(),
) {
    val canSave: Boolean get() = blocks.isEmpty()
}

data class FillUpDraft(
    val odometerKm: Double?,
    val volumeLitres: Double?,
    val totalCost: Double?,
    val dateMillis: Long,
)

data class ExpenseDraft(
    val odometerKm: Double?,
    val totalCost: Double?,
    val dateMillis: Long,
)

fun validateFillUp(
    draft: FillUpDraft,
    previousOdometerKm: Double?,
    tankCapacityLitres: Double?,
    nowMillis: Long,
    isDuplicate: Boolean = false,
): ValidationResult {
    val blocks = mutableSetOf<EntryBlock>()
    val warnings = mutableListOf<EntryWarning>()

    // Cost of zero is allowed silently: warranty work and free top-ups exist.
    if (draft.odometerKm == null || draft.volumeLitres == null || draft.totalCost == null) {
        blocks += EntryBlock.REQUIRED_FIELD_MISSING
    }

    if (draft.odometerKm != null && previousOdometerKm != null &&
        draft.odometerKm < previousOdometerKm
    ) {
        warnings += EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS
    }
    if (draft.volumeLitres != null && tankCapacityLitres != null &&
        draft.volumeLitres > tankCapacityLitres
    ) {
        warnings += EntryWarning.VOLUME_OVER_TANK_CAPACITY
    }
    if (draft.dateMillis > nowMillis) warnings += EntryWarning.FUTURE_DATE
    if (isDuplicate) warnings += EntryWarning.LOOKS_LIKE_DUPLICATE

    return ValidationResult(blocks, warnings)
}

fun validateExpense(
    draft: ExpenseDraft,
    previousOdometerKm: Double?,
    nowMillis: Long,
): ValidationResult {
    val blocks = mutableSetOf<EntryBlock>()
    val warnings = mutableListOf<EntryWarning>()

    // Odometer is optional here - a service has a reading, insurance does not.
    if (draft.totalCost == null) blocks += EntryBlock.REQUIRED_FIELD_MISSING

    if (draft.odometerKm != null && previousOdometerKm != null &&
        draft.odometerKm < previousOdometerKm
    ) {
        warnings += EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS
    }
    if (draft.dateMillis > nowMillis) warnings += EntryWarning.FUTURE_DATE

    return ValidationResult(blocks, warnings)
}
