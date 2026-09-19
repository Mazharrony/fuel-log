package com.fuelexpenselog.app

import android.app.Application
import android.os.StrictMode

class FuelLogApp : Application() {

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
