package com.fuelexpenselog.domain.validate

import com.fuelexpenselog.domain.parse.DecimalParser
import com.fuelexpenselog.domain.parse.NumberKind
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.fuelexpenselog.domain.unit.Plausibility

/**
 * **Warn, never block.**
 *
 * Every hard validation rule in a competitor app generates one-star reviews from people
 * whose situation is genuinely unusual: a replaced odometer, a jerry can, a twin-tank truck,
 * a vehicle bought second-hand mid-life, a fill-up paid for by someone else. The user is
 * standing at a pump and knows more about their own vehicle than this app does. Warn them,
 * let them save, let them fix it later.
 *
 * There is exactly one blocking condition in the whole app, and it is an empty required
 * field - which is not really a rule, just the absence of an entry to save.
 */
enum class EntryBlock { REQUIRED_FIELD_MISSING }

enum class EntryWarning {
    /** No reading means this entry cannot produce a consumption figure. Still saveable. */
    ODOMETER_MISSING,

    /** The odometer was replaced, or the reading was mistyped. Both happen. */
    ODOMETER_LOWER_THAN_PREVIOUS,

    /** Jerry cans and twin tanks are real. */
    VOLUME_OVER_TANK_CAPACITY,

    VOLUME_IS_ZERO,

    FUTURE_DATE,

    LOOKS_LIKE_DUPLICATE,

    /** Shown AGAINST the figure, never instead of it. */
    IMPLAUSIBLE_CONSUMPTION,
}

data class ValidationResult(
    val blocks: List<EntryBlock> = emptyList(),
    val warnings: List<EntryWarning> = emptyList(),
) {
    val canSave: Boolean get() = blocks.isEmpty()
}

/** Exactly what the user has typed so far, before any of it is known to be valid. */
data class FillUpDraft(
    val odometerText: String,
    val volumeText: String,
    val totalText: String,
    val isFull: Boolean,
    val date: CivilDate,
    val volumeUnit: EnergyUnit,
    val distanceUnit: DistanceUnit,
)

data class ExpenseDraft(
    val amountText: String,
    val odometerText: String,
    val date: CivilDate,
    val distanceUnit: DistanceUnit,
)

/** What the app already knows, which is what turns a value into a warning. */
data class EntryContext(
    val today: CivilDate,
    val previousOdometerM: Long? = null,
    val tankCapacity: Energy? = null,
    val looksLikeDuplicate: Boolean = false,
)

object EntryValidation {

    fun validate(draft: FillUpDraft, context: EntryContext): ValidationResult {
        val blocks = mutableListOf<EntryBlock>()
        val warnings = mutableListOf<EntryWarning>()

        val volume = DecimalParser.parseOrNull(draft.volumeText, NumberKind.VOLUME)
        val total = DecimalParser.parseOrNull(draft.totalText, NumberKind.MONEY_TOTAL)

        // A fuel entry with no fuel and no amount is not an entry. Everything else is a
        // judgement call, and the user's judgement wins.
        if (volume == null || total == null) blocks += EntryBlock.REQUIRED_FIELD_MISSING

        if (volume != null && volume == 0.0) warnings += EntryWarning.VOLUME_IS_ZERO

        // A cost of zero is accepted in silence: warranty work, a company fuel card, and a
        // friend filling the tank are all ordinary.

        val odometerM = odometerMetres(draft.odometerText, draft.distanceUnit)
        if (odometerM == null) {
            warnings += EntryWarning.ODOMETER_MISSING
        } else {
            context.previousOdometerM?.let { previous ->
                if (odometerM < previous) warnings += EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS
            }
        }

        if (volume != null && context.tankCapacity != null) {
            val entered = Energy.of(draft.volumeUnit, volume)
            if (entered.micro > context.tankCapacity.micro) {
                warnings += EntryWarning.VOLUME_OVER_TANK_CAPACITY
            }
        }

        if (draft.date > context.today) warnings += EntryWarning.FUTURE_DATE
        if (context.looksLikeDuplicate) warnings += EntryWarning.LOOKS_LIKE_DUPLICATE

        // The figure this entry would produce, checked only when it is knowable at all.
        if (draft.isFull && volume != null && volume > 0.0 && odometerM != null) {
            context.previousOdometerM?.let { previous ->
                val distance = odometerM - previous
                if (distance > 0) {
                    val energy = Energy.of(draft.volumeUnit, volume)
                    val kmPerUnit = ConsumptionFormat.kmPerUnit(distance, energy)
                    if (kmPerUnit != null && !Plausibility.isPlausible(kmPerUnit, energy.kind)) {
                        warnings += EntryWarning.IMPLAUSIBLE_CONSUMPTION
                    }
                }
            }
        }

        return ValidationResult(blocks, warnings)
    }

    fun validate(draft: ExpenseDraft, context: EntryContext): ValidationResult {
        val blocks = mutableListOf<EntryBlock>()
        val warnings = mutableListOf<EntryWarning>()

        if (DecimalParser.parseOrNull(draft.amountText, NumberKind.MONEY_TOTAL) == null) {
            blocks += EntryBlock.REQUIRED_FIELD_MISSING
        }

        // An expense needs no reading: a service has one, a parking ticket does not. So a
        // missing odometer here is not even worth warning about - only a backwards one is.
        val odometerM = odometerMetres(draft.odometerText, draft.distanceUnit)
        if (odometerM != null) {
            context.previousOdometerM?.let { previous ->
                if (odometerM < previous) warnings += EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS
            }
        }

        if (draft.date > context.today) warnings += EntryWarning.FUTURE_DATE

        return ValidationResult(blocks, warnings)
    }

    fun odometerMetres(text: String, unit: DistanceUnit): Long? =
        DecimalParser.parseOrNull(text, NumberKind.ODOMETER)?.let { unit.toMetres(it) }
}
