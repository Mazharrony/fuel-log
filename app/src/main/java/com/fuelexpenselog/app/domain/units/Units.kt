package com.fuelexpenselog.app.domain.units

import com.fuelexpenselog.app.domain.model.DistanceUnit
import com.fuelexpenselog.app.domain.model.VolumeUnit

/**
 * Canonical storage is kilometres and litres. Conversion happens only at the
 * display and input edges, never in between - changing a vehicle's units
 * changes what is shown, never what is stored.
 */
object Units {
    const val KM_PER_MILE = 1.609344
    const val LITRES_PER_US_GALLON = 3.785411784
    const val LITRES_PER_UK_GALLON = 4.54609

    fun kmToDisplay(km: Double, unit: DistanceUnit): Double = when (unit) {
        DistanceUnit.KILOMETRE -> km
        DistanceUnit.MILE -> km / KM_PER_MILE
    }

    fun displayToKm(value: Double, unit: DistanceUnit): Double = when (unit) {
        DistanceUnit.KILOMETRE -> value
        DistanceUnit.MILE -> value * KM_PER_MILE
    }

    fun litresToDisplay(litres: Double, unit: VolumeUnit): Double = when (unit) {
        VolumeUnit.LITRE -> litres
        VolumeUnit.US_GALLON -> litres / LITRES_PER_US_GALLON
        VolumeUnit.UK_GALLON -> litres / LITRES_PER_UK_GALLON
    }

    fun displayToLitres(value: Double, unit: VolumeUnit): Double = when (unit) {
        VolumeUnit.LITRE -> value
        VolumeUnit.US_GALLON -> value * LITRES_PER_US_GALLON
        VolumeUnit.UK_GALLON -> value * LITRES_PER_UK_GALLON
    }
}
