package com.fuelexpenselog.app.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fuelexpenselog.app.ui.entry.ExpenseEditorRoute
import com.fuelexpenselog.app.ui.entry.FillUpEditorRoute
import com.fuelexpenselog.app.ui.garage.GarageRoute
import com.fuelexpenselog.app.ui.vehicles.VehicleEditorRoute

/**
 * A 220ms fade-through: the outgoing screen fades out over the first third, the incoming one
 * fades in over the rest. No slide, no shared elements. Back is the same curve.
 */
private const val OUT_MS = 77
private const val IN_MS = 143

private val fadeThroughIn: EnterTransition = fadeIn(tween(IN_MS, delayMillis = OUT_MS))
private val fadeThroughOut: ExitTransition = fadeOut(tween(OUT_MS))

private val idArg: List<NamedNavArgument> = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType })
private val vehicleArg: List<NamedNavArgument> = listOf(
    navArgument(Routes.ARG_VEHICLE) {
        type = NavType.LongType
        defaultValue = 0L
    },
)

@Composable
fun FuelNavHost(navController: NavHostController = rememberNavController()) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(
        navController = navController,
        startDestination = Routes.GARAGE,
        enterTransition = { fadeThroughIn },
        exitTransition = { fadeThroughOut },
        popEnterTransition = { fadeThroughIn },
        popExitTransition = { fadeThroughOut },
    ) {
        composable(Routes.GARAGE) {
            GarageRoute(
                onAddVehicle = { navController.navigate(Routes.VEHICLE_NEW) },
                onOpenVehicle = { id -> navController.navigate(Routes.vehicleEdit(id)) },
                onAddFillUp = { navController.navigate(Routes.fillUpNew()) },
                onAddExpense = { navController.navigate(Routes.expenseNew()) },
            )
        }
        composable(Routes.VEHICLE_NEW) { VehicleEditorRoute(onDone = back) }
        composable(Routes.VEHICLE_EDIT, arguments = idArg) { VehicleEditorRoute(onDone = back) }
        composable(Routes.FILLUP_NEW, arguments = vehicleArg) { FillUpEditorRoute(onDone = back) }
        composable(Routes.FILLUP_EDIT, arguments = idArg) { FillUpEditorRoute(onDone = back) }
        composable(Routes.EXPENSE_NEW, arguments = vehicleArg) { ExpenseEditorRoute(onDone = back) }
        composable(Routes.EXPENSE_EDIT, arguments = idArg) { ExpenseEditorRoute(onDone = back) }
    }
}
