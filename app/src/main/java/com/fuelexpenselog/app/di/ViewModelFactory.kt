package com.fuelexpenselog.app.di

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fuelexpenselog.app.FuelLogApp
import com.fuelexpenselog.app.ui.entry.ExpenseEditorViewModel
import com.fuelexpenselog.app.ui.entry.FillUpEditorViewModel
import com.fuelexpenselog.app.ui.garage.GarageViewModel
import com.fuelexpenselog.app.ui.history.HistoryViewModel
import com.fuelexpenselog.app.ui.months.MonthDetailViewModel
import com.fuelexpenselog.app.ui.months.MonthsViewModel
import com.fuelexpenselog.app.ui.onboarding.OnboardingViewModel
import com.fuelexpenselog.app.ui.settings.SettingsViewModel
import com.fuelexpenselog.app.ui.stats.StatisticsViewModel
import com.fuelexpenselog.app.ui.vehicle.VehicleViewModel
import com.fuelexpenselog.app.ui.vehicles.VehicleEditorViewModel

/**
 * One factory for every screen. Each ViewModel gets exactly the nodes it uses, and a
 * SavedStateHandle carrying its navigation arguments.
 */
object FuelViewModels {

    val Factory = viewModelFactory {
        initializer { GarageViewModel(container.repository, container.prefs, container.clock, container.zone) }
        initializer {
            val c = container
            VehicleViewModel(createSavedStateHandle(), c.repository, c.prefs, c.clock, c.zone)
        }
        initializer { HistoryViewModel(createSavedStateHandle(), container.repository, container.prefs) }
        initializer {
            val c = container
            StatisticsViewModel(createSavedStateHandle(), c.repository, c.prefs, c.clock, c.zone)
        }
        initializer {
            val c = container
            MonthsViewModel(createSavedStateHandle(), c.repository, c.prefs, c.clock, c.zone)
        }
        initializer {
            val c = container
            MonthDetailViewModel(createSavedStateHandle(), c.repository, c.prefs, c.clock, c.zone)
        }
        initializer { VehicleEditorViewModel(createSavedStateHandle(), container.repository, container.prefs) }
        initializer {
            val c = container
            FillUpEditorViewModel(createSavedStateHandle(), c.repository, c.prefs, c.clock, c.zone)
        }
        initializer {
            val c = container
            ExpenseEditorViewModel(createSavedStateHandle(), c.repository, c.prefs, c.clock, c.zone)
        }
        initializer { OnboardingViewModel(createSavedStateHandle(), container.repository, container.prefs) }
        initializer { SettingsViewModel(container.prefs) }
    }

    private val CreationExtras.container: AppContainer
        get() = (checkNotNull(this[APPLICATION_KEY]) as FuelLogApp).container
}
