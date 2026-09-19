package com.fuelexpenselog.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The consumption engine lives in this module, and it is the code where a wrong answer looks
 * right. Keeping the module free of Android means its tests run in milliseconds with no
 * Robolectric, and that `import android.*` is a compile error rather than a review comment.
 *
 * The Gradle setup already guarantees this by declaring a `kotlin("jvm")` module. This test
 * exists so that if someone later converts it to an Android library "just to get one thing",
 * a test fails and says why, instead of the property quietly evaporating.
 */
class ModulePurityTest {

    @Test
    fun `the android framework is not on this module's classpath`() {
        val loaded = runCatching { Class.forName("android.os.Build") }

        assertThat(loaded.isFailure).isTrue()
    }

    @Test
    fun `java time is available without desugaring`() {
        // minSdk 26 was chosen so java.time is native. The domain layer assumes it freely.
        val date = java.time.LocalDate.of(2026, 9, 19)

        assertThat(date.monthValue).isEqualTo(9)
    }
}
