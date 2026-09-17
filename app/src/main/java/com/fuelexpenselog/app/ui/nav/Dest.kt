package com.fuelexpenselog.app.ui.nav

/**
 * Destinations, as string routes in a sealed hierarchy.
 *
 * Navigation Compose rather than a hand-rolled router, for one concrete reason:
 * the release checklist requires that a half-typed odometer survives process
 * death, and Navigation Compose gives every destination its own
 * SavedStateHandle and a back stack that is itself saved. Hand-rolling that is
 * reimplementing exactly the bug class this app cannot afford.
 *
 * String routes rather than type-safe @Serializable routes: those need the
 * kotlinx-serialization plugin and runtime, which is another Kotlin-coupled
 * plugin and another artifact in the permission audit, to type a dozen
 * destinations that carry at most one Long each.
 */
sealed interface Dest {
    val route: String

    data object Garage : Dest {
        override val route = "garage"
    }

    data object Vehicle : Dest {
        override val route = "vehicle/{vehicleId}"
        fun of(vehicleId: Long) = "vehicle/$vehicleId"
    }

    /** Serves both add and edit - entryId 0 means a new entry. */
    data object FillUp : Dest {
        override val route = "vehicle/{vehicleId}/fillup?entryId={entryId}"
        fun add(vehicleId: Long) = "vehicle/$vehicleId/fillup?entryId=0"
        fun edit(vehicleId: Long, entryId: Long) = "vehicle/$vehicleId/fillup?entryId=$entryId"
    }

    data object Expense : Dest {
        override val route = "vehicle/{vehicleId}/expense?entryId={entryId}"
        fun add(vehicleId: Long) = "vehicle/$vehicleId/expense?entryId=0"
        fun edit(vehicleId: Long, entryId: Long) = "vehicle/$vehicleId/expense?entryId=$entryId"
    }

    data object Statistics : Dest {
        override val route = "vehicle/{vehicleId}/stats"
        fun of(vehicleId: Long) = "vehicle/$vehicleId/stats"
    }

    data object Months : Dest {
        override val route = "vehicle/{vehicleId}/months"
        fun of(vehicleId: Long) = "vehicle/$vehicleId/months"
    }

    data object MonthDetail : Dest {
        override val route = "vehicle/{vehicleId}/months/{monthKey}"
        fun of(vehicleId: Long, monthKey: Int) = "vehicle/$vehicleId/months/$monthKey"
    }

    data object VehicleEditor : Dest {
        override val route = "vehicle-editor?vehicleId={vehicleId}"
        fun add() = "vehicle-editor?vehicleId=0"
        fun edit(vehicleId: Long) = "vehicle-editor?vehicleId=$vehicleId"
    }

    data object Settings : Dest {
        override val route = "settings"
    }

    data object Privacy : Dest {
        override val route = "privacy"
    }

    data object ImportPreview : Dest {
        override val route = "import-preview"
    }

    // Onboarding.
    data object CountryCurrency : Dest {
        override val route = "onboarding/country"
    }

    data object Units : Dest {
        override val route = "onboarding/units"
    }

    data object FirstVehicle : Dest {
        override val route = "onboarding/vehicle"
    }

    companion object {
        const val ARG_VEHICLE_ID = "vehicleId"
        const val ARG_ENTRY_ID = "entryId"
        const val ARG_MONTH_KEY = "monthKey"
    }
}
