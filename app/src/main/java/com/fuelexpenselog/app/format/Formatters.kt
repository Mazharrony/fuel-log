package com.fuelexpenselog.app.format

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Every display formatter, built for one locale. Provided once at the root of the UI so a
 * locale change rebuilds them all together.
 */
class Formatters(
    val locale: Locale,
    val currency: CurrencyFormatter,
    val distance: DistanceFormatter,
    val volume: VolumeFormatter,
    val consumption: ConsumptionFormatter,
    val date: DateFormatter,
) {
    companion object {
        fun from(context: Context): Formatters {
            val res = context.resources
            val locale = res.configuration.locales[0] ?: Locale.getDefault()
            return Formatters(
                locale = locale,
                currency = CurrencyFormatter(locale),
                distance = DistanceFormatter(locale, res),
                volume = VolumeFormatter(locale, res),
                consumption = ConsumptionFormatter(locale, res),
                date = DateFormatter(locale),
            )
        }
    }
}

val LocalFormatters = staticCompositionLocalOf<Formatters> {
    error("Formatters are provided by FuelLogRoot")
}

/** Rebuilt when the configuration's locale changes, and only then. */
@Composable
fun rememberFormatters(): Formatters {
    val context = LocalContext.current
    val locales = LocalConfiguration.current.locales
    return remember(locales) { Formatters.from(context) }
}
