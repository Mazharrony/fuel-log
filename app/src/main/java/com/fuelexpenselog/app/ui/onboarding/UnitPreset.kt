package com.fuelexpenselog.app.ui.onboarding

import androidx.annotation.StringRes
import com.fuelexpenselog.app.R
import com.fuelexpenselog.domain.region.RegionDefaults
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit

/**
 * The unit sets offered at first launch. UK gallons stay on offer even though the UK default
 * is litres: plenty of British drivers still think in gallons.
 */
enum class UnitPreset(val distance: DistanceUnit, val volume: EnergyUnit, @StringRes val label: Int) {
    US(DistanceUnit.MILE, EnergyUnit.US_GALLON, R.string.preset_us),
    UK(DistanceUnit.MILE, EnergyUnit.LITRE, R.string.preset_uk),
    UK_GALLONS(DistanceUnit.MILE, EnergyUnit.IMP_GALLON, R.string.preset_uk_gallons),
    METRIC(DistanceUnit.KILOMETRE, EnergyUnit.LITRE, R.string.preset_metric),
    ;

    /** Metric keeps the region's own convention: km/L in India, L/100 km elsewhere. */
    fun format(region: RegionDefaults): ConsumptionFormat = when (this) {
        US -> ConsumptionFormat.MPG_US
        UK, UK_GALLONS -> ConsumptionFormat.MPG_UK
        METRIC -> if (region.distanceUnit == DistanceUnit.KILOMETRE) region.consumptionFormat else ConsumptionFormat.L_PER_100KM
    }

    companion object {
        fun of(region: RegionDefaults): UnitPreset =
            entries.firstOrNull { it.distance == region.distanceUnit && it.volume == region.volumeUnit } ?: METRIC
    }
}
