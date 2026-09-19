plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Deliberately a pure-JVM module, not an Android library. The consumption engine is the
// riskiest code in this app; making the module pure means `import android.*` is a COMPILE
// ERROR rather than something a reviewer has to notice, and the tests run in milliseconds
// with no Robolectric and no emulator.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging { events("passed", "skipped", "failed") }
}
