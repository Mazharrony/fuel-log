package com.fuelexpenselog.app.ui.entry

import com.fuelexpenselog.app.data.prefs.AppPrefs
import com.fuelexpenselog.app.data.repo.FuelLogRepository
import com.fuelexpenselog.domain.time.CivilDate
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId

/**
 * Which vehicle a new entry belongs to: the one asked for, else the one used last, else the
 * first still driven. Null means there is no active vehicle to log against.
 */
internal suspend fun resolveVehicleId(requested: Long, repository: FuelLogRepository, prefs: AppPrefs): Long? {
    val active = repository.allVehicles().filter { !it.isArchived }
    return active.firstOrNull { it.id == requested }?.id
        ?: active.firstOrNull { it.id == prefs.lastVehicleId }?.id
        ?: active.firstOrNull()?.id
}

/**
 * The instant an entry happened, used only for ordering same-day entries and the duplicate
 * probe - never for bucketing, which reads the civil date.
 *
 * An entry logged today gets the clock's time. A back-dated one gets noon on its day, the
 * instant least likely to land on another date under any zone the user later travels to. An
 * edit that keeps its date keeps its instant.
 */
internal fun entryInstant(
    date: CivilDate,
    today: CivilDate,
    clock: Clock,
    zone: ZoneId,
    original: Pair<CivilDate, Long>? = null,
): Long = when {
    original != null && original.first == date -> original.second
    date == today -> clock.millis()
    else -> date.toLocalDate().atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
}
