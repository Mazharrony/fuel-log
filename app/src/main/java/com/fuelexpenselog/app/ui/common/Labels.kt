package com.fuelexpenselog.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.format.ConsumptionFormatter
import com.fuelexpenselog.app.format.VolumeFormatter
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.FuelType
import com.fuelexpenselog.domain.model.VehicleType
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit

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
