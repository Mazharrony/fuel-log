package com.fuelexpenselog.app.ui.nav

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fuelexpenselog.app.FuelLogApplication
import com.fuelexpenselog.app.ui.entries.FillUpEditorViewModel

/**
 * Manual ViewModel construction. No DI framework: at this size it would cost
 * more in build configuration and audit surface than it returns.
 */
private val CreationExtras.app: FuelLogApplication
    get() = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FuelLogApplication

object VmFactory {

    fun fillUpEditor(vehicleId: Long, entryId: Long) = viewModelFactory {
        initializer {
            FillUpEditorViewModel(
                handle = createSavedStateHandle(),
                repo = app.container.repository,
                settings = app.container.settings,
                vehicleId = vehicleId,
                entryId = entryId,
            )
        }
    }
}
