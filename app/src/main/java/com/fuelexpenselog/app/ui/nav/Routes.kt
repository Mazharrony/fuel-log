package com.fuelexpenselog.app.ui.nav

/**
 * String routes. Type-safe routes would need the Kotlin serialization plugin, which is one
 * more shipped dependency for a graph this small.
 */
object Routes {
    const val ARG_ID = "id"
    const val ARG_VEHICLE = "vehicle"

    const val GARAGE = "garage"

    const val VEHICLE_NEW = "vehicle/new"
    const val VEHICLE_EDIT = "vehicle/{$ARG_ID}/edit"
    fun vehicleEdit(id: Long) = "vehicle/$id/edit"

    /** [ARG_VEHICLE] 0 means "the vehicle used last". */
    const val FILLUP_NEW = "fillup/new?$ARG_VEHICLE={$ARG_VEHICLE}"
    fun fillUpNew(vehicleId: Long = 0) = "fillup/new?$ARG_VEHICLE=$vehicleId"
    const val FILLUP_EDIT = "fillup/{$ARG_ID}/edit"
    fun fillUpEdit(id: Long) = "fillup/$id/edit"

    const val EXPENSE_NEW = "expense/new?$ARG_VEHICLE={$ARG_VEHICLE}"
    fun expenseNew(vehicleId: Long = 0) = "expense/new?$ARG_VEHICLE=$vehicleId"
    const val EXPENSE_EDIT = "expense/{$ARG_ID}/edit"
    fun expenseEdit(id: Long) = "expense/$id/edit"
}
