package com.fuelexpenselog.app.format

import android.content.res.Resources
import com.fuelexpenselog.app.R
import com.fuelexpenselog.domain.format.wholeUnits
import com.fuelexpenselog.domain.unit.DistanceUnit
import java.text.NumberFormat
import java.util.Locale

/** Odometers and distances, in whole dashboard units with the locale's grouping. */
class DistanceFormatter(private val locale: Locale, private val res: Resources) {

    fun unitLabel(unit: DistanceUnit): String = res.getString(
        when (unit) {
            DistanceUnit.KILOMETRE -> R.string.unit_km
            DistanceUnit.MILE -> R.string.unit_mi
        },
    )

    /** "84,210" - the number alone, for a hint or a column that labels its unit elsewhere. */
    fun number(metres: Long, unit: DistanceUnit): String =
        NumberFormat.getIntegerInstance(locale).format(unit.wholeUnits(metres))

    /** "84,210 mi". */
    fun format(metres: Long, unit: DistanceUnit): String =
        res.getString(R.string.value_with_unit, number(metres, unit), unitLabel(unit))
}
