package com.fuelexpenselog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.domain.model.DistanceUnit
import com.fuelexpenselog.app.domain.model.Vehicle
import com.fuelexpenselog.app.domain.model.VolumeUnit
import com.fuelexpenselog.app.domain.units.ConsumptionConvention
import com.fuelexpenselog.app.ui.entries.FillUpEditorScreen
import com.fuelexpenselog.app.ui.entries.FillUpEditorViewModel
import com.fuelexpenselog.app.ui.motion.LocalMotionScale
import com.fuelexpenselog.app.ui.motion.rememberSystemMotionScale
import com.fuelexpenselog.app.ui.nav.VmFactory
import com.fuelexpenselog.app.ui.theme.FuelLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Mandatory at targetSdk 35+; the system will not letterbox us out of it.
        enableEdgeToEdge()

        val container = (application as FuelLogApplication).container

        setContent {
            FuelLogTheme {
                CompositionLocalProvider(LocalMotionScale provides rememberSystemMotionScale()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        // Insets are handled per screen so the action bar can sit
                        // flush against the bottom edge while still clearing the
                        // keyboard and the navigation bar.
                        contentWindowInsets = WindowInsets(0),
                    ) { padding ->
                        // TEMPORARY host for the critical path. The real
                        // NavHost arrives with the Garage screen in chunk 7.
                        var vehicleId by remember { mutableStateOf(0L) }

                        LaunchedEffect(Unit) {
                            val existing = container.repository.allVehicles().firstOrNull()
                            vehicleId = existing?.id ?: container.repository.addVehicle(
                                Vehicle(
                                    name = "Honda Civic",
                                    distanceUnit = DistanceUnit.MILE,
                                    volumeUnit = VolumeUnit.US_GALLON,
                                    currency = "USD",
                                    tankCapacityLitres = 47.0,
                                    createdAt = System.currentTimeMillis(),
                                )
                            )
                        }

                        if (vehicleId != 0L) {
                            val vm: FillUpEditorViewModel = viewModel(
                                key = "fillup-$vehicleId",
                                factory = VmFactory.fillUpEditor(vehicleId, 0L),
                            )
                            FillUpEditorScreen(
                                viewModel = vm,
                                convention = ConsumptionConvention.MPG_US,
                                onDone = { vm.clear() },
                                onBack = { },
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
                }
            }
        }
    }
}
