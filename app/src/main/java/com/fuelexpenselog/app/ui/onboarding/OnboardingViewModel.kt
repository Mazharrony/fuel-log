package com.fuelexpenselog.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.app.format.LocaleDefaults
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.FuelType
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.VehicleType
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.region.RegionDefaults
import com.fuelexpenselog.domain.region.Regions
import com.fuelexpenselog.domain.unit.EnergyUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

enum class OnboardingStep { COUNTRY, UNITS, VEHICLE }

data class OnboardingUiState(
    val step: OnboardingStep,
    /** What the phone's own locale says, e.g. "en-US". */
    val localeTag: String,
    val detected: RegionDefaults,
    val region: RegionDefaults,
    val preset: UnitPreset,
    val name: String,
    val done: Boolean,
) {
    val canFinish: Boolean get() = name.isNotBlank()
}

/**
 * Country, then units, then the first vehicle, then out of the way. No tutorial, no account.
 *
 * Everything lives in the SavedStateHandle, so a rotation or process death mid-flow resumes
 * on the same step with the same choices.
 */
class OnboardingViewModel(
    private val handle: SavedStateHandle,
    private val repository: FuelLogRepository,
    private val prefs: AppPrefs,
    locale: () -> Locale = { Locale.getDefault() },
) : ViewModel() {

    private val deviceLocale = locale()
    private val detected = LocaleDefaults.detect(deviceLocale)

    private val step = handle.getStateFlow(KEY_STEP, OnboardingStep.COUNTRY.name)
    private val country = handle.getStateFlow(KEY_COUNTRY, detected.countryCode)
    /** Empty means "whatever the region implies". */
    private val preset = handle.getStateFlow(KEY_PRESET, "")
    private val name = handle.getStateFlow(KEY_NAME, "")
    private val done = MutableStateFlow(false)

    init {
        // Someone who already has vehicles - from a restore, or an earlier build - has
        // nothing to set up, and must not be handed a second first vehicle.
        viewModelScope.launch {
            if (repository.vehicleCount() > 0) {
                prefs.onboardingDone = true
                done.value = true
            }
        }
    }

    val state: StateFlow<OnboardingUiState> = combine(step, country, preset, name, done, ::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    private fun build(s: String, c: String, p: String, n: String, d: Boolean): OnboardingUiState {
        val region = Regions.forCountry(c)
        return OnboardingUiState(
            step = decodeOr(s, OnboardingStep.COUNTRY),
            localeTag = deviceLocale.toLanguageTag(),
            detected = detected,
            region = region,
            preset = if (p.isEmpty()) UnitPreset.of(region) else decodeOr(p, UnitPreset.of(region)),
            name = n,
            done = d,
        )
    }

    /** From the sources, not from `state`, which only runs while someone watches it. */
    private fun current() = build(step.value, country.value, preset.value, name.value, done.value)

    /** A new country resets the unit set to what that country implies. */
    fun onCountry(code: String) {
        handle[KEY_COUNTRY] = code
        handle[KEY_PRESET] = ""
    }

    fun onPreset(preset: UnitPreset) {
        handle[KEY_PRESET] = preset.name
    }

    fun onName(value: String) {
        handle[KEY_NAME] = value
    }

    fun next() {
        val current = decodeOr(step.value, OnboardingStep.COUNTRY)
        if (current != OnboardingStep.VEHICLE) handle[KEY_STEP] = OnboardingStep.entries[current.ordinal + 1].name
    }

    /** False when already on the first step, so system back can leave the app as usual. */
    fun back(): Boolean {
        val current = decodeOr(step.value, OnboardingStep.COUNTRY)
        if (current == OnboardingStep.COUNTRY) return false
        handle[KEY_STEP] = OnboardingStep.entries[current.ordinal - 1].name
        return true
    }

    /**
     * Writes the first vehicle and the defaults, then marks onboarding done. The region's
     * currency goes on the vehicle; the unit set chosen here goes on both the vehicle and the
     * app's defaults for the next one.
     */
    fun finish(importUri: String? = null) {
        val s = current().takeIf { it.canFinish } ?: return
        viewModelScope.launch {
            val region = s.region
            prefs.regionCountry = region.countryCode.ifEmpty { null }
            prefs.defaultDistanceUnit = s.preset.distance
            prefs.defaultVolumeUnit = s.preset.volume
            prefs.defaultCurrency = region.currencyCode
            prefs.consumptionFormat = s.preset.format(region)

            val id = repository.addVehicle(
                Vehicle(
                    id = 0,
                    name = s.name.trim(),
                    type = VehicleType.CAR,
                    fuelType = FuelType.PETROL,
                    distanceUnit = s.preset.distance,
                    volumeUnit = s.preset.volume,
                    energyUnit = EnergyUnit.KWH,
                    // Null follows the app setting just written, so Settings can change it later.
                    consumptionFormat = null,
                    currencyCode = region.currencyCode,
                    tankCapacity = null,
                    batteryCapacity = null,
                    defaultTag = EntryTag.PERSONAL,
                ),
            )
            prefs.lastVehicleId = id
            prefs.onboardingDone = true
            importUri?.let { _handoff.value = ImportHandoff(it, id) }
            done.value = true
        }
    }

    /** "Import a CSV": the first vehicle is created as usual, then its history comes in. */
    data class ImportHandoff(val uri: String, val vehicleId: Long)

    private val _handoff = MutableStateFlow<ImportHandoff?>(null)
    val handoff: StateFlow<ImportHandoff?> = _handoff

    private companion object {
        const val KEY_STEP = "onboarding_step"
        const val KEY_COUNTRY = "onboarding_country"
        const val KEY_PRESET = "onboarding_preset"
        const val KEY_NAME = "onboarding_name"
    }
}
