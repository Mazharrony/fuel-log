package com.fuelexpenselog.app.ui.nav

/**
 * String routes. Type-safe routes would need the Kotlin serialization plugin, which is one
 * more shipped dependency for a graph this small.
 */
object Routes {
    const val ARG_ID = "id"
    const val ARG_VEHICLE = "vehicle"
    /** A month as `yyyymm`; 0 means none. */
    const val ARG_MONTH = "month"
    const val ARG_YEAR = "year"

    const val ONBOARDING = "onboarding"
    const val GARAGE = "garage"
    const val SETTINGS = "settings"
    const val COLLECTS = "settings/collects"

    /** [ARG_VEHICLE] 0 exports every vehicle; [ARG_MONTH] 0 offers no single month. */
    const val EXPORT = "export?$ARG_VEHICLE={$ARG_VEHICLE}&$ARG_MONTH={$ARG_MONTH}"
    fun export(vehicleId: Long = 0, month: Int = 0) = "export?$ARG_VEHICLE=$vehicleId&$ARG_MONTH=$month"

    const val VEHICLE = "vehicle/{$ARG_ID}"
    fun vehicle(id: Long) = "vehicle/$id"
    const val VEHICLE_NEW = "vehicle/new"
    const val VEHICLE_EDIT = "vehicle/{$ARG_ID}/edit"
    fun vehicleEdit(id: Long) = "vehicle/$id/edit"

    const val HISTORY = "vehicle/{$ARG_ID}/history?$ARG_MONTH={$ARG_MONTH}"
    fun history(id: Long, month: Int = 0) = "vehicle/$id/history?$ARG_MONTH=$month"
    const val STATISTICS = "vehicle/{$ARG_ID}/statistics"
    fun statistics(id: Long) = "vehicle/$id/statistics"
    const val MONTHS = "vehicle/{$ARG_ID}/months?$ARG_YEAR={$ARG_YEAR}"
    fun months(id: Long, year: Int = 0) = "vehicle/$id/months?$ARG_YEAR=$year"
    const val MONTH_DETAIL = "vehicle/{$ARG_ID}/month/{$ARG_MONTH}"
    fun monthDetail(id: Long, month: Int) = "vehicle/$id/month/$month"

    const val REMINDERS = "vehicle/{$ARG_ID}/reminders"
    fun reminders(vehicleId: Long) = "vehicle/$vehicleId/reminders"
    const val REMINDER_NEW = "reminder/new?$ARG_VEHICLE={$ARG_VEHICLE}"
    fun reminderNew(vehicleId: Long) = "reminder/new?$ARG_VEHICLE=$vehicleId"
    const val REMINDER_EDIT = "reminder/{$ARG_ID}/edit"
    fun reminderEdit(id: Long) = "reminder/$id/edit"
    const val REMINDER_DONE = "reminder/{$ARG_ID}/done"
    fun reminderDone(id: Long) = "reminder/$id/done"

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
