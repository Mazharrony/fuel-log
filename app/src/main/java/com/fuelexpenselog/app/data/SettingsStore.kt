package com.fuelexpenselog.app.data

import android.content.Context
import android.content.SharedPreferences
import com.fuelexpenselog.app.domain.units.ConsumptionConvention
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Five keys, on plain SharedPreferences.
 *
 * Not DataStore: for this much state it would add a dependency and its
 * transitives to the permission-audit surface for no benefit, and
 * SharedPreferences is already inside the auto-backup set.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** The one app-level unit setting. Everything else is per vehicle. */
    var convention: ConsumptionConvention
        get() = runCatching {
            ConsumptionConvention.valueOf(prefs.getString(KEY_CONVENTION, null) ?: "")
        }.getOrDefault(ConsumptionConvention.L_PER_100KM)
        set(value) = prefs.edit().putString(KEY_CONVENTION, value.name).apply()

    /** Defaults for NEW vehicles only. Never rewrites existing records. */
    var regionCode: String?
        get() = prefs.getString(KEY_REGION, null)
        set(value) = prefs.edit().putString(KEY_REGION, value).apply()

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /**
     * Drives the gentle export reminder. A lost phone is the one real weakness
     * of an offline app, so the nudge is deliberate rather than an afterthought.
     */
    var fillUpsSinceExport: Int
        get() = prefs.getInt(KEY_SINCE_EXPORT, 0)
        set(value) = prefs.edit().putInt(KEY_SINCE_EXPORT, value).apply()

    var exportPromptDismissedAt: Long
        get() = prefs.getLong(KEY_PROMPT_DISMISSED, 0L)
        set(value) = prefs.edit().putLong(KEY_PROMPT_DISMISSED, value).apply()

    fun conventionFlow(): Flow<ConsumptionConvention> = watch(KEY_CONVENTION) { convention }

    fun exportNudgeFlow(): Flow<Boolean> = watch(KEY_SINCE_EXPORT) {
        fillUpsSinceExport >= EXPORT_NUDGE_AFTER
    }

    private fun <T> watch(key: String, read: () -> T): Flow<T> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    companion object {
        /** Listed by name in the auto-backup rules. */
        const val NAME = "fuel-log-settings"
        const val EXPORT_NUDGE_AFTER = 20

        private const val KEY_CONVENTION = "consumption_convention"
        private const val KEY_REGION = "region_code"
        private const val KEY_ONBOARDED = "onboarding_complete"
        private const val KEY_SINCE_EXPORT = "fill_ups_since_export"
        private const val KEY_PROMPT_DISMISSED = "export_prompt_dismissed_at"
    }
}
