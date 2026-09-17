package com.fuelexpenselog.app.domain.model

enum class DistanceUnit { KILOMETRE, MILE }

enum class VolumeUnit { LITRE, US_GALLON, UK_GALLON }

/**
 * Units and currency are per vehicle. Only the consumption display convention
 * (MPG US / MPG UK / L per 100km / km per L) is app-level.
 *
 * [tankCapacityLitres] is optional and used only for sanity warnings - never to
 * block a save. Jerry cans and twin tanks are real.
 */
data class Vehicle(
    val id: Long = 0,
    val name: String,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
    val currency: String,
    val tankCapacityLitres: Double? = null,
    /** Sold vehicles are hidden, never deleted - the history stays. */
    val isActive: Boolean = true,
    /** Epoch millis. Orders the garage so vehicles keep the order they were added. */
    val createdAt: Long = 0,
)
