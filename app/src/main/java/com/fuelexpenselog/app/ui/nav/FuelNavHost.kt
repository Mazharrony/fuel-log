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
import com.fuelexpenselog.app.ui.export.ExportRoute
import com.fuelexpenselog.app.ui.garage.GarageRoute
import com.fuelexpenselog.app.ui.history.HistoryRoute
import com.fuelexpenselog.app.ui.importflow.ImportRoute
import com.fuelexpenselog.app.ui.months.MonthDetailRoute
import com.fuelexpenselog.app.ui.months.MonthsRoute
import com.fuelexpenselog.app.ui.onboarding.OnboardingRoute
import com.fuelexpenselog.app.ui.reminders.ReminderDoneRoute
import com.fuelexpenselog.app.ui.reminders.ReminderEditorRoute
import com.fuelexpenselog.app.ui.reminders.RemindersRoute
import com.fuelexpenselog.app.ui.settings.CollectsScreen
import com.fuelexpenselog.app.ui.settings.SettingsRoute
import com.fuelexpenselog.app.ui.stats.StatisticsRoute
import com.fuelexpenselog.app.ui.vehicle.VehicleNavigation
import com.fuelexpenselog.app.ui.vehicle.VehicleRoute
import com.fuelexpenselog.app.ui.vehicles.VehicleEditorRoute
import com.fuelexpenselog.domain.model.HistoryEntry

/**
 * A 220ms fade-through: the outgoing screen fades out over the first third, the incoming one
 * fades in over the rest. No slide, no shared elements. Back is the same curve.
 */
private const val OUT_MS = 77
private const val IN_MS = 143

private val fadeThroughIn: EnterTransition = fadeIn(tween(IN_MS, delayMillis = OUT_MS))
private val fadeThroughOut: ExitTransition = fadeOut(tween(OUT_MS))

private val idArg: NamedNavArgument = navArgument(Routes.ARG_ID) { type = NavType.LongType }
private val vehicleArg: NamedNavArgument = navArgument(Routes.ARG_VEHICLE) {
    type = NavType.LongType
    defaultValue = 0L
}
private val monthArg: NamedNavArgument = navArgument(Routes.ARG_MONTH) {
    type = NavType.IntType
    defaultValue = 0
}
private val yearArg: NamedNavArgument = navArgument(Routes.ARG_YEAR) {
    type = NavType.IntType
    defaultValue = 0
}

/**
 * [onboarded] is read once at launch: a fresh install starts in onboarding, which replaces
 * itself with Garage when it finishes, so back from Garage leaves the app rather than
 * re-opening the first-run flow.
 */
@Composable
fun FuelNavHost(onboarded: Boolean, navController: NavHostController = rememberNavController()) {
    val back: () -> Unit = { navController.popBackStack() }
    val openEntry: (HistoryEntry) -> Unit = { entry ->
        when (entry) {
            is HistoryEntry.Fuel -> navController.navigate(Routes.fillUpEdit(entry.fillUp.id))
            is HistoryEntry.Cost -> navController.navigate(Routes.expenseEdit(entry.expense.id))
        }
    }
    NavHost(
        navController = navController,
        startDestination = if (onboarded) Routes.GARAGE else Routes.ONBOARDING,
        enterTransition = { fadeThroughIn },
        exitTransition = { fadeThroughOut },
        popEnterTransition = { fadeThroughIn },
        popExitTransition = { fadeThroughOut },
    ) {
        composable(Routes.ONBOARDING) {
            val toGarage = {
                navController.navigate(Routes.GARAGE) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            }
            OnboardingRoute(
                onDone = toGarage,
                // Garage first, so back from the import lands at home rather than in first run.
                onImport = { uri, vehicleId ->
                    toGarage()
                    navController.navigate(Routes.import(uri, vehicleId))
                },
            )
        }
        composable(Routes.GARAGE) {
            GarageRoute(
                onAddVehicle = { navController.navigate(Routes.VEHICLE_NEW) },
                onOpenVehicle = { id -> navController.navigate(Routes.vehicle(id)) },
                onAddFillUp = { navController.navigate(Routes.fillUpNew()) },
                onAddExpense = { navController.navigate(Routes.expenseNew()) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = back,
                onCollects = { navController.navigate(Routes.COLLECTS) },
                onExport = { navController.navigate(Routes.export()) },
                onImport = { uri -> navController.navigate(Routes.import(uri)) },
            )
        }
        composable(Routes.COLLECTS) { CollectsScreen(onBack = back) }
        composable(
            Routes.IMPORT,
            arguments = listOf(navArgument(Routes.ARG_URI) { type = NavType.StringType }, vehicleArg),
        ) { ImportRoute(onDone = back) }
        composable(Routes.EXPORT, arguments = listOf(vehicleArg, monthArg)) { ExportRoute(onBack = back) }

        composable(Routes.VEHICLE_NEW) { VehicleEditorRoute(onDone = back) }
        composable(Routes.VEHICLE, arguments = listOf(idArg)) {
            VehicleRoute(
                VehicleNavigation(
                    onBack = back,
                    onEdit = { navController.navigate(Routes.vehicleEdit(it)) },
                    onSwitch = { id ->
                        navController.navigate(Routes.vehicle(id)) {
                            popUpTo(Routes.VEHICLE) { inclusive = true }
                        }
                    },
                    onStatistics = { navController.navigate(Routes.statistics(it)) },
                    onMonths = { navController.navigate(Routes.months(it)) },
                    onHistory = { navController.navigate(Routes.history(it)) },
                    onOpenEntry = openEntry,
                    onOpenFillUp = { navController.navigate(Routes.fillUpEdit(it)) },
                    onAddFillUp = { navController.navigate(Routes.fillUpNew(it)) },
                    onAddExpense = { navController.navigate(Routes.expenseNew(it)) },
                    onReminders = { navController.navigate(Routes.reminders(it)) },
                    onOpenReminder = { navController.navigate(Routes.reminderEdit(it)) },
                    onReminderDone = { navController.navigate(Routes.reminderDone(it)) },
                ),
            )
        }
        composable(Routes.REMINDERS, arguments = listOf(idArg)) {
            RemindersRoute(
                onBack = back,
                onAdd = { navController.navigate(Routes.reminderNew(it)) },
                onOpen = { navController.navigate(Routes.reminderEdit(it)) },
                onDone = { navController.navigate(Routes.reminderDone(it)) },
            )
        }
        composable(Routes.REMINDER_NEW, arguments = listOf(vehicleArg)) { ReminderEditorRoute(onDone = back) }
        composable(Routes.REMINDER_EDIT, arguments = listOf(idArg)) { ReminderEditorRoute(onDone = back) }
        composable(Routes.REMINDER_DONE, arguments = listOf(idArg)) { ReminderDoneRoute(onDone = back) }
        composable(Routes.VEHICLE_EDIT, arguments = listOf(idArg)) { VehicleEditorRoute(onDone = back) }
        composable(Routes.HISTORY, arguments = listOf(idArg, monthArg)) {
            HistoryRoute(onBack = back, onOpenEntry = openEntry)
        }
        composable(Routes.STATISTICS, arguments = listOf(idArg)) { StatisticsRoute(onBack = back) }
        composable(Routes.MONTHS, arguments = listOf(idArg, yearArg)) { entry ->
            val vehicleId = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L
            MonthsRoute(
                onBack = back,
                onOpenMonth = { month -> navController.navigate(Routes.monthDetail(vehicleId, month.value)) },
            )
        }
        composable(Routes.MONTH_DETAIL, arguments = listOf(idArg, navArgument(Routes.ARG_MONTH) { type = NavType.IntType })) { entry ->
            val vehicleId = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L
            MonthDetailRoute(
                onBack = back,
                // Stepping between months replaces this one, so back returns to the list.
                onMonth = { month ->
                    navController.navigate(Routes.monthDetail(vehicleId, month.value)) {
                        popUpTo(Routes.MONTH_DETAIL) { inclusive = true }
                    }
                },
                onSeeEntries = { month -> navController.navigate(Routes.history(vehicleId, month.value)) },
                onExport = { month -> navController.navigate(Routes.export(vehicleId, month.value)) },
            )
        }

        composable(Routes.FILLUP_NEW, arguments = listOf(vehicleArg)) { FillUpEditorRoute(onDone = back) }
        composable(Routes.FILLUP_EDIT, arguments = listOf(idArg)) { FillUpEditorRoute(onDone = back) }
        composable(Routes.EXPENSE_NEW, arguments = listOf(vehicleArg)) { ExpenseEditorRoute(onDone = back) }
        composable(Routes.EXPENSE_EDIT, arguments = listOf(idArg)) { ExpenseEditorRoute(onDone = back) }
    }
}
