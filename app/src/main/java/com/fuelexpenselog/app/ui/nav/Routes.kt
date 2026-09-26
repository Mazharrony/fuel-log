package com.fuelexpenselog.app.ui.nav

/**
 * String routes. Type-safe routes would need the Kotlin serialization plugin, which is one
 * more shipped dependency for a graph this small.
 */
object Routes {
    const val ARG_ID = "id"

    const val GARAGE = "garage"

    const val VEHICLE_NEW = "vehicle/new"
    const val VEHICLE_EDIT = "vehicle/{$ARG_ID}/edit"
    fun vehicleEdit(id: Long) = "vehicle/$id/edit"
}
