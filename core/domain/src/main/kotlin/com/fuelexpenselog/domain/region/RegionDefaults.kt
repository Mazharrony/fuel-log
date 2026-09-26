package com.fuelexpenselog.domain.region

import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import java.util.Currency
import java.util.Locale

/**
 * What a region implies for a new vehicle. Defaults only: every vehicle keeps its own units
 * and currency, and changing the region later touches no existing vehicle.
 */
data class RegionDefaults(
    /** ISO 3166 alpha-2, upper case. Empty when the phone names no country at all. */
    val countryCode: String,
    val distanceUnit: DistanceUnit,
    val volumeUnit: EnergyUnit,
    val consumptionFormat: ConsumptionFormat,
    /** ISO 4217. */
    val currencyCode: String,
)

/**
 * The country table, from the platform's own lists - no download, because there is no
 * network to download it with and no need for one.
 */
object Regions {

    /** Shown first, before any search: the markets the app is written for. */
    val seedCodes: List<String> = listOf("US", "GB", "CA", "AU", "DE", "IN")

    val seed: List<RegionDefaults> get() = seedCodes.map(::forCountry)

    val all: List<RegionDefaults> by lazy { Locale.getISOCountries().map(::forCountry) }

    /** Miles and US gallons. */
    private val MILES_US_GALLONS = setOf("US", "LR", "MM")

    /**
     * Miles, and litres rather than UK gallons: UK pumps sell by the litre, and MPG (UK)
     * reads correctly from either. The units step still offers UK gallons.
     */
    private val MILES_LITRES = setOf("GB")

    fun forCountry(iso2: String): RegionDefaults {
        val code = iso2.uppercase(Locale.ROOT)
        val miles = code in MILES_US_GALLONS || code in MILES_LITRES
        return RegionDefaults(
            countryCode = code,
            distanceUnit = if (miles) DistanceUnit.MILE else DistanceUnit.KILOMETRE,
            volumeUnit = if (code in MILES_US_GALLONS) EnergyUnit.US_GALLON else EnergyUnit.LITRE,
            consumptionFormat = when {
                code in MILES_US_GALLONS -> ConsumptionFormat.MPG_US
                code in MILES_LITRES -> ConsumptionFormat.MPG_UK
                code == "IN" -> ConsumptionFormat.KM_PER_L
                else -> ConsumptionFormat.L_PER_100KM
            },
            currencyCode = currencyOf(code),
        )
    }

    /** A country with no currency of its own - Antarctica, an unknown code - gets USD. */
    private fun currencyOf(code: String): String {
        if (code.length != 2) return FALLBACK_CURRENCY
        @Suppress("DEPRECATION") // Locale.of is JDK 19+; this module targets 17.
        val locale = Locale("", code)
        return runCatching { Currency.getInstance(locale)?.currencyCode }.getOrNull() ?: FALLBACK_CURRENCY
    }

    const val FALLBACK_CURRENCY = "USD"
}
