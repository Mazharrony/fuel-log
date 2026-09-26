package com.fuelexpenselog.app.format

import android.icu.util.LocaleData
import android.os.Build
import android.icu.util.ULocale
import com.fuelexpenselog.domain.region.RegionDefaults
import com.fuelexpenselog.domain.region.Regions
import java.util.Locale

/**
 * The first guess at a region, from the phone's own settings. Device-local only: there is no
 * network to ask, and no reason to - the locale already says where the user lives.
 */
object LocaleDefaults {

    fun detect(locale: Locale): RegionDefaults {
        val country = locale.country.uppercase(Locale.ROOT)
        if (country.length == 2 && country in isoCountries) return Regions.forCountry(country)

        // A locale with no country ("en", "pt") says nothing about units by itself. ICU's
        // measurement system for the language is the next best source; before API 28 there
        // is no such query, and metric is the guess right for most of the world.
        val usMeasure = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            LocaleData.getMeasurementSystem(ULocale.forLocale(locale)) == LocaleData.MeasurementSystem.US
        return if (usMeasure) Regions.forCountry("US") else Regions.forCountry("")
    }

    private val isoCountries: Set<String> by lazy { Locale.getISOCountries().toSet() }
}
