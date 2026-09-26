package com.fuelexpenselog.app.backup

import android.util.JsonReader
import android.util.JsonWriter
import com.fuelexpenselog.app.data.prefs.AppPrefs
import java.io.StringReader
import java.io.StringWriter

/**
 * What a `.fuellogbak` says about itself. JSON through android.util's reader and writer: no
 * serialization plugin for one small file.
 */
data class BackupManifest(
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: String,
    val counts: Map<String, Int>,
    /** The app settings, so the figures come back in the format they were shown in. */
    val settings: Map<String, String>,
    val dismissedProposals: Set<String>,
) {
    fun toJson(): String {
        val out = StringWriter()
        JsonWriter(out).use { w ->
            w.setIndent("  ")
            w.beginObject()
            w.name("format").value(FORMAT)
            w.name("schemaVersion").value(schemaVersion.toLong())
            w.name("appVersion").value(appVersion)
            w.name("createdAt").value(createdAt)
            w.name("counts").beginObject()
            counts.forEach { (k, v) -> w.name(k).value(v.toLong()) }
            w.endObject()
            w.name("settings").beginObject()
            settings.forEach { (k, v) -> w.name(k).value(v) }
            w.endObject()
            w.name("dismissedProposals").beginArray()
            dismissedProposals.sorted().forEach { w.value(it) }
            w.endArray()
            w.endObject()
        }
        return out.toString()
    }

    /** Writes the settings back through AppPrefs, so every listener hears about them. */
    fun applySettings(prefs: AppPrefs) {
        settings[AppPrefs.KEY_ONBOARDING_DONE]?.let { prefs.onboardingDone = it.toBoolean() }
        settings[AppPrefs.KEY_CONSUMPTION_FORMAT]?.let { raw ->
            runCatching { enumValueOf<com.fuelexpenselog.domain.unit.ConsumptionFormat>(raw) }.getOrNull()
                ?.let { prefs.consumptionFormat = it }
        }
        prefs.regionCountry = settings[AppPrefs.KEY_REGION_COUNTRY]
        prefs.defaultDistanceUnit = settings[AppPrefs.KEY_DEFAULT_DISTANCE_UNIT]
            ?.let { runCatching { enumValueOf<com.fuelexpenselog.domain.unit.DistanceUnit>(it) }.getOrNull() }
        prefs.defaultVolumeUnit = settings[AppPrefs.KEY_DEFAULT_VOLUME_UNIT]
            ?.let { runCatching { enumValueOf<com.fuelexpenselog.domain.unit.EnergyUnit>(it) }.getOrNull() }
        prefs.defaultCurrency = settings[AppPrefs.KEY_DEFAULT_CURRENCY]
        settings[AppPrefs.KEY_REMINDERS_NOTIFY]?.let { prefs.remindersNotify = it.toBoolean() }
        settings[AppPrefs.KEY_LAST_VEHICLE_ID]?.toLongOrNull()?.let { prefs.lastVehicleId = it }
        prefs.dismissedProposals = dismissedProposals
    }

    companion object {
        const val FORMAT = "fuel-log-backup"
        const val ENTRY_MANIFEST = "manifest.json"
        const val ENTRY_DATABASE = "fuel-log.db"

        fun settingsOf(prefs: AppPrefs): Map<String, String> = buildMap {
            put(AppPrefs.KEY_ONBOARDING_DONE, prefs.onboardingDone.toString())
            put(AppPrefs.KEY_CONSUMPTION_FORMAT, prefs.consumptionFormat.name)
            prefs.regionCountry?.let { put(AppPrefs.KEY_REGION_COUNTRY, it) }
            prefs.defaultDistanceUnit?.let { put(AppPrefs.KEY_DEFAULT_DISTANCE_UNIT, it.name) }
            prefs.defaultVolumeUnit?.let { put(AppPrefs.KEY_DEFAULT_VOLUME_UNIT, it.name) }
            prefs.defaultCurrency?.let { put(AppPrefs.KEY_DEFAULT_CURRENCY, it) }
            put(AppPrefs.KEY_REMINDERS_NOTIFY, prefs.remindersNotify.toString())
            put(AppPrefs.KEY_LAST_VEHICLE_ID, prefs.lastVehicleId.toString())
        }

        /** Null when the text is not a Fuel Log manifest at all. */
        fun parse(json: String): BackupManifest? = runCatching {
            var format: String? = null
            var schema = -1
            var appVersion = ""
            var createdAt = ""
            val counts = mutableMapOf<String, Int>()
            val settings = mutableMapOf<String, String>()
            val dismissed = mutableSetOf<String>()
            JsonReader(StringReader(json)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "format" -> format = r.nextString()
                        "schemaVersion" -> schema = r.nextInt()
                        "appVersion" -> appVersion = r.nextString()
                        "createdAt" -> createdAt = r.nextString()
                        "counts" -> {
                            r.beginObject()
                            while (r.hasNext()) counts[r.nextName()] = r.nextInt()
                            r.endObject()
                        }
                        "settings" -> {
                            r.beginObject()
                            while (r.hasNext()) settings[r.nextName()] = r.nextString()
                            r.endObject()
                        }
                        "dismissedProposals" -> {
                            r.beginArray()
                            while (r.hasNext()) dismissed += r.nextString()
                            r.endArray()
                        }
                        // A newer version may add fields; skipping them is the whole point.
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
            if (format != FORMAT || schema < 1) null
            else BackupManifest(schema, appVersion, createdAt, counts, settings, dismissed)
        }.getOrNull()
    }
}
