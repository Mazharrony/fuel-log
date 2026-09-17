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
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.fuelexpenselog.app.ui.gallery.GalleryScreen
import com.fuelexpenselog.app.ui.motion.LocalMotionScale
import com.fuelexpenselog.app.ui.motion.rememberSystemMotionScale
import com.fuelexpenselog.app.ui.theme.FuelLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Mandatory at targetSdk 35+; the system will not letterbox us out of it.
        enableEdgeToEdge()

        setContent {
            FuelLogTheme {
                CompositionLocalProvider(LocalMotionScale provides rememberSystemMotionScale()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        // Insets are handled per screen, so the action bar can sit
                        // flush against the bottom edge while still clearing the
                        // keyboard and the navigation bar.
                        contentWindowInsets = WindowInsets(0),
                    ) { padding ->
                        GalleryScreen(Modifier.padding(padding))
                    }
                }
            }
        }
    }
}
