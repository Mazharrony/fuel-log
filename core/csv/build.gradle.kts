plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure JVM for the same reason as :core:domain - every byte of import and export logic is
// testable against a ByteArrayInputStream with no ContentResolver in sight. The Android
// layer supplies a stream and nothing else.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:domain"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging { events("passed", "skipped", "failed") }
}
