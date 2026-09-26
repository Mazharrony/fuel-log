package com.fuelexpenselog.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The handful of app-level settings. Everything per vehicle lives on the vehicle row.
 *
 * SharedPreferences rather than DataStore: DataStore is another shipped dependency to lock
 * and audit, and nine keys do not need it. The file name is pinned by both backup XML files,
 * so it can never be renamed without a migration.
 *
 * Enums are stored by name and read leniently: a value written by a newer version falls back
 * to the default rather than crashing the settings screen.
 */
class AppPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = sp.edit { putBoolean(KEY_ONBOARDING_DONE, value) }

    /** The app-wide display convention. A vehicle with its own opinion overrides it. */
    var consumptionFormat: ConsumptionFormat
        get() = decodeOr(sp.getString(KEY_CONSUMPTION_FORMAT, null), ConsumptionFormat.L_PER_100KM)
        set(value) = sp.edit { putString(KEY_CONSUMPTION_FORMAT, value.name) }

    /** ISO 3166 alpha-2. Sets defaults for NEW vehicles only; never touches an existing one. */
    var regionCountry: String?
        get() = sp.getString(KEY_REGION_COUNTRY, null)
        set(value) = sp.edit { putString(KEY_REGION_COUNTRY, value) }

    var defaultDistanceUnit: DistanceUnit?
        get() = sp.getString(KEY_DEFAULT_DISTANCE_UNIT, null)?.let { decodeOr<DistanceUnit>(it, DistanceUnit.KILOMETRE) }
        set(value) = sp.edit { putString(KEY_DEFAULT_DISTANCE_UNIT, value?.name) }

    var defaultVolumeUnit: EnergyUnit?
        get() = sp.getString(KEY_DEFAULT_VOLUME_UNIT, null)
            ?.let { decodeOr<EnergyUnit>(it, EnergyUnit.LITRE) }
            ?.takeIf { it in EnergyUnit.liquid }
        set(value) = sp.edit { putString(KEY_DEFAULT_VOLUME_UNIT, value?.name) }

    /** ISO 4217. */
    var defaultCurrency: String?
        get() = sp.getString(KEY_DEFAULT_CURRENCY, null)?.takeIf { it.length == 3 }
        set(value) = sp.edit { putString(KEY_DEFAULT_CURRENCY, value) }

    var remindersNotify: Boolean
        get() = sp.getBoolean(KEY_REMINDERS_NOTIFY, false)
        set(value) = sp.edit { putBoolean(KEY_REMINDERS_NOTIFY, value) }

    /** The vehicle the entry screens default to. 0 means none chosen yet. */
    var lastVehicleId: Long
        get() = sp.getLong(KEY_LAST_VEHICLE_ID, 0L)
        set(value) = sp.edit { putLong(KEY_LAST_VEHICLE_ID, value) }

    /** Odometer proposals the user said no to, keyed so an edited reading asks again. */
    var dismissedProposals: Set<String>
        get() = sp.getStringSet(KEY_DISMISSED_PROPOSALS, null)?.toSet() ?: emptySet()
        set(value) = sp.edit { putStringSet(KEY_DISMISSED_PROPOSALS, value.toSet()) }

    fun observeOnboardingDone(): Flow<Boolean> = observe(KEY_ONBOARDING_DONE) { onboardingDone }

    fun observeConsumptionFormat(): Flow<ConsumptionFormat> =
        observe(KEY_CONSUMPTION_FORMAT) { consumptionFormat }

    fun observeRegionCountry(): Flow<String?> = observe(KEY_REGION_COUNTRY) { regionCountry }

    fun observeRemindersNotify(): Flow<Boolean> = observe(KEY_REMINDERS_NOTIFY) { remindersNotify }

    fun observeDismissedProposals(): Flow<Set<String>> =
        observe(KEY_DISMISSED_PROPOSALS) { dismissedProposals }

    /** One emission now and one per change to any key: for a screen that shows them all. */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        sp.registerOnSharedPreferenceChangeListener(listener)
        send(Unit)
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    /**
     * Current value first, then one emission per change to [key]. A null key in the callback
     * means the whole file was cleared (API 30+), which changes every value.
     */
    private fun <T> observe(key: String, read: () -> T): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == null || changed == key) trySend(read())
        }
        // Registered before the first read, so a write racing the subscription is not lost.
        sp.registerOnSharedPreferenceChangeListener(listener)
        send(read())
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged()

    companion object {
        /** Named in backup_rules.xml and data_extraction_rules.xml. Never rename. */
        const val FILE_NAME = "fuel-log-settings"

        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_CONSUMPTION_FORMAT = "consumption_format"
        const val KEY_REGION_COUNTRY = "region_country"
        const val KEY_DEFAULT_DISTANCE_UNIT = "default_distance_unit"
        const val KEY_DEFAULT_VOLUME_UNIT = "default_volume_unit"
        const val KEY_DEFAULT_CURRENCY = "default_currency"
        const val KEY_REMINDERS_NOTIFY = "reminders_notify"
        const val KEY_LAST_VEHICLE_ID = "last_vehicle_id"
        const val KEY_DISMISSED_PROPOSALS = "dismissed_proposals"
    }
}
