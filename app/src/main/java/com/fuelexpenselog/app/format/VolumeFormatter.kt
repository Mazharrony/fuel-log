package com.fuelexpenselog.app.format

import android.content.res.Resources
import com.fuelexpenselog.app.R
import com.fuelexpenselog.domain.format.Rounding
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit
import java.util.Locale

/** Fuel volumes, in the unit the vehicle is shown in. Two decimals, as a pump prints them. */
class VolumeFormatter(private val locale: Locale, private val res: Resources) {

    fun unitLabel(unit: EnergyUnit): String = res.getString(shortLabelRes(unit))

    fun number(energy: Energy, unit: EnergyUnit, decimals: Int = 2): String =
        Rounding.toLocalisedString(energy.inUnit(unit), decimals, locale)

    fun format(energy: Energy, unit: EnergyUnit): String =
        res.getString(R.string.value_with_unit, number(energy, unit), unitLabel(unit))

    companion object {
        fun shortLabelRes(unit: EnergyUnit): Int = when (unit) {
            EnergyUnit.LITRE -> R.string.unit_litre
            EnergyUnit.US_GALLON -> R.string.unit_us_gal
            EnergyUnit.IMP_GALLON -> R.string.unit_uk_gal
            EnergyUnit.KWH -> R.string.unit_kwh
        }

        fun longLabelRes(unit: EnergyUnit): Int = when (unit) {
            EnergyUnit.LITRE -> R.string.unit_litres_long
            EnergyUnit.US_GALLON -> R.string.unit_us_gallons_long
            EnergyUnit.IMP_GALLON -> R.string.unit_uk_gallons_long
            EnergyUnit.KWH -> R.string.unit_kwh
        }
    }
}
