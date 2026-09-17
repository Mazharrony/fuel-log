pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // The JetBrains Runtime on this machine has javac but no jlink, which AGP's
    // JdkImageTransform requires. This provisions a complete JDK 17 for the
    // build. It is a settings plugin: it never reaches the APK or the manifest.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Fuel Log"

include(":app")
