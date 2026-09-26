package com.fuelexpenselog.csv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every byte of import and export logic is testable against a string or a byte array, with
 * no ContentResolver in sight - because Android is not on this module's classpath. This test
 * fails, and says why, if someone converts the module to an Android library.
 */
class CsvModulePurityTest {

    @Test
    fun `the android framework is not on this module's classpath`() {
        assertThat(runCatching { Class.forName("android.os.Build") }.isFailure).isTrue()
    }
}
