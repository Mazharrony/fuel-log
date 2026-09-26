package com.fuelexpenselog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        val container = (application as FuelLogApp).container
        // A force stop clears the app's alarms and nothing else would bring them back until a
        // reboot. Opening the app puts the daily check back, keeping a check still to come.
        if (savedInstanceState == null) container.reminderNotifications.rearm()
        setContent {
            FuelLogTheme {
                CompositionLocalProvider(LocalFormatters provides rememberFormatters()) {
                    val epoch by container.restoreEpoch.collectAsStateWithLifecycle()
                    // A restore replaces the database underneath everything: a new epoch builds
                    // a new graph, so no screen keeps showing - or querying - the old one.
                    key(epoch) {
                        // One small SharedPreferences read, decided before the first frame so a
                        // fresh install never flashes Garage on its way to onboarding.
                        val onboarded = remember { container.prefs.onboardingDone }
                        FuelNavHost(onboarded)
                    }
                }
            }
        }
    }
}
