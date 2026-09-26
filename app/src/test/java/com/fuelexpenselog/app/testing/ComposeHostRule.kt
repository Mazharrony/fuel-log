package com.fuelexpenselog.app.testing

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf

/**
 * Registers the empty activity `createComposeRule()` launches.
 *
 * Robolectric reads the manifest compiled into the local-test resource APK, which is the
 * app's own merged manifest - test dependencies' manifests never reach it, so
 * ui-test-manifest's activity declaration is invisible. The usual fix is
 * `debugImplementation(ui-test-manifest)`, which would ship an exported activity in every
 * debug build; registering it here keeps the debug manifest exactly what it was.
 *
 * Must run before the compose rule: `@get:Rule(order = 0)`.
 */
class ComposeHostRule : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val info = ActivityInfo().apply {
            name = ComponentActivity::class.java.name
            packageName = app.packageName
            exported = true
        }
        shadowOf(app.packageManager).addOrUpdateActivity(info)
        check(app.packageManager.getActivityInfo(ComponentName(app, ComponentActivity::class.java), 0) != null)
    }
}
