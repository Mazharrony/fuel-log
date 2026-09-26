package com.fuelexpenselog.app

import android.app.Application
import android.os.StrictMode
import com.fuelexpenselog.app.di.AppContainer

class FuelLogApp : Application() {

    /** Lazy, so a cold start pays for nothing until the first screen asks. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()

        // A runtime tripwire for the offline guarantee. The manifest ratchet and the
        // build-time permission check cover what ships; this one makes an accidental
        // network call during development fail loudly and immediately instead of quietly
        // working on the developer's machine. Costs nothing in release.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyDeath()
                    .build(),
            )
        }
    }
}
