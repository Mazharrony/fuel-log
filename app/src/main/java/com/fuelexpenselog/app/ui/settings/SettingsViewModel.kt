package com.fuelexpenselog.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.format.LocaleDefaults
import com.fuelexpenselog.domain.region.RegionDefaults
import com.fuelexpenselog.domain.region.Regions
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

data class SettingsUiState(
    val format: ConsumptionFormat,
    val defaultDistance: DistanceUnit,
    val defaultVolume: EnergyUnit,
    val defaultCurrency: String,
    /** The chosen region, or the phone's guess when none has been chosen. */
    val region: RegionDefaults,
)

/**
 * App-level settings: how consumption is shown, and what a NEW vehicle starts with. Nothing
 * here rewrites an existing vehicle - units and currency live on each vehicle row.
 */
class SettingsViewModel(
    private val prefs: AppPrefs,
    private val locale: () -> Locale = { Locale.getDefault() },
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = prefs.changes()
        .map { read() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), read())

    private fun read(): SettingsUiState {
        val region = prefs.regionCountry?.let(Regions::forCountry) ?: LocaleDefaults.detect(locale())
        return SettingsUiState(
            format = prefs.consumptionFormat,
            defaultDistance = prefs.defaultDistanceUnit ?: region.distanceUnit,
            defaultVolume = prefs.defaultVolumeUnit ?: region.volumeUnit,
            defaultCurrency = prefs.defaultCurrency ?: region.currencyCode,
            region = region,
        )
    }

    /** Every vehicle without its own opinion re-renders in the new format; the others keep theirs. */
    fun onFormat(format: ConsumptionFormat) {
        prefs.consumptionFormat = format
    }

    fun onDistance(unit: DistanceUnit) {
        prefs.defaultDistanceUnit = unit
    }

    fun onVolume(unit: EnergyUnit) {
        prefs.defaultVolumeUnit = unit
    }

    fun onCurrency(code: String) {
        prefs.defaultCurrency = code
    }

    /** New-vehicle defaults follow the region. No existing vehicle is touched. */
    fun onRegion(code: String) {
        val region = Regions.forCountry(code)
        prefs.regionCountry = region.countryCode
        prefs.defaultDistanceUnit = region.distanceUnit
        prefs.defaultVolumeUnit = region.volumeUnit
        prefs.defaultCurrency = region.currencyCode
    }
}
