package com.fuelexpenselog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.format.rememberFormatters
import com.fuelexpenselog.app.ui.nav.FuelNavHost
import com.fuelexpenselog.app.ui.theme.FuelLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest launches with Theme.FuelLog.Starting so the process-start window is the
        // splash. Swapping before super.onCreate means the activity's own window is paper from
        // its first frame, and the splash leaves the moment Garage draws.
        setTheme(R.style.Theme_FuelLog)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // One small SharedPreferences read, decided before the first frame so a fresh install
        // never flashes Garage on its way to onboarding.
        val onboarded = (application as FuelLogApp).container.prefs.onboardingDone
        setContent {
            FuelLogTheme {
                CompositionLocalProvider(LocalFormatters provides rememberFormatters()) {
                    FuelNavHost(onboarded)
                }
            }
        }
    }
}
