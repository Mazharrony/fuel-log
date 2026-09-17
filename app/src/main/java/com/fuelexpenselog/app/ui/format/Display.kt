package com.fuelexpenselog.app.ui.format

import com.fuelexpenselog.app.domain.format.Rounding
import com.fuelexpenselog.app.domain.model.DistanceUnit
import com.fuelexpenselog.app.domain.model.ExpenseCategory
import com.fuelexpenselog.app.domain.model.VolumeUnit
import com.fuelexpenselog.app.domain.units.ConsumptionConvention
import com.fuelexpenselog.app.domain.units.Units
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** An em dash, used wherever a figure genuinely does not exist. */
const val EM_DASH = "—"

/** A middle dot, the separator used throughout the design. */
const val DOT = "·"

/** A true minus sign, not a hyphen. The design uses U+2212 in deltas. */
const val MINUS = "−"

/**
 * Display labels are separate from enum names on purpose. The name is what gets
 * stored and what CSV round-trips; the label is what the market reads. The
 * design targets the US and UK, so TYRES reads as "Tires" without the stored
 * value ever changing.
 */
fun ExpenseCategory.label(): String = when (this) {
    ExpenseCategory.OIL_CHANGE -> "Oil change"
    ExpenseCategory.SERVICE -> "Service"
    ExpenseCategory.REPAIR -> "Repair"
    ExpenseCategory.TYRES -> "Tires"
    ExpenseCategory.PARTS -> "Parts"
    ExpenseCategory.TOLL -> "Toll"
    ExpenseCategory.PARKING -> "Parking"
    ExpenseCategory.INSURANCE -> "Insurance"
    ExpenseCategory.TAX -> "Tax"
    ExpenseCategory.FINE -> "Fine"
    ExpenseCategory.WASH -> "Wash"
    ExpenseCategory.OTHER -> "Other"
}

fun DistanceUnit.shortLabel(): String = when (this) {
    DistanceUnit.KILOMETRE -> "km"
    DistanceUnit.MILE -> "mi"
}

/** The volume field label changes with the unit, as the design specifies. */
fun VolumeUnit.fieldLabel(): String = when (this) {
    VolumeUnit.LITRE -> "Litres"
    VolumeUnit.US_GALLON -> "Gallons"
    VolumeUnit.UK_GALLON -> "Gallons (UK)"
}

fun VolumeUnit.shortLabel(): String = when (this) {
    VolumeUnit.LITRE -> "L"
    VolumeUnit.US_GALLON -> "gal"
    VolumeUnit.UK_GALLON -> "gal"
}

/** "mpg" for both MPG conventions - the design lowercases and drops the region. */
fun ConsumptionConvention.unitLabel(): String = when (this) {
    ConsumptionConvention.MPG_US, ConsumptionConvention.MPG_UK -> "mpg"
    ConsumptionConvention.L_PER_100KM -> "L/100km"
    ConsumptionConvention.KM_PER_L -> "km/L"
}

fun ConsumptionConvention.settingLabel(): String = when (this) {
    ConsumptionConvention.MPG_US -> "MPG US"
    ConsumptionConvention.MPG_UK -> "MPG UK"
    ConsumptionConvention.L_PER_100KM -> "L/100km"
    ConsumptionConvention.KM_PER_L -> "km/L"
}

/**
 * Money, in the vehicle's own currency.
 *
 * Fraction digits come from the currency rather than a hard-coded 2, since JPY
 * and KWD disagree with the dollar. Rounding happens once, here, at display
 * time - never on intermediate sums.
 */
fun formatMoney(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
    if (!amount.isFinite()) return EM_DASH
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
    val digits = currency?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
    return NumberFormat.getCurrencyInstance(locale).apply {
        if (currency != null) this.currency = currency
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(amount)
}

/** A bare number with grouping, no unit and no symbol. */
fun formatNumber(value: Double, decimals: Int, locale: Locale = Locale.getDefault()): String {
    if (!value.isFinite()) return EM_DASH
    return NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(Rounding.to(value, decimals))
}

fun formatDistance(km: Double, unit: DistanceUnit, locale: Locale = Locale.getDefault()): String =
    formatNumber(Units.kmToDisplay(km, unit), 0, locale)

fun formatVolume(litres: Double, unit: VolumeUnit, locale: Locale = Locale.getDefault()): String =
    formatNumber(Units.litresToDisplay(litres, unit), 1, locale)

/** Consumption, or an em dash when there is genuinely no figure. */
fun formatConsumption(
    kmPerLitre: Double?,
    convention: ConsumptionConvention,
    locale: Locale = Locale.getDefault(),
): String {
    if (kmPerLitre == null || !kmPerLitre.isFinite() || kmPerLitre <= 0.0) return EM_DASH
    return formatNumber(convention.fromKmPerLitre(kmPerLitre), 1, locale)
}

/** "+4%" / "-9%", using a true minus sign. Null renders blank, never "0%". */
fun formatDelta(fraction: Double?): String {
    if (fraction == null || !fraction.isFinite()) return ""
    val percent = (fraction * 100).toInt()
    return if (percent >= 0) "+$percent%" else "$MINUS${-percent}%"
}

private val dayMonth = DateTimeFormatter.ofPattern("MMM d")

fun formatShortDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    dayMonth.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
