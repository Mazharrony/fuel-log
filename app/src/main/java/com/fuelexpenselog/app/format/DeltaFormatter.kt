package com.fuelexpenselog.app.format

import com.fuelexpenselog.domain.stats.Delta
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** "+6.3%" or "−4.1%", with a true minus sign and the locale's decimal mark. */
class DeltaFormatter(private val locale: Locale) {
    fun format(delta: Delta): String {
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(abs(delta.percent))
        val sign = when {
            delta.percent > 0.05 -> "+"
            delta.percent < -0.05 -> "−"
            else -> ""
        }
        return "$sign$number%"
    }
}
