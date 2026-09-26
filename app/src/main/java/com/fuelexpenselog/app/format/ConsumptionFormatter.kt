package com.fuelexpenselog.app.format

import android.content.res.Resources
import com.fuelexpenselog.app.R
import com.fuelexpenselog.domain.format.Rounding
import com.fuelexpenselog.domain.format.decimals
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import java.util.Locale

/**
 * A consumption figure, or an em dash when there is none. Null is the engine saying it does
 * not know, and the only honest rendering of that is a dash - a zero would be a lie.
 */
class ConsumptionFormatter(private val locale: Locale, private val res: Resources) {

    fun value(value: Double?, format: ConsumptionFormat): String =
        if (value == null || !value.isFinite()) Rounding.EM_DASH
        else Rounding.toLocalisedString(value, format.decimals(), locale)

    fun unitLabel(format: ConsumptionFormat): String = res.getString(labelRes(format))

    companion object {
        fun labelRes(format: ConsumptionFormat): Int = when (format) {
            ConsumptionFormat.L_PER_100KM -> R.string.format_l_per_100km
            ConsumptionFormat.KM_PER_L -> R.string.format_km_per_l
            ConsumptionFormat.MPG_US -> R.string.format_mpg_us
            ConsumptionFormat.MPG_UK -> R.string.format_mpg_uk
            ConsumptionFormat.KWH_PER_100KM -> R.string.format_kwh_per_100km
            ConsumptionFormat.KM_PER_KWH -> R.string.format_km_per_kwh
            ConsumptionFormat.MI_PER_KWH -> R.string.format_mi_per_kwh
        }
    }
}
