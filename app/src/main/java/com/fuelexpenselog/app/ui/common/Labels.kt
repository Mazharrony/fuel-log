package com.fuelexpenselog.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.format.ConsumptionFormatter
import com.fuelexpenselog.app.format.VolumeFormatter
import com.fuelexpenselog.domain.consumption.GapReason
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.FuelType
import com.fuelexpenselog.domain.model.VehicleType
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import com.fuelexpenselog.domain.validate.EntryWarning

/** Every enum the UI names, mapped to a string resource in one place. */

@StringRes
fun VehicleType.labelRes(): Int = when (this) {
    VehicleType.CAR -> R.string.vehicle_type_car
    VehicleType.BIKE -> R.string.vehicle_type_bike
    VehicleType.VAN -> R.string.vehicle_type_van
    VehicleType.OTHER -> R.string.vehicle_type_other
}

@StringRes
fun FuelType.labelRes(): Int = when (this) {
    FuelType.PETROL -> R.string.fuel_type_petrol
    FuelType.DIESEL -> R.string.fuel_type_diesel
    FuelType.LPG -> R.string.fuel_type_lpg
    FuelType.CNG -> R.string.fuel_type_cng
    FuelType.HYBRID -> R.string.fuel_type_hybrid
    FuelType.PLUGIN_HYBRID -> R.string.fuel_type_plugin_hybrid
    FuelType.ELECTRIC -> R.string.fuel_type_electric
    FuelType.OTHER -> R.string.fuel_type_other
}

@StringRes
fun EntryTag.labelRes(): Int = when (this) {
    EntryTag.PERSONAL -> R.string.tag_personal
    EntryTag.BUSINESS -> R.string.tag_business
}

@StringRes
fun DistanceUnit.longLabelRes(): Int = when (this) {
    DistanceUnit.KILOMETRE -> R.string.unit_kilometres_long
    DistanceUnit.MILE -> R.string.unit_miles_long
}

@StringRes
fun ExpenseCategory.labelRes(): Int = when (this) {
    ExpenseCategory.OIL_CHANGE -> R.string.category_oil_change
    ExpenseCategory.SERVICE -> R.string.category_service
    ExpenseCategory.REPAIR -> R.string.category_repair
    ExpenseCategory.TYRES -> R.string.category_tyres
    ExpenseCategory.PARTS -> R.string.category_parts
    ExpenseCategory.TOLL -> R.string.category_toll
    ExpenseCategory.PARKING -> R.string.category_parking
    ExpenseCategory.INSURANCE -> R.string.category_insurance
    ExpenseCategory.TAX -> R.string.category_tax
    ExpenseCategory.FINE -> R.string.category_fine
    ExpenseCategory.WASH -> R.string.category_wash
    ExpenseCategory.OTHER -> R.string.category_other
}

@StringRes
fun GapReason.labelRes(): Int = when (this) {
    GapReason.FIRST_FILL_UP -> R.string.gap_first_fill_up
    GapReason.MISSED_FILL_UP -> R.string.gap_missed_fill_up
    GapReason.PARTIAL_ONLY -> R.string.gap_partial_only
    GapReason.ODOMETER_RESET -> R.string.gap_odometer_reset
    GapReason.MISSING_ODOMETER -> R.string.gap_missing_odometer
    GapReason.ZERO_OR_NEGATIVE_DISTANCE -> R.string.gap_zero_distance
    GapReason.NO_ENERGY_RECORDED -> R.string.gap_no_energy
}

/**
 * Every warning, in words. [previousReading] fills the one that names the reading it was
 * compared with.
 */
@Composable
fun EntryWarning.message(previousReading: String?): String = when (this) {
    EntryWarning.ODOMETER_MISSING -> stringResource(R.string.warning_odometer_missing)
    EntryWarning.ODOMETER_LOWER_THAN_PREVIOUS -> stringResource(R.string.warning_odometer_lower, previousReading.orEmpty())
    EntryWarning.VOLUME_OVER_TANK_CAPACITY -> stringResource(R.string.warning_volume_over_tank)
    EntryWarning.VOLUME_IS_ZERO -> stringResource(R.string.warning_volume_zero)
    EntryWarning.FUTURE_DATE -> stringResource(R.string.warning_future_date)
    EntryWarning.LOOKS_LIKE_DUPLICATE -> stringResource(R.string.warning_duplicate)
    EntryWarning.IMPLAUSIBLE_CONSUMPTION -> stringResource(R.string.warning_implausible)
}

@Composable
fun ExpenseCategory.label(): String = stringResource(labelRes())

@Composable
fun GapReason.label(): String = stringResource(labelRes())

@Composable
fun VehicleType.label(): String = stringResource(labelRes())

@Composable
fun FuelType.label(): String = stringResource(labelRes())

@Composable
fun EntryTag.label(): String = stringResource(labelRes())

@Composable
fun DistanceUnit.longLabel(): String = stringResource(longLabelRes())

@Composable
fun EnergyUnit.longLabel(): String = stringResource(VolumeFormatter.longLabelRes(this))

@Composable
fun ConsumptionFormat.label(): String = stringResource(ConsumptionFormatter.labelRes(this))
