package com.fuelexpenselog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelLogTheme
import com.fuelexpenselog.app.ui.theme.FuelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FuelLogTheme {
                SkeletonScreen()
            }
        }
    }
}

/** Placeholder until the garage screen lands. Exists so chunk 0 is a running app. */
@Composable
private fun SkeletonScreen() {
    val colors = FuelTheme.colors
    val type = FuelTheme.type

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = Dimens.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Fuel Log", style = type.title, color = colors.textPrimary)
        Text(
            "MPG & CAR EXPENSES",
            style = type.eyebrow,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Dimens.labelToContent),
        )
    }
}
